package dev.juiced.gui.weltprobe;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Config;
import dev.juiced.core.Hud;
import dev.juiced.core.MenuState;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.gui.CameraKeeper;
import dev.juiced.gui.ColorPickerScreen;
import dev.juiced.gui.HudEditorScreen;
import dev.juiced.gui.SettingsList;
import dev.juiced.gui.WardrobeScreen;
import dev.juiced.render.Fx;

/**
 * The Weltprobe menu (Right Shift): a remote control for your world. The mouse wheel zaps from module to module and
 * each one runs live around you as a try-out; Space keeps it on. The dial at the bottom shows every module as a
 * lamp, the card bottom left the tuned one, typing searches and Tab opens its settings.
 *
 * <p>The screen owns three things while it is open and gives them back on every way out: the try-out
 * ({@link ProbeController}), the camera ({@link MenuCamera}) and the hidden HUD ({@link MenuState}).
 */
public class WeltprobeScreen extends Screen implements CameraKeeper {
	/** Right Shift opens the menu and is also a Shift key: ignore Shift this long after opening. */
	private static final double SHIFT_GRACE = 0.3;
	/** Key repeats of the held menu key come within this time of each other (and of the opening). */
	private static final double REPEAT_WINDOW = 0.7;
	private static final String FIRST_CHANNEL = "ambient";

	/** The tuned channel, remembered across openings in this session (-1 before the first opening). */
	private static int lastChannel = -1;
	/** "Nur an": off lamps dim and zapping skips them. Kept for the session. */
	private static boolean onlyOn;

	private final MenuCamera camera = new MenuCamera();
	private final ProbeController probe = new ProbeController(camera);
	private final List<Channel> channels = Channels.all();
	private final Hits hits = new Hits();
	private final Search search = new Search();
	private final Dial dial = new Dial(this, channels);
	private final ChannelCard card = new ChannelCard(this);
	private final HudStage hudStage = new HudStage();
	private final TopBar topBar = new TopBar(this);
	private final StageNotes notes = new StageNotes();
	private final SettingsList settingsList;
	private final Drawer drawer;
	private final Toggles toggles;

	private Layout layout = new Layout(854, 480);
	private int tuned;
	private int previous = -1;
	private boolean opened;
	private double openedAt;
	/** True while the screen holds the try-out, the camera and the hidden HUD. */
	private boolean active;
	/** A child screen we opened takes over; don't clean up when this screen is removed for it. */
	private boolean keepCamera;
	private boolean menuKeyHeld = true;
	private double menuKeyAt;
	private boolean stageDrag;
	private double wheel;
	private int mouseX;
	private int mouseY;
	private @Nullable String emptyCaption;
	private double emptyCaptionAt = -10;

	public WeltprobeScreen() {
		super(Component.literal("Juiced"));
		settingsList = new SettingsList((x1, y1, x2, y2, action) -> hits.add(x1, y1, x2, y2, () -> {
			action.run();
			settingsChanged();
		}), this::openColorPicker);
		drawer = new Drawer(this, settingsList);
		toggles = new Toggles(this, probe, dial, topBar, notes, card);
	}

	// ---- lifecycle ---------------------------------------------------------------------------

	@Override
	protected void init() {
		super.init();
		// Minecraft only sends typed characters to screens that ask for them
		this.minecraft.textInputManager().startTextInput(this);
		layout = new Layout(this.width, this.height);

		if (channels.isEmpty()) {
			return;
		}

		if (!opened) {
			opened = true;
			openedAt = Clock.t();
			menuKeyAt = openedAt;
			Theme.countMenuOpen();

			if (Theme.menuOpens() <= 3) {
				notes.startCoach();
			}

			tuned = startChannel();
			lastChannel = tuned;
		}

		MenuState.setHudHidden(true);
		camera.open();
		active = true;

		// Back from the wardrobe or the HUD editor: the try-out was stopped for them, start it again
		if (probe.channel() == null) {
			SearchHit hit = search.current();
			int focus = hit != null && hit.channel().index == tuned ? hit.focus() : -1;
			probe.tune(channels.get(tuned), focus, true);
		}

		drawer.show(channels.get(tuned).module);
	}

	private int startChannel() {
		if (lastChannel >= 0 && lastChannel < channels.size()) {
			return lastChannel;
		}

		for (Channel channel : channels) {
			if (channel.module.id.equals(FIRST_CHANNEL)) {
				return channel.index;
			}
		}

		return 0;
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the world running so the try-outs, the dummy and the particles move
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		probe.tick();
	}

	/** Gives back the try-out, the camera and the HUD. Safe to call more than once. */
	private void cleanup() {
		if (!active) {
			return;
		}

		active = false;
		probe.stop();
		camera.close();
		MenuState.setHudHidden(false);
		Config.save();
	}

	@Override
	public void onClose() {
		cleanup();
		super.onClose();
	}

	@Override
	public void removed() {
		super.removed();
		this.minecraft.textInputManager().stopTextInput(this);

		if (keepCamera) {
			keepCamera = false;
			return;
		}

		// The game closed us (disconnect, death): give everything back now
		cleanup();
	}

	@Override
	public void releaseCamera() {
		// The colour picker closed without coming back here
		cleanup();
	}

	void openWardrobe() {
		if (this.minecraft.player == null) {
			return;
		}

		Fx.sound("ui.button.click", 0.3f, 1.2f);
		probe.stop();
		// The wardrobe takes the camera over where it is, without the glide back
		camera.release();
		MenuState.setHudHidden(false);
		active = false;
		keepCamera = true;
		this.minecraft.gui.setScreen(new WardrobeScreen(this));
	}

	void openHudEditor() {
		Fx.sound("ui.button.click", 0.3f, 1.2f);
		Channel channel = channels.get(tuned);
		probe.stop();
		camera.close();
		MenuState.setHudHidden(false);
		active = false;
		keepCamera = true;
		this.minecraft.gui.setScreen(new HudEditorScreen(this, channel.hudModule()));
	}

	private void openColorPicker(ColorSetting color) {
		// The try-out and the hidden HUD stay, so the colour changes live behind the picker
		keepCamera = true;
		this.minecraft.gui.setScreen(new ColorPickerScreen(this, color));
	}

	// ---- drawing -----------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// No blur and no darkening: the world is the stage. Only a little shade behind the top row and the dial.
		g.fillGradient(0, 0, this.width, 40, Colors.argb(0.45f, 0x05050A), 0x0005050A);
		g.fillGradient(0, this.height - 100, this.width, this.height, 0x0005050A, Colors.argb(0.65f, 0x05050A));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Clock.frame();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		hits.clear();
		Ui.alpha = 1f;

		if (channels.isEmpty()) {
			return;
		}

		layout = new Layout(this.width, this.height);
		Layout l = layout;
		camera.frame();
		probe.frame(partialTick);

		Channel channel = channels.get(tuned);
		card.show(channel, cardHit(channel));
		drawer.show(channel.module);

		// The tuned channel's own screen part first, under all the chrome
		// (not on a server that blocks it: then only the 2D preview says so)
		hudStage.draw(g, channel.module.blockedHere() ? null : channel, new Hud(this.minecraft, this.width, this.height, partialTick), l.margin);

		if (drawer.isOpen() && !hudStage.dragging()) {
			boolean left = dockLeft(channel);

			if (left != drawer.dockedLeft()) {
				drawer.open(left);
			}
		}

		int stageLeft = l.margin;
		int stageRight = l.width - l.margin;

		if (drawer.isOpen()) {
			if (drawer.dockedLeft()) {
				stageLeft = l.drawerX(true) + l.drawerW + 8;
			} else {
				stageRight = l.drawerX(false) - 8;
			}
		}

		boolean cardShown = !(drawer.isOpen() && Ui.overlaps(l.drawerX(drawer.dockedLeft()), l.drawerY1(drawer.dockedLeft()),
				l.drawerX(drawer.dockedLeft()) + l.drawerW, l.drawerY2, l.cardX, l.cardY, l.cardX + l.cardW, l.cardY + l.cardH));

		String reason = probe.fallbackReason();

		if (reason == null && hudStage.empty()) {
			reason = emptyCaption(channel);
		}

		if (reason != null) {
			notes.drawFallback(g, l, channel.module, reason, stageLeft, stageRight, cardShown);
		}

		notes.drawCoach(g, l);
		// The chrome draws above everything on the stage, text included
		g.nextStratum();

		// Opening: the card slides in from the left, the dial rises from below
		float cardIn = openPhase(0.1, 0.2);
		float dialIn = openPhase(0.06, 0.2);
		float topIn = openPhase(0.0, 0.2);

		if (cardShown) {
			float fade = hudStage.covers(l.cardX, l.cardY, l.cardX + l.cardW, l.cardY + l.cardH) ? 0.3f : 1f;
			card.draw(g, l, hits, mouseX, mouseY, cardIn * fade, -Math.round(20f * (1f - cardIn)), search.active());
		}

		float drawerFade = hudStage.covers(l.drawerX(drawer.dockedLeft()), l.drawerY1(drawer.dockedLeft()),
				l.drawerX(drawer.dockedLeft()) + l.drawerW, l.drawerY2) ? 0.3f : 1f;
		drawer.draw(g, l, hits, mouseX, mouseY, drawerFade);

		float dialFade = hudStage.covers(l.dialX1, l.dialY1, l.dialX2, l.dialY2) ? 0.3f : 1f;
		g.pose().pushMatrix();
		g.pose().translate(0f, (1f - dialIn) * 20f);
		Ui.alpha = dialIn * dialFade;
		dial.draw(g, l, hits, mouseX, mouseY, tuned, onlyOn, search);
		Ui.alpha = 1f;
		g.pose().popMatrix();
		// The bubble covers the card and the drawer
		g.nextStratum();
		dial.drawBubble(g, l);

		Ui.alpha = topIn;
		topBar.drawButtons(g, l, hits, mouseX, mouseY, channel, hudStage, 1f);
		drawStatus(g, l, channel);
		Ui.alpha = 1f;
		topBar.drawToast(g, l, stageLeft, stageRight);

		int[] box = hudStage.box();
		boolean overElement = box != null && !hits.pointerAt(mouseX, mouseY) && Ui.inside(mouseX, mouseY, box[0] - 2, box[1] - 2, box[0] + box[2] + 2, box[1] + box[3] + 2);

		if (!settingsList.isDragging() && (hudStage.dragging() || overElement || hits.pointerAt(mouseX, mouseY))) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	/** The bug top right, or the search pill (which takes the bug's place when the top row is too narrow). */
	private void drawStatus(GuiGraphicsExtractor g, Layout l, Channel channel) {
		int bugRight = l.width - l.margin;

		if (drawer.visible() && !drawer.dockedLeft()) {
			bugRight = Math.min(bugRight, drawer.leftEdge() - 8);
		}

		ProbeStatus status = probe.status();
		boolean showBug = true;

		if (search.active()) {
			int left = l.buttonsRight() + 8;
			int right = bugRight - TopBar.bugWidth(status) - 8;

			if (right - left < TopBar.PILL_MIN) {
				showBug = false;
				right = bugRight;
			}

			topBar.drawPill(g, l, hits, search, left, right);
		}

		if (showBug) {
			topBar.drawBug(g, l, status, channel.category(), bugRight);
		}
	}

	/** The search hit the card names, while the try-out shows it. */
	private @Nullable SearchHit cardHit(Channel channel) {
		SearchHit hit = search.active() ? search.current() : null;

		if (hit == null || hit.channel() != channel || probe.channel() != channel || probe.focus() != hit.focus()) {
			return null;
		}

		return hit;
	}

	/** Caption when a HUD element has nothing to show: the module's own reason if it has one. */
	private String emptyCaption(Channel channel) {
		double now = Clock.t();

		if (emptyCaption == null || now - emptyCaptionAt > 1.0) {
			String reason = null;

			try {
				reason = channel.module.probeLiveReason(this.minecraft);
			} catch (RuntimeException e) {
				// Leave the general caption
			}

			emptyCaption = reason != null ? reason : "Gerade nichts zu zeigen";
			emptyCaptionAt = now;
		}

		return emptyCaption;
	}

	private float openPhase(double delay, double duration) {
		return Clock.easeOut((float) ((Clock.t() - openedAt - delay) / duration));
	}

	/** HUD elements in the right half get the drawer on the left, so it doesn't cover them. */
	private boolean dockLeft(Channel channel) {
		return channel.hudModule() != null && hudStage.rightHalf(this.width);
	}

	// ---- actions (also called by the chrome) -------------------------------------------------

	/** Tunes a channel; {@code focus} is a part or entry from a search hit, else -1. */
	private void tune(int index, int focus, boolean deliberate) {
		if (index < 0 || index >= channels.size()) {
			return;
		}

		if (index != tuned) {
			previous = tuned;
			tuned = index;
			lastChannel = index;
			emptyCaption = null;
		}

		Channel channel = channels.get(index);
		probe.tune(channel, focus, deliberate);
		drawer.show(channel.module);
	}

	/** Wheel or Left/Right: the next channel that way (only switched-on ones with "Nur an"); stops at the ends. */
	private void zap(int direction) {
		int next = tuned + direction;

		while (onlyOn && next >= 0 && next < channels.size() && !channels.get(next).module.isSwitchedOn()) {
			next += direction;
		}

		if (next < 0 || next >= channels.size()) {
			dial.bump(direction);
			return;
		}

		tune(next, -1, false);
		Fx.sound("ui.button.click", 0.15f, 1.6f);
	}

	void jump(int index) {
		if (index != tuned || probe.focus() >= 0) {
			tune(index, -1, true);
			Fx.sound("ui.button.click", 0.25f, 1.2f);
		}
	}

	/** Click on a lamp: tune it; Shift+click toggles it in place; right-click tunes it and opens the drawer. */
	void lampClicked(int index, int button, boolean shift) {
		if (button == 1) {
			jump(index);
			openDrawer();
		} else if (button == 0 && shift) {
			toggles.toggle(channels.get(index));
		} else if (button == 0) {
			jump(index);
		}
	}

	/** Category label or 1-6: the first channel of that category. */
	void jumpTo(Category category) {
		for (Channel channel : channels) {
			if (channel.category() == category) {
				jump(channel.index);
				return;
			}
		}
	}

	void toggleOnlyOn() {
		onlyOn = !onlyOn;
		Fx.sound("ui.button.click", 0.25f, onlyOn ? 1.3f : 0.9f);
	}

	void toggleTuned() {
		toggles.toggle(channels.get(tuned));
	}

	void toggleDrawer() {
		if (drawer.isOpen()) {
			drawer.close();
		} else {
			openDrawer();
		}
	}

	void openDrawer() {
		drawer.show(channels.get(tuned).module);
		drawer.open(dockLeft(channels.get(tuned)));
		notes.used();
	}

	/** Up/Down, Shift+wheel or the card arrows: the tuned channel's next or previous style, saved as its choice. */
	void changeStyle(int direction) {
		ChoiceSetting style = channels.get(tuned).mainStyle();

		if (style == null) {
			return;
		}

		if (direction > 0) {
			style.next();
		} else {
			style.previous();
		}

		probe.restyled();
		card.styleChanged();
		Fx.sound("ui.button.click", 0.2f, 1.3f);
	}

	/** Anything in the drawer changed: the try-out shows it in the same frame. */
	void settingsChanged() {
		probe.restyled();
	}

	/** Enter (or Space after Enter) in a search: toggles the selected hit and marks the query as done. */
	private void toggleHit() {
		SearchHit hit = search.current();

		if (hit == null) {
			Toggles.deny();
			return;
		}

		search.finish();
		toggles.toggleHit(hit);
	}

	int tuned() {
		return tuned;
	}

	/** A deliberate jump to a channel, showing only one part or entry when {@code focus} is not -1. */
	void tuneTo(int index, int focus) {
		tune(index, focus, true);
	}

	// ---- search ------------------------------------------------------------------------------

	private void typed(String text) {
		search.type(text);
		notes.used();
		tuneBestHit();
	}

	/** The best (or selected) hit is tuned at once, as a deliberate jump. With no hit the selection stays. */
	private void tuneBestHit() {
		SearchHit hit = search.current();

		if (hit != null && (hit.channel().index != tuned || probe.focus() != hit.focus())) {
			tune(hit.channel().index, hit.focus(), true);
		}
	}

	/** The query is gone: a try-out that showed only one part or entry shows the whole channel again. */
	private void searchEnded() {
		if (probe.focus() >= 0) {
			tune(tuned, -1, true);
		}
	}

	private void stepHit(int direction) {
		if (search.step(direction)) {
			tuneBestHit();
			Fx.sound("ui.button.click", 0.15f, 1.6f);
		} else {
			dial.bump(direction);
		}
	}

	// ---- input -------------------------------------------------------------------------------

	/** Shift, but not the Right Shift that opened the menu and may still be held. */
	private boolean shiftHeld(boolean down) {
		double now = Clock.t();
		return down && now - openedAt > SHIFT_GRACE && !(menuKeyHeld && now - menuKeyAt < REPEAT_WINDOW);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();
		int button = event.button();
		boolean shift = shiftHeld(event.hasShiftDown());

		// Buttons and lamps first, then the outlined HUD element, then panels, then the stage
		if (hits.pointerAt(x, y)) {
			hits.click(x, y, button, shift);
			return true;
		}

		if (button == 0 && hudStage.grab(channels.isEmpty() ? null : channels.get(tuned), x, y)) {
			return true;
		}

		if (hits.click(x, y, button, shift)) {
			return true;
		}

		if (button == 0) {
			if (doubleClick) {
				camera.resetDrag();
			}

			stageDrag = true;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (settingsList.isDragging()) {
			settingsList.drag(event.x());
			settingsChanged();
			return true;
		}

		if (hudStage.dragging()) {
			hudStage.drag(event.x(), event.y(), this.width, this.height, shiftHeld(this.minecraft.hasShiftDown()));
			return true;
		}

		if (stageDrag) {
			camera.drag(dragX, dragY);
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (settingsList.isDragging()) {
			settingsList.release();
			return true;
		}

		if (hudStage.release()) {
			return true;
		}

		if (stageDrag) {
			stageDrag = false;
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (drawer.contains(x, y)) {
			drawer.wheel(scrollY);
			return true;
		}

		if (channels.isEmpty()) {
			return true;
		}

		boolean shift = shiftHeld(this.minecraft.hasShiftDown());
		// Some systems turn Shift+wheel into a sideways scroll
		double amount = scrollY == 0 && shift ? scrollX : scrollY;
		// Trackpads send small steps: collect them until they make a whole notch
		wheel += amount;
		int steps = (int) wheel;

		if (steps == 0) {
			return true;
		}

		wheel -= steps;
		int direction = steps > 0 ? -1 : 1;

		for (int i = 0; i < Math.abs(steps); i++) {
			if (shift) {
				changeStyle(direction);
			} else if (search.active() && !search.hits().isEmpty()) {
				stepHit(direction);
			} else {
				zap(direction);
			}
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (JuicedClient.MENU_KEY != null && JuicedClient.MENU_KEY.matches(event)) {
			// The held key that opened the menu repeats; only a new press closes
			double now = Clock.t();
			boolean repeat = menuKeyHeld && now - menuKeyAt < REPEAT_WINDOW;
			menuKeyAt = now;

			if (!repeat) {
				onClose();
			}

			return true;
		}

		if (channels.isEmpty()) {
			return super.keyPressed(event);
		}

		if (event.hasControlDown()) {
			int key = event.key();

			if (key == InputConstants.KEY_Z) {
				toggles.undo();
				return true;
			}

			if (key == InputConstants.KEY_G) {
				openWardrobe();
				return true;
			}

			if (key == InputConstants.KEY_H) {
				openHudEditor();
				return true;
			}
		}

		if (event.isEscape()) {
			if (search.active()) {
				search.clear();
				searchEnded();
			} else if (drawer.isOpen()) {
				drawer.close();
			} else {
				onClose();
			}

			return true;
		}

		if (event.isCycleFocus()) {
			// Tab: the drawer, never the vanilla widget focus
			toggleDrawer();
			return true;
		}

		if (event.key() == InputConstants.KEY_BACKSPACE) {
			if (search.active()) {
				search.backspace();

				if (search.active()) {
					tuneBestHit();
				} else {
					searchEnded();
				}
			} else if (previous >= 0) {
				// TV "last channel": pressing it again goes back and forth between two
				jump(previous);
			}

			return true;
		}

		if (event.isConfirmation()) {
			if (search.active()) {
				toggleHit();
			} else {
				toggleTuned();
			}

			return true;
		}

		if (event.isSelection()) {
			// Space: inside a query that is still being typed it is a space (charTyped adds it)
			if (search.active() && !search.done()) {
				return true;
			}

			if (search.active()) {
				toggleHit();
			} else {
				toggleTuned();
			}

			return true;
		}

		if (event.isLeft() || event.isRight()) {
			int direction = event.isLeft() ? -1 : 1;

			if (drawer.hasFocus()) {
				drawer.changeFocused(direction);
				settingsChanged();
			} else {
				zap(direction);
			}

			return true;
		}

		if (event.isUp() || event.isDown()) {
			int direction = event.isUp() ? -1 : 1;

			if (search.active() && !search.hits().isEmpty()) {
				stepHit(direction);
			} else if (drawer.isOpen()) {
				drawer.moveFocus(direction);
			} else {
				changeStyle(direction);
			}

			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (JuicedClient.MENU_KEY != null && JuicedClient.MENU_KEY.matches(event)) {
			menuKeyHeld = false;
		}

		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String typed = event.codepointAsString();

		if (typed.isEmpty() || channels.isEmpty()) {
			return false;
		}

		if (this.minecraft.hasControlDown()) {
			// Strg+Z, Strg+G and Strg+H are shortcuts, not search letters
			return false;
		}

		char c = typed.charAt(0);

		if (Character.isDigit(c)) {
			// Digits never search; 1-6 jump to a category while there is no query
			if (!search.active() && c >= '1' && c <= '6') {
				jumpTo(Category.values()[c - '1']);
			}

			return true;
		}

		if (Character.isLetter(c)) {
			typed(typed);
			return true;
		}

		if ((c == ' ' || c == '-' || c == '&') && search.active() && !search.done()) {
			typed(typed);
			return true;
		}

		return false;
	}
}

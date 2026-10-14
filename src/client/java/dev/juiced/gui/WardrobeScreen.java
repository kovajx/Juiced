package dev.juiced.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Config;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleManager;
import dev.juiced.core.Outfits;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.Setting;
import dev.juiced.render.FreeCamera;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

/**
 * The wardrobe: your player in the middle with a camera you can turn around them,
 * slots on the left (head, ears, back, tail, companion, blades, aura), options and settings
 * on the right, and four outfits you can save and put on with one click.
 */
public class WardrobeScreen extends Screen implements CameraKeeper {
	private static final int LEFT_W = 124;
	private static final int RIGHT_W = 156;
	private static final int TOP = 30;
	private static final int TEXT = 0xFFEDEDF2;
	private static final int DIM = 0xFF8A8A99;
	private static final int MUTED = 0xFF55556A;
	private static final int PANEL = 0xE2111117;

	private record Option(String label, String moduleId, @Nullable String choiceId, int choiceIndex) {
	}

	private record Slot(String name, String icon, List<Option> options) {
	}

	private record HitBox(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private static Option option(String label, String moduleId) {
		return new Option(label, moduleId, null, 0);
	}

	private static Option option(String label, String moduleId, String choiceId, int index) {
		return new Option(label, moduleId, choiceId, index);
	}

	private static final List<Slot> SLOTS = List.of(
			new Slot("Kopf", "\u265B", List.of(option("Halo", "head", "kind", 0), option("Krone", "head", "kind", 1), option("China-Hut", "head", "kind", 2),
					option("Blumenkranz", "head", "kind", 3), option("Strohhut", "head", "kind", 8), option("Kopfhörer", "head", "kind", 9),
					option("Zylinder", "head", "kind", 4), option("Zauberhut", "head", "kind", 5), option("Kristall", "head", "kind", 6),
					option("Mondsichel", "head", "kind", 10), option("Regenwolke", "head", "kind", 7))),
			new Slot("Ohren", "\u273F", List.of(option("Katzenohren", "ears", "kind", 0), option("Hasenohren", "ears", "kind", 1),
					option("Fuchsohren", "ears", "kind", 4), option("Bärenohren", "ears", "kind", 6), option("Elfenohren", "ears", "kind", 5),
					option("Teufelshörner", "ears", "kind", 2), option("Widderhörner", "ears", "kind", 3), option("Fühler", "ears", "kind", 7))),
			new Slot("Rücken", "\u2756", List.of(option("Cape", "back", "kind", 0), option("Engelsflügel", "back", "kind", 1),
					option("Schmetterling", "back", "kind", 4), option("Papierflügel", "back", "kind", 5), option("Drachenflügel", "back", "kind", 2),
					option("Energieflügel", "back", "kind", 3))),
			new Slot("Schweif", "\u223F", List.of(option("Fuchs", "tail", "kind", 0), option("Kitsune", "tail", "kind", 4), option("Katze", "tail", "kind", 1),
					option("Drache", "tail", "kind", 2), option("Teufel", "tail", "kind", 3), option("Sterne", "tail", "kind", 5))),
			new Slot("Begleiter", "\u2665", List.of(option("Orb", "pet", "kind", 2), option("Qualle", "pet", "kind", 8), option("Himmelswal", "pet", "kind", 9),
					option("Papierkranich", "pet", "kind", 10), option("Axolotl", "pet", "kind", 11), option("Fee", "pet", "kind", 4),
					option("Koi-Blase", "pet", "kind", 7), option("Planet", "pet", "kind", 5), option("Kristall", "pet", "kind", 6),
					option("Geist", "pet", "kind", 0), option("Mini-Drache", "pet", "kind", 1), option("Slime", "pet", "kind", 3))),
			new Slot("Klingen", "\u2694", List.of(option("Fächer", "blades", "layout", 0), option("Flügel", "blades", "layout", 3),
					option("Kreis", "blades", "layout", 1), option("Krone", "blades", "layout", 2))),
			new Slot("Aura", "\u2726", List.of(option("Orbit", "aura", "style", 0), option("Ring", "aura", "style", 1),
					option("Spirale", "aura", "style", 2), option("Magiekreis", "aura", "style", 3), option("Glühwürmchen", "aura", "style", 4),
					option("Blütenwirbel", "aura", "style", 5), option("Musiknoten", "aura", "style", 6), option("Seifenblasen", "aura", "style", 7))));

	/** Remembered while the game runs, so the wardrobe opens the way you left it. */
	private static int selectedSlot;
	private static boolean autoRotate;

	private final @Nullable Screen parent;
	private final List<HitBox> hits = new ArrayList<>();
	private final double openedAt = Clock.t();
	private final double[] savedAt = new double[Outfits.SLOTS];
	private final SettingsList settingsList = new SettingsList(
			(x1, y1, x2, y2, action) -> hits.add(new HitBox(x1, y1, x2, y2, action)),
			color -> {
				keepCamera = true;
				this.minecraft.gui.setScreen(new ColorPickerScreen(this, color));
			});

	private boolean keepCamera;
	private boolean rotating;
	// Where the camera is turning to (buttons, A/D keys); dragging moves it directly
	private float targetYaw = Float.NaN;
	private float targetPitch = Float.NaN;
	private boolean turnLeft;
	private boolean turnRight;
	private int settingsScroll;
	private int settingsMaxScroll;
	private int mouseX;
	private int mouseY;

	public WardrobeScreen(@Nullable Screen parent) {
		super(Component.literal("Garderobe"));
		this.parent = parent;
	}

	// ---- outfits and slots ----------------------------------------------------------------

	private static @Nullable ChoiceSetting choice(Module module, String id) {
		for (Setting<?> setting : module.settings) {
			if (setting instanceof ChoiceSetting choice && choice.id.equals(id)) {
				return choice;
			}
		}

		return null;
	}

	/** What is worn in a slot right now, or null if nothing. */
	private static @Nullable Option worn(Slot slot) {
		for (Option option : slot.options) {
			Module module = ModuleManager.byId(option.moduleId);

			if (module == null || !module.isEnabled()) {
				continue;
			}

			if (option.choiceId == null) {
				return option;
			}

			ChoiceSetting choice = choice(module, option.choiceId);

			if (choice == null || choice.index() == option.choiceIndex) {
				return option;
			}
		}

		return null;
	}

	/** Puts on an option and takes off whatever else was in the slot. Null takes everything off. */
	private static void wear(Slot slot, @Nullable Option option) {
		for (Option other : slot.options) {
			if (option == null || !other.moduleId.equals(option.moduleId)) {
				Module module = ModuleManager.byId(other.moduleId);

				if (module != null) {
					module.setEnabled(false);
				}
			}
		}

		if (option == null) {
			return;
		}

		Module module = ModuleManager.byId(option.moduleId);

		if (module == null) {
			return;
		}

		if (option.choiceId != null) {
			ChoiceSetting choice = choice(module, option.choiceId);

			if (choice != null) {
				choice.set(option.choiceIndex);
			}
		}

		module.setEnabled(true);
	}

	// ---- camera ---------------------------------------------------------------------------

	@Override
	protected void init() {
		super.init();
		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null && !FreeCamera.ownedBy(FreeCamera.Owner.WARDROBE)) {
			// Look at the player from the front, slightly from above
			FreeCamera.start(FreeCamera.Owner.WARDROBE, player.yBodyRot + 180f + 25f, 12f, 3.2f);
		}
	}

	@Override
	public void releaseCamera() {
		FreeCamera.stop(FreeCamera.Owner.WARDROBE, false);
	}

	private boolean inStage(double x, double y) {
		return x > 10 + LEFT_W + 4 && x < this.width - 10 - RIGHT_W - 4 && y > TOP;
	}

	// ---- rendering ------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// Darken only the sides so the player in the middle stays bright
		g.fillGradient(0, this.height / 2, this.width, this.height, 0x00000000, 0x55000000);
		Gui2D.fill(g, 0, 0, 10 + LEFT_W + 14, this.height, 0x22000000);
		Gui2D.fill(g, this.width - 10 - RIGHT_W - 14, 0, this.width, this.height, 0x22000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Clock.frame();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		hits.clear();

		updateTurning();

		int accent = Theme.accent();
		float open = Clock.easeOut(Colors.clamp01((float) ((Clock.t() - openedAt) / 0.25)));
		g.pose().pushMatrix();
		g.pose().translate(0, (1f - open) * 12f);

		drawTopBar(g, accent);
		drawSlots(g, accent);
		drawRight(g, accent);

		g.pose().popMatrix();

		if (!settingsList.isDragging()) {
			boolean pointer = false;

			for (HitBox hit : hits) {
				pointer |= hit.contains(mouseX, mouseY);
			}

			if (pointer) {
				g.requestCursor(CursorTypes.POINTING_HAND);
			}
		}
	}

	/** Eases the camera towards the target angle; A/D held down and auto-turn move the target. */
	private void updateTurning() {
		if (!FreeCamera.ownedBy(FreeCamera.Owner.WARDROBE)) {
			return;
		}

		float dt = Clock.dt();
		float yaw = FreeCamera.yaw();
		float pitch = FreeCamera.pitch();

		if (Float.isNaN(targetYaw) || rotating) {
			targetYaw = yaw;
			targetPitch = pitch;
			return;
		}

		if (turnLeft) {
			targetYaw -= dt * 110f;
		}

		if (turnRight) {
			targetYaw += dt * 110f;
		}

		if (autoRotate) {
			targetYaw += dt * 12f;
		}

		float ease = 1f - (float) Math.exp(-9f * dt);
		FreeCamera.setRotation(yaw + (targetYaw - yaw) * ease, pitch + (targetPitch - pitch) * ease);
	}

	private void turnBy(float degrees) {
		if (Float.isNaN(targetYaw)) {
			targetYaw = FreeCamera.yaw();
			targetPitch = FreeCamera.pitch();
		}

		targetYaw += degrees;
		Fx.sound("ui.button.click", 0.2f, 1.5f);
	}

	/** Turns the camera back to look at your front. */
	private void faceFront() {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		float front = player.yBodyRot + 180f;
		float yaw = FreeCamera.yaw();
		// Pick the way round that is shortest
		targetYaw = yaw + (((front - yaw) % 360f + 540f) % 360f - 180f);
		targetPitch = 12f;
		Fx.sound("ui.button.click", 0.2f, 1.3f);
	}

	private boolean hover(int x1, int y1, int x2, int y2) {
		return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
	}

	private void button(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, String label, boolean active, int accent, Runnable action) {
		boolean hovered = hover(x1, y1, x2, y2);
		int bg = hovered ? Colors.argb(1f, accent) : active ? Colors.argb(0.35f, accent) : 0xE01A1A22;
		Gui2D.rounded(g, x1, y1, x2, y2, bg);
		Gui2D.centered(g, label, (x1 + x2) / 2f, y1 + (y2 - y1 - 6) / 2f, hovered ? 0xFF0B0B10 : TEXT, false, 0.75f);
		hits.add(new HitBox(x1, y1, x2, y2, action));
	}

	private void drawTopBar(GuiGraphicsExtractor g, int accent) {
		Gui2D.text(g, "♛ GARDEROBE", 12, 10, Colors.argb(1f, accent), true, 1.3f);
		Gui2D.text(g, "nur du siehst sie", 14 + Gui2D.width("♛ GARDEROBE", 1.3f), 13, DIM, false, 0.7f);

		String done = "Fertig";
		int dw = Gui2D.width(done, 0.75f) + 18;
		int dx2 = this.width - 10;
		button(g, dx2 - dw, 8, dx2, 22, done, false, accent, this::onClose);

		String spin = "Auto-Drehen";
		int sw = Gui2D.width(spin, 0.75f) + 14;
		int sx2 = dx2 - dw - 6;
		button(g, sx2 - sw, 8, sx2, 22, spin, autoRotate, accent, () -> autoRotate = !autoRotate);

		// Turn controls above the player
		int stageCenter = (10 + LEFT_W + this.width - 10 - RIGHT_W) / 2;
		String front = "Vorne";
		int fw = Gui2D.width(front, 0.75f) + 14;
		int by = TOP + 2;
		button(g, stageCenter - fw / 2 - 22, by, stageCenter - fw / 2 - 4, by + 14, "\u27F2", false, accent, () -> turnBy(-45f));
		button(g, stageCenter - fw / 2, by, stageCenter + fw / 2, by + 14, front, false, accent, this::faceFront);
		button(g, stageCenter + fw / 2 + 4, by, stageCenter + fw / 2 + 22, by + 14, "\u27F3", false, accent, () -> turnBy(45f));

		String hint = "Ziehen oder A / D = drehen  \u00B7  Mausrad = Zoom";

		if (Gui2D.width(hint, 0.65f) < this.width - LEFT_W - RIGHT_W - 40) {
			Gui2D.centered(g, hint, stageCenter, by + 18, MUTED, false, 0.65f);
		}
	}

	private void drawSlots(GuiGraphicsExtractor g, int accent) {
		int x1 = 10;
		int x2 = x1 + LEFT_W;
		int bottom = this.height - 6;
		Gui2D.rounded2(g, x1, TOP, x2, bottom, PANEL);
		int y = TOP + 6;
		Gui2D.text(g, "SLOTS", x1 + 8, y, MUTED, false, 0.6f);
		y += 9;

		for (int i = 0; i < SLOTS.size(); i++) {
			Slot slot = SLOTS.get(i);
			Option worn = worn(slot);
			boolean selected = i == selectedSlot;
			boolean hovered = hover(x1 + 3, y, x2 - 3, y + 15);

			if (selected) {
				Gui2D.rounded(g, x1 + 3, y, x2 - 3, y + 15, Colors.argb(0.22f, accent));
				Gui2D.fill(g, x1 + 3, y + 3, x1 + 5, y + 12, Colors.argb(1f, accent));
			} else if (hovered) {
				Gui2D.rounded(g, x1 + 3, y, x2 - 3, y + 15, 0x14FFFFFF);
			}

			Gui2D.text(g, slot.icon, x1 + 9, y + 4, worn != null ? Colors.argb(1f, accent) : DIM, false, 0.8f);
			Gui2D.text(g, slot.name, x1 + 21, y + 4, TEXT, false, 0.75f);
			String wornText = worn == null ? "—" : worn.label;
			int maxW = x2 - 8 - (x1 + 21 + Gui2D.width(slot.name, 0.75f) + 6);
			String shown = trim(wornText, maxW, 0.65f);
			Gui2D.text(g, shown, x2 - 8 - Gui2D.width(shown, 0.65f), y + 5, worn == null ? MUTED : DIM, false, 0.65f);
			int index = i;
			hits.add(new HitBox(x1 + 3, y, x2 - 3, y + 15, () -> {
				if (selectedSlot != index) {
					selectedSlot = index;
					settingsScroll = 0;
					Fx.sound("ui.button.click", 0.2f, 1.5f);
				}
			}));
			y += 16;
		}

		// Outfits
		y += 6;

		if (y + 12 > bottom) {
			return;
		}

		Gui2D.fill(g, x1 + 8, y - 3, x2 - 8, y - 2, 0x18FFFFFF);
		Gui2D.text(g, "OUTFITS", x1 + 8, y + 1, MUTED, false, 0.6f);
		y += 10;

		for (int slot = 0; slot < Outfits.SLOTS && y + 14 <= bottom - 3; slot++) {
			boolean has = Outfits.has(slot);
			boolean justSaved = Clock.t() - savedAt[slot] < 1.2;
			Gui2D.text(g, String.valueOf(slot + 1), x1 + 9, y + 4, has ? Colors.argb(1f, accent) : MUTED, false, 0.75f);
			int saveW = 18;
			int loadW = 30;
			int saveX2 = x2 - 6;
			int saveX1 = saveX2 - saveW;
			int loadX2 = saveX1 - 3;
			int loadX1 = loadX2 - loadW;
			String summary = justSaved ? "gesichert ✔" : Outfits.summary(slot);
			int textMax = (has ? loadX1 : saveX1) - 4 - (x1 + 18);
			Gui2D.text(g, trim(summary, textMax, 0.6f), x1 + 18, y + 4, justSaved ? Colors.argb(1f, accent) : has ? DIM : MUTED, false, 0.6f);

			if (has) {
				int outfit = slot;
				button(g, loadX1, y, loadX2, y + 13, "Laden", false, accent, () -> {
					Outfits.load(outfit);
					Config.save();
					Fx.sound("ui.button.click", 0.3f, 1.3f);
				});
			}

			int outfit = slot;
			button(g, saveX1, y, saveX2, y + 13, "+", false, accent, () -> {
				Outfits.save(outfit);
				Config.save();
				savedAt[outfit] = Clock.t();
				Fx.sound("ui.button.click", 0.3f, 1.6f);
			});

			if (hover(saveX1, y, saveX2, y + 13)) {
				Gui2D.text(g, "aktuellen Look speichern", x2 + 6, y + 4, TEXT, true, 0.6f);
			}

			y += 15;
		}
	}

	private void drawRight(GuiGraphicsExtractor g, int accent) {
		int x2 = this.width - 10;
		int x1 = x2 - RIGHT_W;
		int bottom = this.height - 6;
		Gui2D.rounded2(g, x1, TOP, x2, bottom, PANEL);
		Slot slot = SLOTS.get(selectedSlot);
		Option worn = worn(slot);
		int y = TOP + 6;
		Gui2D.text(g, slot.icon + "  " + slot.name.toUpperCase(java.util.Locale.ROOT), x1 + 8, y, Colors.argb(1f, accent), false, 0.75f);
		y += 12;

		// Option chips, wrapping into rows
		int cx = x1 + 8;
		List<Option> choices = new ArrayList<>();
		choices.add(null);
		choices.addAll(slot.options);

		for (Option option : choices) {
			String label = option == null ? "Aus" : option.label;
			int w = Gui2D.width(label, 0.7f) + 10;

			if (cx + w > x2 - 8) {
				cx = x1 + 8;
				y += 15;
			}

			boolean active = worn == option;
			boolean hovered = hover(cx, y, cx + w, y + 12);
			int bg = active ? Colors.argb(1f, accent) : hovered ? 0xFF2A2A36 : 0xFF1A1A22;
			Gui2D.rounded(g, cx, y, cx + w, y + 12, bg);
			Gui2D.centered(g, label, cx + w / 2f, y + 3, active ? 0xFF0B0B10 : TEXT, false, 0.7f);
			hits.add(new HitBox(cx, y, cx + w, y + 12, () -> {
				wear(slot, option);
				settingsScroll = 0;
				Fx.sound("ui.button.click", 0.3f, option == null ? 0.8f : 1.4f);
			}));
			cx += w + 4;
		}

		y += 18;
		Gui2D.fill(g, x1 + 8, y - 3, x2 - 8, y - 2, 0x18FFFFFF);

		if (worn == null) {
			settingsMaxScroll = 0;
			int lineY = y + 4;

			for (String line : Gui2D.wrap("Wähle oben etwas aus, um es anzuziehen. Danach kannst du hier Farbe, Größe und mehr einstellen.", RIGHT_W - 16, 0.65f)) {
				Gui2D.text(g, line, x1 + 8, lineY, DIM, false, 0.65f);
				lineY += 8;
			}

			return;
		}

		Module module = ModuleManager.byId(worn.moduleId);

		if (module == null) {
			return;
		}

		settingsList.originX = 0;
		settingsList.originY = 0;
		settingsList.mouseX = mouseX;
		settingsList.mouseY = mouseY;
		int listTop = y + 1;
		int listBottom = bottom - 4;
		settingsScroll = Math.max(0, Math.min(settingsScroll, settingsMaxScroll));
		int contentHeight = settingsList.draw(g, module.settings, x1 + 8, RIGHT_W - 16, listTop, listBottom, settingsScroll, true);
		settingsMaxScroll = Math.max(0, contentHeight - (listBottom - listTop));

		if (settingsMaxScroll > 0) {
			// Thin scroll bar
			int trackH = listBottom - listTop;
			int barH = Math.max(12, trackH * trackH / Math.max(1, contentHeight));
			int barY = listTop + Math.round((trackH - barH) * (settingsScroll / (float) settingsMaxScroll));
			Gui2D.fill(g, x2 - 4, barY, x2 - 2, barY + barH, Colors.argb(0.5f, accent));
		}
	}

	private static String trim(String text, int maxWidth, float scale) {
		if (maxWidth <= 0) {
			return "";
		}

		if (Gui2D.width(text, scale) <= maxWidth) {
			return text;
		}

		String result = text;

		while (!result.isEmpty() && Gui2D.width(result + "…", scale) > maxWidth) {
			result = result.substring(0, result.length() - 1);
		}

		return result + "…";
	}

	// ---- input ----------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();

		for (int i = hits.size() - 1; i >= 0; i--) {
			HitBox hit = hits.get(i);

			if (hit.contains(x, y)) {
				hit.action().run();
				return true;
			}
		}

		if (inStage(x, y)) {
			rotating = true;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (settingsList.isDragging()) {
			settingsList.drag(event.x());
			return true;
		}

		if (rotating) {
			// Drag right = you turn to the right
			FreeCamera.setRotation(FreeCamera.yaw() + (float) dragX * 0.8f, Math.max(-40f, Math.min(70f, FreeCamera.pitch() + (float) dragY * 0.6f)));
			targetYaw = FreeCamera.yaw();
			targetPitch = FreeCamera.pitch();
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (settingsList.isDragging()) {
			settingsList.release();
			Config.save();
			return true;
		}

		if (rotating) {
			rotating = false;
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= this.width - 10 - RIGHT_W) {
			settingsScroll = Math.max(0, Math.min(settingsMaxScroll, settingsScroll - (int) Math.signum(scrollY) * 14));
			return true;
		}

		if (inStage(x, y)) {
			FreeCamera.setDistance(Math.max(1.6f, Math.min(6f, FreeCamera.distance() - (float) Math.signum(scrollY) * 0.3f)));
			return true;
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_A || event.key() == InputConstants.KEY_D) {
			if (Float.isNaN(targetYaw)) {
				targetYaw = FreeCamera.yaw();
				targetPitch = FreeCamera.pitch();
			}

			turnLeft |= event.key() == InputConstants.KEY_A;
			turnRight |= event.key() == InputConstants.KEY_D;
			return true;
		}

		if (event.isUp() || event.isDown()) {
			int delta = event.isUp() ? -1 : 1;
			selectedSlot = Math.floorMod(selectedSlot + delta, SLOTS.size());
			settingsScroll = 0;
			return true;
		}

		if (event.isLeft() || event.isRight()) {
			// Step through the options of the selected slot
			Slot slot = SLOTS.get(selectedSlot);
			List<Option> choices = new ArrayList<>();
			choices.add(null);
			choices.addAll(slot.options);
			int current = choices.indexOf(worn(slot));
			int next = Math.floorMod(current + (event.isLeft() ? -1 : 1), choices.size());
			wear(slot, choices.get(next));
			settingsScroll = 0;
			Fx.sound("ui.button.click", 0.2f, 1.4f);
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.key() == InputConstants.KEY_A) {
			turnLeft = false;
			return true;
		}

		if (event.key() == InputConstants.KEY_D) {
			turnRight = false;
			return true;
		}

		return super.keyReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the world running so pets, capes and tails keep moving
		return false;
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		super.removed();

		if (keepCamera) {
			keepCamera = false;
		} else {
			FreeCamera.stop(FreeCamera.Owner.WARDROBE, false);
		}

		Config.save();
	}
}

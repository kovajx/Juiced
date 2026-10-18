package dev.juiced.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Config;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

/**
 * Pick any color: a saturation/brightness field, a hue bar, a hex field and your recent colors.
 * Changes show up live (the world stays visible behind the panel); "Abbrechen" puts the old color back.
 */
public class ColorPickerScreen extends Screen {
	private static final int W = 232;
	private static final int H = 154;
	private static final int FIELD_W = 112;
	private static final int FIELD_H = 96;
	private static final int HUE_W = 10;
	private static final int SIDE_W = 76;
	private static final int TEXT = 0xFFEDEDF2;
	private static final int DIM = 0xFF8A8A99;

	private enum Drag {
		NONE,
		FIELD,
		HUE
	}

	private record Button(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final Screen parent;
	private final ColorSetting setting;
	private final int originalValue;
	private final int originalRgb;
	private final List<Button> buttons = new ArrayList<>();
	private final double openedAt = Clock.t();

	private float hue;
	private float saturation;
	private float brightness;
	private String hex;
	private boolean hexFocused;
	private Drag drag = Drag.NONE;
	private boolean returning;

	private int left;
	private int top;

	public ColorPickerScreen(Screen parent, ColorSetting setting) {
		super(Component.literal("Farbe wählen"));
		this.parent = parent;
		this.setting = setting;
		this.originalValue = setting.get();
		this.originalRgb = setting.rgb();
		setFromRgb(originalRgb);
	}

	@Override
	protected void init() {
		super.init();
		// Needed to receive typed characters for the hex field
		this.minecraft.textInputManager().startTextInput(this);
	}

	// ---- color state ----------------------------------------------------------------------

	private int currentRgb() {
		return Colors.hsv(hue, saturation, brightness);
	}

	private void setFromRgb(int rgb) {
		float[] hsv = Colors.toHsv(rgb);

		// Grays have no hue: keep the hue bar where it was
		if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
			hue = hsv[0];
		}

		saturation = hsv[1];
		brightness = hsv[2];
		hex = String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
	}

	/** Applies the current HSV values to the setting, so the change is visible right away. */
	private void applyHsv() {
		int rgb = currentRgb();
		setting.setCustom(rgb);

		if (!hexFocused) {
			hex = String.format(Locale.ROOT, "%06X", rgb);
		}
	}

	/** Parses "RRGGBB" or "RGB"; returns -1 if it isn't a color yet. */
	static int parseHex(String text) {
		String clean = text.trim();

		if (clean.startsWith("#")) {
			clean = clean.substring(1);
		}

		if (clean.length() == 3) {
			StringBuilder longer = new StringBuilder();

			for (char c : clean.toCharArray()) {
				longer.append(c).append(c);
			}

			clean = longer.toString();
		}

		if (clean.length() != 6) {
			return -1;
		}

		try {
			return Integer.parseInt(clean, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void applyHex() {
		int rgb = parseHex(hex);

		if (rgb >= 0) {
			setFromRgb(rgb);
			setting.setCustom(rgb);
		}
	}

	/** Leaves the hex field: applies a complete code, otherwise shows the current color again. */
	private void leaveHex() {
		if (!hexFocused) {
			return;
		}

		hexFocused = false;
		applyHex();

		if (parseHex(hex) < 0) {
			hex = String.format(Locale.ROOT, "%06X", currentRgb());
		}
	}

	private static boolean isHexChar(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	private void confirm() {
		leaveHex();

		int rgb = currentRgb();
		setting.setCustom(rgb);
		Colors.remember(rgb);
		Config.save();
		Fx.sound("ui.button.click", 0.3f, 1.4f);
		back();
	}

	private void cancel() {
		setting.set(originalValue);
		back();
	}

	private void back() {
		returning = true;
		this.minecraft.gui.setScreen(parent);
	}

	// ---- rendering ------------------------------------------------------------------------

	private void layout() {
		// Off to the right when there is room, so your player in the middle stays visible
		if (this.width >= W * 2 + 60) {
			left = this.width / 2 + 40;
		} else {
			left = (this.width - W) / 2;
		}

		top = Math.max(8, (this.height - H) / 2);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// The menu behind keeps showing its live try-out (HUD element, crosshair) while you pick
		if (parent instanceof CameraKeeper keeper) {
			keeper.drawBehind(g, partialTick);
		}

		Gui2D.fill(g, 0, 0, this.width, this.height, 0x30000000);
	}

	@Override
	public void tick() {
		super.tick();

		if (parent instanceof CameraKeeper keeper) {
			keeper.tickBehind();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Clock.frame();
		layout();
		buttons.clear();
		int accent = Theme.accent();
		float open = Clock.easeOut(Colors.clamp01((float) ((Clock.t() - openedAt) / 0.18)));
		g.pose().pushMatrix();
		g.pose().translate(0, (1f - open) * 10f);

		Gui2D.glow(g, left, top, left + W, top + H, accent, 0.5f * open, 3);
		Gui2D.rounded2(g, left, top, left + W, top + H, 0xF4111117);
		Gui2D.outline(g, left, top, left + W, top + H, Colors.argb(0.5f, accent));
		Gui2D.text(g, "Farbe wählen", left + 10, top + 8, Colors.argb(1f, accent), false, 0.9f);
		Gui2D.text(g, setting.label, left + 12 + Gui2D.width("Farbe wählen", 0.9f), top + 9, DIM, false, 0.75f);

		int fx = left + 10;
		int fy = top + 24;
		drawField(g, fx, fy);
		drawHueBar(g, fx + FIELD_W + 6, fy);
		drawSide(g, fx + FIELD_W + 6 + HUE_W + 8, fy, mouseX, mouseY, accent);
		drawButtons(g, mouseX, mouseY, accent);

		g.pose().popMatrix();

		boolean pointer = drag != Drag.NONE || inside(mouseX, mouseY, fx, fy, fx + FIELD_W + 6 + HUE_W, fy + FIELD_H);

		for (Button button : buttons) {
			pointer |= button.contains(mouseX, mouseY);
		}

		if (pointer) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	private void drawField(GuiGraphicsExtractor g, int x, int y) {
		for (int col = 0; col < FIELD_W; col++) {
			float s = col / (float) (FIELD_W - 1);
			g.fillGradient(x + col, y, x + col + 1, y + FIELD_H, 0xFF000000 | Colors.hsv(hue, s, 1f), 0xFF000000);
		}

		Gui2D.outline(g, x - 1, y - 1, x + FIELD_W + 1, y + FIELD_H + 1, 0xFF2A2A36);
		int mx = x + Math.round(saturation * (FIELD_W - 1));
		int my = y + Math.round((1f - brightness) * (FIELD_H - 1));
		int ring = brightness > 0.55f && saturation < 0.45f ? 0xFF101014 : 0xFFFFFFFF;
		Gui2D.outline(g, mx - 3, my - 3, mx + 4, my + 4, ring);
		Gui2D.fill(g, mx - 1, my - 1, mx + 2, my + 2, 0xFF000000 | currentRgb());
	}

	private void drawHueBar(GuiGraphicsExtractor g, int x, int y) {
		for (int row = 0; row < FIELD_H; row++) {
			Gui2D.fill(g, x, y + row, x + HUE_W, y + row + 1, 0xFF000000 | Colors.hsv(row / (float) (FIELD_H - 1), 1f, 1f));
		}

		Gui2D.outline(g, x - 1, y - 1, x + HUE_W + 1, y + FIELD_H + 1, 0xFF2A2A36);
		int my = y + Math.round(hue * (FIELD_H - 1));
		Gui2D.fill(g, x - 2, my - 1, x + HUE_W + 2, my + 2, 0xFFFFFFFF);
		Gui2D.fill(g, x - 1, my, x + HUE_W + 1, my + 1, 0xFF000000 | Colors.hsv(hue, 1f, 1f));
	}

	private void drawSide(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, int accent) {
		int rgb = currentRgb();
		// New color on top, the old one below for comparison
		Gui2D.fill(g, x, y, x + SIDE_W, y + 18, 0xFF000000 | rgb);
		Gui2D.fill(g, x, y + 18, x + SIDE_W, y + 28, 0xFF000000 | originalRgb);
		Gui2D.outline(g, x - 1, y - 1, x + SIDE_W + 1, y + 29, 0xFF2A2A36);
		Gui2D.text(g, "Neu", x + 3, y + 3, readable(rgb), false, 0.65f);
		Gui2D.text(g, "Vorher", x + 3, y + 20, readable(originalRgb), false, 0.55f);
		buttons.add(new Button(x, y + 18, x + SIDE_W, y + 28, () -> {
			setFromRgb(originalRgb);
			applyHsv();
		}));

		// Hex field
		int hy = y + 34;
		boolean valid = parseHex(hex) >= 0;
		Gui2D.rounded(g, x, hy, x + SIDE_W, hy + 14, hexFocused ? 0xFF1C1C26 : 0xFF16161E);
		Gui2D.outline(g, x, hy, x + SIDE_W, hy + 14, hexFocused ? Colors.argb(1f, accent) : 0xFF2A2A36);
		boolean caret = hexFocused && (Clock.t() * 2) % 2 < 1;
		int textColor = valid || hexFocused ? TEXT : 0xFFFF6B6B;
		Gui2D.text(g, "#" + hex + (caret ? "_" : ""), x + 5, hy + 3, textColor, false, 0.85f);
		buttons.add(new Button(x, hy, x + SIDE_W, hy + 14, () -> {
			hexFocused = true;
			hex = "";
		}));

		Gui2D.text(g, String.format(Locale.ROOT, "R %d  G %d  B %d", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF), x, hy + 18, DIM, false, 0.6f);

		// Recent colors
		int ry = hy + 28;
		Gui2D.text(g, "Zuletzt", x, ry, DIM, false, 0.6f);

		if (Colors.RECENT.isEmpty()) {
			Gui2D.text(g, "noch keine", x, ry + 9, 0xFF55556A, false, 0.6f);
			return;
		}

		for (int i = 0; i < Colors.RECENT.size() && i < 8; i++) {
			int recent = Colors.RECENT.get(i);
			int sx = x + (i % 4) * 20;
			int sy = ry + 8 + (i / 4) * 13;
			boolean hovered = inside(mouseX, mouseY, sx, sy, sx + 16, sy + 10);
			Gui2D.rounded(g, sx, sy, sx + 16, sy + 10, 0xFF000000 | recent);

			if (hovered) {
				Gui2D.outline(g, sx - 1, sy - 1, sx + 17, sy + 11, 0xFFFFFFFF);
			}

			buttons.add(new Button(sx, sy, sx + 16, sy + 10, () -> {
				hexFocused = false;
				setFromRgb(recent);
				applyHsv();
			}));
		}
	}

	private void drawButtons(GuiGraphicsExtractor g, int mouseX, int mouseY, int accent) {
		int by = top + H - 22;
		String ok = "Übernehmen";
		String no = "Abbrechen";
		int okW = Gui2D.width(ok, 0.8f) + 16;
		int noW = Gui2D.width(no, 0.8f) + 16;
		int okX2 = left + W - 10;
		int okX1 = okX2 - okW;
		int noX2 = okX1 - 6;
		int noX1 = noX2 - noW;

		boolean noHover = inside(mouseX, mouseY, noX1, by, noX2, by + 14);
		Gui2D.rounded(g, noX1, by, noX2, by + 14, noHover ? 0xFF2A2A36 : 0xFF1A1A22);
		Gui2D.centered(g, no, (noX1 + noX2) / 2f, by + 4, noHover ? TEXT : DIM, false, 0.8f);
		buttons.add(new Button(noX1, by, noX2, by + 14, this::cancel));

		boolean okHover = inside(mouseX, mouseY, okX1, by, okX2, by + 14);
		Gui2D.rounded(g, okX1, by, okX2, by + 14, Colors.argb(okHover ? 1f : 0.85f, accent));
		Gui2D.centered(g, ok, (okX1 + okX2) / 2f, by + 4, 0xFF0B0B10, false, 0.8f);
		buttons.add(new Button(okX1, by, okX2, by + 14, this::confirm));

		Gui2D.text(g, "Enter = übernehmen · Esc = abbrechen", left + 10, by + 4, 0xFF55556A, false, 0.55f);
	}

	/** Dark text on light colors, light text on dark ones. */
	private static int readable(int rgb) {
		float luminance = 0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF);
		return luminance > 150f ? 0xFF101014 : 0xFFFFFFFF;
	}

	private static boolean inside(double x, double y, int x1, int y1, int x2, int y2) {
		return x >= x1 && x < x2 && y >= y1 && y < y2;
	}

	// ---- input ----------------------------------------------------------------------------

	private void dragTo(double x, double y) {
		int fx = left + 10;
		int fy = top + 24;

		if (drag == Drag.FIELD) {
			saturation = Colors.clamp01((float) (x - fx) / (FIELD_W - 1));
			brightness = 1f - Colors.clamp01((float) (y - fy) / (FIELD_H - 1));
			applyHsv();
		} else if (drag == Drag.HUE) {
			hue = Colors.clamp01((float) (y - fy) / (FIELD_H - 1));
			applyHsv();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();

		for (int i = buttons.size() - 1; i >= 0; i--) {
			Button button = buttons.get(i);

			if (button.contains(x, y)) {
				button.action().run();
				return true;
			}
		}

		leaveHex();
		int fx = left + 10;
		int fy = top + 24;

		if (inside(x, y, fx - 2, fy - 2, fx + FIELD_W + 2, fy + FIELD_H + 2)) {
			drag = Drag.FIELD;
			dragTo(x, y);
			return true;
		}

		int hx = fx + FIELD_W + 6;

		if (inside(x, y, hx - 3, fy - 2, hx + HUE_W + 3, fy + FIELD_H + 2)) {
			drag = Drag.HUE;
			dragTo(x, y);
			return true;
		}

		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (drag != Drag.NONE) {
			dragTo(event.x(), event.y());
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag != Drag.NONE) {
			drag = Drag.NONE;
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		// Scroll over the field or the hue bar for fine steps
		int fx = left + 10;
		int fy = top + 24;
		int hx = fx + FIELD_W + 6;
		float step = (float) Math.signum(scrollY) * 0.01f;

		if (inside(x, y, hx - 3, fy - 2, hx + HUE_W + 3, fy + FIELD_H + 2)) {
			hue = (hue - step + 1f) % 1f;
			applyHsv();
			return true;
		}

		if (inside(x, y, fx, fy, fx + FIELD_W, fy + FIELD_H)) {
			brightness = Colors.clamp01(brightness + step);
			applyHsv();
			return true;
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			confirm();
			return true;
		}

		if (hexFocused) {
			if (key == InputConstants.KEY_BACKSPACE) {
				if (!hex.isEmpty()) {
					hex = hex.substring(0, hex.length() - 1);
				}

				return true;
			}

			boolean shortcut = (event.modifiers() & (InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER)) != 0;

			if (shortcut && key == InputConstants.KEY_V) {
				String pasted = this.minecraft.keyboardHandler.getClipboard();
				int rgb = parseHex(pasted == null ? "" : pasted);

				if (rgb >= 0) {
					hexFocused = false;
					setFromRgb(rgb);
					setting.setCustom(rgb);
				}

				return true;
			}
		} else if ((event.modifiers() & (InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER)) != 0 && key == InputConstants.KEY_C) {
			this.minecraft.keyboardHandler.setClipboard("#" + String.format(Locale.ROOT, "%06X", currentRgb()));
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String typed = event.codepointAsString();

		if (typed.length() != 1) {
			return super.charTyped(event);
		}

		char c = typed.charAt(0);

		if (c == '#') {
			hexFocused = true;
			hex = "";
			return true;
		}

		if (!isHexChar(c)) {
			return super.charTyped(event);
		}

		if (!hexFocused) {
			// Typing a hex digit anywhere starts a new hex code
			hexFocused = true;
			hex = "";
		}

		if (hex.length() < 6) {
			hex += Character.toUpperCase(c);
		}

		if (hex.length() == 6) {
			// Complete: apply it, the next digit starts a new code
			leaveHex();
		}

		return true;
	}

	@Override
	public boolean isPauseScreen() {
		// Pause only if the screen below does: over the wardrobe and the Weltprobe menu the live preview behind
		// the panel has to keep moving
		return parent.isPauseScreen();
	}

	@Override
	public void onClose() {
		cancel();
	}

	@Override
	public void removed() {
		super.removed();
		this.minecraft.textInputManager().stopTextInput(this);

		if (!returning) {
			// Closed by the game (disconnect, death screen): keep the color and give the camera back
			Config.save();

			if (parent instanceof CameraKeeper keeper) {
				keeper.releaseCamera();
			}
		}
	}
}

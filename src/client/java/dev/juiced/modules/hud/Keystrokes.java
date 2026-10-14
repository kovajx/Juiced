package dev.juiced.modules.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clicks;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

public final class Keystrokes extends HudModule {
	private static final int KEY = 20;
	private static final int GAP = 2;

	private final BoolSetting mouse = add(new BoolSetting("mouse", "Maustasten", true));
	private final BoolSetting cps = add(new BoolSetting("cps", "CPS anzeigen", true));
	private final BoolSetting space = add(new BoolSetting("space", "Leertaste", true));
	private final BoolSetting sneak = add(new BoolSetting("sneak", "Shift & Sprint", false));
	private final SliderSetting fade = SliderSetting.percent("fade", "Leuchten", 0.0, 1.0, 0.6);

	/** Smoothed "pressed" amount per key: W A S D LMB RMB Space Shift Sprint. */
	private final float[] glow = new float[9];

	public Keystrokes() {
		super("keystrokes", "Keystrokes", false, 0f, 0.62f,
				"Zeigt WASD, Maustasten mit CPS und Leertaste. Gedrückte Tasten leuchten in der Theme-Farbe.");
		add(fade);
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		width = KEY * 3 + GAP * 2;
		height = KEY * 2 + GAP;

		if (mouse.on()) {
			height += GAP + KEY;
		}

		if (space.on()) {
			height += GAP + 10;
		}

		if (sneak.on()) {
			height += GAP + 12;
		}

		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		Options options = hud.minecraft.options;
		KeyMapping[] keys = {options.keyUp, options.keyLeft, options.keyDown, options.keyRight, options.keyAttack, options.keyUse, options.keyJump, options.keyShift, options.keySprint};

		for (int i = 0; i < keys.length; i++) {
			glow[i] = Clock.approach(glow[i], keys[i].isDown() ? 1f : 0f, keys[i].isDown() ? 40f : 9f);
		}

		int col = KEY + GAP;
		key(g, col, 0, KEY, KEY, "W", glow[0], null);
		key(g, 0, col, KEY, KEY, "A", glow[1], null);
		key(g, col, col, KEY, KEY, "S", glow[2], null);
		key(g, col * 2, col, KEY, KEY, "D", glow[3], null);
		int y = col * 2;

		if (mouse.on()) {
			int half = (width - GAP) / 2;
			key(g, 0, y, half, KEY, "LMB", glow[4], cps.on() ? Clicks.leftCps() + " CPS" : null);
			key(g, half + GAP, y, width - half - GAP, KEY, "RMB", glow[5], cps.on() ? Clicks.rightCps() + " CPS" : null);
			y += KEY + GAP;
		}

		if (space.on()) {
			key(g, 0, y, width, 10, "", glow[6], null);
			int barW = width / 3;
			Gui2D.fill(g, (width - barW) / 2, y + 4, (width + barW) / 2, y + 5, Colors.argb(0.8f, Colors.mixRgb(0xCFCFDA, 0x0B0B10, glow[6])));
			y += 10 + GAP;
		}

		if (sneak.on()) {
			int half = (width - GAP) / 2;
			key(g, 0, y, half, 12, "Shift", glow[7], null);
			key(g, half + GAP, y, width - half - GAP, 12, "Sprint", glow[8], null);
		}
	}

	private void key(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, float pressed, String sub) {
		int accent = Theme.accent();
		float bg = Theme.hudBackground();
		int base = Colors.argb(Math.max(0.25f, bg), 0x0D0D13);
		int lit = Colors.argb(0.92f, accent);
		Gui2D.rounded(g, x, y, x + w, y + h, Colors.mix(base, lit, pressed));

		if (pressed > 0.05f && fade.get() > 0) {
			Gui2D.glow(g, x, y, x + w, y + h, accent, pressed * fade.f(), 2);
		}

		int textColor = Colors.mix(0xFFEDEDF2, 0xFF0B0B10, pressed);

		if (sub == null) {
			Gui2D.centered(g, label, x + w / 2f, y + (h - 8) / 2f + 0.5f, textColor, false, h < 14 ? 0.7f : 1f);
		} else {
			Gui2D.centered(g, label, x + w / 2f, y + 3, textColor, false, 0.8f);
			Gui2D.centered(g, sub, x + w / 2f, y + 12, Colors.fade(textColor, 0.8f), false, 0.6f);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int k = 14;
		int gap = 2;
		int ox = x + (w - (k * 3 + gap * 2)) / 2;
		int oy = y + (h - (k * 3 + gap * 2)) / 2;
		String[] labels = {"W", "A", "S", "D"};
		int[][] pos = {{1, 0}, {0, 1}, {1, 1}, {2, 1}};
		int pressedKey = (int) (time * 3) % 4;
		int accent = Theme.accent();

		for (int i = 0; i < 4; i++) {
			int kx = ox + pos[i][0] * (k + gap);
			int ky = oy + pos[i][1] * (k + gap);
			boolean down = i == pressedKey;
			Gui2D.rounded(g, kx, ky, kx + k, ky + k, down ? Colors.argb(1f, accent) : 0x90202028);
			Gui2D.centered(g, labels[i], kx + k / 2f, ky + 3, down ? 0xFF0B0B10 : 0xFFEDEDF2, false, 1f);
		}

		int my = oy + 2 * (k + gap);
		int half = (k * 3 + gap * 2 - gap) / 2;
		boolean click = (time * 6) % 1 < 0.4f;
		Gui2D.rounded(g, ox, my, ox + half, my + k, click ? Colors.argb(1f, accent) : 0x90202028);
		Gui2D.rounded(g, ox + half + gap, my, ox + k * 3 + gap * 2, my + k, 0x90202028);
		Gui2D.centered(g, "12 CPS", ox + half / 2f, my + 4, click ? 0xFF0B0B10 : 0xFFEDEDF2, false, 0.6f);
	}
}

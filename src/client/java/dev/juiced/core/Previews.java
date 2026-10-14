package dev.juiced.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.render.Gui2D;

/** Shared bits for the animated previews on the menu cards. */
public final class Previews {
	private Previews() {
	}

	public static void genericIcon(GuiGraphicsExtractor g, int x, int y, int w, int h, float time, String icon) {
		int accent = Theme.accent(time * 0.1f);
		float pulse = 0.6f + 0.4f * (float) Math.sin(time * 3);
		Gui2D.ellipse(g, x + w / 2f, y + h / 2f, w * 0.28f, h * 0.28f, 18, 2, accent, 0.5f * pulse, time * 0.8f, false);
		Gui2D.centered(g, icon, x + w / 2f, y + h / 2f - 8, Colors.argb(1f, accent), true, 2f);
	}

	/** Ground line + figure in the middle, returns the feet Y. */
	public static float stage(GuiGraphicsExtractor g, int x, int y, int w, int h, float bob) {
		return stage(g, x, y, w, h, x + w / 2f, bob);
	}

	/** Ground line + figure at {@code figureX}, returns the feet Y. */
	public static float stage(GuiGraphicsExtractor g, int x, int y, int w, int h, float figureX, float bob) {
		float feetY = y + h * 0.8f;
		Gui2D.fill(g, x + 10, Math.round(feetY), x + w - 10, Math.round(feetY) + 1, 0x22FFFFFF);
		Gui2D.figure(g, figureX, feetY - bob, 1.1f, 0xFFD8D8E0);
		return feetY;
	}

	/** Fraction (0..1) through a repeating cycle of {@code period} seconds. */
	public static float cycle(float time, float period) {
		float t = time % period;
		return t / period;
	}
}

package dev.juiced.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Colors;

/** Drawing helpers for the HUD and the menu. All colors are 0xAARRGGBB. */
public final class Gui2D {
	private Gui2D() {
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		if (x2 > x1 && y2 > y1 && (color >>> 24) != 0) {
			g.fill(x1, y1, x2, y2, color);
		}
	}

	/** Rectangle with cut corners, which reads as rounded at Minecraft's pixel scale. */
	public static void rounded(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		fill(g, x1 + 1, y1, x2 - 1, y2, color);
		fill(g, x1, y1 + 1, x1 + 1, y2 - 1, color);
		fill(g, x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	/** Larger radius (2px) for big panels. */
	public static void rounded2(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		fill(g, x1 + 2, y1, x2 - 2, y2, color);
		fill(g, x1 + 1, y1 + 1, x1 + 2, y2 - 1, color);
		fill(g, x2 - 2, y1 + 1, x2 - 1, y2 - 1, color);
		fill(g, x1, y1 + 2, x1 + 1, y2 - 2, color);
		fill(g, x2 - 1, y1 + 2, x2, y2 - 2, color);
	}

	/** Vertical gradient from {@code top} to fully transparent. */
	public static void fillGradientSafe(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top) {
		if (x2 > x1 && y2 > y1 && (top >>> 24) != 0) {
			g.fillGradient(x1, y1, x2, y2, top, top & 0x00FFFFFF);
		}
	}

	/** Standard HUD box: dark rounded background (theme opacity) with an optional accent stripe on the left. */
	public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
		float bg = dev.juiced.core.Theme.hudBackground();

		if (bg > 0.01f) {
			rounded2(g, x1, y1, x2, y2, Colors.argb(bg * 0.95f, 0x0D0D13));
		}

		if (dev.juiced.core.Theme.hudAccent()) {
			fill(g, x1, y1 + 3, x1 + 2, y2 - 3, Colors.argb(1f, dev.juiced.core.Theme.accent()));
		}
	}

	public static void outline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		fill(g, x1 + 1, y1, x2 - 1, y1 + 1, color);
		fill(g, x1 + 1, y2 - 1, x2 - 1, y2, color);
		fill(g, x1, y1 + 1, x1 + 1, y2 - 1, color);
		fill(g, x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	/** Soft glow around a rectangle: a few outlines with falling alpha. */
	public static void glow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int rgb, float strength, int size) {
		for (int i = 1; i <= size; i++) {
			float a = strength * (1f - (i - 1f) / size) * 0.45f;
			outline(g, x1 - i, y1 - i, x2 + i, y2 + i, Colors.argb(a, rgb));
		}
	}

	public static void dot(GuiGraphicsExtractor g, float x, float y, float size, int color) {
		float h = Math.max(0.5f, size / 2f);
		fill(g, Math.round(x - h), Math.round(y - h), Math.round(x + h), Math.round(y + h), color);
	}

	/** A dot with a faint halo, used for "lights" in previews and HUD. */
	public static void glowDot(GuiGraphicsExtractor g, float x, float y, float size, int rgb, float alpha) {
		dot(g, x, y, size * 3f, Colors.argb(alpha * 0.12f, rgb));
		dot(g, x, y, size * 2f, Colors.argb(alpha * 0.3f, rgb));
		dot(g, x, y, size, Colors.argb(alpha, Colors.shade(rgb, 0.35f)));
	}

	/** Dots along an ellipse; {@code alphaAt} fades parts of the ring (for "behind" segments). */
	public static void ellipse(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int count, float size, int rgb, float alpha, float phase, boolean depthFade) {
		for (int i = 0; i < count; i++) {
			double angle = phase + i * Math.PI * 2 / count;
			float x = cx + (float) Math.cos(angle) * rx;
			float y = cy + (float) Math.sin(angle) * ry;
			float a = alpha;

			if (depthFade) {
				a *= 0.45f + 0.55f * (float) ((Math.sin(angle) + 1) / 2);
			}

			dot(g, x, y, size, Colors.argb(a, rgb));
		}
	}

	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int fg, int bg) {
		fill(g, x, y, x + w, y + h, bg);
		fill(g, x, y, x + Math.round(w * Colors.clamp01(fraction)), y + h, fg);
	}

	public static void text(GuiGraphicsExtractor g, String text, float x, float y, int color, boolean shadow, float scale) {
		if ((color >>> 24) < 4) {
			return;
		}

		if (scale == 1f) {
			g.text(font(), text, Math.round(x), Math.round(y), color, shadow);
			return;
		}

		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale);
		g.text(font(), text, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public static void centered(GuiGraphicsExtractor g, String text, float cx, float y, int color, boolean shadow, float scale) {
		float width = font().width(text) * scale;
		text(g, text, cx - width / 2f, y, color, shadow, scale);
	}

	public static int width(String text, float scale) {
		return Math.round(font().width(text) * scale);
	}

	/** Blocky little player figure for previews. {@code feetY} is where the feet touch the ground. */
	public static void figure(GuiGraphicsExtractor g, float cx, float feetY, float s, int color) {
		int c = color;
		int dark = Colors.mix(color, 0xFF000000, 0.35f);
		fill(g, Math.round(cx - 3 * s), Math.round(feetY - 32 * s), Math.round(cx + 3 * s), Math.round(feetY - 26 * s), c); // head
		fill(g, Math.round(cx - 4 * s), Math.round(feetY - 26 * s), Math.round(cx + 4 * s), Math.round(feetY - 14 * s), dark); // body
		fill(g, Math.round(cx - 6 * s), Math.round(feetY - 26 * s), Math.round(cx - 4 * s), Math.round(feetY - 16 * s), c); // arms
		fill(g, Math.round(cx + 4 * s), Math.round(feetY - 26 * s), Math.round(cx + 6 * s), Math.round(feetY - 16 * s), c);
		fill(g, Math.round(cx - 4 * s), Math.round(feetY - 14 * s), Math.round(cx - 0.5f * s), Math.round(feetY), c); // legs
		fill(g, Math.round(cx + 0.5f * s), Math.round(feetY - 14 * s), Math.round(cx + 4 * s), Math.round(feetY), c);
	}

	/** Splits text into lines that fit {@code maxWidth} pixels at the given scale. */
	public static java.util.List<String> wrap(String text, int maxWidth, float scale) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();

		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;

			if (width(candidate, scale) > maxWidth && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}

		if (!line.isEmpty()) {
			lines.add(line.toString());
		}

		return lines;
	}
}

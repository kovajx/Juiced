package dev.juiced.gui.weltprobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Theme;
import dev.juiced.render.Gui2D;

/**
 * Colours and small drawing helpers shared by the Weltprobe chrome. Every helper multiplies its colours by
 * {@link #alpha}, so a whole panel can fade (opening, a HUD element underneath) without each caller doing it.
 */
final class Ui {
	static final int PANEL = 0xCC111117;
	static final int PANEL_SOLID = 0xF2111117;
	static final int EDGE = 0xFF2A2A36;
	static final int TEXT = 0xFFEDEDF2;
	static final int DIM = 0xFF8A8A99;
	static final int MUTED = 0xFF55556A;
	static final int KEY_BG = 0xFF1E1E28;
	static final int KEY_EDGE = 0xFF3A3A48;
	static final int BUTTON_HOVER = 0xFF2C2C3A;
	static final int INK = 0xFF0B0B10;
	static final int LAMP_OFF = 0xFF2A2433;
	static final int ERROR = 0xFFFF6B6B;
	static final int SWITCH_OFF = 0xFF22222C;
	static final int HAIRLINE = 0x24FFFFFF;
	/** Text line height at scale 1. */
	static final int LINE = 9;

	/** Opacity of everything drawn through these helpers right now. */
	static float alpha = 1f;

	private Ui() {
	}

	/** The theme colour with an alpha; read every frame because the theme can change while the menu is open. */
	static int accent(float a) {
		return Colors.argb(a, Theme.accent());
	}

	static int cat(Category category, float a) {
		return Colors.argb(a, category.color);
	}

	private static int faded(int argb) {
		return alpha >= 0.999f ? argb : Colors.fade(argb, alpha);
	}

	static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
		Gui2D.fill(g, x1, y1, x2, y2, faded(argb));
	}

	/** Square 1 px frame just inside the box. */
	static void frame(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
		int c = faded(argb);
		Gui2D.fill(g, x1, y1, x2, y1 + 1, c);
		Gui2D.fill(g, x1, y2 - 1, x2, y2, c);
		Gui2D.fill(g, x1, y1 + 1, x1 + 1, y2 - 1, c);
		Gui2D.fill(g, x2 - 1, y1 + 1, x2, y2 - 1, c);
	}

	/** A panel: background plus a 1 px frame. */
	static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int bg, int edge) {
		fill(g, x1, y1, x2, y2, bg);
		frame(g, x1, y1, x2, y2, edge);
	}

	static void text(GuiGraphicsExtractor g, String text, int x, int y, int argb) {
		Gui2D.text(g, text, x, y, faded(argb), false, 1f);
	}

	static void shadowText(GuiGraphicsExtractor g, String text, int x, int y, int argb, float scale) {
		Gui2D.text(g, text, x, y, faded(argb), true, scale);
	}

	static void centered(GuiGraphicsExtractor g, String text, float cx, int y, int argb) {
		Gui2D.text(g, text, Math.round(cx - width(text) / 2f), y, faded(argb), false, 1f);
	}

	static int width(String text) {
		return Gui2D.width(text, 1f);
	}

	/** Shortens {@code text} with "…" until it fits {@code maxWidth} at scale 1. */
	static String ellipsize(String text, int maxWidth) {
		return ellipsize(text, maxWidth, "…", 1f);
	}

	static String ellipsize(String text, int maxWidth, String tail, float scale) {
		if (Gui2D.width(text, scale) <= maxWidth) {
			return text;
		}

		String result = text;

		while (!result.isEmpty() && Gui2D.width(result + tail, scale) > maxWidth) {
			result = result.substring(0, result.length() - 1);
		}

		// Don't end on a space or a comma before the dots
		while (!result.isEmpty() && " ,:;-".indexOf(result.charAt(result.length() - 1)) >= 0) {
			result = result.substring(0, result.length() - 1);
		}

		return result.isEmpty() ? "" : result + tail;
	}

	/** The first sentence of a description, or the whole text if it has no sentence end. */
	static String firstSentence(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);

			if ((c == '.' || c == '!' || c == '?') && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) {
				return text.substring(0, i + 1);
			}
		}

		return text;
	}

	static int keycapWidth(String label) {
		return Math.max(10, width(label) + 4);
	}

	/** A key cap like "Tab" or "Strg+Z": dark box with a lighter edge. Returns its width. */
	static int keycap(GuiGraphicsExtractor g, String label, int x, int y, int h, int textColor) {
		int w = keycapWidth(label);
		panel(g, x, y, x + w, y + h, KEY_BG, KEY_EDGE);
		centered(g, label, x + w / 2f, y + (h - 8) / 2 + 1, textColor);
		return w;
	}

	/** Upward triangle with its tip at (cx, y), {@code rows} pixels tall. */
	static void triangleUp(GuiGraphicsExtractor g, int cx, int y, int rows, int argb) {
		for (int r = 0; r < rows; r++) {
			fill(g, cx - r, y + r, cx + r + 1, y + r + 1, argb);
		}
	}

	/** Downward triangle with its flat side at y and the tip {@code rows} pixels below. */
	static void triangleDown(GuiGraphicsExtractor g, int cx, int y, int rows, int argb) {
		for (int r = 0; r < rows; r++) {
			int half = rows - 1 - r;
			fill(g, cx - half, y + r, cx + half + 1, y + r + 1, argb);
		}
	}

	/** 1 px diagonal stripes every 4 px over a box (a lamp the server blocks). */
	static void hatch(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
		for (int y = y1; y < y2; y++) {
			for (int x = x1 + Math.floorMod(y1 - y, 4); x < x2; x += 4) {
				fill(g, x, y, x + 1, y + 1, argb);
			}
		}
	}

	static boolean inside(double px, double py, int x1, int y1, int x2, int y2) {
		return px >= x1 && px < x2 && py >= y1 && py < y2;
	}

	static boolean overlaps(int ax1, int ay1, int ax2, int ay2, int bx1, int by1, int bx2, int by2) {
		return ax1 < bx2 && bx1 < ax2 && ay1 < by2 && by1 < ay2;
	}

	/** 0..1 progress of an animation that started at {@code start} and lasts {@code seconds}. */
	static float progress(double now, double start, double seconds) {
		return Colors.clamp01((float) ((now - start) / seconds));
	}
}

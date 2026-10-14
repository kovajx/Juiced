package dev.juiced.core;

/** Color helpers. Colors are ints: 0xAARRGGBB, or 0xRRGGBB where noted. */
public final class Colors {
	public static final String[] PALETTE_NAMES = {
			"Cyan", "Blau", "Violett", "Pink", "Rot", "Orange", "Gold", "Lime", "Mint", "Weiß", "Schwarz", "Braun", "Grau"
	};
	public static final int[] PALETTE = {
			0x35D0FF, 0x4D7CFF, 0xA77BFF, 0xFF4FA3, 0xFF4D4D, 0xFF9A3D, 0xFFC23D, 0x8CFF4F, 0x3DFFB0, 0xFFFFFF, 0x24242C, 0x8B5A2B, 0x8A8A99
	};

	public static final int PINK = 3;
	public static final int RED = 4;
	public static final int ORANGE = 5;
	public static final int GOLD = 6;
	public static final int WHITE = 9;
	public static final int BLACK = 10;
	public static final int BROWN = 11;
	public static final int GRAY = 12;

	/** Free colors picked recently in the color picker (newest first), shared by all modules. */
	public static final java.util.List<Integer> RECENT = new java.util.ArrayList<>();

	public static void remember(int rgb) {
		RECENT.remove(Integer.valueOf(rgb & 0xFFFFFF));
		RECENT.add(0, rgb & 0xFFFFFF);

		while (RECENT.size() > 8) {
			RECENT.remove(RECENT.size() - 1);
		}
	}

	/** Hue, saturation and value (0..1) of an 0xRRGGBB color. */
	public static float[] toHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h = 0f;

		if (delta > 1.0e-5f) {
			if (max == r) {
				h = ((g - b) / delta) % 6f;
			} else if (max == g) {
				h = (b - r) / delta + 2f;
			} else {
				h = (r - g) / delta + 4f;
			}

			h /= 6f;

			if (h < 0f) {
				h += 1f;
			}
		}

		return new float[] {h, max <= 0f ? 0f : delta / max, max};
	}

	private Colors() {
	}

	/** Combines an alpha (0..1) with an 0xRRGGBB color. */
	public static int argb(float alpha, int rgb) {
		int a = Math.round(clamp01(alpha) * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Multiplies the alpha of an ARGB color. */
	public static int fade(int argb, float mul) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(mul));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int from, int to, float t) {
		t = clamp01(t);
		int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
		int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = lerp(from & 0xFF, to & 0xFF, t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Mixes two 0xRRGGBB colors. */
	public static int mixRgb(int from, int to, float t) {
		return mix(0xFF000000 | from, 0xFF000000 | to, t) & 0xFFFFFF;
	}

	/** Hue, saturation, value (all 0..1) to 0xRRGGBB. */
	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		int i = (int) (h * 6f);
		float f = h * 6f - i;
		float p = v * (1f - s);
		float q = v * (1f - f * s);
		float t = v * (1f - (1f - f) * s);
		float r;
		float g;
		float b;

		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}

		return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}

	/** Lightens (t > 0, towards white) or darkens (t < 0, towards black) an 0xRRGGBB color. */
	public static int shade(int rgb, float t) {
		return t >= 0 ? mixRgb(rgb, 0xFFFFFF, t) : mixRgb(rgb, 0x000000, -t);
	}

	public static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}
}

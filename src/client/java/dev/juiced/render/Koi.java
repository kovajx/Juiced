package dev.juiced.render;

import java.util.Random;

import dev.juiced.core.Colors;

/**
 * A koi fish drawn as a flat, gently swimming shape in a plane. The body is a strip that bends
 * with a travelling wave, with a tail fin and two side fins. Used by the koi pond trail and
 * the koi companion.
 */
public final class Koi {
	public static final int SEGMENTS = 8;
	public static final String[] LOOKS = {"Klassisch", "Gold", "Geister", "Bunt"};

	private static final float[] WIDTH = {0.55f, 0.85f, 1f, 0.97f, 0.86f, 0.7f, 0.52f, 0.34f, 0.2f};
	private static final int WHITE = 0xF4F1EA;
	private static final int RED = 0xE8552B;
	private static final int BLACK = 0x23232B;

	// Scratch space, only used on the render thread
	private static final double[] PX = new double[SEGMENTS + 1];
	private static final double[] PY = new double[SEGMENTS + 1];
	private static final double[] PZ = new double[SEGMENTS + 1];
	private static final float[] SX = new float[SEGMENTS + 1];
	private static final float[] SY = new float[SEGMENTS + 1];
	private static final float[] SZ = new float[SEGMENTS + 1];
	private static final float[] TX = new float[SEGMENTS + 1];
	private static final float[] TY = new float[SEGMENTS + 1];
	private static final float[] TZ = new float[SEGMENTS + 1];

	private Koi() {
	}

	/** Fills {@code out} (SEGMENTS + 1 colors, head first) with the scale pattern of one fish. */
	public static void pattern(int look, long seed, int[] out) {
		Random random = new Random(seed * 0x5DEECE66DL + 11);

		switch (look) {
			case 1 -> {
				// Ogon: metallic gold, now and then a platinum one
				boolean platinum = random.nextInt(4) == 0;
				int base = platinum ? 0xE6E8EE : 0xF2B33D;

				for (int k = 0; k <= SEGMENTS; k++) {
					out[k] = Colors.shade(base, 0.25f - k * 0.04f);
				}
			}
			case 2 -> {
				for (int k = 0; k <= SEGMENTS; k++) {
					out[k] = Colors.mixRgb(0xCFF6FF, 0x6FB8FF, k / (float) SEGMENTS);
				}
			}
			case 3 -> {
				int base = Colors.hsv(random.nextFloat(), 0.45f, 1f);

				for (int k = 0; k <= SEGMENTS; k++) {
					out[k] = random.nextFloat() < 0.3f ? WHITE : base;
				}
			}
			default -> {
				int variety = random.nextInt(3);

				for (int k = 0; k <= SEGMENTS; k++) {
					out[k] = switch (variety) {
						// Tancho: white with one red spot on the head
						case 2 -> k == 1 ? RED : WHITE;
						// Showa: black base with red and white
						case 1 -> random.nextFloat() < 0.45f ? RED : random.nextFloat() < 0.5f ? WHITE : BLACK;
						// Kohaku: white with red patches
						default -> random.nextFloat() < 0.5f ? RED : WHITE;
					};
				}

				// Patches look nicer in blobs than single segments
				for (int k = 1; k < SEGMENTS; k++) {
					if (out[k - 1] == out[k + 1] && random.nextBoolean()) {
						out[k] = out[k - 1];
					}
				}
			}
		}
	}

	/**
	 * Draws one koi. The head is at (hx, hy, hz) and the fish swims along {@code forward}; {@code side}
	 * lies in the body plane. {@code wag} (0..1.5) is how hard it swims. The shadow offset is skipped when zero.
	 */
	public static void draw(Geo geo, double hx, double hy, double hz,
			float fx, float fy, float fz, float sx, float sy, float sz,
			float length, float time, float phase, float wag, int[] colors, float alpha,
			double shadowX, double shadowY, double shadowZ) {
		if (alpha <= 0.01f) {
			return;
		}

		// Plane normal = forward x side
		float nx = fy * sz - fz * sy;
		float ny = fz * sx - fx * sz;
		float nz = fx * sy - fy * sx;
		float speed = 3.5f + 3.5f * wag;
		float amplitude = length * 0.12f * (0.45f + 0.55f * Math.min(1.2f, wag));

		for (int k = 0; k <= SEGMENTS; k++) {
			float t = k / (float) SEGMENTS;
			float lateral = (float) Math.sin(time * speed + phase - k * 0.8f) * amplitude * (float) Math.pow(t, 1.4);
			double back = t * length;
			PX[k] = hx - fx * back + sx * lateral;
			PY[k] = hy - fy * back + sy * lateral;
			PZ[k] = hz - fz * back + sz * lateral;
		}

		for (int k = 0; k <= SEGMENTS; k++) {
			int a = Math.max(0, k - 1);
			int b = Math.min(SEGMENTS, k + 1);
			// Tangent pointing to the head
			float tx = (float) (PX[a] - PX[b]);
			float ty = (float) (PY[a] - PY[b]);
			float tz = (float) (PZ[a] - PZ[b]);
			float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);

			if (len < 1.0e-6f) {
				tx = fx;
				ty = fy;
				tz = fz;
			} else {
				tx /= len;
				ty /= len;
				tz /= len;
			}

			TX[k] = tx;
			TY[k] = ty;
			TZ[k] = tz;
			// Side in the body plane = normal x tangent
			SX[k] = ny * tz - nz * ty;
			SY[k] = nz * tx - nx * tz;
			SZ[k] = nx * ty - ny * tx;
		}

		boolean hasShadow = shadowX != 0 || shadowY != 0 || shadowZ != 0;

		if (hasShadow) {
			strip(geo, length, colors, alpha, true, shadowX, shadowY, shadowZ);
		}

		strip(geo, length, colors, alpha, false, 0, 0, 0);
		fins(geo, length, colors, alpha, time * speed + phase);
	}

	private static void strip(Geo geo, float length, int[] colors, float alpha, boolean shadow, double ox, double oy, double oz) {
		for (int k = 0; k < SEGMENTS; k++) {
			int j = k + 1;
			float w0 = WIDTH[k] * length * 0.16f;
			float w1 = WIDTH[j] * length * 0.16f;
			int c0 = shadow ? Colors.argb(alpha * 0.22f, 0x06121C) : Colors.argb(alpha, colors[k]);
			int c1 = shadow ? Colors.argb(alpha * 0.22f, 0x06121C) : Colors.argb(alpha, colors[j]);
			geo.quadWorld(PX[k] - SX[k] * w0 + ox, PY[k] - SY[k] * w0 + oy, PZ[k] - SZ[k] * w0 + oz, c0,
					PX[k] + SX[k] * w0 + ox, PY[k] + SY[k] * w0 + oy, PZ[k] + SZ[k] * w0 + oz, c0,
					PX[j] + SX[j] * w1 + ox, PY[j] + SY[j] * w1 + oy, PZ[j] + SZ[j] * w1 + oz, c1,
					PX[j] - SX[j] * w1 + ox, PY[j] - SY[j] * w1 + oy, PZ[j] - SZ[j] * w1 + oz, c1);
		}
	}

	private static void fins(Geo geo, float length, int[] colors, float alpha, float wave) {
		int n = SEGMENTS;
		int finColor = Colors.argb(alpha * 0.7f, Colors.mixRgb(colors[n], 0xFFFFFF, 0.35f));
		float flutter = (float) Math.sin(wave - n * 0.8f + 0.6f) * 0.3f;
		float finLength = length * 0.26f;
		float spread = length * 0.18f;
		// Tail fin: a fan behind the last segment
		double bx = PX[n] - TX[n] * finLength;
		double by = PY[n] - TY[n] * finLength;
		double bz = PZ[n] - TZ[n] * finLength;
		double mx = PX[n] - TX[n] * finLength * 0.55;
		double my = PY[n] - TY[n] * finLength * 0.55;
		double mz = PZ[n] - TZ[n] * finLength * 0.55;
		float up = spread * (1f + flutter);
		float down = spread * (1f - flutter);
		geo.triWorld(PX[n], PY[n], PZ[n], finColor, bx + SX[n] * up, by + SY[n] * up, bz + SZ[n] * up, finColor, mx, my, mz, finColor);
		geo.triWorld(PX[n], PY[n], PZ[n], finColor, mx, my, mz, finColor, bx - SX[n] * down, by - SY[n] * down, bz - SZ[n] * down, finColor);

		// Side fins behind the head
		int k = 2;
		float w = WIDTH[k] * length * 0.16f;
		float out = w + length * 0.12f;
		int sideFin = Colors.argb(alpha * 0.55f, Colors.mixRgb(colors[k], 0xFFFFFF, 0.4f));

		for (int sign = -1; sign <= 1; sign += 2) {
			double ax = PX[k] + SX[k] * w * 0.6 * sign;
			double ay = PY[k] + SY[k] * w * 0.6 * sign;
			double az = PZ[k] + SZ[k] * w * 0.6 * sign;
			geo.triWorld(ax + TX[k] * length * 0.03, ay + TY[k] * length * 0.03, az + TZ[k] * length * 0.03, sideFin,
					ax - TX[k] * length * 0.06, ay - TY[k] * length * 0.06, az - TZ[k] * length * 0.06, sideFin,
					PX[k] + SX[k] * out * sign - TX[k] * length * 0.08, PY[k] + SY[k] * out * sign - TY[k] * length * 0.08,
					PZ[k] + SZ[k] * out * sign - TZ[k] * length * 0.08, sideFin);
		}
	}
}

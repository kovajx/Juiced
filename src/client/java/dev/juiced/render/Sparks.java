package dev.juiced.render;

import java.util.concurrent.ThreadLocalRandom;

import dev.juiced.core.Theme;

/**
 * Our own tiny particle system for colored effects (sparks, stars, orbs). Stored as plain arrays,
 * no objects per particle, with a hard limit from the performance settings.
 */
public final class Sparks {
	public static final int DOT = 0;
	public static final int STAR = 1;
	public static final int STREAK = 2;

	private static final int CAPACITY = 600;
	private static final double[] X = new double[CAPACITY];
	private static final double[] Y = new double[CAPACITY];
	private static final double[] Z = new double[CAPACITY];
	private static final float[] VX = new float[CAPACITY];
	private static final float[] VY = new float[CAPACITY];
	private static final float[] VZ = new float[CAPACITY];
	private static final float[] AGE = new float[CAPACITY];
	private static final float[] LIFE = new float[CAPACITY];
	private static final float[] SIZE = new float[CAPACITY];
	private static final float[] ROT = new float[CAPACITY];
	private static final float[] SPIN = new float[CAPACITY];
	private static final float[] GRAVITY = new float[CAPACITY];
	private static final float[] DRAG = new float[CAPACITY];
	private static final int[] RGB = new int[CAPACITY];
	private static final byte[] KIND = new byte[CAPACITY];
	private static int count;

	private Sparks() {
	}

	public static int count() {
		return count;
	}

	public static void spawn(double x, double y, double z, float vx, float vy, float vz,
			float life, float size, int rgb, int kind, float gravity, float drag) {
		int limit = Math.min(CAPACITY, Theme.maxSparks());

		if (count >= limit) {
			return;
		}

		int i = count++;
		X[i] = x;
		Y[i] = y;
		Z[i] = z;
		VX[i] = vx;
		VY[i] = vy;
		VZ[i] = vz;
		AGE[i] = 0;
		LIFE[i] = life;
		SIZE[i] = size;
		ROT[i] = ThreadLocalRandom.current().nextFloat() * 6.28f;
		SPIN[i] = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 8f;
		GRAVITY[i] = gravity;
		DRAG[i] = drag;
		RGB[i] = rgb;
		KIND[i] = (byte) kind;
	}

	/** Sphere-shaped burst. {@code upBias} pushes the burst upwards (0 = even). */
	public static void burst(double x, double y, double z, int amount, float speed, float size, float life,
			Geo.Tint tint, int kind, float gravity, float upBias) {
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < amount; i++) {
			double theta = random.nextDouble() * Math.PI * 2;
			double phi = Math.acos(2 * random.nextDouble() - 1);
			float s = speed * (0.45f + random.nextFloat() * 0.75f);
			float vx = (float) (Math.sin(phi) * Math.cos(theta)) * s;
			float vy = (float) Math.cos(phi) * s + upBias * speed;
			float vz = (float) (Math.sin(phi) * Math.sin(theta)) * s;
			float l = life * (0.7f + random.nextFloat() * 0.6f);
			spawn(x, y, z, vx, vy, vz, l, size * (0.7f + random.nextFloat() * 0.6f), tint.rgb(random.nextFloat()), kind, gravity, 2.2f);
		}
	}

	public static void update(float dt) {
		int i = 0;

		while (i < count) {
			AGE[i] += dt;

			if (AGE[i] >= LIFE[i]) {
				removeAt(i);
				continue;
			}

			float damping = (float) Math.exp(-DRAG[i] * dt);
			VX[i] *= damping;
			VY[i] = VY[i] * damping - GRAVITY[i] * dt;
			VZ[i] *= damping;
			X[i] += VX[i] * dt;
			Y[i] += VY[i] * dt;
			Z[i] += VZ[i] * dt;
			ROT[i] += SPIN[i] * dt;
			i++;
		}
	}

	public static void render(Geo geo) {
		double maxDistanceSq = Theme.effectRange() * Theme.effectRange();

		for (int i = 0; i < count; i++) {
			if (geo.distanceSq(X[i], Y[i], Z[i]) > maxDistanceSq) {
				continue;
			}

			float t = AGE[i] / LIFE[i];
			float alpha = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
			float size = SIZE[i] * (1f - t * 0.5f);

			switch (KIND[i]) {
				case STAR -> geo.star(X[i], Y[i], Z[i], size, ROT[i], RGB[i], alpha);
				case STREAK -> {
					float stretch = 0.045f;
					int head = dev.juiced.core.Colors.argb(alpha, dev.juiced.core.Colors.shade(RGB[i], 0.5f));
					int tail = dev.juiced.core.Colors.argb(0f, RGB[i]);
					geo.line(X[i], Y[i], Z[i], X[i] - VX[i] * stretch, Y[i] - VY[i] * stretch, Z[i] - VZ[i] * stretch, size * 0.25f, head, tail);
				}
				default -> geo.glowDot(X[i], Y[i], Z[i], size, RGB[i], alpha);
			}
		}
	}

	public static void clear() {
		count = 0;
	}

	private static void removeAt(int i) {
		int last = --count;

		if (i == last) {
			return;
		}

		X[i] = X[last];
		Y[i] = Y[last];
		Z[i] = Z[last];
		VX[i] = VX[last];
		VY[i] = VY[last];
		VZ[i] = VZ[last];
		AGE[i] = AGE[last];
		LIFE[i] = LIFE[last];
		SIZE[i] = SIZE[last];
		ROT[i] = ROT[last];
		SPIN[i] = SPIN[last];
		GRAVITY[i] = GRAVITY[last];
		DRAG[i] = DRAG[last];
		RGB[i] = RGB[last];
		KIND[i] = KIND[last];
	}
}

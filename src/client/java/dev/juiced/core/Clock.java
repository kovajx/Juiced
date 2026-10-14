package dev.juiced.core;

/** Real-time clock for animations, so effects stay smooth at any frame rate and keep moving while paused. */
public final class Clock {
	private static final long START = System.nanoTime();
	private static long lastFrame = System.nanoTime();
	private static float frameDelta = 0.016f;

	private Clock() {
	}

	/** Seconds since the game started. */
	public static double t() {
		return (System.nanoTime() - START) / 1.0e9;
	}

	/** Seconds since the game started, as a float for short animations. */
	public static float tf() {
		return (float) (t() % 10_000.0);
	}

	/** Called by the world renderer, the HUD and the menu; extra calls in the same frame are ignored. */
	public static void frame() {
		long now = System.nanoTime();

		if (now - lastFrame < 2_000_000L) {
			return;
		}

		frameDelta = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
	}

	/** Seconds since the previous frame (capped at 0.1). */
	public static float dt() {
		return frameDelta;
	}

	public static float easeOut(float t) {
		t = Colors.clamp01(t);
		float inv = 1f - t;
		return 1f - inv * inv * inv;
	}

	public static float easeInOut(float t) {
		t = Colors.clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	/** Moves {@code current} towards {@code target}, frame-rate independent. */
	public static float approach(float current, float target, float speed) {
		float k = 1f - (float) Math.exp(-speed * frameDelta);
		return current + (target - current) * k;
	}
}

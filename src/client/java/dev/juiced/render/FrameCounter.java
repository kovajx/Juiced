package dev.juiced.render;

/** Counts rendered frames to show FPS without touching the game's internals. */
public final class FrameCounter {
	private static int frames;
	private static int fps;
	private static long windowStart = System.nanoTime();

	private FrameCounter() {
	}

	public static void frame() {
		frames++;
		long now = System.nanoTime();

		if (now - windowStart >= 1_000_000_000L) {
			fps = Math.round(frames * 1.0e9f / (now - windowStart));
			frames = 0;
			windowStart = now;
		}
	}

	public static int fps() {
		return fps;
	}
}

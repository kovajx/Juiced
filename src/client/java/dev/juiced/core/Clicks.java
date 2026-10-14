package dev.juiced.core;

/**
 * Remembers recent mouse clicks for the CPS counter. Fed by MouseHandlerMixin
 * with every real button press while no menu is open.
 */
public final class Clicks {
	private static final int SIZE = 64;
	private static final long[] LEFT = new long[SIZE];
	private static final long[] RIGHT = new long[SIZE];
	private static int leftHead;
	private static int rightHead;

	private Clicks() {
	}

	public static void press(int button) {
		long now = System.nanoTime();

		if (button == 0) {
			LEFT[leftHead] = now;
			leftHead = (leftHead + 1) % SIZE;
		} else if (button == 1) {
			RIGHT[rightHead] = now;
			rightHead = (rightHead + 1) % SIZE;
		}
	}

	/** Clicks in the last second. */
	public static int leftCps() {
		return count(LEFT);
	}

	public static int rightCps() {
		return count(RIGHT);
	}

	private static int count(long[] times) {
		long cutoff = System.nanoTime() - 1_000_000_000L;
		int n = 0;

		for (long time : times) {
			if (time != 0 && time > cutoff) {
				n++;
			}
		}

		return n;
	}
}

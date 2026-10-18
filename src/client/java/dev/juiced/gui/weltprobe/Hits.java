package dev.juiced.gui.weltprobe;

import java.util.ArrayList;
import java.util.List;

/**
 * Click areas of one frame, rebuilt while drawing. Later areas lie on top. Panels register a "blocker" so a
 * click on their empty background doesn't fall through to the stage (which would turn the camera).
 */
final class Hits {
	@FunctionalInterface
	interface Click {
		/** {@code button}: 0 left, 1 right. */
		void run(int button, boolean shift);
	}

	private record Box(int x1, int y1, int x2, int y2, boolean pointer, Click action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final List<Box> boxes = new ArrayList<>();

	void clear() {
		boxes.clear();
	}

	/** A clickable area (shows the hand cursor). */
	void add(int x1, int y1, int x2, int y2, Click action) {
		boxes.add(new Box(x1, y1, x2, y2, true, action));
	}

	/** Left click only. */
	void add(int x1, int y1, int x2, int y2, Runnable action) {
		add(x1, y1, x2, y2, (button, shift) -> {
			if (button == 0) {
				action.run();
			}
		});
	}

	/** A panel background: swallows clicks, no hand cursor. */
	void block(int x1, int y1, int x2, int y2) {
		boxes.add(new Box(x1, y1, x2, y2, false, (button, shift) -> {
		}));
	}

	/** Runs the topmost area under the point. Returns false if there is none (the click is on the stage). */
	boolean click(double x, double y, int button, boolean shift) {
		for (int i = boxes.size() - 1; i >= 0; i--) {
			Box box = boxes.get(i);

			if (box.contains(x, y)) {
				box.action().run(button, shift);
				return true;
			}
		}

		return false;
	}

	/** True if the topmost area under the point is clickable (for the hand cursor). */
	boolean pointerAt(double x, double y) {
		for (int i = boxes.size() - 1; i >= 0; i--) {
			Box box = boxes.get(i);

			if (box.contains(x, y)) {
				return box.pointer();
			}
		}

		return false;
	}

	/** True if any area (clickable or panel) covers the point. */
	boolean covers(double x, double y) {
		for (Box box : boxes) {
			if (box.contains(x, y)) {
				return true;
			}
		}

		return false;
	}
}

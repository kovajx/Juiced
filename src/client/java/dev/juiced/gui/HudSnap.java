package dev.juiced.gui;

import java.util.List;

import dev.juiced.core.HudModule;

/**
 * Snapping for dragged HUD elements, shared by the HUD editor and the Weltprobe menu: the box snaps to the screen
 * edges (with the HUD padding), the centre lines and the edges, middles and 2 px gaps of the other boxes, within
 * 4 px; Shift drags freely.
 */
public final class HudSnap {
	/** Snapped top-left, and the guide lines it snapped to (-1 = none) for drawing. */
	public record Result(int x, int y, int guideX, int guideY) {
	}

	private HudSnap() {
	}

	/**
	 * Snaps the top-left {@code (x, y)} of a {@code w} x {@code h} box. {@code others} are the boxes of the other
	 * shown elements as {x, y, w, h}. With {@code free} it only clamps to the screen.
	 */
	public static Result snap(HudModule dragged, int x, int y, int w, int h, int screenW, int screenH, List<int[]> others, boolean free) {
		return new Result(x, y, -1, -1); // TODO(gui-support)
	}
}

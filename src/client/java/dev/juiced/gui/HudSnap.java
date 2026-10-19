package dev.juiced.gui;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.juiced.core.HudModule;

/**
 * Snapping for dragged HUD elements, shared by the HUD editor and the Weltprobe menu: the box snaps to the screen
 * edges (with the HUD padding), the centre lines and the edges, middles and 2 px gaps of the other boxes, within
 * 4 px; Shift drags freely.
 */
public final class HudSnap {
	/** How close (in GUI pixels) an edge or middle has to come to a line to snap onto it. */
	public static final int SNAP = 4;
	/** Gap left when an element is stacked next to another one. */
	private static final int GAP = 2;

	/** Snapped top-left, and the guide lines it snapped to (-1 = none) for drawing. */
	public record Result(int x, int y, int guideX, int guideY) {
	}

	private HudSnap() {
	}

	/**
	 * Snaps the top-left {@code (x, y)} of a {@code w} x {@code h} box. {@code others} are the boxes of the other
	 * shown elements as {x, y, w, h}. With {@code free} it only clamps to the screen.
	 *
	 * <p>A box in {@code others} that is exactly where {@code dragged} sits right now is skipped: snapping to its
	 * own last position would hold the element in place on slow drags.
	 */
	public static Result snap(@Nullable HudModule dragged, int x, int y, int w, int h, int screenW, int screenH, List<int[]> others, boolean free) {
		float snappedX = x;
		float snappedY = y;
		int guideX = -1;
		int guideY = -1;

		if (!free) {
			List<int[]> targets = withoutSelf(dragged, screenW, screenH, others);
			float[] alongX = snapAxis(x, w, screenW, targets, true);
			float[] alongY = snapAxis(y, h, screenH, targets, false);
			snappedX = alongX[0];
			snappedY = alongY[0];

			if (alongX[1] >= 0) {
				guideX = Math.round(alongX[1]);
			}

			if (alongY[1] >= 0) {
				guideY = Math.round(alongY[1]);
			}
		}

		return new Result(Math.round(clamp(snappedX, w, screenW)), Math.round(clamp(snappedY, h, screenH)), guideX, guideY);
	}

	/**
	 * Snaps one axis of a box at {@code position} with {@code size} on a screen {@code screen} long. Returns
	 * {new position, guide line or -1}; the position is not clamped yet (see {@link #clamp}). Lines: the screen
	 * edges with the HUD padding, the screen middle, and the start, end, middle and 2 px gaps of the other boxes.
	 * The box's start, middle and end are tried against each line; the nearest within {@link #SNAP} px wins.
	 */
	public static float[] snapAxis(float position, int size, int screen, List<int[]> others, boolean horizontal) {
		List<Float> lines = new ArrayList<>();
		lines.add((float) HudModule.PAD);
		lines.add((float) (screen - HudModule.PAD));
		lines.add(screen / 2f);

		for (int[] box : others) {
			float start = horizontal ? box[0] : box[1];
			float length = horizontal ? box[2] : box[3];
			lines.add(start);
			lines.add(start + length);
			lines.add(start + length / 2f);
			lines.add(start - GAP);
			lines.add(start + length + GAP);
		}

		float[] anchors = {0f, size / 2f, size};
		float best = SNAP + 1;
		float result = position;
		float guide = -1;

		for (float line : lines) {
			for (float anchor : anchors) {
				float distance = Math.abs(position + anchor - line);

				if (distance < best) {
					best = distance;
					result = line - anchor;
					guide = line;
				}
			}
		}

		return new float[] {result, guide};
	}

	/** Keeps a box of {@code size} inside the screen, {@link HudModule#PAD} away from both edges. */
	public static float clamp(float position, int size, int screen) {
		return Math.max(HudModule.PAD, Math.min(screen - HudModule.PAD - size, position));
	}

	private static List<int[]> withoutSelf(@Nullable HudModule dragged, int screenW, int screenH, List<int[]> others) {
		List<int[]> result = new ArrayList<>(others.size());
		int selfX = dragged == null ? 0 : dragged.screenX(screenW);
		int selfY = dragged == null ? 0 : dragged.screenY(screenH);
		int selfW = dragged == null ? 0 : dragged.scaledWidth();
		int selfH = dragged == null ? 0 : dragged.scaledHeight();

		for (int[] box : others) {
			if (box.length < 4) {
				continue;
			}

			if (dragged != null && box[0] == selfX && box[1] == selfY && box[2] == selfW && box[3] == selfH) {
				continue;
			}

			result.add(box);
		}

		return result;
	}
}

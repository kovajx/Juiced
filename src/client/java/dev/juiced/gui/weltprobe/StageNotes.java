package dev.juiced.gui.weltprobe;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;

/**
 * Things that sit on the open stage: the framed 2D preview for channels that can't show in the world right now,
 * and the coach mark with two key hints on the first openings.
 */
final class StageNotes {
	private static final double COACH_DELAY = 0.5;
	private static final double COACH_TIME = 8.0;
	private static final int COACH_W = 240;
	private static final int COACH_H = 32;
	private static final int FRAME = 128;
	/** Caption line under the frame. */
	private static final int CAPTION = 18;

	/** 0 hidden, 1 "Mausrad / Leertaste", 2 "Tippen / Tab". */
	private int coach;
	private double coachAt;
	private double coachEndAt = Double.MAX_VALUE;

	void startCoach() {
		coach = 1;
		coachAt = Clock.t() + COACH_DELAY;
		coachEndAt = coachAt + COACH_TIME;
	}

	/** The first toggle: the coach mark moves on to its second pair of hints. */
	void toggled() {
		if (coach == 1) {
			coach = 2;
			coachAt = Clock.t();
			coachEndAt = coachAt + COACH_TIME;
		}
	}

	/** The second hints were used (a search, the drawer): the coach mark fades out. */
	void used() {
		if (coach == 2) {
			coachEndAt = Math.min(coachEndAt, Clock.t());
		}
	}

	private float coachAlpha() {
		if (coach == 0) {
			return 0f;
		}

		double now = Clock.t();
		float in = Colors.clamp01((float) ((now - coachAt) / 0.2));
		float out = Colors.clamp01((float) ((coachEndAt + 0.3 - now) / 0.3));
		return Math.min(in, out);
	}

	/** Where the coach mark is (x1, y1, x2, y2), or null while it is hidden. */
	int @Nullable [] coachBox(Layout l) {
		if (coachAlpha() <= 0f) {
			return null;
		}

		int x1 = Math.max(l.margin, Math.min(l.width - l.margin - COACH_W, l.cardX + l.cardW / 2 - COACH_W / 2));
		int y1 = l.cardY - 10 - COACH_H;
		return new int[] {x1, y1, x1 + COACH_W, y1 + COACH_H};
	}

	void drawCoach(GuiGraphicsExtractor g, Layout l) {
		int[] box = coachBox(l);

		if (box == null) {
			return;
		}

		float saved = Ui.alpha;
		Ui.alpha = saved * coachAlpha();
		Ui.panel(g, box[0], box[1], box[2], box[3], Ui.PANEL_SOLID, Ui.accent(1f));
		float cx = (box[0] + box[2]) / 2f;
		Ui.centered(g, coach == 1 ? "Mausrad: nächster Sender" : "Tippen: suchen", cx, box[1] + 6, Ui.TEXT);
		Ui.centered(g, coach == 1 ? "Leertaste: anlassen" : "Tab: Einstellungen", cx, box[1] + 18, Ui.DIM);
		Ui.alpha = saved;
	}

	/**
	 * The framed 2D preview with its reason, in the biggest free part of the stage: above the card, or right of it
	 * on short screens. {@code left}..{@code right} is the stage width left free by the drawer.
	 */
	void drawFallback(GuiGraphicsExtractor g, Layout l, Module module, String caption, int left, int right, boolean cardShown) {
		int[] coachBox = coachBox(l);
		int top = l.stageTop();
		int aboveBottom = cardShown ? l.cardY - 8 : l.dialY1 - 8;
		int[] best = place(left, top, right, aboveBottom, caption, coachBox);

		if (cardShown) {
			int[] beside = place(Math.max(left, l.cardX + l.cardW + 8), top, right, l.dialY1 - 8, caption, coachBox);

			if (beside[2] > best[2]) {
				best = beside;
			}
		}

		int size = best[2];

		if (size < 24) {
			return;
		}

		int x = best[0];
		int y = best[1];
		int rgb = module.category.color;
		Ui.fill(g, x, y, x + size, y + size, Colors.argb(0.7f, rgb));
		Ui.fill(g, x + 2, y + 2, x + size - 2, y + size - 2, 0xCC0A0A0F);
		module.drawPreview(g, x + 2, y + 2, size - 4, size - 4, Clock.tf());
		Ui.centered(g, Ui.ellipsize(caption, Math.max(size, right - left - 16)), x + size / 2f, y + size + 8, Ui.DIM);
	}

	/** Frame position and size {x, y, size} centred in a region, kept clear of the coach mark. */
	private static int[] place(int x1, int y1, int x2, int y2, String caption, int @Nullable [] coach) {
		int[] spot = center(x1, y1, x2, y2);

		if (coach != null && spot[2] > 0) {
			int captionHalf = Math.max(spot[2], Ui.width(caption)) / 2;
			int cx = spot[0] + spot[2] / 2;

			if (Ui.overlaps(cx - captionHalf, spot[1], cx + captionHalf, spot[1] + spot[2] + CAPTION, coach[0] - 6, coach[1] - 6, coach[2] + 6, coach[3] + 6)) {
				spot = center(x1, y1, x2, Math.min(y2, coach[1] - 6));
			}
		}

		return spot;
	}

	private static int[] center(int x1, int y1, int x2, int y2) {
		int size = Math.min(FRAME, Math.min(x2 - x1, y2 - y1 - CAPTION));

		if (size <= 0) {
			return new int[] {x1, y1, 0};
		}

		int x = (x1 + x2) / 2 - size / 2;
		int y = (y1 + y2 - CAPTION) / 2 - size / 2;
		return new int[] {x, y, size};
	}
}

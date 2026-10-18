package dev.juiced.gui.weltprobe;

/**
 * Where the fixed panels sit for a screen size, in GUI pixels. The spec is drawn for 854 x 480; down to 427 x 240
 * the widths, margins and lamp pitch shrink, never the text.
 */
final class Layout {
	static final int DIAL_H = 44;
	static final int BUTTON_H = 20;
	/** Room left of the first lamp inside the dial: 6 px, the 42 px "Nur an" chip, 8 px. */
	static final int CHIP_AREA = 56;
	static final int SEGMENT_GAP = 12;
	/** Space kept free right of the last lamp. */
	static final int DIAL_END = 12;

	final int width;
	final int height;
	/** Side margin: 16, or 8 on narrow screens. */
	final int margin;

	final int dialX1;
	final int dialY1;
	final int dialX2;
	final int dialY2;

	final int cardX;
	final int cardY;
	final int cardW;
	final int cardH;
	/** Short screens: the card drops its second text row. */
	final boolean compactCard;

	final int drawerW;
	final int drawerY2;
	final int buttonW;

	Layout(int width, int height) {
		this.width = width;
		this.height = height;
		margin = width < 640 ? 8 : 16;

		dialX1 = margin;
		dialX2 = width - margin;
		dialY2 = height - 8;
		dialY1 = dialY2 - DIAL_H;

		compactCard = height < 300;
		cardH = compactCard ? 72 : 88;
		cardW = Math.max(240, Math.min(340, Math.round(width * 0.42f)));
		cardX = margin;
		cardY = dialY1 - 8 - cardH;

		drawerW = width < 640 ? 180 : 220;
		drawerY2 = dialY1 - 8;
		buttonW = width < 640 ? 70 : 84;
	}

	/** Lamp pitch (lamp width + 2) so {@code lamps} lamps in {@code segments} groups fit the dial, at most 14. */
	int pitch(int lamps, int segments) {
		int room = dialX2 - dialX1 - CHIP_AREA - Math.max(0, segments - 1) * SEGMENT_GAP - DIAL_END;
		return Math.max(3, Math.min(14, room / Math.max(1, lamps)));
	}

	/** Right end of the two top-left buttons. */
	int buttonsRight() {
		return margin + 2 * buttonW + 4;
	}

	/** Toasts sit under the top row. */
	int toastY() {
		return margin + 28;
	}

	/** Top of the free stage below the top row. */
	int stageTop() {
		return margin + 24;
	}

	int drawerX(boolean left) {
		return left ? margin : width - margin - drawerW;
	}

	/** On the left the drawer starts below the Garderobe and HUD-Editor buttons. */
	int drawerY1(boolean left) {
		return left ? margin + BUTTON_H + 8 : margin;
	}
}

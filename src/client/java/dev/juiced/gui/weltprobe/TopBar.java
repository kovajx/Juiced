package dev.juiced.gui.weltprobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;

/**
 * The thin chrome along the top: the Garderobe and HUD-Editor buttons, the search pill, the channel bug (PROBE,
 * AN, AUS, VORSCHAU, GESPERRT) and the toast after a toggle.
 */
final class TopBar {
	private static final double TOAST_TIME = 2.2;
	private static final int PILL_W = 280;
	/** Narrower than this, the search pill gets the bug's place. */
	static final int PILL_MIN = 150;

	private final WeltprobeScreen menu;
	private String toast = "";
	private int toastRgb;
	private boolean toastUndo;
	private double toastAt = -10;

	TopBar(WeltprobeScreen menu) {
		this.menu = menu;
	}

	/** Shows a short message under the top row for 2.2 s, with the Strg+Z keycap after a toggle. */
	void toast(String text, int rgb, boolean undo) {
		toast = text;
		toastRgb = rgb;
		toastUndo = undo;
		toastAt = Clock.t();
	}

	void drawButtons(GuiGraphicsExtractor g, Layout l, Hits hits, int mouseX, int mouseY, Channel channel, HudStage stage, float alpha) {
		int x = l.margin;
		int bw = l.buttonW;
		int wardrobeEdge = channel.category() == Category.COSMETICS ? Ui.cat(Category.COSMETICS, 1f) : Ui.EDGE;
		int hudEdge = channel.hudModule() != null ? Ui.cat(Category.HUD, 1f) : Ui.EDGE;
		button(g, l, hits, "Garderobe", "Strg+G", x, bw, wardrobeEdge, mouseX, mouseY, stage, alpha, menu::openWardrobe);
		button(g, l, hits, "HUD-Editor", "Strg+H", x + bw + 4, bw, hudEdge, mouseX, mouseY, stage, alpha, menu::openHudEditor);
	}

	private static void button(GuiGraphicsExtractor g, Layout l, Hits hits, String label, String key, int x, int w, int edge, int mouseX, int mouseY,
			HudStage stage, float alpha, Runnable action) {
		int y1 = l.margin;
		int y2 = y1 + Layout.BUTTON_H;
		boolean hover = Ui.inside(mouseX, mouseY, x, y1, x + w, y2);
		float saved = Ui.alpha;
		Ui.alpha = saved * alpha * (stage.covers(x, y1, x + w, y2) ? 0.3f : 1f);
		Ui.panel(g, x, y1, x + w, y2, hover ? Ui.accent(1f) : 0xE01A1A22, hover ? Ui.accent(1f) : edge);
		Ui.centered(g, label, x + w / 2f, y1 + 6, hover ? Ui.INK : Ui.TEXT);

		if (hover) {
			Ui.centered(g, key, x + w / 2f, y2 + 4, Ui.DIM);
		}

		Ui.alpha = saved;
		hits.add(x, y1, x + w, y2, action);
	}

	/** Width of the bug for a status. */
	static int bugWidth(ProbeStatus status) {
		return 14 + Ui.width(status.name()) + 6;
	}

	/** The channel logo; {@code right} is its right edge. */
	void drawBug(GuiGraphicsExtractor g, Layout l, ProbeStatus status, Category category, int right) {
		int w = bugWidth(status);
		int x1 = right - w;
		int y1 = l.margin;
		int rgb = category.color;
		boolean on = status == ProbeStatus.AN;
		Ui.panel(g, x1, y1, right, y1 + 16, on ? Colors.argb(1f, rgb) : 0x99000000, 0x40FFFFFF);
		int dot = switch (status) {
			case PROBE -> Colors.argb(0.7f + 0.3f * (float) Math.sin(Clock.t() * Math.PI * 2.0 / 1.2), rgb);
			case AN -> 0xFFFFFFFF;
			case AUS -> Ui.MUTED;
			case VORSCHAU -> Ui.DIM;
			case GESPERRT -> Ui.ERROR;
		};
		Ui.fill(g, x1 + 5, y1 + 5, x1 + 10, y1 + 10, dot);
		Ui.text(g, status.name(), x1 + 14, y1 + 4, on ? Ui.INK : Ui.TEXT);
	}

	/** The search pill between {@code left} and {@code right}, centred on the screen where there is room. */
	void drawPill(GuiGraphicsExtractor g, Layout l, Hits hits, Search search, int left, int right) {
		int x1 = Math.max(left, Math.min(l.width / 2 - PILL_W / 2, right - PILL_W));
		int x2 = Math.min(x1 + PILL_W, right);
		int y1 = l.margin;
		int y2 = y1 + 22;
		int w = x2 - x1;
		Ui.panel(g, x1, y1, x2, y2, Ui.PANEL_SOLID, Ui.accent(1f));
		hits.block(x1, y1, x2, y2);

		int ty = y1 + 7;
		int qx = x1 + 8;

		if (w >= 200) {
			Ui.text(g, "Suche", qx, ty, Ui.MUTED);
			qx = x1 + 42;
		}

		String count;
		int countColor;

		if (search.hits().isEmpty()) {
			count = w >= 200 ? "Nichts gefunden" : "0 Treffer";
			countColor = Ui.ERROR;
		} else {
			count = search.hits().size() + " Treffer";
			countColor = Ui.DIM;
		}

		int countX = x2 - 8 - Ui.width(count);
		Ui.text(g, count, countX, ty, countColor);

		// Long queries show their end, so you see what you are typing
		String query = search.query();
		int room = countX - 6 - qx;

		while (query.length() > 1 && Ui.width("…" + query) > room) {
			query = query.substring(1);
		}

		if (!query.equals(search.query())) {
			query = "…" + query;
		}

		int qw = Ui.width(query);

		if (search.done()) {
			Ui.text(g, query, qx, ty, Ui.DIM);
			Ui.fill(g, qx, ty + 10, qx + qw, ty + 11, Ui.accent(1f));
		} else {
			Ui.text(g, query, qx, ty, Ui.TEXT);

			if (Clock.t() % 1.06 < 0.53) {
				Ui.fill(g, qx + qw + 1, ty - 1, qx + qw + 2, ty + 8, Ui.accent(1f));
			}
		}
	}

	void drawToast(GuiGraphicsExtractor g, Layout l, int stageLeft, int stageRight) {
		double t = Clock.t() - toastAt;

		if (t >= TOAST_TIME || toast.isEmpty()) {
			return;
		}

		float alpha = Math.min(Colors.clamp01((float) (t / 0.1)), Colors.clamp01((float) ((TOAST_TIME - t) / 0.25)));
		int keyW = toastUndo ? Ui.keycapWidth("Strg+Z") + 8 : 0;
		String text = Ui.ellipsize(toast, 300 - 16 - keyW);
		int w = Ui.width(text) + keyW + 16;
		int cx = (stageLeft + stageRight) / 2;
		int x1 = Math.max(stageLeft, Math.min(stageRight - w, cx - w / 2));
		int y1 = l.toastY();
		float saved = Ui.alpha;
		Ui.alpha = saved * alpha;
		Ui.panel(g, x1, y1, x1 + w, y1 + 18, Ui.PANEL_SOLID, Colors.argb(1f, toastRgb));
		Ui.text(g, text, x1 + 8, y1 + 5, Ui.TEXT);

		if (toastUndo) {
			Ui.keycap(g, "Strg+Z", x1 + 8 + Ui.width(text) + 8, y1 + 3, 12, Ui.DIM);
		}

		Ui.alpha = saved;
	}
}

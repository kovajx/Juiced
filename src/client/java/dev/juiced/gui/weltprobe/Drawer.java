package dev.juiced.gui.weltprobe;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.setting.Setting;
import dev.juiced.gui.SettingsList;
import dev.juiced.render.Fx;

/**
 * The Schublade: the tuned channel's settings in a panel that slides in from the side. Every change goes straight
 * to the running try-out. With the drawer open, Up/Down move a keyboard focus over its rows and Left/Right change
 * the focused value.
 */
final class Drawer {
	private static final double OPEN_TIME = 0.18;
	private static final double CLOSE_TIME = 0.14;
	private static final int ROW_STEP = 14;

	private final WeltprobeScreen menu;
	private final SettingsList list;
	private @Nullable Module module;
	private boolean open;
	private boolean left;
	private double changedAt = -10;
	private int scroll;
	private int maxScroll;
	/** Where the panel was drawn in the last frame (x1, y1, x2, y2), for wheel and click routing. */
	private final int[] box = new int[4];
	private int listWidth = 204;
	private int listHeight = 100;

	Drawer(WeltprobeScreen menu, SettingsList list) {
		this.menu = menu;
		this.list = list;
	}

	boolean isOpen() {
		return open;
	}

	/** True while the panel can be seen (also while it slides out). */
	boolean visible() {
		return open || slide() > 0f;
	}

	boolean dockedLeft() {
		return left;
	}

	void open(boolean dockLeft) {
		if (open) {
			left = dockLeft;
			return;
		}

		open = true;
		left = dockLeft;
		changedAt = Clock.t();
		list.focus = -1;
		Fx.sound("ui.button.click", 0.25f, 1.1f);
	}

	void close() {
		if (!open) {
			return;
		}

		open = false;
		changedAt = Clock.t();
		list.focus = -1;
		Fx.sound("ui.button.click", 0.2f, 0.9f);
	}

	/** Shows another module's settings (zapping with the drawer open): starts at the top without a focused row. */
	void show(Module next) {
		if (next != module) {
			module = next;
			scroll = 0;
			list.focus = -1;
		}
	}

	/** 0 closed .. 1 fully open. */
	float slide() {
		float t = (float) ((Clock.t() - changedAt) / (open ? OPEN_TIME : CLOSE_TIME));

		if (open) {
			return Clock.easeOut(t);
		}

		float p = Colors.clamp01(t);
		return 1f - p * p;
	}

	/** Left edge of the panel when fully open, for the bug and the stage area. */
	int openX(Layout l) {
		return l.drawerX(left);
	}

	/** Left edge of the panel as drawn in the last frame (it moves while sliding). */
	int leftEdge() {
		return box[0];
	}

	boolean contains(double x, double y) {
		return visible() && Ui.inside(x, y, box[0], box[1], box[2], box[3]);
	}

	void scrollBy(double amount) {
		scroll = Math.max(0, Math.min(maxScroll, scroll + (int) Math.round(amount)));
	}

	/** Up/Down with the drawer open: moves the focus and scrolls it into view. */
	void moveFocus(int delta) {
		if (module == null) {
			return;
		}

		list.moveFocus(module.settings, delta);
		Fx.sound("ui.button.click", 0.12f, 1.8f);
		int[] span = list.focusedRowSpan(module.settings, listWidth);

		if (span != null) {
			if (span[0] < scroll) {
				scroll = span[0];
			}

			if (span[1] > scroll + listHeight) {
				scroll = span[1] - listHeight;
			}

			scroll = Math.max(0, scroll);
		}
	}

	/** True if a row has the keyboard focus, so Left/Right change it instead of zapping. */
	boolean hasFocus() {
		return open && list.focus >= 0;
	}

	void changeFocused(int direction) {
		if (module != null) {
			list.changeFocused(module.settings, direction);
		}
	}

	void draw(GuiGraphicsExtractor g, Layout l, Hits hits, int mouseX, int mouseY, float panelAlpha) {
		float slide = slide();
		Module shown = module;

		if (slide <= 0f || shown == null) {
			box[2] = box[0];
			return;
		}

		int w = l.drawerW;
		int targetX = l.drawerX(left);
		int x1 = left ? Math.round(targetX - (targetX + w) * (1f - slide)) : Math.round(targetX + (l.width - targetX) * (1f - slide));
		int y1 = l.drawerY1(left);
		int y2 = l.drawerY2;
		int x2 = x1 + w;
		boolean interactive = open && slide >= 1f;
		box[0] = x1;
		box[1] = y1;
		box[2] = x2;
		box[3] = y2;

		float saved = Ui.alpha;
		Ui.alpha = saved * panelAlpha;
		Ui.panel(g, x1, y1, x2, y2, Ui.PANEL_SOLID, Ui.EDGE);
		Ui.fill(g, x1, y1, x1 + 2, y2, Ui.cat(shown.category, 1f));
		hits.block(x1, y1, x2, y2);

		// Header: name and [x]
		Ui.text(g, Ui.ellipsize(shown.name, w - 44), x1 + 10, y1 + 10, Ui.TEXT);
		int cx = x2 - 20;
		int cy = y1 + 7;
		boolean closeHover = interactive && Ui.inside(mouseX, mouseY, cx, cy, cx + 14, cy + 14);
		Ui.panel(g, cx, cy, cx + 14, cy + 14, closeHover ? Ui.BUTTON_HOVER : Ui.KEY_BG, Ui.KEY_EDGE);
		Ui.centered(g, "x", cx + 7.5f, cy + 3, closeHover ? Ui.accent(1f) : Ui.DIM);
		Ui.fill(g, x1 + 1, y1 + 28, x2 - 1, y1 + 29, Ui.EDGE);

		if (interactive) {
			hits.add(cx, cy, cx + 14, cy + 14, this::close);
		}

		// The settings
		int listLeft = x1 + 8;
		listWidth = w - 16;
		int top = y1 + 32;
		int bottom = y2 - 26;
		listHeight = bottom - top;
		List<Setting<?>> settings = shown.settings;
		Ui.alpha = saved;
		list.originX = 0;
		list.originY = 0;
		list.mouseX = mouseX;
		list.mouseY = mouseY;

		if (settings.isEmpty()) {
			Ui.centered(g, "Keine Einstellungen", (x1 + x2) / 2f, top + 8, Ui.DIM);
			maxScroll = 0;
		} else {
			int content = list.draw(g, settings, listLeft, listWidth, top, bottom, scroll, interactive);
			maxScroll = Math.max(0, content - listHeight);
			scroll = Math.max(0, Math.min(scroll, maxScroll));
		}

		Ui.alpha = saved * panelAlpha;

		if (maxScroll > 0) {
			int trackH = bottom - top;
			int thumbH = Math.max(12, trackH * trackH / (trackH + maxScroll));
			int thumbY = top + Math.round((trackH - thumbH) * (scroll / (float) maxScroll));
			Ui.fill(g, x2 - 4, top, x2 - 2, bottom, 0x20FFFFFF);
			Ui.fill(g, x2 - 4, thumbY, x2 - 2, thumbY + thumbH, Ui.accent(0.8f));
		}

		// Footer: [Zurücksetzen] [Fertig Tab]
		int bw = Math.min(90, (w - 22) / 2);
		int by = y2 - 22;
		footerButton(g, hits, "Zurücksetzen", null, x1 + 8, by, bw, mouseX, mouseY, interactive, () -> {
			shown.settings.forEach(Setting::reset);
			Fx.sound("ui.button.click", 0.25f, 0.7f);
			menu.settingsChanged();
		});
		footerButton(g, hits, "Fertig", "Tab", x2 - 8 - bw, by, bw, mouseX, mouseY, interactive, this::close);
		Ui.alpha = saved;
	}

	private static void footerButton(GuiGraphicsExtractor g, Hits hits, String label, @Nullable String key, int x, int y, int w, int mouseX, int mouseY,
			boolean interactive, Runnable action) {
		int h = 16;
		boolean hover = interactive && Ui.inside(mouseX, mouseY, x, y, x + w, y + h);
		Ui.fill(g, x, y, x + w, y + h, hover ? Ui.BUTTON_HOVER : Ui.KEY_BG);
		int keyW = key == null ? 0 : Ui.keycapWidth(key) + 4;
		int lx = x + (w - Ui.width(label) - keyW) / 2;
		Ui.text(g, label, lx, y + 4, Ui.TEXT);

		if (key != null) {
			Ui.keycap(g, key, lx + Ui.width(label) + 4, y + 3, 10, Ui.DIM);
		}

		if (interactive) {
			hits.add(x, y, x + w, y + h, action);
		}
	}

	/** Wheel over the drawer. */
	void wheel(double scrollY) {
		scrollBy(-scrollY * ROW_STEP);
	}
}

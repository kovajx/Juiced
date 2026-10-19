package dev.juiced.gui.weltprobe;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;

/**
 * The Senderskala at the bottom: every module as a small lamp, grouped by category. A lit lamp is switched on, the
 * needle sits under the tuned one, and hovering a lamp shows a bubble with its live mini preview.
 */
final class Dial {
	/** Lamps sit 18 px below the dial top and are 18 px tall. */
	private static final int LAMP_TOP = 18;
	private static final int LAMP_H = 18;
	/** Free space kept right of the last lamp when the lamps don't fill the dial. */
	private static final int RIGHT_PAD = 20;
	private static final double FILL_TIME = 0.16;
	private static final double DROP_TIME = 0.3;
	private static final double BUMP_TIME = 0.15;

	private final WeltprobeScreen menu;
	private final List<Channel> channels;
	private final int[] lampX;
	private final double[] juiceAt;
	private final boolean[] juiceOn;
	private int pitch = 14;
	private int lampW = 12;
	private float needleX = Float.NaN;
	private double bumpAt = -10;
	private int bumpDir;
	private float searchFade;
	private float onlyFade;
	private float bubbleFade;
	private int hovered = -1;
	private int bubbleLamp = -1;

	Dial(WeltprobeScreen menu, List<Channel> channels) {
		this.menu = menu;
		this.channels = channels;
		lampX = new int[channels.size()];
		juiceAt = new double[channels.size()];
		juiceOn = new boolean[channels.size()];
		Arrays.fill(juiceAt, -10);
	}

	/** The lamp fills (on) or drains (off), with a splash of droplets when it fills. */
	void juice(int index, boolean on) {
		juiceAt[index] = Clock.t();
		juiceOn[index] = on;
	}

	/** Zapping hit an end: the needle nudges 3 px that way and springs back. */
	void bump(int direction) {
		bumpAt = Clock.t();
		bumpDir = direction;
	}

	/** The lamp under the mouse in the last frame, or -1. */
	int hovered() {
		return hovered;
	}

	void draw(GuiGraphicsExtractor g, Layout l, Hits hits, int mouseX, int mouseY, int tuned, boolean onlyOn, Search search) {
		double now = Clock.t();
		searchFade = Clock.approach(searchFade, search.active() && !search.hits().isEmpty() ? 1f : 0f, 20f);
		onlyFade = Clock.approach(onlyFade, onlyOn ? 1f : 0f, 20f);
		place(l);

		int y1 = l.dialY1;
		Ui.fill(g, l.dialX1, y1, l.dialX2, l.dialY2, Ui.PANEL);
		Ui.fill(g, l.dialX1, y1, l.dialX2, y1 + 1, Ui.HAIRLINE);
		hits.block(l.dialX1, y1, l.dialX2, l.dialY2);

		drawChip(g, l, hits, mouseX, mouseY, onlyOn);
		drawSegments(g, l, hits, mouseX, mouseY);

		hovered = -1;

		for (int i = 0; i < channels.size(); i++) {
			int x = lampX[i];
			int index = i;

			if (Ui.inside(mouseX, mouseY, x - 1, y1 + 14, x - 1 + pitch, l.dialY2)) {
				hovered = i;
			}

			hits.add(x - 1, y1 + 14, x - 1 + pitch, l.dialY2, (button, shift) -> menu.lampClicked(index, button, shift));
		}

		for (int i = 0; i < channels.size(); i++) {
			drawLamp(g, l, i, i == hovered, i == tuned, search, now);
		}

		if (tuned >= 0 && tuned < channels.size()) {
			float target = lampX[tuned] + lampW / 2f;
			needleX = Float.isNaN(needleX) ? target : Clock.approach(needleX, target, 30f);
			float bump = 0f;
			float p = Ui.progress(now, bumpAt, BUMP_TIME);

			if (p < 1f) {
				bump = bumpDir * 3f * (float) Math.sin(Math.PI * p);
			}

			Ui.triangleUp(g, Math.round(needleX + bump), y1 + LAMP_TOP + LAMP_H + 3, 4, Ui.accent(1f));
		}
	}

	/** Works out the lamp positions for this screen size: segments in dial order with a 12 px gap between. */
	private void place(Layout l) {
		int segments = 0;
		Category last = null;

		for (Channel channel : channels) {
			if (channel.category() != last) {
				segments++;
				last = channel.category();
			}
		}

		pitch = l.pitch(channels.size(), segments);
		lampW = Math.max(2, pitch - 2);
		int used = channels.size() * pitch + Math.max(0, segments - 1) * Layout.SEGMENT_GAP;
		int free = l.dialX2 - l.dialX1 - Layout.CHIP_AREA - RIGHT_PAD - used;
		int x = l.dialX1 + Layout.CHIP_AREA + Math.max(0, free / 2);
		last = null;

		for (int i = 0; i < channels.size(); i++) {
			Category category = channels.get(i).category();

			if (last != null && category != last) {
				x += Layout.SEGMENT_GAP;
			}

			lampX[i] = x;
			x += pitch;
			last = category;
		}
	}

	private void drawChip(GuiGraphicsExtractor g, Layout l, Hits hits, int mouseX, int mouseY, boolean onlyOn) {
		int x1 = l.dialX1 + 6;
		int y1 = l.dialY1 + LAMP_TOP;
		int x2 = x1 + 42;
		int y2 = y1 + 18;
		boolean hover = Ui.inside(mouseX, mouseY, x1, y1, x2, y2);

		if (onlyOn) {
			Ui.fill(g, x1, y1, x2, y2, Ui.accent(1f));
		} else {
			Ui.panel(g, x1, y1, x2, y2, hover ? Ui.BUTTON_HOVER : Ui.KEY_BG, Ui.KEY_EDGE);
		}

		Ui.centered(g, "Nur an", (x1 + x2) / 2f, y1 + 5, onlyOn ? Ui.INK : hover ? Ui.TEXT : Ui.DIM);
		hits.add(x1, y1, x2, y2, menu::toggleOnlyOn);
	}

	/** Category labels above each group ("KAMPF 3/5"), clickable, and the divider lines between groups. */
	private void drawSegments(GuiGraphicsExtractor g, Layout l, Hits hits, int mouseX, int mouseY) {
		int start = 0;

		while (start < channels.size()) {
			Category category = channels.get(start).category();
			int end = start;
			int on = 0;

			while (end < channels.size() && channels.get(end).category() == category) {
				if (channels.get(end).module.isSwitchedOn()) {
					on++;
				}

				end++;
			}

			boolean lastSegment = end >= channels.size();
			int x = lampX[start];
			int segmentEnd = lampX[end - 1] + lampW;
			int maxW = lastSegment ? l.dialX2 - 6 - x : segmentEnd - x + 4;
			String name = category.de.toUpperCase(Locale.ROOT);
			String count = category == Category.SYSTEM || pitch < 10 ? "" : " " + on + "/" + (end - start);

			if (Ui.width(name + count) > maxW) {
				count = "";
			}

			name = abbreviate(name, maxW);
			int w = Ui.width(name + count);
			int y = l.dialY1 + 5;
			boolean hover = Ui.inside(mouseX, mouseY, x - 1, y - 3, x + w + 1, y + 10);
			Ui.text(g, name, x, y, Ui.cat(category, 1f));

			if (!count.isEmpty()) {
				Ui.text(g, count, x + Ui.width(name), y, Ui.DIM);
			}

			if (hover) {
				Ui.fill(g, x, y + 9, x + w, y + 10, Ui.cat(category, 0.9f));
			}

			hits.add(x - 1, y - 3, x + w + 1, y + 10, () -> menu.jumpTo(category));

			if (!lastSegment) {
				int dx = segmentEnd + (Layout.SEGMENT_GAP + 2) / 2;
				Ui.fill(g, dx, l.dialY1 + 4, dx + 1, l.dialY1 + 38, Ui.HAIRLINE);
			}

			start = end;
		}
	}

	/** Shortens a category name to fit ("KOSMETIK" -> "KOSM."), keeping at least one letter. */
	private static String abbreviate(String name, int maxW) {
		if (Ui.width(name) <= maxW) {
			return name;
		}

		for (int n = name.length() - 1; n >= 1; n--) {
			String shortName = name.substring(0, n) + ".";

			if (Ui.width(shortName) <= maxW) {
				return shortName;
			}
		}

		return name.substring(0, 1);
	}

	private void drawLamp(GuiGraphicsExtractor g, Layout l, int i, boolean hover, boolean tuned, Search search, double now) {
		Channel channel = channels.get(i);
		boolean on = channel.module.isSwitchedOn();
		int rgb = hover ? Colors.shade(channel.category().color, 0.15f) : channel.category().color;
		float a = 1f;

		if (!search.matches(i)) {
			a *= 1f - 0.88f * searchFade;
		}

		if (!on) {
			a *= 1f - 0.8f * onlyFade;
		}

		int x = lampX[i];
		int top = l.dialY1 + LAMP_TOP - (hover ? 2 : 0);
		int bottom = top + LAMP_H;
		float fill = on ? 1f : 0f;
		float juice = Ui.progress(now, juiceAt[i], FILL_TIME);

		if (juice < 1f) {
			fill = juiceOn[i] ? juice : 1f - juice;
		}

		Ui.fill(g, x, top, x + lampW, bottom, Colors.fade(Ui.LAMP_OFF, a));

		if (fill < 1f) {
			Ui.fill(g, x, top, x + lampW, top + 2, Colors.argb(0.35f * a, rgb));
		}

		int filled = Math.round(LAMP_H * fill);

		if (filled > 0) {
			Ui.fill(g, x, bottom - filled, x + lampW, bottom, Colors.argb(a, rgb));
		}

		if (fill >= 1f) {
			Ui.fill(g, x, top, x + lampW, top + 1, Colors.argb(0.45f * a, 0xFFFFFF));
			Ui.fill(g, x, l.dialY1 + 38, x + lampW, l.dialY1 + 39, Colors.argb(0.6f * a, channel.category().color));
		}

		if (channel.module.blockedHere()) {
			Ui.hatch(g, x, top, x + lampW, bottom, Colors.fade(Ui.MUTED, a));
		}

		if (searchFade > 0.05f && search.matches(i)) {
			Ui.frame(g, x - 1, top - 1, x + lampW + 1, bottom + 1, Colors.argb(0.9f * searchFade, 0xFFFFFF));
		}

		if (tuned) {
			Ui.frame(g, x - 1, l.dialY1 + LAMP_TOP - 2, x + lampW + 1, l.dialY1 + LAMP_TOP + LAMP_H + 2, 0xFFFFFFFF);
		}

		float drops = Ui.progress(now, juiceAt[i], DROP_TIME);

		if (juiceOn[i] && drops < 1f) {
			// Six droplets burst up and out from the top of the lamp and fall back a little
			float cx = x + lampW / 2f;
			float spread = 8f * Clock.easeOut(drops);

			for (int d = 0; d < 6; d++) {
				double angle = Math.PI + Math.PI * (d + 0.5) / 6.0;
				int dx = Math.round(cx + (float) Math.cos(angle) * spread) - 1;
				int dy = Math.round(top + (float) Math.sin(angle) * spread + 6f * drops * drops) - 1;
				Ui.fill(g, dx, dy, dx + 2, dy + 2, Colors.argb(1f - drops, channel.category().color));
			}
		}
	}

	/** The hover bubble ("Lupe"): live mini preview and name above the lamp under the mouse. */
	void drawBubble(GuiGraphicsExtractor g, Layout l) {
		bubbleFade = Clock.approach(bubbleFade, hovered >= 0 ? 1f : 0f, 40f);

		if (hovered >= 0) {
			bubbleLamp = hovered;
		}

		if (bubbleLamp < 0 || bubbleLamp >= channels.size() || bubbleFade < 0.05f) {
			return;
		}

		Channel channel = channels.get(bubbleLamp);
		int rgb = channel.category().color;
		String name = channel.name();
		int w = Math.min(l.width - 2 * l.margin, Math.max(72, Ui.width(name) + 12));
		int h = 74;
		int cx = lampX[bubbleLamp] + lampW / 2;
		int x1 = Math.max(l.margin, Math.min(l.width - l.margin - w, cx - w / 2));
		int y2 = l.dialY1 - 4;
		int y1 = y2 - h;
		float saved = Ui.alpha;
		Ui.alpha = saved * bubbleFade;
		Ui.panel(g, x1, y1, x1 + w, y2, Ui.PANEL_SOLID, Colors.argb(1f, rgb));
		Ui.triangleDown(g, cx, y2, 4, Colors.argb(1f, rgb));

		if (bubbleFade > 0.6f) {
			channel.module.drawPreview(g, x1 + w / 2 - 28, y1 + 4, 56, 56, Clock.tf());
		}

		Ui.centered(g, Ui.ellipsize(name, w - 8), x1 + w / 2f, y1 + 64, channel.module.isSwitchedOn() ? Colors.argb(1f, rgb) : Ui.TEXT);
		Ui.alpha = saved;
	}
}

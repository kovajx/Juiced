package dev.juiced.gui.weltprobe;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleGroup;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.render.Gui2D;

/**
 * The Senderkarte bottom left: what the tuned channel is, whether it is on, its style, and the way into its
 * settings. On a search hit for a part or entry it names that ("Low Fire, Teil von Klare Sicht").
 */
final class ChannelCard {
	private static final double OUT_TIME = 0.05;
	private static final double IN_TIME = 0.08;
	private static final String STYLE_HINT = "Pfeil hoch/runter";

	private final WeltprobeScreen menu;
	private @Nullable Channel shown;
	private @Nullable SearchHit shownHit;
	private @Nullable Channel old;
	private @Nullable SearchHit oldHit;
	private double changedAt = -10;
	private double styleAt = -10;
	private float knob = Float.NaN;

	ChannelCard(WeltprobeScreen menu) {
		this.menu = menu;
	}

	/** Shows another channel (or search hit): the old content fades out, the new one slides in. */
	void show(Channel channel, @Nullable SearchHit hit) {
		if (channel == shown && sameHit(hit, shownHit)) {
			return;
		}

		old = shown;
		oldHit = shownHit;
		shown = channel;
		shownHit = hit;
		changedAt = Clock.t();

		if (old == null || old.module != channel.module) {
			knob = Float.NaN;
		}
	}

	/** The style value changed: it fades in again. */
	void styleChanged() {
		styleAt = Clock.t();
	}

	private static boolean sameHit(@Nullable SearchHit a, @Nullable SearchHit b) {
		if (a == null || b == null) {
			return a == b;
		}

		return a.channel() == b.channel() && a.focus() == b.focus();
	}

	/**
	 * Draws the card. {@code panelAlpha} fades the whole card (open animation, a HUD element underneath),
	 * {@code slideX} moves it while it slides in.
	 */
	void draw(GuiGraphicsExtractor g, Layout l, Hits hits, int mouseX, int mouseY, float panelAlpha, int slideX, boolean searching) {
		Channel channel = shown;

		if (channel == null) {
			return;
		}

		int x1 = l.cardX + slideX;
		int y1 = l.cardY;
		int x2 = x1 + l.cardW;
		int y2 = y1 + l.cardH;
		float saved = Ui.alpha;
		Ui.alpha = saved * panelAlpha;
		Ui.panel(g, x1, y1, x2, y2, Ui.PANEL, Ui.EDGE);
		hits.block(x1, y1, x2, y2);

		double t = Clock.t() - changedAt;

		if (t < OUT_TIME && old != null) {
			Ui.alpha = saved * panelAlpha * (float) (1 - t / OUT_TIME);
			drawContent(g, l, hits, old, oldHit, x1, y1, mouseX, mouseY, false, searching);
		} else {
			float p = Colors.clamp01((float) ((t - OUT_TIME) / IN_TIME));
			Ui.alpha = saved * panelAlpha * p;
			int dy = Math.round(6f * (1f - Clock.easeOut(p)));
			drawContent(g, l, hits, channel, shownHit, x1, y1 + dy, mouseX, mouseY, true, searching);
		}

		Ui.alpha = saved;
	}

	private void drawContent(GuiGraphicsExtractor g, Layout l, Hits hits, Channel channel, @Nullable SearchHit hit, int x, int y,
			int mouseX, int mouseY, boolean live, boolean searching) {
		Module module = channel.module;
		Category category = channel.category();
		int w = l.cardW;
		boolean part = hit != null && hit.isPart() && hit.channel() == channel;

		// Category stripe and headline
		Ui.fill(g, x, l.cardY, x + 3, l.cardY + l.cardH, Ui.cat(category, 1f));
		String cat = category.de.toUpperCase(Locale.ROOT);
		Ui.text(g, cat, x + 10, y + 8, Ui.cat(category, 1f));
		String sub = part && hit.partOf() != null ? "Teil von " + hit.partOf() : "Sender " + (channel.index + 1) + " von " + Channels.all().size();
		int subX = x + 10 + Ui.width(cat) + 8;
		Ui.text(g, Ui.ellipsize(sub, x + w - 8 - subX), subX, y + 8, Ui.DIM);

		// Name at scale 2 and the switch
		int switchW = w >= 300 ? 76 : 56;
		int switchX = x + w - 8 - switchW;
		String name = part ? hit.label() : channel.name();
		name = Ui.ellipsize(name, switchX - 10 - (x + 10), "..", 2f);
		Gui2D.text(g, name, x + 10, y + 20, Colors.fade(Ui.TEXT, Ui.alpha), false, 2f);
		drawSwitch(g, hits, module, switchX, y + 20, switchW, mouseX, mouseY, live);

		// Description (first sentence) and the style row
		ChoiceSetting style = channel.mainStyle();
		int row = y + 42;
		int descLines = l.compactCard || style != null ? 1 : 2;

		if (!(l.compactCard && style != null)) {
			drawDescription(g, describe(channel, hit, part), x + 10, row, w - 18, descLines);
			row += 12 * descLines;
		}

		if (style != null) {
			drawStyle(g, hits, channel, hit, part, style, x, row, w, mouseX, mouseY, live);
		}

		int footerY = y + l.cardH - 18;
		drawFooter(g, hits, channel, x, footerY, w, mouseX, mouseY, live, searching);
	}

	private void drawSwitch(GuiGraphicsExtractor g, Hits hits, Module module, int x, int y, int w, int mouseX, int mouseY, boolean live) {
		int h = 18;

		if (module.alwaysOn()) {
			Ui.panel(g, x, y, x + w, y + h, Ui.KEY_BG, Ui.KEY_EDGE);
			Ui.centered(g, "SYSTEM", x + w / 2f, y + 5, Ui.DIM);
			return;
		}

		boolean on = module.isSwitchedOn();
		boolean blocked = module.blockedHere();
		boolean hover = live && !blocked && Ui.inside(mouseX, mouseY, x, y, x + w, y + h);

		if (live) {
			knob = Float.isNaN(knob) ? (on ? 1f : 0f) : Clock.approach(knob, on ? 1f : 0f, 26f);
		}

		float k = live ? knob : on ? 1f : 0f;
		float saved = Ui.alpha;

		if (blocked) {
			Ui.alpha = saved * 0.4f;
		}

		Ui.fill(g, x, y, x + w, y + h, on ? Ui.accent(1f) : Ui.SWITCH_OFF);
		int knobX = Math.round(x + 3 + (w - 18) * k);
		Ui.fill(g, knobX, y + 3, knobX + 12, y + 15, on ? 0xFFFFFFFF : Ui.MUTED);

		if (on) {
			Ui.text(g, "AN", x + 12, y + 5, Ui.INK);
		} else {
			Ui.text(g, "AUS", x + 24, y + 5, Ui.DIM);
		}

		if (hover) {
			Ui.frame(g, x, y, x + w, y + h, 0x66FFFFFF);
		}

		Ui.alpha = saved;

		if (live) {
			hits.add(x, y, x + w, y + h, menu::toggleTuned);
		}
	}

	/** The first sentence of what the card shows: the part's or entry's own description on a search hit. */
	private static String describe(Channel channel, @Nullable SearchHit hit, boolean part) {
		ModuleGroup group = channel.group();

		if (part && group != null) {
			int index = group.isChoice() ? group.entryPart(hit.focus()) : hit.focus();

			if (index >= 0 && index < group.parts().size()) {
				return Ui.firstSentence(group.parts().get(index).description);
			}
		}

		return Ui.firstSentence(channel.module.description);
	}

	private static void drawDescription(GuiGraphicsExtractor g, String text, int x, int y, int width, int maxLines) {
		List<String> lines = Gui2D.wrap(text, width, 1f);

		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			String line = lines.get(i);

			if (i == maxLines - 1 && lines.size() > maxLines) {
				line = Ui.ellipsize(line + " " + lines.get(i + 1), width);
			}

			Ui.text(g, Ui.ellipsize(line, width), x, y + 12 * i, Ui.DIM);
		}
	}

	/** "Art  < Kirschblüten >  Pfeil hoch/runter". */
	private void drawStyle(GuiGraphicsExtractor g, Hits hits, Channel channel, @Nullable SearchHit hit, boolean part, ChoiceSetting style,
			int x, int y, int w, int mouseX, int mouseY, boolean live) {
		int right = x + w - 8;
		int valueX = x + 46;
		int hintW = Ui.width(STYLE_HINT);
		int valueW = Math.min(128, right - valueX - 20 - hintW);
		boolean hint = valueW >= 80;

		if (!hint) {
			valueW = Math.min(128, right - valueX - 12);
		}

		Ui.text(g, "Art", x + 10, y, Ui.MUTED);
		arrow(g, hits, "<", x + 34, y - 2, mouseX, mouseY, live, () -> menu.changeStyle(-1));
		arrow(g, hits, ">", valueX + valueW + 2, y - 2, mouseX, mouseY, live, () -> menu.changeStyle(1));

		// A search hit on a choice entry shows that entry while it is only tried out
		ModuleGroup group = channel.group();
		String value = part && group != null && group.isChoice() && !channel.module.isSwitchedOn() ? hit.label() : style.name();
		float saved = Ui.alpha;

		if (live) {
			Ui.alpha = saved * Ui.progress(Clock.t(), styleAt, 0.1);
		}

		Ui.centered(g, Ui.ellipsize(value, valueW), valueX + valueW / 2f, y, Ui.TEXT);
		Ui.alpha = saved;

		if (hint) {
			Ui.text(g, STYLE_HINT, valueX + valueW + 20, y, Ui.MUTED);
		}
	}

	private static void arrow(GuiGraphicsExtractor g, Hits hits, String label, int x, int y, int mouseX, int mouseY, boolean live, Runnable action) {
		boolean hover = live && Ui.inside(mouseX, mouseY, x - 2, y - 2, x + 12, y + 14);
		Ui.panel(g, x, y, x + 10, y + 12, hover ? Ui.BUTTON_HOVER : Ui.KEY_BG, Ui.KEY_EDGE);
		Ui.centered(g, label, x + 5.5f, y + 2, hover ? Ui.accent(1f) : Ui.TEXT);

		if (live) {
			hits.add(x - 2, y - 2, x + 12, y + 14, action);
		}
	}

	/** [Einstellungen Tab], [Garderobe] or [Verschieben], and the key hint on the right. */
	private void drawFooter(GuiGraphicsExtractor g, Hits hits, Channel channel, int x, int y, int w, int mouseX, int mouseY, boolean live, boolean searching) {
		int bx = x + 10;
		int end = button(g, hits, "Einstellungen", "Tab", bx, y, 112, mouseX, mouseY, live, menu::toggleDrawer);

		if (channel.category() == Category.COSMETICS) {
			end = button(g, hits, "Garderobe", null, bx + 116, y, 66, mouseX, mouseY, live, menu::openWardrobe);
		} else if (channel.hudModule() != null) {
			end = button(g, hits, "Verschieben", null, bx + 116, y, 74, mouseX, mouseY, live, menu::openHudEditor);
		}

		if (channel.module.alwaysOn()) {
			return;
		}

		String hint = searching ? "Enter: an/aus" : "Leertaste: an/aus";
		int hintX = x + w - 8 - Ui.width(hint);

		if (hintX >= end + 8) {
			Ui.text(g, hint, hintX, y + 3, Ui.MUTED);
		}
	}

	/** A footer button; returns its right end. */
	private static int button(GuiGraphicsExtractor g, Hits hits, String label, @Nullable String key, int x, int y, int w, int mouseX, int mouseY,
			boolean live, Runnable action) {
		int h = 14;
		boolean hover = live && Ui.inside(mouseX, mouseY, x, y, x + w, y + h);
		Ui.fill(g, x, y, x + w, y + h, hover ? Ui.BUTTON_HOVER : Ui.KEY_BG);

		if (key == null) {
			Ui.centered(g, label, x + w / 2f, y + 3, Ui.TEXT);
		} else {
			int keyW = Ui.keycapWidth(key);
			int contentW = Ui.width(label) + 6 + keyW;
			int lx = x + (w - contentW) / 2;
			Ui.text(g, label, lx, y + 3, Ui.TEXT);
			Ui.keycap(g, key, lx + Ui.width(label) + 6, y + 2, 10, Ui.DIM);
		}

		if (live) {
			hits.add(x, y, x + w, y + h, action);
		}

		return x + w;
	}
}

package dev.juiced.gui.weltprobe;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.juiced.core.Module;
import dev.juiced.core.ModuleGroup;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.render.Fx;

/**
 * Switching modules on and off from the menu (Space, Enter, the card switch, Shift+click on a lamp, Enter on a
 * search hit) and taking it back with Strg+Z. Each toggle tells the try-out, fills or drains the lamp, clicks and
 * shows a toast.
 */
final class Toggles {
	private static final int UNDO_MAX = 10;

	/** One toggle Strg+Z can take back: the module switch, and the part switch or style that changed with it. */
	private record Undo(Channel channel, String label, boolean wasOn, @Nullable BoolSetting part, boolean partWas,
			@Nullable ChoiceSetting kind, int kindWas) {
	}

	private final WeltprobeScreen menu;
	private final ProbeController probe;
	private final Dial dial;
	private final TopBar topBar;
	private final StageNotes notes;
	private final ChannelCard card;
	private final Deque<Undo> undo = new ArrayDeque<>();

	Toggles(WeltprobeScreen menu, ProbeController probe, Dial dial, TopBar topBar, StageNotes notes, ChannelCard card) {
		this.menu = menu;
		this.probe = probe;
		this.dial = dial;
		this.topBar = topBar;
		this.notes = notes;
		this.card = card;
	}

	void toggle(Channel channel) {
		Module module = channel.module;

		if (module.alwaysOn()) {
			// System modules can't be switched off: their settings are the point
			menu.jump(channel.index);
			menu.openDrawer();
			return;
		}

		if (module.blockedHere()) {
			deny();
			return;
		}

		remember(channel, channel.name(), null, null);
		module.toggle();
		switched(channel, channel.name(), module.isSwitchedOn());
	}

	/** After the module's own switch flipped: the try-out, the lamp, the sound and the toast follow. */
	private void switched(Channel channel, String label, boolean on) {
		if (channel.index == menu.tuned()) {
			probe.toggled(channel.module.isSwitchedOn());
		}

		dial.juice(channel.index, channel.module.isSwitchedOn());
		Fx.sound("ui.button.click", 0.3f, on ? 1.4f : 0.8f);
		topBar.toast(label + (on ? " an" : " aus"), channel.category().color, true);
		notes.toggled();
	}

	/** A part or style changed while the module stays on: no lamp change, but the same click and toast. */
	private void restyled(Channel channel, String label, boolean on) {
		probe.restyled();
		card.styleChanged();
		Fx.sound("ui.button.click", 0.3f, on ? 1.4f : 0.8f);
		topBar.toast(label + (on ? " an" : " aus"), channel.category().color, true);
		notes.toggled();
	}

	/**
	 * Enter on a search hit. A whole channel toggles. A bundle part turns that part and the bundle on, and the next
	 * Enter turns the part off again. A choice entry switches the card on with that style, the next Enter switches
	 * it off.
	 */
	void toggleHit(SearchHit hit) {
		Channel channel = hit.channel();
		Module module = channel.module;
		ModuleGroup group = channel.group();

		if (channel.index != menu.tuned() || probe.focus() != hit.focus()) {
			menu.tuneTo(channel.index, hit.focus());
		}

		if (!hit.isPart() || group == null || module.alwaysOn()) {
			toggle(channel);
			return;
		}

		if (module.blockedHere()) {
			deny();
			return;
		}

		if (group.isChoice()) {
			toggleEntry(channel, hit, group);
		} else {
			togglePart(channel, hit, group);
		}
	}

	private void toggleEntry(Channel channel, SearchHit hit, ModuleGroup group) {
		Module module = channel.module;
		ChoiceSetting kind = group.kindSetting();

		if (kind == null) {
			toggle(channel);
			return;
		}

		remember(channel, hit.label(), null, kind);

		if (!module.isSwitchedOn()) {
			kind.set(hit.focus());
			module.toggle();
			switched(channel, hit.label(), true);
		} else if (kind.index() != hit.focus()) {
			kind.set(hit.focus());
			restyled(channel, hit.label(), true);
		} else {
			module.toggle();
			switched(channel, channel.name(), false);
		}
	}

	private void togglePart(Channel channel, SearchHit hit, ModuleGroup group) {
		Module module = channel.module;
		List<BoolSetting> toggles = group.partToggles();

		if (hit.focus() < 0 || hit.focus() >= toggles.size()) {
			toggle(channel);
			return;
		}

		BoolSetting part = toggles.get(hit.focus());
		remember(channel, hit.label(), part, null);

		if (!module.isSwitchedOn()) {
			part.set(true);
			module.toggle();
			switched(channel, hit.label(), true);
		} else if (!part.on()) {
			part.set(true);
			restyled(channel, hit.label(), true);
		} else {
			part.set(false);

			if (toggles.stream().noneMatch(BoolSetting::on)) {
				// That was the last part: the whole bundle goes off
				module.toggle();
				switched(channel, hit.label(), false);
			} else {
				restyled(channel, hit.label(), false);
			}
		}
	}

	private void remember(Channel channel, String label, @Nullable BoolSetting part, @Nullable ChoiceSetting kind) {
		undo.push(new Undo(channel, label, channel.module.isSwitchedOn(), part, part != null && part.on(), kind, kind != null ? kind.index() : 0));

		while (undo.size() > UNDO_MAX) {
			undo.removeLast();
		}
	}

	/** Strg+Z: puts the last toggle back. */
	void undo() {
		Undo last = undo.poll();

		if (last == null) {
			deny();
			return;
		}

		Channel channel = last.channel();
		Module module = channel.module;
		boolean wasOn = module.isSwitchedOn();

		if (last.part() != null) {
			last.part().set(last.partWas());
		}

		if (last.kind() != null) {
			last.kind().set(last.kindWas());
		}

		if (module.isSwitchedOn() != last.wasOn()) {
			module.toggle();
		}

		boolean on = module.isSwitchedOn();

		if (channel.index == menu.tuned()) {
			// toggled() only for a real switch change: on a part change it would switch the try-out's part on again
			if (on != wasOn) {
				probe.toggled(on);
			} else {
				probe.restyled();
			}
		}

		if (on != wasOn) {
			dial.juice(channel.index, on);
		}

		boolean shownOn = last.part() != null ? last.partWas() && on : on;
		topBar.toast("Rückgängig: " + last.label() + (shownOn ? " an" : " aus"), channel.category().color, false);
		Fx.sound("ui.button.click", 0.3f, shownOn ? 1.4f : 0.8f);
	}

	static void deny() {
		Fx.sound("block.note_block.bass", 0.3f, 0.6f);
	}
}

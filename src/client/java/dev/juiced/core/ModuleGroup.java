package dev.juiced.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.Setting;
import dev.juiced.render.Geo;

/**
 * Several related modules shown as one card in the menu.
 *
 * <p>A <b>choice</b> group wears one of its parts at a time (head items, back items, atmosphere):
 * the card has an "Art" list, and only the settings of the chosen part are shown.
 * A <b>bundle</b> group runs all its parts side by side (Klare Sicht: overlays, Low Fire, Low Shield,
 * hand view), each with its own switch and settings on the back of the card.
 *
 * <p>The parts are ordinary modules that aren't registered on their own. Their settings get a prefix
 * ({@code "fire."}) so they don't clash in the config. A part counts as enabled while the card is on
 * and the part is chosen or switched on, so static checks inside the parts keep working.
 */
public final class ModuleGroup extends Module {
	/** One choosable look: a part, and optionally a style of that part to pick. */
	private record Entry(String label, int part, @Nullable String choiceId, int choiceIndex) {
	}

	private final boolean choice;
	private final List<Module> parts = new ArrayList<>();
	private final List<Entry> entries = new ArrayList<>();
	private final List<BoolSetting> toggles = new ArrayList<>();
	private ChoiceSetting kind;
	private String kindLabel = "Art";
	/** While the menu tries the card out: the one entry (choice) or part (bundle) to show, or -1. */
	private int probeFocus = -1;

	private ModuleGroup(String id, String name, Category category, boolean defaultEnabled, String description, boolean choice) {
		super(id, name, category, defaultEnabled, description);
		this.choice = choice;
	}

	/** A card that wears one part at a time. */
	public static ModuleGroup choice(String id, String name, Category category, String description) {
		return new ModuleGroup(id, name, category, false, description, true);
	}

	/** A card whose parts all run at once, each switchable on its own. */
	public static ModuleGroup bundle(String id, String name, Category category, boolean defaultEnabled, String description) {
		return new ModuleGroup(id, name, category, defaultEnabled, description, false);
	}

	public ModuleGroup kindLabel(String label) {
		kindLabel = label;
		return this;
	}

	/** Adds a part to a choice group, with one entry per listed style (empty = one entry with the part's name). */
	public ModuleGroup part(String prefix, Supplier<? extends Module> create, @Nullable String choiceId, String... labels) {
		Module part = Setting.withIdPrefix(prefix, create::get);
		int index = parts.size();
		parts.add(part);

		if (labels.length == 0) {
			entries.add(new Entry(part.name, index, null, 0));
		} else {
			for (int i = 0; i < labels.length; i++) {
				entries.add(new Entry(labels[i], index, choiceId, i));
			}
		}

		return this;
	}

	/** Like {@link #part}, but picks specific styles of the part: pairs of label and style index. */
	public ModuleGroup styles(String prefix, Supplier<? extends Module> create, String choiceId, Object... labelAndIndex) {
		Module part = Setting.withIdPrefix(prefix, create::get);
		int index = parts.size();
		parts.add(part);

		for (int i = 0; i + 1 < labelAndIndex.length; i += 2) {
			entries.add(new Entry((String) labelAndIndex[i], index, choiceId, (Integer) labelAndIndex[i + 1]));
		}

		return this;
	}

	/** Adds a part to a bundle, with an on/off switch labelled {@code label}. */
	public ModuleGroup bundled(String prefix, Supplier<? extends Module> create, String label, String key) {
		Module part = Setting.withIdPrefix(prefix, create::get);
		parts.add(part);
		BoolSetting toggle = new BoolSetting("part_" + key, "■ " + label, part.isEnabled());
		toggles.add(toggle);
		return this;
	}

	/** Finishes the card: lays out its settings. Call once after adding all parts. */
	public ModuleGroup build() {
		if (choice) {
			String[] labels = new String[entries.size()];

			for (int i = 0; i < labels.length; i++) {
				labels[i] = entries.get(i).label;
			}

			kind = add(new ChoiceSetting("kind", kindLabel, 0, labels));

			for (int p = 0; p < parts.size(); p++) {
				int partIndex = p;

				for (Setting<?> setting : parts.get(p).settings) {
					if (!isPickedByGroup(partIndex, setting)) {
						settings.add(setting.showIf(() -> activePart() == partIndex));
					}
				}
			}
		} else {
			for (int p = 0; p < parts.size(); p++) {
				BoolSetting toggle = toggles.get(p);
				settings.add(toggle);

				for (Setting<?> setting : parts.get(p).settings) {
					settings.add(setting.showIf(toggle::on));
				}
			}
		}

		sync();
		return this;
	}

	/** The style setting the group sets itself is hidden from the card. */
	private boolean isPickedByGroup(int partIndex, Setting<?> setting) {
		for (Entry entry : entries) {
			if (entry.part == partIndex && entry.choiceId != null && isStyle(setting, entry.choiceId)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isStyle(Setting<?> setting, String choiceId) {
		return setting.id.equals(choiceId) || setting.id.endsWith("." + choiceId);
	}

	private int activePart() {
		if (kind == null || entries.isEmpty()) {
			return -1;
		}

		return activeEntry().part;
	}

	public List<Module> parts() {
		return parts;
	}

	/** True for a card that wears one part at a time (with an "Art" list), false for a bundle. */
	public boolean isChoice() {
		return choice;
	}

	/** The "Art" list of a choice card, or null for a bundle. */
	public @Nullable ChoiceSetting kindSetting() {
		return kind;
	}

	/** Number of entries in the "Art" list (choice cards), 0 for bundles. */
	public int entryCount() {
		return choice ? entries.size() : 0;
	}

	public String entryLabel(int entry) {
		return entries.get(entry).label;
	}

	/** The part an entry of the "Art" list belongs to. */
	public int entryPart(int entry) {
		return entries.get(entry).part;
	}

	/** The on/off switches of a bundle's parts, one per part (empty for choice cards). */
	public List<BoolSetting> partToggles() {
		return toggles;
	}

	/** Name of a bundle part as shown on its switch ("Low Fire"), or the part's own name for choice cards. */
	public String partLabel(int part) {
		if (!choice && part < toggles.size()) {
			String label = toggles.get(part).label;
			return label.startsWith("■ ") ? label.substring(2) : label;
		}

		return parts.get(part).name;
	}

	/**
	 * While the menu tries the card out (and it isn't switched on), show only this entry (choice card) or part
	 * (bundle), without changing the saved choice or switches. -1 shows the card as it is set up.
	 */
	public void setProbeFocus(int index) {
		if (probeFocus != index) {
			probeFocus = index;
			sync();
		}
	}

	public int probeFocus() {
		return probeFocus;
	}

	/** True while only the menu runs the card, so the probe focus applies. */
	private boolean probeOnly() {
		return isProbing() && !isSwitchedOn();
	}

	/** The entry of the "Art" list that is worn right now (the probe focus while the menu tries it out). */
	private Entry activeEntry() {
		int index = probeOnly() && probeFocus >= 0 ? probeFocus : kind.index();
		return entries.get(Math.max(0, Math.min(entries.size() - 1, index)));
	}

	/** Whether a bundle part runs while the card is on. */
	private boolean partActive(int p) {
		if (probeOnly()) {
			if (probeFocus >= 0) {
				return p == probeFocus;
			}

			// Tried out with every switch off: show the first part, like switching it on would
			return toggles.get(p).on() || (p == 0 && toggles.stream().noneMatch(BoolSetting::on));
		}

		return toggles.get(p).on();
	}

	/** Index of the entry with this label, or -1. Used by the wardrobe. */
	public int entryIndex(String label) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).label.equals(label)) {
				return i;
			}
		}

		return -1;
	}

	/** Switches parts on and off to match the card, and applies the chosen style. */
	private void sync() {
		boolean on = isEnabled();

		if (choice) {
			if (kind == null) {
				return;
			}

			Entry entry = activeEntry();

			if (entry.choiceId != null) {
				Module part = parts.get(entry.part);

				for (Setting<?> setting : part.settings) {
					if (setting instanceof ChoiceSetting style && isStyle(style, entry.choiceId)) {
						style.set(entry.choiceIndex);
					}
				}
			}

			for (int p = 0; p < parts.size(); p++) {
				parts.get(p).setEnabled(on && p == entry.part);
			}
		} else {
			for (int p = 0; p < parts.size(); p++) {
				parts.get(p).setEnabled(on && partActive(p));
			}
		}
	}

	// ---- hooks: passed on to the active parts ------------------------------------------------

	@Override
	protected void onEnable() {
		// A bundle switched on with every part off would do nothing: turn on the first part.
		// Not while the menu only tries it out, that must not change any saved switch.
		if (!choice && isSwitchedOn() && !toggles.isEmpty() && toggles.stream().noneMatch(BoolSetting::on)) {
			toggles.get(0).set(true);
		}

		sync();
	}

	@Override
	protected void onDisable() {
		sync();
	}

	@Override
	public void clearEffects() {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.clearEffects();
			}
		}
	}

	@Override
	public void tick(Minecraft minecraft) {
		sync();

		for (Module part : parts) {
			if (part.isEnabled()) {
				part.tick(minecraft);
			}
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		sync();

		for (Module part : parts) {
			if (part.isEnabled()) {
				part.renderWorld(geo);
			}
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, Hud hud) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.renderHud(graphics, hud);
			}
		}
	}

	@Override
	public void onHit(LivingEntity target, boolean crit) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.onHit(target, crit);
			}
		}
	}

	@Override
	public void onKill(LivingEntity target) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.onKill(target);
			}
		}
	}

	@Override
	public void onHealthChange(LivingEntity entity, float amount, boolean byMe, boolean crit) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.onHealthChange(entity, amount, byMe, crit);
			}
		}
	}

	@Override
	public void onDeath() {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.onDeath();
			}
		}
	}

	@Override
	public void onTotemPop(LivingEntity entity, int pops) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.onTotemPop(entity, pops);
			}
		}
	}

	@Override
	public void onJump(Entity entity) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.onJump(entity);
			}
		}
	}

	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.probeTick(minecraft, ticks);
			}
		}
	}

	/** Live if any running part can be seen right now; otherwise the reason of the first running part. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		String reason = null;
		boolean any = false;

		for (Module part : parts) {
			if (part.isEnabled()) {
				any = true;
				String partReason = part.probeLiveReason(minecraft);

				if (partReason == null) {
					return null;
				}

				if (reason == null) {
					reason = partReason;
				}
			}
		}

		return any ? reason : "Alle Teile ausgeschaltet";
	}

	@Override
	public void onLand(Entity entity, float fallHeight) {
		for (Module part : parts) {
			if (part.isEnabled()) {
				part.onLand(entity, fallHeight);
			}
		}
	}

	@Override
	public void resetToDefaults() {
		super.resetToDefaults();
		sync();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float time) {
		if (choice) {
			int part = activePart();

			// Show the chosen look even while the card is off
			if (part >= 0) {
				Entry entry = activeEntry();

				if (entry.choiceId != null) {
					for (Setting<?> setting : parts.get(part).settings) {
						if (setting instanceof ChoiceSetting style && isStyle(style, entry.choiceId)) {
							style.set(entry.choiceIndex);
						}
					}
				}

				parts.get(part).drawPreview(graphics, x, y, w, h, time);
				return;
			}
		}

		// Bundles: the part the menu focuses, else the first switched-on part, otherwise the first part
		if (probeOnly() && probeFocus >= 0 && probeFocus < parts.size()) {
			parts.get(probeFocus).drawPreview(graphics, x, y, w, h, time);
			return;
		}

		for (int p = 0; p < toggles.size(); p++) {
			if (toggles.get(p).on()) {
				parts.get(p).drawPreview(graphics, x, y, w, h, time);
				return;
			}
		}

		if (!parts.isEmpty()) {
			parts.get(0).drawPreview(graphics, x, y, w, h, time);
		}
	}
}

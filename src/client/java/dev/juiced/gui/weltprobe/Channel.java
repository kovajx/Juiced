package dev.juiced.gui.weltprobe;

import org.jspecify.annotations.Nullable;

import dev.juiced.core.Category;
import dev.juiced.core.HudModule;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleGroup;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.Setting;

/** One module as a channel of the Weltprobe menu: its place on the dial, its camera shot and how it is shown. */
public final class Channel {
	public final Module module;
	/** Position on the dial, 0..48, in dial order. */
	public final int index;
	public final Shot shot;
	/** Where to look for HIMMEL shots (Minecraft yaw, 180 = north), or NaN for the way you face. */
	public final float aimYaw;
	/** Pitch for HIMMEL shots (negative looks up), or NaN for the shot's default. */
	public final float aimPitch;
	public final DummyMode dummy;
	/** Id of the setting {@link #mainStyle} returns, for modules that are not choice cards. */
	final @Nullable String styleId;

	Channel(Module module, int index, Shot shot, float aimYaw, float aimPitch, DummyMode dummy, @Nullable String styleId) {
		this.module = module;
		this.index = index;
		this.shot = shot;
		this.aimYaw = aimYaw;
		this.aimPitch = aimPitch;
		this.dummy = dummy;
		this.styleId = styleId;
	}

	public Category category() {
		return module.category;
	}

	public String name() {
		return module.name;
	}

	/** HUD channels show their element at its real place in your normal view and can be dragged there. */
	public boolean isHud() {
		return module.category == Category.HUD;
	}

	/** A HUD channel whose element has a position (all but the centred attack indicator and low-health warning). */
	public @Nullable HudModule hudModule() {
		return module instanceof HudModule hud ? hud : null;
	}

	public @Nullable ModuleGroup group() {
		return module instanceof ModuleGroup group ? group : null;
	}

	/**
	 * The setting Up/Down cycles through ("Art < Kirschblüten >" on the card): the Art list of a choice card,
	 * or the module's main style. Null if the channel has none.
	 */
	public @Nullable ChoiceSetting mainStyle() {
		if (module instanceof ModuleGroup group && group.isChoice()) {
			return group.kindSetting();
		}

		if (styleId == null) {
			return null;
		}

		for (Setting<?> setting : module.settings) {
			if (setting instanceof ChoiceSetting choice && setting.id.equals(styleId)) {
				return choice;
			}
		}

		return null;
	}

	@Override
	public String toString() {
		return "Channel[" + index + " " + module.id + "]";
	}
}

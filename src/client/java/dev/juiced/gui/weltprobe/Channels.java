package dev.juiced.gui.weltprobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleManager;

/**
 * The 49 channels in dial order, sorted so that zapping through a category moves the camera as little as
 * possible (fights at the stage, then things around you, then the sky, then your own view).
 */
public final class Channels {
	private static final float ANY = Float.NaN;
	private static List<Channel> all;

	private Channels() {
	}

	public static List<Channel> all() {
		if (all == null) {
			all = build();
		}

		return all;
	}

	public static List<Channel> in(Category category) {
		List<Channel> result = new ArrayList<>();

		for (Channel channel : all()) {
			if (channel.category() == category) {
				result.add(channel);
			}
		}

		return result;
	}

	public static @Nullable Channel of(Module module) {
		for (Channel channel : all()) {
			if (channel.module == module) {
				return channel;
			}
		}

		return null;
	}

	private static List<Channel> build() {
		List<Channel> list = new ArrayList<>();
		// Kampf
		add(list, "hit_effects", Shot.BUEHNE, DummyMode.HITS, "style");
		add(list, "kill_effects", Shot.BUEHNE, DummyMode.KILLS, "style");
		add(list, "totem_pops", Shot.BUEHNE, DummyMode.TOTEMS, null);
		add(list, "knockback_comet", Shot.BUEHNE, DummyMode.KNOCKBACK, null);
		add(list, "target_marker", Shot.BUEHNE, DummyMode.TARGET, "style");
		// Optik
		add(list, "damage_numbers", Shot.BUEHNE, DummyMode.DAMAGE, "format");
		add(list, "jump_circles", Shot.NAH_TOP, DummyMode.NONE, "style");
		add(list, "footprints", Shot.NAH_TOP, DummyMode.NONE, "style");
		add(list, "trails", Shot.BUEHNE, DummyMode.NONE, "style");
		add(list, "ambient", Shot.BUEHNE, DummyMode.NONE, null);
		add(list, "environment", Shot.BUEHNE, DummyMode.NONE, null);
		add(list, "koi_pond", Shot.BUEHNE, DummyMode.NONE, "look");
		add(list, "fishing", Shot.BUEHNE, DummyMode.NONE, null);
		add(list, "sky", Shot.HIMMEL, 180f, -32f, DummyMode.NONE, "aurora.colors");
		add(list, "screen_effects", Shot.KEEP, DummyMode.NONE, null);
		add(list, "block_outline", Shot.EGO, DummyMode.NONE, "style");
		add(list, "crosshair", Shot.EGO, DummyMode.NONE, "style");
		// Tweaks
		add(list, "fullbright", Shot.EGO, DummyMode.NONE, null);
		add(list, "zoom", Shot.EGO, DummyMode.NONE, null);
		add(list, "clear_view", Shot.EGO, DummyMode.NONE, null);
		add(list, "trajectories", Shot.EGO, DummyMode.NONE, null);
		add(list, "explosions", Shot.KEEP, DummyMode.NONE, null);
		add(list, "smooth_ui", Shot.KEEP, DummyMode.NONE, null);
		add(list, "toggle_sprint", Shot.EGO, DummyMode.NONE, null);
		add(list, "freelook", Shot.NAH_REAR, DummyMode.NONE, null);
		add(list, "zen_mode", Shot.BUEHNE, DummyMode.NONE, null);
		// Kosmetik: one sweep from the front round to the back
		add(list, "head", Shot.NAH_FRONT, DummyMode.NONE, null);
		add(list, "ears", Shot.NAH_FRONT, DummyMode.NONE, "kind");
		add(list, "aura", Shot.NAH_FRONT, DummyMode.NONE, "style");
		add(list, "pet", Shot.NAH_FRONT, DummyMode.NONE, "kind");
		add(list, "blades", Shot.NAH_REAR, DummyMode.NONE, "layout");
		add(list, "shadow_pet", Shot.NAH_TOP, DummyMode.NONE, "kind");
		add(list, "tail", Shot.NAH_REAR, DummyMode.NONE, "kind");
		add(list, "back", Shot.NAH_REAR, DummyMode.NONE, null);
		// HUD
		add(list, "info_hud", Shot.EGO, DummyMode.NONE, "layout");
		add(list, "compass", Shot.EGO, DummyMode.NONE, null);
		add(list, "armor_hud", Shot.EGO, DummyMode.NONE, "layout");
		add(list, "effects_hud", Shot.EGO, DummyMode.NONE, null);
		add(list, "target_hud", Shot.EGO, DummyMode.NONE, null);
		add(list, "keystrokes", Shot.EGO, DummyMode.NONE, null);
		add(list, "item_counter", Shot.EGO, DummyMode.NONE, "layout");
		add(list, "cooldowns", Shot.EGO, DummyMode.NONE, null);
		add(list, "totem_warning", Shot.EGO, DummyMode.NONE, null);
		add(list, "reach", Shot.EGO, DummyMode.NONE, null);
		add(list, "session_stats", Shot.EGO, DummyMode.NONE, null);
		add(list, "attack_indicator", Shot.EGO, DummyMode.NONE, "style");
		add(list, "low_health", Shot.EGO, DummyMode.NONE, null);
		// System
		add(list, "theme", Shot.KEEP, DummyMode.NONE, null);
		add(list, "performance", Shot.KEEP, DummyMode.NONE, "quality");

		// Anything registered but missing above still gets a channel at the end of its category
		for (Module module : ModuleManager.all()) {
			if (list.stream().noneMatch(c -> c.module == module)) {
				JuicedClient.LOGGER.warn("Juiced: module {} has no Weltprobe channel entry, adding it", module.id);
				list.add(new Channel(module, 0, module.category == Category.HUD ? Shot.EGO : Shot.KEEP, ANY, ANY, DummyMode.NONE, null));
			}
		}

		// Dial order: by category, keeping the order above inside each category
		List<Channel> ordered = new ArrayList<>();

		for (Category category : Category.values()) {
			for (Channel channel : list) {
				if (channel.category() == category) {
					ordered.add(new Channel(channel.module, ordered.size(), channel.shot, channel.aimYaw, channel.aimPitch, channel.dummy, channel.styleId));
				}
			}
		}

		return Collections.unmodifiableList(ordered);
	}

	private static void add(List<Channel> list, String id, Shot shot, DummyMode dummy, @Nullable String styleId) {
		add(list, id, shot, ANY, ANY, dummy, styleId);
	}

	private static void add(List<Channel> list, String id, Shot shot, float aimYaw, float aimPitch, DummyMode dummy, @Nullable String styleId) {
		Module module = ModuleManager.byId(id);

		if (module == null) {
			JuicedClient.LOGGER.warn("Juiced: Weltprobe channel {} has no module", id);
			return;
		}

		list.add(new Channel(module, list.size(), shot, aimYaw, aimPitch, dummy, styleId));
	}
}

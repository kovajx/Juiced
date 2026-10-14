package dev.juiced.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.juiced.core.setting.Setting;

/**
 * Saved cosmetic outfits. An outfit remembers which cosmetics are on and all their settings,
 * so one click brings back a complete look.
 */
public final class Outfits {
	public static final int SLOTS = 4;
	private static final JsonObject[] SAVED = new JsonObject[SLOTS];

	private Outfits() {
	}

	public static boolean has(int slot) {
		return SAVED[slot] != null;
	}

	/** Stores the current cosmetics in a slot. */
	public static void save(int slot) {
		JsonObject outfit = new JsonObject();

		for (Module module : ModuleManager.in(Category.COSMETICS)) {
			JsonObject entry = new JsonObject();
			entry.addProperty("enabled", module.isEnabled());
			JsonObject settings = new JsonObject();

			for (Setting<?> setting : module.settings) {
				if (setting.saved()) {
					settings.add(setting.id, setting.toJson());
				}
			}

			entry.add("settings", settings);
			outfit.add(module.id, entry);
		}

		SAVED[slot] = outfit;
	}

	/** Puts on a saved outfit. Cosmetics that weren't saved in it are switched off. */
	public static boolean load(int slot) {
		JsonObject outfit = SAVED[slot];

		if (outfit == null) {
			return false;
		}

		for (Module module : ModuleManager.in(Category.COSMETICS)) {
			JsonObject entry = outfit.getAsJsonObject(module.id);

			if (entry == null) {
				module.setEnabled(false);
				continue;
			}

			JsonElement enabled = entry.get("enabled");
			module.setEnabled(enabled != null && enabled.isJsonPrimitive() && enabled.getAsBoolean());
			JsonObject settings = entry.getAsJsonObject("settings");

			if (settings != null) {
				for (Setting<?> setting : module.settings) {
					setting.fromJson(settings.get(setting.id));
				}
			}
		}

		return true;
	}

	/** Short description like "Halo, Cape, Begleiter". */
	public static String summary(int slot) {
		JsonObject outfit = SAVED[slot];

		if (outfit == null) {
			return "leer";
		}

		StringBuilder text = new StringBuilder();

		for (Module module : ModuleManager.in(Category.COSMETICS)) {
			JsonObject entry = outfit.getAsJsonObject(module.id);
			JsonElement enabled = entry == null ? null : entry.get("enabled");

			if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsBoolean()) {
				if (!text.isEmpty()) {
					text.append(", ");
				}

				text.append(module.name);
			}
		}

		return text.isEmpty() ? "nichts an" : text.toString();
	}

	static JsonElement toJson() {
		JsonArray array = new JsonArray();

		for (JsonObject outfit : SAVED) {
			array.add(outfit == null ? new JsonObject() : outfit);
		}

		return array;
	}

	static void fromJson(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return;
		}

		JsonArray array = json.getAsJsonArray();

		for (int i = 0; i < SLOTS && i < array.size(); i++) {
			JsonElement element = array.get(i);
			SAVED[i] = element.isJsonObject() && element.getAsJsonObject().size() > 0 ? element.getAsJsonObject() : null;
		}
	}
}

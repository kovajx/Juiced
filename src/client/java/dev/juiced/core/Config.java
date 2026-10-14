package dev.juiced.core;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import dev.juiced.JuicedClient;
import dev.juiced.core.setting.Setting;

/** Saves which modules are on and all their settings to config/juiced.json. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Config() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("juiced.json");
	}

	public static void load() {
		Path path = path();

		if (!Files.exists(path)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement root = JsonParser.parseReader(reader);

			if (!root.isJsonObject()) {
				return;
			}

			JsonObject rootObject = root.getAsJsonObject();
			Outfits.fromJson(rootObject.get("outfits"));
			JsonElement recent = rootObject.get("recent_colors");

			if (recent != null && recent.isJsonArray()) {
				Colors.RECENT.clear();

				for (JsonElement color : recent.getAsJsonArray()) {
					if (color.isJsonPrimitive() && Colors.RECENT.size() < 8) {
						Colors.RECENT.add(color.getAsInt() & 0xFFFFFF);
					}
				}
			}

			JsonObject modules = rootObject.getAsJsonObject("modules");

			if (modules == null) {
				return;
			}

			for (Module module : ModuleManager.all()) {
				JsonObject saved = modules.getAsJsonObject(module.id);

				if (saved == null) {
					continue;
				}

				JsonElement enabled = saved.get("enabled");

				if (enabled != null && enabled.isJsonPrimitive()) {
					module.setEnabled(enabled.getAsBoolean());
				}

				if (module instanceof HudModule hudModule) {
					hudModule.positionFromJson(saved.getAsJsonObject("hud"));
				}

				JsonObject settings = saved.getAsJsonObject("settings");

				if (settings != null) {
					for (Setting<?> setting : module.settings) {
						setting.fromJson(settings.get(setting.id));
					}
				}
			}
		} catch (Exception e) {
			JuicedClient.LOGGER.warn("Could not read {}, using defaults", path, e);
		}
	}

	public static void save() {
		JsonObject modules = new JsonObject();

		for (Module module : ModuleManager.all()) {
			JsonObject saved = new JsonObject();
			saved.addProperty("enabled", module.isEnabled());
			JsonObject settings = new JsonObject();

			for (Setting<?> setting : module.settings) {
				if (setting.saved()) {
					settings.add(setting.id, setting.toJson());
				}
			}

			saved.add("settings", settings);

			if (module instanceof HudModule hudModule) {
				saved.add("hud", hudModule.positionToJson());
			}

			modules.add(module.id, saved);
		}

		JsonObject root = new JsonObject();
		root.addProperty("version", 2);
		root.add("modules", modules);
		root.add("outfits", Outfits.toJson());
		JsonArray recent = new JsonArray();

		for (int color : Colors.RECENT) {
			recent.add(color);
		}

		root.add("recent_colors", recent);
		Path path = path();

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception e) {
			JuicedClient.LOGGER.warn("Could not save {}", path, e);
		}
	}
}

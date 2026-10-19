package dev.juiced.core;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

		JsonObject root;

		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement parsed = JsonParser.parseReader(reader);

			if (!parsed.isJsonObject()) {
				throw new IllegalStateException("not a JSON object");
			}

			root = parsed.getAsJsonObject();
		} catch (Exception e) {
			// Keep the broken file, the next save would overwrite it with the defaults
			Path backup = path.resolveSibling("juiced.json.broken");
			JuicedClient.LOGGER.warn("Could not read {}, using defaults. The old file is kept as {}", path, backup.getFileName(), e);

			try {
				Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception copyError) {
				JuicedClient.LOGGER.warn("Could not back up {}", path, copyError);
			}

			return;
		}

		// Every part is read on its own, so one broken entry never loses the rest
		try {
			Outfits.fromJson(root.get("outfits"));
		} catch (RuntimeException e) {
			JuicedClient.LOGGER.warn("Ignoring broken outfits in {}", path, e);
		}

		JsonElement recent = root.get("recent_colors");

		if (recent != null && recent.isJsonArray()) {
			Colors.RECENT.clear();

			for (JsonElement color : recent.getAsJsonArray()) {
				if (color.isJsonPrimitive() && color.getAsJsonPrimitive().isNumber() && Colors.RECENT.size() < 8) {
					Colors.RECENT.add(color.getAsInt() & 0xFFFFFF);
				}
			}
		}

		JsonObject modules = object(root, "modules");

		if (modules == null) {
			return;
		}

		for (Module module : ModuleManager.all()) {
			JsonObject saved = object(modules, module.id);

			if (saved == null) {
				continue;
			}

			try {
				JsonElement enabled = saved.get("enabled");

				if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
					module.setEnabled(enabled.getAsBoolean());
				}

				if (module instanceof HudModule hudModule) {
					hudModule.positionFromJson(object(saved, "hud"));
				}

				JsonObject settings = object(saved, "settings");

				if (settings != null) {
					for (Setting<?> setting : module.settings) {
						setting.fromJson(settings.get(setting.id));
					}
				}
			} catch (RuntimeException e) {
				JuicedClient.LOGGER.warn("Ignoring broken settings of module {} in {}", module.id, path, e);
			}
		}
	}

	/** The child object called {@code key}, or null if it is missing or not an object. */
	private static JsonObject object(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	public static void save() {
		JsonObject modules = new JsonObject();

		for (Module module : ModuleManager.all()) {
			JsonObject saved = new JsonObject();
			saved.addProperty("enabled", module.isSwitchedOn());
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
		Path temp = path.resolveSibling("juiced.json.tmp");

		try {
			Files.createDirectories(path.getParent());

			// Write next to the config first and swap it in, so a crash mid-save never leaves half a file
			try (Writer writer = Files.newBufferedWriter(temp)) {
				GSON.toJson(root, writer);
			}

			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			JuicedClient.LOGGER.warn("Could not save {}", path, e);
		}
	}
}

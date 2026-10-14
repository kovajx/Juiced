package dev.juiced.core.setting;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/** A list of texts that is saved but not shown directly (edited through buttons). */
public final class StringListSetting extends Setting<List<String>> {
	public StringListSetting(String id, List<String> defaults) {
		super(id, id, List.copyOf(defaults));
		this.value = new ArrayList<>(defaults);
	}

	@Override
	public void reset() {
		value = new ArrayList<>(defaultValue);
	}

	@Override
	public boolean visible() {
		return false;
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();

		for (String entry : value) {
			array.add(entry);
		}

		return array;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return;
		}

		List<String> loaded = new ArrayList<>();

		for (JsonElement element : json.getAsJsonArray()) {
			if (element.isJsonPrimitive()) {
				loaded.add(element.getAsString());
			}
		}

		value = loaded;
	}
}

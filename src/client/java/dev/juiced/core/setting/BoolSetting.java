package dev.juiced.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, String label, boolean defaultValue) {
		super(id, label, defaultValue);
	}

	public boolean on() {
		return value;
	}

	public void toggle() {
		value = !value;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			value = json.getAsBoolean();
		}
	}
}

package dev.juiced.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Pick one of a few named options (styles, positions, ...). Saved by name so reordering options is safe. */
public final class ChoiceSetting extends Setting<Integer> {
	public final String[] options;

	public ChoiceSetting(String id, String label, int defaultIndex, String... options) {
		super(id, label, defaultIndex);
		this.options = options;
	}

	public int index() {
		return value;
	}

	public String name() {
		return options[value];
	}

	public boolean is(int index) {
		return value == index;
	}

	public void next() {
		value = (value + 1) % options.length;
	}

	public void previous() {
		value = (value - 1 + options.length) % options.length;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(options[value]);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonPrimitive()) {
			return;
		}

		String saved = json.getAsString();

		for (int i = 0; i < options.length; i++) {
			if (options[i].equals(saved)) {
				value = i;
				return;
			}
		}
	}
}

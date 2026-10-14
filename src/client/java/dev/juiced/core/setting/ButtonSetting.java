package dev.juiced.core.setting;

import java.util.function.Supplier;

import com.google.gson.JsonElement;

/** A row with a text on the left and a button on the right. Stores nothing. */
public final class ButtonSetting extends Setting<Integer> {
	private final Supplier<String> text;
	private final Supplier<String> button;
	private final Runnable action;

	public ButtonSetting(String id, Supplier<String> text, Supplier<String> button, Runnable action) {
		super(id, "", 0);
		this.text = text;
		this.button = button;
		this.action = action;
	}

	public String text() {
		return text.get();
	}

	public String button() {
		return button.get();
	}

	public void press() {
		action.run();
	}

	@Override
	public boolean saved() {
		return false;
	}

	@Override
	public JsonElement toJson() {
		return null;
	}

	@Override
	public void fromJson(JsonElement json) {
	}
}

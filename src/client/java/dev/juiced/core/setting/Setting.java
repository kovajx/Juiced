package dev.juiced.core.setting;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.google.gson.JsonElement;

/** One adjustable value of a module, shown on the back of its card in the menu. */
public abstract class Setting<T> {
	public final String id;
	public final String label;
	protected T value;
	protected final T defaultValue;
	private BooleanSupplier shownWhen;

	/** Prepended to the ids of settings created inside {@link #withIdPrefix}. */
	private static String idPrefix = "";

	protected Setting(String id, String label, T defaultValue) {
		this.id = idPrefix + id;
		this.label = label;
		this.value = defaultValue;
		this.defaultValue = defaultValue;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		this.value = defaultValue;
	}

	/**
	 * Runs {@code build} so that every setting created meanwhile gets {@code prefix} in front of its id.
	 * Used when several modules are shown as one card, so their settings don't clash in the config.
	 */
	public static <R> R withIdPrefix(String prefix, Supplier<R> build) {
		String previous = idPrefix;
		idPrefix = previous + prefix;

		try {
			return build.get();
		} finally {
			idPrefix = previous;
		}
	}

	/** Only show this setting while {@code condition} is true (combined with any earlier condition). */
	public Setting<T> showIf(BooleanSupplier condition) {
		BooleanSupplier earlier = shownWhen;
		shownWhen = earlier == null ? condition : () -> earlier.getAsBoolean() && condition.getAsBoolean();
		return this;
	}

	/** False for settings that are only stored, never shown (like lists edited by buttons), or hidden right now. */
	public boolean visible() {
		return shownWhen == null || shownWhen.getAsBoolean();
	}

	/** False for settings that hold no value (buttons). */
	public boolean saved() {
		return true;
	}

	public abstract JsonElement toJson();

	/** Reads a saved value. Invalid values are ignored so a broken config never crashes the game. */
	public abstract void fromJson(JsonElement json);
}

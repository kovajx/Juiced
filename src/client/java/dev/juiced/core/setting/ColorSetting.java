package dev.juiced.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import dev.juiced.core.Colors;
import dev.juiced.core.Theme;

/**
 * A color: one of the palette colors, a free color from the color picker,
 * the theme accent, or a cycling rainbow.
 */
public final class ColorSetting extends Setting<Integer> {
	public static final int THEME = -1;
	public static final int RAINBOW = -2;
	/** Values with this bit set hold a free 0xRRGGBB color picked in the color picker. */
	public static final int CUSTOM = 0x1000000;

	public final boolean allowTheme;

	/** @param defaultValue a palette index, {@link #THEME} or {@link #RAINBOW} */
	public ColorSetting(String id, String label, int defaultValue, boolean allowTheme) {
		super(id, label, defaultValue);
		this.allowTheme = allowTheme;
	}

	public ColorSetting(String id, String label) {
		this(id, label, THEME, true);
	}

	/** The color as 0xRRGGBB. {@code offset} shifts rainbow colors along a trail or ring (0..1). */
	public int rgb(float offset) {
		int v = value;

		if (v == THEME) {
			return Theme.accent(offset);
		}

		if (v == RAINBOW) {
			return Theme.rainbow(offset);
		}

		if (isCustom(v)) {
			return v & 0xFFFFFF;
		}

		return Colors.PALETTE[Math.floorMod(v, Colors.PALETTE.length)];
	}

	public int rgb() {
		return rgb(0f);
	}

	public static boolean isCustom(int value) {
		return value > 0 && (value & CUSTOM) != 0;
	}

	public boolean isCustom() {
		return isCustom(value);
	}

	/** Sets a free color (0xRRGGBB). */
	public void setCustom(int rgb) {
		value = CUSTOM | (rgb & 0xFFFFFF);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			int saved = json.getAsInt();

			if ((saved == THEME && allowTheme) || saved == RAINBOW || (saved >= 0 && saved < Colors.PALETTE.length) || isCustom(saved)) {
				value = saved;
			}
		}
	}
}

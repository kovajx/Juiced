package dev.juiced.core.setting;

import java.util.function.DoubleFunction;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class SliderSetting extends Setting<Double> {
	public final double min;
	public final double max;
	public final double step;
	private final DoubleFunction<String> format;

	public SliderSetting(String id, String label, double min, double max, double step, double defaultValue, DoubleFunction<String> format) {
		super(id, label, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
	}

	/** Slider shown as a percentage, e.g. 1.0 -> "100%". */
	public static SliderSetting percent(String id, String label, double min, double max, double defaultValue) {
		return new SliderSetting(id, label, min, max, 0.05, defaultValue, v -> Math.round(v * 100) + "%");
	}

	public float f() {
		return (float) (double) value;
	}

	public int i() {
		return (int) Math.round(value);
	}

	/** Position of the value along the slider, 0..1. */
	public double fraction() {
		return (value - min) / (max - min);
	}

	public void setFraction(double t) {
		t = Math.max(0, Math.min(1, t));
		double raw = min + t * (max - min);
		double snapped = Math.round(raw / step) * step;
		value = Math.max(min, Math.min(max, snapped));
	}

	public String display() {
		return format.apply(value);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			value = Math.max(min, Math.min(max, json.getAsDouble()));
		}
	}
}

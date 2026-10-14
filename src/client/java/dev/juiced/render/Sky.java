package dev.juiced.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;

import dev.juiced.JuicedClient;

/**
 * How much day or night it is right now, read from the same sky values the game uses for its own
 * sky (star brightness and sky light). The values are looked up by name once; if one is missing in
 * this Minecraft version, the effects that need it fall back as described below.
 */
public final class Sky {
	private static boolean looked;
	private static @Nullable EnvironmentAttribute<?> starBrightness;
	private static @Nullable EnvironmentAttribute<?> skyLight;

	private Sky() {
	}

	/** True in the overworld, where the sky effects make sense. */
	public static boolean overworld(ClientLevel level) {
		return level.dimension() == Level.OVERWORLD;
	}

	/** 0 by day, 1 in the middle of the night. 0 if the game doesn't tell us. */
	public static float night(ClientLevel level, BlockPos pos) {
		lookUp();
		// Star brightness peaks at 0.5 at night
		return Math.min(1f, value(level, starBrightness, pos, 0f) * 2f);
	}

	/** 1 by day, 0 at night. 1 if the game doesn't tell us. */
	public static float day(ClientLevel level, BlockPos pos) {
		lookUp();
		// Sky light goes from 1 by day down to 0.24 at night
		return Math.max(0f, Math.min(1f, (value(level, skyLight, pos, 1f) - 0.24f) / 0.76f));
	}

	private static float value(ClientLevel level, @Nullable EnvironmentAttribute<?> attribute, BlockPos pos, float fallback) {
		if (attribute == null) {
			return fallback;
		}

		Object value = level.environmentAttributes().getValue(attribute, pos);
		return value instanceof Number number ? number.floatValue() : fallback;
	}

	private static void lookUp() {
		if (looked) {
			return;
		}

		looked = true;
		starBrightness = attribute("STAR_BRIGHTNESS");
		skyLight = attribute("SKY_LIGHT_FACTOR");
	}

	private static @Nullable EnvironmentAttribute<?> attribute(String name) {
		try {
			if (EnvironmentAttributes.class.getField(name).get(null) instanceof EnvironmentAttribute<?> attribute) {
				return attribute;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// handled below
		}

		JuicedClient.LOGGER.warn("Juiced: sky value {} not found, day/night effects use a fallback", name);
		return null;
	}
}

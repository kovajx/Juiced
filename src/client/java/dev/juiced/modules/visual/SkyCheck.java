package dev.juiced.modules.visual;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.render.NearbyBlocks;
import dev.juiced.render.Sky;

/**
 * Why the sky effects (northern lights, constellations, bird flocks) can't be seen right now, for the
 * Weltprobe menu. The effects themselves only check the dimension and the rain; under a roof they are
 * drawn but hidden behind it, which the menu would show as an empty sky.
 */
final class SkyCheck {
	private SkyCheck() {
	}

	/** A caption if there is no open sky above you, else null. Trees don't count as a roof. */
	static @Nullable String reason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			return null;
		}

		if (!Sky.overworld(level)) {
			return "Hier gibt es keinen Himmel";
		}

		if (level.isRaining()) {
			return "Nicht bei Regen";
		}

		if (NearbyBlocks.surface(level, player.getBlockX(), player.getBlockZ(), true) > player.getEyeY() + 0.5) {
			return "Nur unter freiem Himmel";
		}

		return null;
	}
}

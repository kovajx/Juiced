package dev.juiced.render;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Client-only sounds and vanilla particles. Nothing here is sent to the server. */
public final class Fx {
	private Fx() {
	}

	/** Plays a vanilla sound by id (like "block.note_block.hat") that only you hear. */
	public static void sound(String id, float volume, float pitch) {
		if (volume <= 0.001f) {
			return;
		}

		SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));

		if (event != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, Math.min(1f, volume)));
		}
	}

	/** Spawns vanilla particles around a point; the game's own particle engine handles them. */
	public static void particles(ParticleOptions type, double x, double y, double z, int count, double spread, double speed) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || count <= 0) {
			return;
		}

		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < count; i++) {
			double ox = (random.nextDouble() - 0.5) * 2 * spread;
			double oy = (random.nextDouble() - 0.5) * 2 * spread;
			double oz = (random.nextDouble() - 0.5) * 2 * spread;
			double vx = (random.nextDouble() - 0.5) * 2 * speed;
			double vy = (random.nextDouble() - 0.3) * 2 * speed;
			double vz = (random.nextDouble() - 0.5) * 2 * speed;
			level.addParticle(type, x + ox, y + oy, z + oz, vx, vy, vz);
		}
	}

	/** Point on the target's body facing you, so hit effects appear where you see the hit. */
	public static Vec3 impactPoint(Entity target) {
		double x = target.getX();
		double y = target.getY() + target.getBbHeight() * 0.6;
		double z = target.getZ();
		Player player = Minecraft.getInstance().player;

		if (player != null) {
			double dx = player.getX() - x;
			double dz = player.getZ() - z;
			double len = Math.sqrt(dx * dx + dz * dz);

			if (len > 1.0e-3) {
				double push = target.getBbWidth() * 0.5;
				x += dx / len * push;
				z += dz / len * push;
			}
		}

		return new Vec3(x, y, z);
	}
}

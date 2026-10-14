package dev.juiced.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/** Keeps track of the render state that belongs to your own player this frame. */
public final class LocalPlayerState {
	private static @Nullable EntityRenderState state;

	private LocalPlayerState() {
	}

	/** Called for every entity whose render state was just filled in. */
	public static void record(Entity entity, EntityRenderState filled) {
		if (entity == Minecraft.getInstance().player) {
			state = filled;
		} else if (filled == state) {
			// The same state object is now used for someone else
			state = null;
		}
	}

	public static boolean is(EntityRenderState candidate) {
		return candidate != null && candidate == state;
	}
}

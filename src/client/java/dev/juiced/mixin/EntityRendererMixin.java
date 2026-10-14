package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import dev.juiced.render.LocalPlayerState;

/** Remembers which render state belongs to your own player, so layers can tell it apart from others. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void juiced$tagLocalPlayer(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		LocalPlayerState.record(entity, state);
	}
}

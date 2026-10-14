package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import dev.juiced.modules.cosmetic.Cape;
import dev.juiced.render.LocalPlayerState;

/** Hides your real cape while the Juiced cape is on, so the two don't overlap. Other players keep theirs. */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void juiced$hideOwnCape(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
		if (Cape.hidesVanilla() && LocalPlayerState.is(state)) {
			ci.cancel();
		}
	}
}

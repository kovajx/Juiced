package dev.juiced.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import dev.juiced.modules.tweaks.ClearView;
import dev.juiced.modules.tweaks.LowFire;

/** Low Fire, and the water / in-block overlays of "Klare Sicht". */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true, require = 0)
	private static void juiced$fireStart(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
		LowFire.before(poseStack, ci);
	}

	@Inject(method = "submitFire", at = @At("RETURN"), require = 0)
	private static void juiced$fireEnd(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
		LowFire.after(poseStack);
	}

	@Inject(method = "submitWater", at = @At("HEAD"), cancellable = true, require = 0)
	private static void juiced$water(CallbackInfo ci) {
		if (ClearView.noWater()) {
			ci.cancel();
		}
	}

	@Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true, require = 0)
	private static void juiced$inBlock(CallbackInfo ci) {
		if (ClearView.noInBlock()) {
			ci.cancel();
		}
	}
}

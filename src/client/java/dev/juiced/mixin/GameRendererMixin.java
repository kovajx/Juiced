package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import dev.juiced.modules.tweaks.ClearView;

/** Removes the screen tilt when you get hit ("Klare Sicht"). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
	private void juiced$noHurtCam(CallbackInfo ci) {
		if (ClearView.noHurtCam()) {
			ci.cancel();
		}
	}
}

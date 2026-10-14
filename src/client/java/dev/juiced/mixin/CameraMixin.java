package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;

import dev.juiced.modules.tweaks.Zoom;
import dev.juiced.render.FreeCamera;

/**
 * Zoom: narrows the field of view while the zoom key is held.
 * Freelook and wardrobe: turn and move the third-person camera on its own.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void juiced$zoom(CallbackInfoReturnable<Float> cir) {
		float multiplier = Zoom.fovMultiplier();

		if (multiplier != 1f) {
			cir.setReturnValue(cir.getReturnValue() * multiplier);
		}
	}

	@Inject(method = "alignWithEntity", at = @At("HEAD"), require = 0)
	private void juiced$freeCameraFrame(float partialTicks, CallbackInfo ci) {
		FreeCamera.frame();
	}

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), index = 0, require = 0)
	private float juiced$freeCameraYaw(float yaw) {
		return FreeCamera.cameraYaw(yaw);
	}

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), index = 1, require = 0)
	private float juiced$freeCameraPitch(float pitch) {
		return FreeCamera.cameraPitch(pitch);
	}

	@ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true, require = 0)
	private float juiced$freeCameraDistance(float distance) {
		return FreeCamera.cameraDistance(distance);
	}
}

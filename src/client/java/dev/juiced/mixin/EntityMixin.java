package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import dev.juiced.modules.tweaks.ZenMode;
import dev.juiced.render.FreeCamera;

/** While Freelook (or the wardrobe) owns the camera, mouse movement turns the camera instead of your player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
	private void juiced$freeCameraTurn(double xo, double yo, CallbackInfo ci) {
		if (FreeCamera.controlsMouse() && (Object) this == Minecraft.getInstance().player) {
			if (FreeCamera.ownedBy(FreeCamera.Owner.ZEN)) {
				// Moving the mouse ends the Zen screensaver (this runs every frame, so only real movement counts)
				ZenMode.mouseMoved(xo, yo);
			} else {
				FreeCamera.turn(xo, yo);
			}

			ci.cancel();
		}
	}
}

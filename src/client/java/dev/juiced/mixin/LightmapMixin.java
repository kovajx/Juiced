package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.Lightmap;

import dev.juiced.modules.tweaks.Fullbright;

/** Fullbright: replaces the light calculation with "everything fully lit". */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void juiced$fullbright(CallbackInfo ci) {
		if (Fullbright.active() && Fullbright.whiteOut(this)) {
			ci.cancel();
		}
	}
}

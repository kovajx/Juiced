package dev.juiced.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.gui.Hud;

import dev.juiced.modules.tweaks.ClearView;
import dev.juiced.modules.tweaks.ZenMode;

/** Pumpkin and portal overlays of "Klare Sicht", and hiding the HUD in Zen mode. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void juiced$portal(CallbackInfo ci) {
		if (ClearView.noPortal()) {
			ci.cancel();
		}
	}

	@ModifyArg(method = "extractCameraOverlays",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0),
			index = 2, require = 0)
	private float juiced$pumpkin(float alpha) {
		return ClearView.noPumpkin() ? 0f : alpha;
	}

	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;isHidden:Z", opcode = Opcodes.GETFIELD), require = 0)
	private boolean juiced$zenHidesHud(boolean hidden) {
		return hidden || ZenMode.hidesHud();
	}
}

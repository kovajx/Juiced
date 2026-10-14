package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import dev.juiced.modules.tweaks.ClearView;

/** Skips the big totem animation in the middle of the screen ("Klare Sicht"). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
	private void juiced$totemAnimation(ItemStack itemStack, CallbackInfo ci) {
		if (ClearView.hideTotemAnimation(itemStack)) {
			ci.cancel();
		}
	}
}

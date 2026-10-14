package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;

import dev.juiced.modules.tweaks.ExplosionTweaks;

/** Notices when the server reports an explosion, so the smoke right after it can be hidden too. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleExplosion", at = @At("HEAD"), require = 0)
	private void juiced$explosion(CallbackInfo ci) {
		ExplosionTweaks.onExplosion();
	}
}

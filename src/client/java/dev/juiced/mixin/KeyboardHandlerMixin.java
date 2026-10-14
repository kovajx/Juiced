package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

import dev.juiced.modules.tweaks.ZenMode;

/** Any key press ends the Zen screensaver, not only the movement keys. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), require = 0)
	private void juiced$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
		// 0 = released; pressed and held keys both count as activity
		if (action != 0) {
			ZenMode.activity();
		}
	}
}

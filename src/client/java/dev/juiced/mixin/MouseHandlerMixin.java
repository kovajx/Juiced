package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import dev.juiced.core.Clicks;
import dev.juiced.modules.tweaks.Freelook;
import dev.juiced.modules.tweaks.ZenMode;
import dev.juiced.modules.tweaks.Zoom;

/** Counts clicks for the CPS counter and lets the mouse wheel change the zoom and the freelook distance. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"), require = 0)
	private void juiced$click(long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
		if (action == 1 && Minecraft.getInstance().gui.screen() == null) {
			Clicks.press(buttonInfo.button());
		}

		ZenMode.activity();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void juiced$scroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
		ZenMode.activity();

		if (Minecraft.getInstance().gui.screen() == null && (Zoom.handleScroll(yOffset) || Freelook.handleScroll(yOffset))) {
			ci.cancel();
		}
	}
}

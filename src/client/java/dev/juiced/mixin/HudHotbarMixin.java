package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.gui.Hud;

import dev.juiced.modules.tweaks.SmoothHotbar;

/** Weiche Hotbar: moves the selection frame smoothly and lets the chosen item hop. */
@Mixin(Hud.class)
public abstract class HudHotbarMixin {
	@ModifyArgs(method = "extractItemHotbar",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
			require = 0)
	private void juiced$hotbarSprite(Args args) {
		SmoothHotbar.adjustSprite(args);
	}

	@ModifyArgs(method = "extractItemHotbar",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"),
			require = 0)
	private void juiced$hotbarItem(Args args) {
		SmoothHotbar.adjustSlot(args);
	}
}

package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import dev.juiced.modules.tweaks.InventoryAnimations;

/** Inventory animations: wiggling hovered items and items flying on Shift-click. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Inject(method = "extractSlot", at = @At("HEAD"), require = 0)
	private void juiced$slotStart(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		InventoryAnimations.beforeSlot(graphics, slot, hoveredSlot);
	}

	@Inject(method = "extractSlot", at = @At("RETURN"), require = 0)
	private void juiced$slotEnd(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		InventoryAnimations.afterSlot(graphics);
	}

	// 26.3 has two overloads; the private MouseButtonEvent one only forwards to this one, so every click passes here
	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), require = 0)
	private void juiced$clicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (input == ContainerInput.QUICK_MOVE) {
			InventoryAnimations.quickMove(((AbstractContainerScreen<?>) (Object) this).getMenu(), slot);
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void juiced$afterRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		InventoryAnimations.draw(graphics, ((AbstractContainerScreen<?>) (Object) this).getMenu(), leftPos, topPos);
	}
}

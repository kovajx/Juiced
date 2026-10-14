package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;

import dev.juiced.combat.CombatEvents;

/**
 * Sees entity events from the server (for example 35 = a totem of undying was used).
 * Marked optional: if the method ever changes, only totem detection stops working.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"), require = 0)
	private void juiced$entityEvent(byte event, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (self.level().isClientSide()) {
			CombatEvents.onEntityEvent(self, event);
		}
	}
}

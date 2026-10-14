package dev.juiced.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.projectile.FishingHook;

/** Lets the fishing moment see when a fish bites (the bobber dips). */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("biting")
	boolean juiced$isBiting();
}

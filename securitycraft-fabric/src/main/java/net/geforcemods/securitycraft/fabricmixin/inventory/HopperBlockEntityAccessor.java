package net.geforcemods.securitycraft.fabricmixin.inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Gives SecurityCraft's hopper item handlers access to the hopper transfer cooldown, like NeoForge's access transformer
 * does for its VanillaHopperItemHandler.
 */
@Mixin(HopperBlockEntity.class)
public interface HopperBlockEntityAccessor {
	@Invoker("setCooldown")
	void securitycraft$setCooldown(int cooldownTime);

	@Invoker("isOnCustomCooldown")
	boolean securitycraft$isOnCustomCooldown();
}

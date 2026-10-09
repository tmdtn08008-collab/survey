package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * Replacement for NeoForge's AbstractHorse#getInventory(), which NeoForge adds to vanilla. Used by the inventory scanner
 * field to scan the chests of donkeys, mules and llamas.
 */
@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
	@Accessor("inventory")
	SimpleContainer securitycraft$getInventory();
}

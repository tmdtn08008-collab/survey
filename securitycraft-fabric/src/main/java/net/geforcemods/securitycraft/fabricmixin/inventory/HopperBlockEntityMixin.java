package net.geforcemods.securitycraft.fabricmixin.inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.api.IOwnable;
import net.geforcemods.securitycraft.fabric.items.ProtectedEntityContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Keeps vanilla's Container-based item transfer away from SecurityCraft's protected inventories. On NeoForge, hoppers,
 * hopper minecarts, droppers and crafters ask for the item handler capability first, which applies SecurityCraft's
 * extraction rules. On Fabric, vanilla uses any Container it finds directly, and Fabric's Transfer API is only consulted
 * when no Container was found.
 * <ul>
 * <li>Owned block entities are hidden from {@link HopperBlockEntity#getContainerAt}, so hoppers, hopper minecarts, droppers
 * and crafters fall back to Fabric's Transfer API hooks, which find SecurityCraft's ItemStorage.SIDED providers
 * (see SCItemStorages).</li>
 * <li>Owned container entities (the Security Sea Boat) are wrapped so that items can only be taken out of them by blocks
 * that are allowed to extract from protected objects, just like NeoForge's entity automation capability.</li>
 * </ul>
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
	@ModifyReturnValue(method = "getBlockContainer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/Container;", at = @At("RETURN"))
	private static Container securitycraft$hideOwnedBlockContainers(Container original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
		//check the block entity instead of the returned container, because a double keypad chest returns a CompoundContainer
		if (original != null && level.getBlockEntity(pos) instanceof IOwnable)
			return null;

		return original;
	}

	@ModifyReturnValue(method = "getEntityContainer(Lnet/minecraft/world/level/Level;DDD)Lnet/minecraft/world/Container;", at = @At("RETURN"))
	private static Container securitycraft$protectOwnedEntityContainers(Container original) {
		if (original instanceof Entity entity && original instanceof IOwnable ownable)
			return new ProtectedEntityContainer(original, entity, ownable);

		return original;
	}
}

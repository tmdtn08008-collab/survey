package net.geforcemods.securitycraft.fabric.items;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

/**
 * Fabric stand-in for the parts of NeoForge's VanillaInventoryCodeHooks that SecurityCraft calls directly. Vanilla's own
 * hoppers, droppers and crafters are handled by Fabric's Transfer API mixins together with SecurityCraft's
 * HopperBlockEntityMixin.
 */
public final class VanillaInventoryCodeHooks {
	private VanillaInventoryCodeHooks() {}

	/**
	 * Lets a dropper insert into the item storage in front of it, like Fabric's DropperBlockMixin does for vanilla droppers.
	 * Used by the reinforced dropper, which replaces DropperBlock#dispenseFrom and is therefore not covered by Fabric's
	 * mixin.
	 *
	 * @param level The level of the dropper
	 * @param pos The position of the dropper
	 * @param dropper The dropper's block entity
	 * @param slot The slot that should be dispensed from
	 * @param stack The stack in that slot
	 * @return true if there is no item storage in front of the dropper and the vanilla behavior should run, false if the
	 *         item was handled (whether or not it could be inserted)
	 */
	public static boolean dropperInsertHook(Level level, BlockPos pos, DispenserBlockEntity dropper, int slot, ItemStack stack) {
		Direction facing = level.getBlockState(pos).getValue(DispenserBlock.FACING);
		Storage<ItemVariant> target = ItemStorage.SIDED.find(level, pos.relative(facing), facing.getOpposite());

		if (target == null)
			return true;

		StorageUtil.move(InventoryStorage.of(dropper, null).getSlot(slot), target, variant -> true, 1, null);
		return false;
	}
}

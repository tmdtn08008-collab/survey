package net.geforcemods.securitycraft.fabric.items;

import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's IItemHandler: a slotted inventory that can be inserted into and extracted from. Used
 * internally by SecurityCraft; it is exposed to other mods through Fabric's Transfer API by {@link SCItemStorages}.
 */
public interface IItemHandler {
	int getSlots();

	/**
	 * The returned stack must not be modified.
	 */
	ItemStack getStackInSlot(int slot);

	/**
	 * @return The part of the stack that could not be inserted
	 */
	ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

	/**
	 * @return The extracted stack
	 */
	ItemStack extractItem(int slot, int amount, boolean simulate);

	int getSlotLimit(int slot);

	boolean isItemValid(int slot, ItemStack stack);
}

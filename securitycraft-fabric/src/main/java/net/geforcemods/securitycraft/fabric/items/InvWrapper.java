package net.geforcemods.securitycraft.fabric.items;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's InvWrapper: an {@link IItemHandler} view of a vanilla {@link Container}.
 */
public class InvWrapper implements IItemHandlerModifiable {
	private final Container inv;

	public InvWrapper(Container inv) {
		this.inv = inv;
	}

	public Container getInv() {
		return inv;
	}

	@Override
	public int getSlots() {
		return inv.getContainerSize();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return inv.getItem(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		return insertIntoContainerSlot(inv, slot, slot, stack, simulate, getSlotLimit(slot));
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return extractFromContainerSlot(inv, slot, amount, simulate);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		inv.setItem(slot, stack);
	}

	@Override
	public int getSlotLimit(int slot) {
		return inv.getMaxStackSize();
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return inv.canPlaceItem(slot, stack);
	}

	/**
	 * Inserts into a container slot, respecting {@link Container#canPlaceItem}, stack sizes and the given slot limit.
	 *
	 * @return The part of the stack that could not be inserted
	 */
	static ItemStack insertIntoContainerSlot(Container inv, int handlerSlot, int containerSlot, ItemStack stack, boolean simulate, int slotLimit) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;

		ItemStack inSlot = inv.getItem(containerSlot);
		int maxInSlot = Math.min(stack.getMaxStackSize(), slotLimit);

		if (!inSlot.isEmpty()) {
			if (inSlot.getCount() >= Math.min(inSlot.getMaxStackSize(), slotLimit) || !ItemStack.isSameItemSameComponents(stack, inSlot))
				return stack;

			maxInSlot -= inSlot.getCount();
		}

		if (!inv.canPlaceItem(containerSlot, stack) || maxInSlot <= 0)
			return stack;

		int inserted = Math.min(maxInSlot, stack.getCount());

		if (!simulate) {
			inv.setItem(containerSlot, stack.copyWithCount(inSlot.getCount() + inserted));
			inv.setChanged();
		}

		return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
	}

	/**
	 * @return The stack extracted from the container slot
	 */
	static ItemStack extractFromContainerSlot(Container inv, int containerSlot, int amount, boolean simulate) {
		if (amount <= 0)
			return ItemStack.EMPTY;

		ItemStack inSlot = inv.getItem(containerSlot);

		if (inSlot.isEmpty())
			return ItemStack.EMPTY;

		int extracted = Math.min(inSlot.getCount(), amount);

		if (simulate)
			return inSlot.copyWithCount(extracted);

		ItemStack result = inv.removeItem(containerSlot, extracted);

		inv.setChanged();
		return result;
	}
}

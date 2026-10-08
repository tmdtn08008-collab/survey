package net.geforcemods.securitycraft.fabric.items;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's SidedInvWrapper: an {@link IItemHandler} view of the slots of a {@link WorldlyContainer}
 * that are accessible from one side, respecting {@link WorldlyContainer#canPlaceItemThroughFace} and
 * {@link WorldlyContainer#canTakeItemThroughFace}.
 */
public class SidedInvWrapper implements IItemHandlerModifiable {
	protected final WorldlyContainer inv;
	@Nullable
	protected final Direction side;

	public SidedInvWrapper(WorldlyContainer inv, @Nullable Direction side) {
		this.inv = inv;
		this.side = side;
	}

	public WorldlyContainer getInv() {
		return inv;
	}

	@Nullable
	public Direction getSide() {
		return side;
	}

	/**
	 * @return The container slot that the given handler slot refers to, or -1 if there is none
	 */
	protected int getSlot(int slot) {
		if (side == null)
			return slot < inv.getContainerSize() ? slot : -1;

		int[] slots = inv.getSlotsForFace(side);

		return slot >= 0 && slot < slots.length ? slots[slot] : -1;
	}

	@Override
	public int getSlots() {
		return side == null ? inv.getContainerSize() : inv.getSlotsForFace(side).length;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		int containerSlot = getSlot(slot);

		return containerSlot == -1 ? ItemStack.EMPTY : inv.getItem(containerSlot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		int containerSlot = getSlot(slot);

		if (containerSlot == -1 || stack.isEmpty() || !inv.canPlaceItemThroughFace(containerSlot, stack, side))
			return stack;

		return InvWrapper.insertIntoContainerSlot(inv, slot, containerSlot, stack, simulate, getSlotLimit(slot));
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		int containerSlot = getSlot(slot);

		if (containerSlot == -1)
			return ItemStack.EMPTY;

		ItemStack inSlot = inv.getItem(containerSlot);

		if (inSlot.isEmpty() || !inv.canTakeItemThroughFace(containerSlot, inSlot, side))
			return ItemStack.EMPTY;

		return InvWrapper.extractFromContainerSlot(inv, containerSlot, amount, simulate);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		int containerSlot = getSlot(slot);

		if (containerSlot != -1)
			inv.setItem(containerSlot, stack);
	}

	@Override
	public int getSlotLimit(int slot) {
		return inv.getMaxStackSize();
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		int containerSlot = getSlot(slot);

		return containerSlot != -1 && inv.canPlaceItem(containerSlot, stack);
	}
}

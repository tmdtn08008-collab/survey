package net.geforcemods.securitycraft.fabric.items;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's ItemStackHandler, an {@link IItemHandler} backed by a list of stacks.
 */
public class ItemStackHandler implements IItemHandlerModifiable {
	protected NonNullList<ItemStack> stacks;

	public ItemStackHandler() {
		this(1);
	}

	public ItemStackHandler(int size) {
		this(NonNullList.withSize(size, ItemStack.EMPTY));
	}

	public ItemStackHandler(NonNullList<ItemStack> stacks) {
		this.stacks = stacks;
	}

	public void setSize(int size) {
		stacks = NonNullList.withSize(size, ItemStack.EMPTY);
	}

	@Override
	public int getSlots() {
		return stacks.size();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		validateSlotIndex(slot);
		return stacks.get(slot);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		validateSlotIndex(slot);
		stacks.set(slot, stack);
		onContentsChanged(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;

		if (!isItemValid(slot, stack))
			return stack;

		validateSlotIndex(slot);

		ItemStack existing = stacks.get(slot);
		int limit = getStackLimit(slot, stack);

		if (!existing.isEmpty()) {
			if (!ItemStack.isSameItemSameComponents(stack, existing))
				return stack;

			limit -= existing.getCount();
		}

		if (limit <= 0)
			return stack;

		boolean reachedLimit = stack.getCount() > limit;

		if (!simulate) {
			if (existing.isEmpty())
				stacks.set(slot, reachedLimit ? stack.copyWithCount(limit) : stack);
			else
				existing.grow(reachedLimit ? limit : stack.getCount());

			onContentsChanged(slot);
		}

		return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (amount == 0)
			return ItemStack.EMPTY;

		validateSlotIndex(slot);

		ItemStack existing = stacks.get(slot);

		if (existing.isEmpty())
			return ItemStack.EMPTY;

		int toExtract = Math.min(amount, existing.getMaxStackSize());

		if (existing.getCount() <= toExtract) {
			if (simulate)
				return existing.copy();

			stacks.set(slot, ItemStack.EMPTY);
			onContentsChanged(slot);
			return existing;
		}

		if (!simulate) {
			stacks.set(slot, existing.copyWithCount(existing.getCount() - toExtract));
			onContentsChanged(slot);
		}

		return existing.copyWithCount(toExtract);
	}

	@Override
	public int getSlotLimit(int slot) {
		return Item.ABSOLUTE_MAX_STACK_SIZE;
	}

	protected int getStackLimit(int slot, ItemStack stack) {
		return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return true;
	}

	public CompoundTag serializeNBT(HolderLookup.Provider lookupProvider) {
		CompoundTag tag = new CompoundTag();

		tag.putInt("Size", stacks.size());
		ContainerHelper.saveAllItems(tag, stacks, lookupProvider);
		return tag;
	}

	public void deserializeNBT(HolderLookup.Provider lookupProvider, CompoundTag tag) {
		setSize(tag.contains("Size") ? tag.getInt("Size") : stacks.size());
		ContainerHelper.loadAllItems(tag, stacks, lookupProvider);
		onLoad();
	}

	protected void validateSlotIndex(int slot) {
		if (slot < 0 || slot >= stacks.size())
			throw new IndexOutOfBoundsException("Slot " + slot + " not in valid range - [0," + stacks.size() + ")");
	}

	protected void onLoad() {}

	protected void onContentsChanged(int slot) {}
}

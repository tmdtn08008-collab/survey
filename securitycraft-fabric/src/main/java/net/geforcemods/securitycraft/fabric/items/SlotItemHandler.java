package net.geforcemods.securitycraft.fabric.items;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's SlotItemHandler: a menu slot backed by an {@link IItemHandler}.
 */
public class SlotItemHandler extends Slot {
	private static final Container EMPTY_CONTAINER = new SimpleContainer(0);
	private final IItemHandler itemHandler;
	private final int index;

	public SlotItemHandler(IItemHandler itemHandler, int index, int x, int y) {
		super(EMPTY_CONTAINER, index, x, y);
		this.itemHandler = itemHandler;
		this.index = index;
	}

	public IItemHandler getItemHandler() {
		return itemHandler;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !stack.isEmpty() && itemHandler.isItemValid(index, stack);
	}

	@Override
	public ItemStack getItem() {
		return itemHandler.getStackInSlot(index);
	}

	@Override
	public void set(ItemStack stack) {
		((IItemHandlerModifiable) itemHandler).setStackInSlot(index, stack);
		setChanged();
	}

	/**
	 * Kept for parity with NeoForge's SlotItemHandler, so subclasses that override it still compile. Like on NeoForge
	 * 1.21.1, nothing in vanilla calls this (AbstractContainerMenu#initializeContents calls {@link #set(ItemStack)}).
	 */
	public void initialize(ItemStack stack) {
		set(stack);
	}

	@Override
	public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
		set(newStack);
	}

	@Override
	public void setChanged() {}

	@Override
	public int getMaxStackSize() {
		return itemHandler.getSlotLimit(index);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		ItemStack maxAdd = stack.copyWithCount(stack.getMaxStackSize());
		IItemHandler handler = getItemHandler();
		ItemStack currentStack = handler.getStackInSlot(index);

		if (handler instanceof IItemHandlerModifiable handlerModifiable) {
			handlerModifiable.setStackInSlot(index, ItemStack.EMPTY);

			ItemStack remainder = handlerModifiable.insertItem(index, maxAdd, true);

			handlerModifiable.setStackInSlot(index, currentStack);
			return maxAdd.getCount() - remainder.getCount();
		}

		ItemStack remainder = handler.insertItem(index, maxAdd, true);

		return currentStack.getCount() + maxAdd.getCount() - remainder.getCount();
	}

	@Override
	public boolean mayPickup(Player player) {
		return !itemHandler.extractItem(index, 1, true).isEmpty();
	}

	@Override
	public ItemStack remove(int amount) {
		return itemHandler.extractItem(index, amount, false);
	}
}

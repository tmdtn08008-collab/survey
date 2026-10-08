package net.geforcemods.securitycraft.fabric.items;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/**
 * Exposes an {@link ItemStackHandler} (or one of SecurityCraft's subclasses, like the insert-only and extract-only handlers)
 * as a Fabric Transfer API storage. What may be inserted or extracted is decided by the handler itself, by asking
 * {@link ItemStackHandler#insertItem} and {@link ItemStackHandler#extractItem} with {@code simulate == true}, so every
 * restriction a subclass adds is honoured. The actual change is applied directly to the handler's backing list and
 * recorded for the transaction, so aborted transactions restore the exact previous contents (this does not rely on
 * {@link ItemStackHandler#setStackInSlot}, which e.g. ExtractOnlyItemStackHandler turns into a no-op).
 */
public class ItemStackHandlerStorage extends CombinedSlottedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> {
	public ItemStackHandlerStorage(ItemStackHandler handler, @Nullable Runnable onChange) {
		super(createSlots(handler, onChange));
	}

	private static List<SingleSlotStorage<ItemVariant>> createSlots(ItemStackHandler handler, @Nullable Runnable onChange) {
		List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>(handler.getSlots());

		for (int i = 0; i < handler.getSlots(); i++) {
			slots.add(new SlotStorage(handler, i, onChange));
		}

		return slots;
	}

	private static class SlotStorage extends SnapshotParticipant<ItemStack> implements SingleSlotStorage<ItemVariant> {
		private final ItemStackHandler handler;
		private final int slot;
		@Nullable
		private final Runnable onChange;

		SlotStorage(ItemStackHandler handler, int slot, @Nullable Runnable onChange) {
			this.handler = handler;
			this.slot = slot;
			this.onChange = onChange;
		}

		private ItemStack getStack() {
			return handler.stacks.get(slot);
		}

		private void setStack(ItemStack stack) {
			handler.stacks.set(slot, stack);
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);

			ItemStack current = getStack();

			if (maxAmount == 0 || (!current.isEmpty() && !resource.matches(current)))
				return 0;

			int toInsert = (int) Math.min(maxAmount, Integer.MAX_VALUE);
			ItemStack remainder = handler.insertItem(slot, resource.toStack(toInsert), true);
			int inserted = toInsert - remainder.getCount();

			if (inserted <= 0)
				return 0;

			updateSnapshots(transaction);
			setStack(current.isEmpty() ? resource.toStack(inserted) : current.copyWithCount(current.getCount() + inserted));
			return inserted;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);

			ItemStack current = getStack();

			if (maxAmount == 0 || current.isEmpty() || !resource.matches(current))
				return 0;

			ItemStack extractedStack = handler.extractItem(slot, (int) Math.min(maxAmount, Integer.MAX_VALUE), true);

			if (extractedStack.isEmpty() || !resource.matches(extractedStack))
				return 0;

			int extracted = Math.min(extractedStack.getCount(), current.getCount());

			updateSnapshots(transaction);
			setStack(extracted >= current.getCount() ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - extracted));
			return extracted;
		}

		@Override
		public boolean isResourceBlank() {
			return getStack().isEmpty();
		}

		@Override
		public ItemVariant getResource() {
			return ItemVariant.of(getStack());
		}

		@Override
		public long getAmount() {
			return getStack().getCount();
		}

		@Override
		public long getCapacity() {
			ItemStack stack = getStack();

			return stack.isEmpty() ? handler.getSlotLimit(slot) : handler.getStackLimit(slot, stack);
		}

		@Override
		protected ItemStack createSnapshot() {
			return getStack().copy();
		}

		@Override
		protected void readSnapshot(ItemStack snapshot) {
			setStack(snapshot);
		}

		@Override
		protected void onFinalCommit() {
			handler.onContentsChanged(slot);

			if (onChange != null)
				onChange.run();
		}

		@Override
		public String toString() {
			return "ItemStackHandlerStorage.SlotStorage[" + handler + "#" + slot + "]";
		}
	}
}

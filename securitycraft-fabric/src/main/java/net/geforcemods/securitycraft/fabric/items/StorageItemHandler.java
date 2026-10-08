package net.geforcemods.securitycraft.fabric.items;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

/**
 * An {@link IItemHandler} view of a Fabric Transfer API item storage, for SecurityCraft code that used to query NeoForge's
 * item handler capability of another block, e.g.
 * {@code StorageItemHandler.of(ItemStorage.SIDED.find(level, pos, state, blockEntity, side))}. Every change is done in its
 * own transaction, which is committed unless the call is a simulation. The slots are the storage's slots if it is a
 * {@link SlottedStorage}, otherwise the storage views that existed when the handler was created.
 */
public class StorageItemHandler implements IItemHandler {
	private final Storage<ItemVariant> storage;
	private final List<StorageView<ItemVariant>> views = new ArrayList<>();

	public StorageItemHandler(Storage<ItemVariant> storage) {
		this.storage = storage;

		if (storage instanceof SlottedStorage<ItemVariant> slottedStorage)
			views.addAll(slottedStorage.getSlots());
		else
			storage.iterator().forEachRemaining(views::add);
	}

	/**
	 * @return An item handler view of the storage, or null if the storage is null
	 */
	@Nullable
	public static IItemHandler of(@Nullable Storage<ItemVariant> storage) {
		return storage == null ? null : new StorageItemHandler(storage);
	}

	@Override
	public int getSlots() {
		return views.size();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		StorageView<ItemVariant> view = views.get(slot);

		return view.isResourceBlank() || view.getAmount() <= 0 ? ItemStack.EMPTY : view.getResource().toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE));
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;

		ItemVariant resource = ItemVariant.of(stack);
		long inserted;

		try (Transaction transaction = Transaction.openOuter()) {
			if (storage instanceof SlottedStorage<ItemVariant> slottedStorage)
				inserted = slottedStorage.getSlot(slot).insert(resource, stack.getCount(), transaction);
			else
				inserted = storage.insert(resource, stack.getCount(), transaction);

			if (!simulate)
				transaction.commit();
		}

		return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) inserted);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		StorageView<ItemVariant> view = views.get(slot);

		if (amount <= 0 || view.isResourceBlank())
			return ItemStack.EMPTY;

		ItemVariant resource = view.getResource();
		long extracted;

		try (Transaction transaction = Transaction.openOuter()) {
			extracted = view.extract(resource, amount, transaction);

			if (!simulate)
				transaction.commit();
		}

		return extracted <= 0 ? ItemStack.EMPTY : resource.toStack((int) extracted);
	}

	@Override
	public int getSlotLimit(int slot) {
		return (int) Math.min(views.get(slot).getCapacity(), Integer.MAX_VALUE);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return !stack.isEmpty() && insertItem(slot, stack.copyWithCount(1), true).isEmpty();
	}
}

package net.geforcemods.securitycraft.fabric.items;

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.geforcemods.securitycraft.fabricmixin.inventory.CompoundContainerAccessor;
import net.geforcemods.securitycraft.fabricmixin.inventory.HopperBlockEntityAccessor;
import net.geforcemods.securitycraft.inventory.InsertOnlyInvWrapper;
import net.geforcemods.securitycraft.inventory.InsertOnlySidedInvWrapper;
import net.geforcemods.securitycraft.inventory.VanillaHopperInsertOnlyItemHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Translates the {@link IItemHandler}s that SecurityCraft's block entities hand out (the NeoForge item handler capability)
 * into Fabric Transfer API storages, keeping their restrictions:
 * <ul>
 * <li>{@code null} and {@link EmptyItemHandler} become {@link Storage#empty()}. A provider must never return {@code null}
 * for a SecurityCraft block, because Fabric's ItemStorage.SIDED lookup would then fall back to wrapping the block entity's
 * Container, giving full access to it.</li>
 * <li>Container-backed wrappers ({@link InvWrapper}, {@link SidedInvWrapper}, {@link VanillaHopperItemHandler}) become
 * Fabric's own {@link InventoryStorage} of the same container and side, so all of Fabric's transaction handling for vanilla
 * containers (furnaces, chiseled bookshelves, ...) applies.</li>
 * <li>The insert-only wrappers ({@link InsertOnlyInvWrapper}, {@link InsertOnlySidedInvWrapper},
 * {@link VanillaHopperInsertOnlyItemHandler}) become insert-only views of the same storage, so nothing can be extracted
 * through them.</li>
 * <li>{@link ItemStackHandler}s become an {@link ItemStackHandlerStorage}, which asks the handler itself what can be
 * inserted and extracted.</li>
 * </ul>
 * Handlers of any other type are not exposed at all (fail closed).
 */
public final class ItemHandlerStorage {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static boolean warnedAboutUnknownHandler = false;

	private ItemHandlerStorage() {}

	/**
	 * @param handler The item handler to expose
	 * @param onChange Called after a transaction that changed an {@link ItemStackHandler}'s contents was committed, usually
	 *            the owning block entity's setChanged. Container-backed handlers mark their container as changed
	 *            themselves.
	 * @return The storage view of the item handler, never null
	 */
	public static Storage<ItemVariant> of(@Nullable IItemHandler handler, @Nullable Runnable onChange) {
		return switch (handler) {
			case null -> Storage.empty();
			case EmptyItemHandler empty -> Storage.empty();
			//insert-only wrappers first, as they extend the full wrappers
			case VanillaHopperInsertOnlyItemHandler hopperWrapper -> ofHopper(hopperWrapper.getHopper(), false);
			case InsertOnlyInvWrapper wrapper -> FilteringStorage.insertOnlyOf(ofContainer(wrapper.getInv(), null));
			case InsertOnlySidedInvWrapper wrapper -> FilteringStorage.insertOnlyOf(ofContainer(wrapper.getInv(), wrapper.getSide()));
			case VanillaHopperItemHandler hopperWrapper -> ofHopper(hopperWrapper.getHopper(), true);
			case InvWrapper wrapper -> ofContainer(wrapper.getInv(), null);
			case SidedInvWrapper wrapper -> ofContainer(wrapper.getInv(), wrapper.getSide());
			case ItemStackHandler stackHandler -> new ItemStackHandlerStorage(stackHandler, onChange);
			default -> {
				if (!warnedAboutUnknownHandler) {
					warnedAboutUnknownHandler = true;
					LOGGER.warn("Cannot expose item handler {} to Fabric's Transfer API, it will not be accessible to automation", handler.getClass().getName());
				}

				yield Storage.empty();
			}
		};
	}

	/**
	 * @return A storage of the given container and side, or {@link Storage#empty()} if there is no container. The two halves
	 *         of a double chest are wrapped separately, like Fabric does for vanilla double chests.
	 */
	public static Storage<ItemVariant> ofContainer(@Nullable Container container, @Nullable Direction side) {
		if (container == null)
			return Storage.empty();
		else if (container instanceof CompoundContainer compoundContainer) {
			CompoundContainerAccessor accessor = (CompoundContainerAccessor) compoundContainer;
			SlottedStorage<ItemVariant> first = InventoryStorage.of(accessor.securitycraft$getContainer1(), side);
			SlottedStorage<ItemVariant> second = InventoryStorage.of(accessor.securitycraft$getContainer2(), side);

			return new CombinedSlottedStorage<>(List.of(first, second));
		}
		else
			return InventoryStorage.of(container, side);
	}

	/**
	 * Like NeoForge's VanillaHopperItemHandler: inserting into an empty hopper starts its 8 tick transfer cooldown, so items
	 * do not move through a chain of hoppers faster than with vanilla hoppers.
	 */
	private static Storage<ItemVariant> ofHopper(HopperBlockEntity hopper, boolean allowExtraction) {
		return new HopperStorage(hopper, allowExtraction);
	}

	private static class HopperStorage extends FilteringStorage<ItemVariant> {
		private final HopperBlockEntity hopper;
		private final boolean allowExtraction;
		private final SnapshotParticipant<Boolean> cooldownParticipant = new SnapshotParticipant<>() {
			@Override
			protected Boolean createSnapshot() {
				return Boolean.TRUE;
			}

			@Override
			protected void readSnapshot(Boolean snapshot) {}

			@Override
			protected void onFinalCommit() {
				HopperBlockEntityAccessor accessor = (HopperBlockEntityAccessor) hopper;

				if (!accessor.securitycraft$isOnCustomCooldown())
					accessor.securitycraft$setCooldown(8);
			}
		};

		HopperStorage(HopperBlockEntity hopper, boolean allowExtraction) {
			super(InventoryStorage.of(hopper, null));
			this.hopper = hopper;
			this.allowExtraction = allowExtraction;
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			boolean wasEmpty = hopper.isEmpty();
			long inserted = super.insert(resource, maxAmount, transaction);

			if (inserted > 0 && wasEmpty)
				cooldownParticipant.updateSnapshots(transaction);

			return inserted;
		}

		@Override
		protected boolean canExtract(ItemVariant resource) {
			return allowExtraction;
		}

		@Override
		public boolean supportsExtraction() {
			return allowExtraction && super.supportsExtraction();
		}
	}
}

package net.geforcemods.securitycraft.fabric.items;

import java.util.Set;
import java.util.function.Predicate;

import net.geforcemods.securitycraft.api.IOwnable;
import net.geforcemods.securitycraft.util.BlockUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The view of an owned container entity (the Security Sea Boat) that vanilla hoppers, hopper minecarts, droppers and
 * crafters get on Fabric. It behaves exactly like the entity, except that items can only be taken out of it when the block
 * below the entity is allowed to extract from protected objects. This mirrors NeoForge's entity automation capability
 * (SecuritySeaBoat#getCapability), which is queried with {@link Direction#DOWN} for hoppers below the entity, and gives an
 * insert-only handler otherwise. Vanilla only takes items out of an entity container from a hopper below it.
 */
public class ProtectedEntityContainer implements Container {
	private final Container container;
	private final Entity entity;
	private final IOwnable ownable;

	public ProtectedEntityContainer(Container container, Entity entity, IOwnable ownable) {
		this.container = container;
		this.entity = entity;
		this.ownable = ownable;
	}

	@Override
	public boolean canTakeItem(Container target, int slot, ItemStack stack) {
		return BlockUtils.isAllowedToExtractFromProtectedObject(Direction.DOWN, ownable, entity.level(), entity.blockPosition()) && container.canTakeItem(target, slot, stack);
	}

	@Override
	public int getContainerSize() {
		return container.getContainerSize();
	}

	@Override
	public boolean isEmpty() {
		return container.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return container.getItem(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return container.removeItem(slot, amount);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return container.removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		container.setItem(slot, stack);
	}

	@Override
	public int getMaxStackSize() {
		return container.getMaxStackSize();
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return container.getMaxStackSize(stack);
	}

	@Override
	public void setChanged() {
		container.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public void startOpen(Player player) {
		container.startOpen(player);
	}

	@Override
	public void stopOpen(Player player) {
		container.stopOpen(player);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return container.canPlaceItem(slot, stack);
	}

	@Override
	public int countItem(Item item) {
		return container.countItem(item);
	}

	@Override
	public boolean hasAnyOf(Set<Item> items) {
		return container.hasAnyOf(items);
	}

	@Override
	public boolean hasAnyMatching(Predicate<ItemStack> predicate) {
		return container.hasAnyMatching(predicate);
	}

	@Override
	public void clearContent() {
		container.clearContent();
	}
}

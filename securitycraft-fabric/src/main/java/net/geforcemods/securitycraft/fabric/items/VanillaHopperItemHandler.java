package net.geforcemods.securitycraft.fabric.items;

import net.geforcemods.securitycraft.fabricmixin.inventory.HopperBlockEntityAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Fabric stand-in for NeoForge's VanillaHopperItemHandler: an {@link InvWrapper} of a hopper that starts the hopper's 8 tick
 * transfer cooldown when an item is inserted into the empty hopper, like vanilla hopper-to-hopper transfers do. When
 * exposed to other mods through {@link ItemHandlerStorage}, the same cooldown is applied when the transaction is committed.
 */
public class VanillaHopperItemHandler extends InvWrapper {
	private final HopperBlockEntity hopper;

	public VanillaHopperItemHandler(HopperBlockEntity hopper) {
		super(hopper);
		this.hopper = hopper;
	}

	public HopperBlockEntity getHopper() {
		return hopper;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (simulate)
			return super.insertItem(slot, stack, true);

		boolean wasEmpty = hopper.isEmpty();
		int countBefore = stack.getCount();
		ItemStack remainder = super.insertItem(slot, stack, false);

		if (wasEmpty && remainder.getCount() < countBefore) {
			HopperBlockEntityAccessor accessor = (HopperBlockEntityAccessor) hopper;

			if (!accessor.securitycraft$isOnCustomCooldown())
				accessor.securitycraft$setCooldown(8);
		}

		return remainder;
	}
}

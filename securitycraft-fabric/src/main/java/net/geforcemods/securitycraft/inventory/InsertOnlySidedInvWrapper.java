package net.geforcemods.securitycraft.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.geforcemods.securitycraft.fabric.items.SidedInvWrapper;

public class InsertOnlySidedInvWrapper extends SidedInvWrapper {
	public InsertOnlySidedInvWrapper(WorldlyContainer inv, Direction side) {
		super(inv, side);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return ItemStack.EMPTY;
	}
}

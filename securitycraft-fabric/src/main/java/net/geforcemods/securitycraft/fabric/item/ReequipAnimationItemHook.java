package net.geforcemods.securitycraft.fabric.item;

import net.minecraft.world.item.ItemStack;

/**
 * Replacement for NeoForge's IItemExtension#shouldCauseReequipAnimation. Called on the client from SecurityCraft's
 * ItemInHandRenderer mixin at the place NeoForge patched into ItemInHandRenderer#tick, for the item that was previously held,
 * when neither the previously held nor the newly held stack is empty.
 */
public interface ReequipAnimationItemHook {
	/**
	 * @param oldStack The stack that was previously held
	 * @param newStack The stack that is now held
	 * @param slotChanged true if the selected hotbar slot changed since the last check (always false for the off hand)
	 * @return true to play the reequip animation
	 */
	boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged);
}

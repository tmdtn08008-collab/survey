package net.geforcemods.securitycraft.fabric.item;

import net.minecraft.world.item.ItemStack;

/**
 * Replacement for NeoForge's IItemExtension#isBookEnchantable. Called from SecurityCraft's AnvilMenu mixin at the place
 * NeoForge patched into AnvilMenu#createResult: if the right input of the anvil has stored enchantments (an enchanted book)
 * and the item in the left input returns false, the anvil has no result.
 */
public interface BookEnchantableItemHook {
	/**
	 * @param stack The stack in the left slot of the anvil
	 * @param book The stack with stored enchantments in the right slot of the anvil
	 * @return true if the enchantments of the book may be applied to the stack
	 */
	boolean isBookEnchantable(ItemStack stack, ItemStack book);
}

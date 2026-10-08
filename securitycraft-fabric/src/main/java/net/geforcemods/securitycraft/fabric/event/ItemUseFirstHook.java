package net.geforcemods.securitycraft.fabric.event;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Replacement for NeoForge's IItemExtension#onItemUseFirst. Items implementing this are called on both sides when a
 * player right clicks a block with them, after the RightClickBlock event and before the block is used (unless the event
 * set useItem to FALSE). Returning anything other than PASS ends the interaction with that result. Called from
 * SecurityCraft's ServerPlayerGameMode#useItemOn and MultiPlayerGameMode#performUseItemOn mixins via
 * {@link EventHooks#onItemUseFirst(ItemStack, UseOnContext)}, which also applies vanilla's adventure mode check and awards
 * the item used statistic, like NeoForge's ItemStack#onItemUseFirst.
 */
public interface ItemUseFirstHook {
	InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context);
}

package net.geforcemods.securitycraft.fabric.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Fabric stand-in for NeoForge's IContainerFactory.
 */
@FunctionalInterface
public interface IContainerFactory<T extends AbstractContainerMenu> {
	T create(int windowId, Inventory inv, RegistryFriendlyByteBuf data);
}

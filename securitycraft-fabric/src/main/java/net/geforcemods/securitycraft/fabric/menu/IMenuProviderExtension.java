package net.geforcemods.securitycraft.fabric.menu;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Fabric stand-in for NeoForge's IMenuProviderExtension. Implementing this makes a menu provider usable with plain
 * {@code player.openMenu(provider)} for menu types made by {@link IMenuTypeExtension#create}.
 */
public interface IMenuProviderExtension extends ExtendedScreenHandlerFactory<MenuOpenData> {
	/**
	 * Writes extra data that the client-side menu factory reads.
	 */
	default void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {}

	@Override
	default MenuOpenData getScreenOpeningData(ServerPlayer player) {
		//Fabric sets the player's container menu to the newly created menu before asking for the opening data
		AbstractContainerMenu menu = player.containerMenu;

		return MenuOpenData.write(player.registryAccess(), buf -> writeClientSideData(menu, buf));
	}
}

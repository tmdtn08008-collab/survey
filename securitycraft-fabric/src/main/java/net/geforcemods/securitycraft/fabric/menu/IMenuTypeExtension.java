package net.geforcemods.securitycraft.fabric.menu;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Fabric stand-in for NeoForge's IMenuTypeExtension#create. Menus created this way must be opened with an
 * {@link IMenuProviderExtension} (or through {@link MenuHelper}), because Fabric only sends extended menu data from an
 * ExtendedScreenHandlerFactory.
 */
public final class IMenuTypeExtension {
	private IMenuTypeExtension() {}

	public static <T extends AbstractContainerMenu> MenuType<T> create(IContainerFactory<T> factory) {
		return new ExtendedScreenHandlerType<>((windowId, inv, data) -> factory.create(windowId, inv, data.toBuffer(inv.player.registryAccess())), MenuOpenData.STREAM_CODEC);
	}
}

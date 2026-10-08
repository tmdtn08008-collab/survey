package net.geforcemods.securitycraft.fabric.menu;

import java.util.OptionalInt;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Replacements for NeoForge's ServerPlayer#openMenu(MenuProvider, Consumer) and #openMenu(MenuProvider, BlockPos).
 */
public final class MenuHelper {
	private MenuHelper() {}

	public static OptionalInt openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		return openMenu(player, provider, buf -> buf.writeBlockPos(pos));
	}

	public static OptionalInt openMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
		return player.openMenu(new IMenuProviderExtension() {
			@Override
			public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
				return provider.createMenu(windowId, inv, p);
			}

			@Override
			public Component getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
				if (provider instanceof IMenuProviderExtension extension)
					extension.writeClientSideData(menu, buffer);

				extraDataWriter.accept(buffer);
			}
		});
	}
}

package net.geforcemods.securitycraft.compat.hudmods;

import net.geforcemods.securitycraft.ClientHandler;
import net.geforcemods.securitycraft.SecurityCraft;
import net.geforcemods.securitycraft.blocks.FakeLavaBlock;
import net.geforcemods.securitycraft.blocks.FakeWaterBlock;
import net.geforcemods.securitycraft.compat.IOverlayDisplay;
import net.geforcemods.securitycraft.entity.SecuritySeaBoat;
import net.geforcemods.securitycraft.entity.sentry.Sentry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin(SecurityCraft.MODID)
public final class JadeDataProvider extends HudModHandler implements IWailaPlugin {
	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.addConfig(SHOW_OWNER, true);
		registration.addConfig(SHOW_MODULES, true);
		registration.addConfig(SHOW_CUSTOM_NAME, true);

		registration.registerBlockComponent(new SecurityCraftBlockName(), Block.class);
		registration.registerBlockComponent(new SecurityCraftBlockInfo(), Block.class);
		registration.registerEntityComponent(new SecurityCraftEntityInfo(), Sentry.class);
		registration.registerEntityComponent(new SecurityCraftEntityInfo(), SecuritySeaBoat.class);

		registration.addBeforeRenderCallback((tooltip, rect, guiGraphics, accessor) -> ClientHandler.isPlayerMountedOnCamera());
		registration.addRayTraceCallback((hit, accessor, original) -> {
			if (accessor instanceof BlockAccessor blockAccessor) {
				Block block = blockAccessor.getBlock();

				if (block instanceof IOverlayDisplay overlayDisplay) {
					Level level = blockAccessor.getLevel();
					BlockState state = blockAccessor.getBlockState();
					BlockPos pos = blockAccessor.getPosition();

					if (!overlayDisplay.shouldShowSCInfo(level, state, pos))
						return registration.blockAccessor().from(blockAccessor).fakeBlock(overlayDisplay.getDisplayStack(level, state, pos)).build();
				}
				else if (block instanceof FakeWaterBlock)
					return registration.blockAccessor().from(blockAccessor).blockState(Blocks.WATER.defaultBlockState()).build();
				else if (block instanceof FakeLavaBlock)
					return registration.blockAccessor().from(blockAccessor).blockState(Blocks.LAVA.defaultBlockState()).build();
			}

			return accessor;
		});
	}

	/**
	 * Shows the name of what an IOverlayDisplay block displays as (for example its disguise) as the tooltip's title.
	 */
	// PORT-NOTE: Upstream replaced the first element of the tooltip's first line from within SecurityCraftBlockInfo, through
	// Jade internals. In Jade's lite display mode that provider gets a throwaway tooltip, which can be empty, so this threw
	// IndexOutOfBoundsException or replaced the wrong element. This provider runs right after Jade's object name provider
	// (priority -10100), which always gets the real tooltip, and replaces the name element through the public API.
	private class SecurityCraftBlockName implements IBlockComponentProvider {
		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor data, IPluginConfig config) {
			if (data.getBlock() instanceof IOverlayDisplay overlayDisplay)
				tooltip.replace(JadeIds.CORE_OBJECT_NAME, Component.translatable(overlayDisplay.getDisplayStack(data.getLevel(), data.getBlockState(), data.getPosition()).getDescriptionId()).setStyle(ITEM_NAME_STYLE));
		}

		@Override
		public ResourceLocation getUid() {
			return SecurityCraft.resLoc("block_name");
		}

		@Override
		public int getDefaultPriority() {
			return -10090;
		}

		@Override
		public boolean isRequired() {
			return true;
		}
	}

	private class SecurityCraftBlockInfo implements IBlockComponentProvider {
		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor data, IPluginConfig config) {
			Level level = data.getLevel();
			BlockPos pos = data.getPosition();
			BlockState state = data.getBlockState();
			Block block = data.getBlock();

			addDisguisedOwnerModuleNameInfo(level, pos, state, block, data.getBlockEntity(), data.getPlayer(), tooltip::add, config::get);
		}

		@Override
		public ResourceLocation getUid() {
			return SecurityCraft.resLoc("block_info");
		}
	}

	private class SecurityCraftEntityInfo implements IEntityComponentProvider {
		@Override
		public void appendTooltip(ITooltip tooltip, EntityAccessor data, IPluginConfig config) {
			addEntityInfo(data.getEntity(), data.getPlayer(), tooltip::add, config::get);
		}

		@Override
		public ResourceLocation getUid() {
			return SecurityCraft.resLoc("entity_info");
		}
	}
}

package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#isPortalFrame, implemented only by SecurityCraft's blocks (reinforced
 * obsidian). SecurityCraft's PortalShapeMixin and BaseFireBlockMixin ask it in addition to vanilla's obsidian checks when a
 * nether portal frame is validated and when fire decides whether it can light a portal.
 */
public interface PortalFrameBlockHook {
	/**
	 * @param state The state of this block
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @return true if this block can be part of a nether portal frame
	 */
	default boolean isPortalFrame(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(Blocks.OBSIDIAN);
	}
}

package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#isConduitFrame, implemented only by SecurityCraft's blocks (the
 * reinforced prismarine family). SecurityCraft's ConduitBlockEntityMixin asks it in ConduitBlockEntity#updateShape for every
 * block of the conduit's frame.
 */
public interface ConduitFrameBlockHook {
	/**
	 * @param state The state of this block
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @param conduit The position of the conduit
	 * @return true if this block can be part of a conduit's frame
	 */
	default boolean isConduitFrame(BlockState state, LevelReader level, BlockPos pos, BlockPos conduit) {
		return state.is(Blocks.PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS) || state.is(Blocks.SEA_LANTERN) || state.is(Blocks.DARK_PRISMARINE);
	}
}

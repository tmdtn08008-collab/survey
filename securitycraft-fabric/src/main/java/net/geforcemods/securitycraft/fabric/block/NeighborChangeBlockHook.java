package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#onNeighborChange, implemented only by SecurityCraft's blocks.
 * SecurityCraft's LevelNeighborChangeMixin calls it, on both sides, for the blocks in all six directions around a position
 * whenever Level#updateNeighbourForOutputSignal runs for it (a neighboring block entity changed its contents or was
 * destroyed) and after Level#removeBlockEntity, which are the places NeoForge calls it from.
 */
public interface NeighborChangeBlockHook {
	/**
	 * Called when a neighboring block entity changes.
	 *
	 * @param state The state of this block
	 * @param level The level the block is in
	 * @param pos The position of this block
	 * @param neighbor The position of the neighbor that changed
	 */
	default void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {}
}

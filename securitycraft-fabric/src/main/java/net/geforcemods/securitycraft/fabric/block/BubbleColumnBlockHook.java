package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#getBubbleColumnDirection, implemented only by SecurityCraft's blocks
 * (reinforced soul sand and reinforced magma blocks). SecurityCraft's BubbleColumnBlockMixin asks it in
 * BubbleColumnBlock#getColumnState (which kind of column forms above the block) and BubbleColumnBlock#canSurvive (whether a
 * column can stay above the block), like NeoForge does.
 */
public interface BubbleColumnBlockHook {
	/**
	 * @param state The state of this block
	 * @return The direction of the bubble column this block creates in the water above it
	 */
	default BubbleColumnDirection getBubbleColumnDirection(BlockState state) {
		if (state.is(Blocks.SOUL_SAND))
			return BubbleColumnDirection.UPWARD;
		else if (state.is(Blocks.MAGMA_BLOCK))
			return BubbleColumnDirection.DOWNWARD;
		else
			return BubbleColumnDirection.NONE;
	}
}

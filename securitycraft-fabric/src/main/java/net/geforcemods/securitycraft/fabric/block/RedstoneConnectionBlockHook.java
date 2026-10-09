package net.geforcemods.securitycraft.fabric.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#canConnectRedstone, implemented only by SecurityCraft's blocks. It
 * decides whether redstone dust visually connects to this block. SecurityCraft's RedStoneWireBlockMixin asks it in
 * RedStoneWireBlock#shouldConnectTo(BlockState, Direction), which is what RedStoneWireBlock#getConnectingSide uses, so it is
 * consulted at the same points NeoForge consults it.
 */
public interface RedstoneConnectionBlockHook {
	/**
	 * @param state The state of this block
	 * @param level Always null on Fabric: vanilla's shouldConnectTo does not know the level, and no SecurityCraft block needs
	 *            it
	 * @param pos Always null on Fabric, see level
	 * @param direction The direction from the redstone dust to this block, or null if the dust is above or below this block
	 * @return true if redstone dust should connect to this block
	 */
	boolean canConnectRedstone(BlockState state, @Nullable BlockGetter level, @Nullable BlockPos pos, @Nullable Direction direction);
}

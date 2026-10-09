package net.geforcemods.securitycraft.blocks.reinforced;

import net.geforcemods.securitycraft.fabric.block.PortalFrameBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ReinforcedObsidianBlock extends BaseReinforcedBlock implements PortalFrameBlockHook {
	public ReinforcedObsidianBlock(BlockBehaviour.Properties properties, Block vB) {
		super(properties, vB);
	}

	@Override
	public boolean isPortalFrame(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}
}

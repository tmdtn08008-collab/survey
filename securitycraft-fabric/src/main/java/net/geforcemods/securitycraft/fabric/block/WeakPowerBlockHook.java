package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#shouldCheckWeakPower, implemented only by SecurityCraft's blocks.
 * SecurityCraft's SignalGetterMixin consults it in SignalGetter#getSignal(BlockPos, Direction) instead of
 * BlockState#isRedstoneConductor, like NeoForge does. Returning false stops a block from passing on (conducting) the power
 * of a block that strongly powers it, which keeps for example a keypad that is strongly powered from also opening an
 * adjacent door.
 */
public interface WeakPowerBlockHook {
	/**
	 * @param state The state of this block
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @param side The side of this block that is checked for power
	 * @return true if this block should conduct the direct (strong) signals it receives, like a solid block does
	 */
	default boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
		return state.isRedstoneConductor(level, pos);
	}
}

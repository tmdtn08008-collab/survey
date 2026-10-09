package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#onDestroyedByPlayer, implemented only by SecurityCraft's blocks (mines,
 * claymores, the cage trap). SecurityCraft's ServerPlayerGameModeMixin calls it on the server in
 * ServerPlayerGameMode#destroyBlock in place of vanilla's {@code level.removeBlock(pos, false)}, so it runs after
 * Block#playerWillDestroy, both in creative and survival, exactly where NeoForge calls it. The result decides whether the
 * block counts as removed (Block#destroy and Block#playerDestroy only run if it is true).
 * <p>
 * NeoForge also calls this on the client from MultiPlayerGameMode#destroyBlock. All SecurityCraft implementations only act
 * on the server and otherwise fall back to the default, which does exactly what vanilla's client does, so no client hook
 * is needed.
 */
public interface PlayerDestroyBlockHook {
	/**
	 * Called when a player destroys this block, instead of the block being removed directly.
	 *
	 * @param state The state of the block that is being destroyed
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @param player The player destroying the block
	 * @param willHarvest Whether the player can harvest the block's drops (always false in creative mode)
	 * @param fluid The fluid state at the block's position, which the block should leave behind
	 * @return true if the block was removed
	 */
	default boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
		//same as vanilla: the client replaces the block with its fluid, the server removes the block
		if (level.isClientSide())
			return level.setBlock(pos, fluid.createLegacyBlock(), 11);

		return level.removeBlock(pos, false);
	}
}

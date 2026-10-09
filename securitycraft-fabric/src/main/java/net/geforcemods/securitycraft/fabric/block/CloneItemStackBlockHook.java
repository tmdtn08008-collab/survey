package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Fabric replacement for NeoForge's context-aware IBlockExtension#getCloneItemStack, implemented only by SecurityCraft's
 * blocks (for example to pick the disguise of a disguised block or mine instead of the real block). SecurityCraft's client
 * MinecraftPickBlockMixin calls it in Minecraft#pickBlock instead of the vanilla three-argument
 * Block#getCloneItemStack(LevelReader, BlockPos, BlockState), which is the only place NeoForge calls it from.
 */
public interface CloneItemStackBlockHook {
	/**
	 * @param state The state of the targeted block
	 * @param target The hit result of the player's pick block action
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @param player The player picking the block
	 * @return The stack the player picks, or an empty stack for none
	 */
	default ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
		return state.getBlock().getCloneItemStack(level, pos, state);
	}
}

package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#isLadder and #isScaffolding, implemented only by SecurityCraft's blocks
 * (the reinforced scaffolding, which only its owner may climb). SecurityCraft's LivingEntityMixin asks
 * {@link #isLadder} instead of checking the #minecraft:climbable tag in LivingEntity#onClimbable, and {@link #isScaffolding}
 * in addition to the scaffolding check in LivingEntity#handleOnClimbable, like NeoForge does.
 */
public interface ClimbableBlockHook {
	/**
	 * @param state The state of this block
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @param entity The entity that is inside the block
	 * @return true if the entity can climb this block like a ladder
	 */
	default boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
		return state.is(BlockTags.CLIMBABLE);
	}

	/**
	 * @param state The state of this block
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @param entity The entity that is inside the block
	 * @return true if this block behaves like scaffolding for the entity (a sneaking player does not stay in place on it)
	 */
	default boolean isScaffolding(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
		return state.is(Blocks.SCAFFOLDING);
	}
}

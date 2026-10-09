package net.geforcemods.securitycraft.fabric.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's position-aware IBlockExtension#getSoundType, implemented only by SecurityCraft's blocks
 * (disguised blocks sound like their disguise). SecurityCraft's mixins use it instead of BlockState#getSoundType() at the
 * sites NeoForge patched: Entity#playStepSound, the muffled and combination step sounds of Player#playStepSound,
 * LivingEntity#playBlockFallSound, AbstractHorse#playStepSound, Sniffer's digging sound, BlockItem#place, and on the client
 * the block breaking sound (LevelRenderer#levelEvent 2001) and block hitting sound
 * (MultiPlayerGameMode#continueDestroyBlock).
 */
public interface SoundTypeBlockHook {
	/**
	 * @param state The state of this block
	 * @param level The level the block is in
	 * @param pos The position of the block
	 * @param entity The entity that causes the sound, if any
	 * @return The sound type to use for this block at this position
	 */
	default SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
		return state.getSoundType();
	}

	/**
	 * Position-aware sound type lookup for any block state, like NeoForge's IBlockStateExtension#getSoundType.
	 *
	 * @return The sound type of SecurityCraft's hook if the block implements it, the state's vanilla sound type otherwise
	 */
	static SoundType getSoundTypeAt(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook)
			return hook.getSoundType(state, level, pos, entity);

		return state.getSoundType();
	}
}

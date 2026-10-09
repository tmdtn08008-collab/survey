package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the position-aware {@link SoundTypeBlockHook#getSoundType} for the muffled (in water) and combination (carpet or
 * snow on top of a block) step sounds of players walking on SecurityCraft blocks, like NeoForge, which patches
 * Player#playStepSound to pass the positions on to Entity#playMuffledStepSound and Entity#playCombinationStepSounds.
 * Vanilla's versions of these methods have no position, so for SecurityCraft blocks the sounds are played here the same
 * way vanilla's methods play them. For all other blocks, vanilla's methods are called.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@WrapOperation(method = "playStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;playMuffledStepSound(Lnet/minecraft/world/level/block/state/BlockState;)V"))
	private void securitycraft$playMuffledStepSound(Player player, BlockState state, Operation<Void> original, @Local(argsOnly = true) BlockPos pos) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook)
			securitycraft$playMuffledStepSound(player, hook.getSoundType(state, player.level(), pos, player));
		else
			original.call(player, state);
	}

	@WrapOperation(method = "playStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;playCombinationStepSounds(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
	private void securitycraft$playCombinationStepSounds(Player player, BlockState primaryState, BlockState secondaryState, Operation<Void> original, @Local(argsOnly = true) BlockPos secondaryPos) {
		if (primaryState.getBlock() instanceof SoundTypeBlockHook || secondaryState.getBlock() instanceof SoundTypeBlockHook) {
			Level level = player.level();
			//vanilla only plays combination step sounds when the primary step sound block (Entity#getPrimaryStepSoundBlockPos) is the one above
			SoundType primarySoundType = SoundTypeBlockHook.getSoundTypeAt(primaryState, level, secondaryPos.above(), player);

			//same as Entity#playCombinationStepSounds
			player.playSound(primarySoundType.getStepSound(), primarySoundType.getVolume() * 0.15F, primarySoundType.getPitch());
			securitycraft$playMuffledStepSound(player, SoundTypeBlockHook.getSoundTypeAt(secondaryState, level, secondaryPos, player));
		}
		else
			original.call(player, primaryState, secondaryState);
	}

	@Unique
	private static void securitycraft$playMuffledStepSound(Player player, SoundType soundType) {
		//same as Entity#playMuffledStepSound
		player.playSound(soundType.getStepSound(), soundType.getVolume() * 0.05F, soundType.getPitch() * 0.8F);
	}
}

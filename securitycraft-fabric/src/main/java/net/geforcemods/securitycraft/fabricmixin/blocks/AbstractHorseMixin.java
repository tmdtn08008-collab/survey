package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the position-aware {@link SoundTypeBlockHook#getSoundType} for the step sounds of horses walking on SecurityCraft
 * blocks, like NeoForge's patched AbstractHorse#playStepSound. Like on NeoForge, both sound type lookups in that method
 * (the block below and snow above it) use the position of the block below.
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
	@WrapOperation(method = "playStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType securitycraft$getStepSoundType(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook) {
			AbstractHorse self = (AbstractHorse) (Object) this;

			return hook.getSoundType(state, self.level(), pos, self);
		}

		return original.call(state);
	}
}

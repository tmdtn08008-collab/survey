package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the position-aware {@link SoundTypeBlockHook#getSoundType} for step sounds on SecurityCraft blocks (disguised blocks
 * sound like their disguise), like NeoForge's patched Entity#playStepSound. The muffled and combination step sounds, which
 * only players play, are handled in {@link PlayerMixin}.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@WrapOperation(method = "playStepSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType securitycraft$getStepSoundType(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook) {
			Entity self = (Entity) (Object) this;

			return hook.getSoundType(state, self.level(), pos, self);
		}

		return original.call(state);
	}
}

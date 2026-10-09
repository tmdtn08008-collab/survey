package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the position-aware {@link SoundTypeBlockHook#getSoundType} for the digging sound of a sniffer digging into a
 * SecurityCraft block, like NeoForge's patched Sniffer#emitDiggingParticles.
 */
@Mixin(Sniffer.class)
public abstract class SnifferMixin {
	@WrapOperation(method = "emitDiggingParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType securitycraft$getDiggingSoundType(BlockState state, Operation<SoundType> original, @Local BlockPos headPos) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook) {
			Sniffer self = (Sniffer) (Object) this;

			return hook.getSoundType(state, self.level(), headPos.below(), self);
		}

		return original.call(state);
	}
}

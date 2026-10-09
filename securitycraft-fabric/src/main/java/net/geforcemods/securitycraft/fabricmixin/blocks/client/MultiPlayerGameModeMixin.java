package net.geforcemods.securitycraft.fabricmixin.blocks.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the position-aware {@link SoundTypeBlockHook#getSoundType} for the hitting sound while mining SecurityCraft blocks,
 * like NeoForge's patched MultiPlayerGameMode#continueDestroyBlock.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@WrapOperation(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType securitycraft$getHitSoundType(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook && minecraft.level != null)
			return hook.getSoundType(state, minecraft.level, pos, minecraft.player);

		return original.call(state);
	}
}

package net.geforcemods.securitycraft.fabricmixin.blocks.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the position-aware {@link SoundTypeBlockHook#getSoundType} for the breaking sound of SecurityCraft blocks (level
 * event 2001), like NeoForge's patched LevelRenderer#levelEvent.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow
	private ClientLevel level;

	@WrapOperation(method = "levelEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType securitycraft$getBreakSoundType(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook && level != null)
			return hook.getSoundType(state, level, pos, null);

		return original.call(state);
	}
}

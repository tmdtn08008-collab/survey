package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the position-aware {@link SoundTypeBlockHook#getSoundType} for the placement sound of SecurityCraft blocks, like
 * NeoForge's patched BlockItem#place (which uses the position-aware sound type for the volume and pitch, and its
 * position-aware getPlaceSound overload for the sound itself). For other blocks, vanilla's BlockItem#getPlaceSound is
 * called, so item overrides of it keep working.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType securitycraft$getPlaceSoundType(BlockState state, Operation<SoundType> original, @Local Level level, @Local BlockPos pos, @Local Player player) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook)
			return hook.getSoundType(state, level, pos, player);

		return original.call(state);
	}

	@WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;getPlaceSound(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/sounds/SoundEvent;"))
	private SoundEvent securitycraft$getPlaceSound(BlockItem item, BlockState state, Operation<SoundEvent> original, @Local Level level, @Local BlockPos pos, @Local Player player) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook)
			return hook.getSoundType(state, level, pos, player).getPlaceSound();

		return original.call(item, state);
	}
}

package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.BubbleColumnBlockHook;
import net.geforcemods.securitycraft.fabric.block.BubbleColumnDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft blocks (reinforced soul sand and magma blocks) create bubble columns, like NeoForge's patched
 * BubbleColumnBlock, which asks IBlockExtension#getBubbleColumnDirection in getColumnState and canSurvive instead of
 * checking for soul sand and magma blocks.
 */
@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnBlockMixin {
	@Inject(method = "getColumnState", at = @At("HEAD"), cancellable = true)
	private static void securitycraft$getBubbleColumnDirection(BlockState state, CallbackInfoReturnable<BlockState> cir) {
		if (state.getBlock() instanceof BubbleColumnBlockHook hook) {
			BubbleColumnDirection direction = hook.getBubbleColumnDirection(state);

			if (direction == BubbleColumnDirection.UPWARD)
				cir.setReturnValue(Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
			else if (direction == BubbleColumnDirection.DOWNWARD)
				cir.setReturnValue(Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true));
		}
	}

	@ModifyReturnValue(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
	private boolean securitycraft$canSurviveAboveBubbleColumnBlock(boolean canSurvive, @Local(argsOnly = true) LevelReader level, @Local(argsOnly = true) BlockPos pos) {
		if (canSurvive)
			return true;

		BlockState below = level.getBlockState(pos.below());

		return below.getBlock() instanceof BubbleColumnBlockHook hook && hook.getBubbleColumnDirection(below) != BubbleColumnDirection.NONE;
	}
}

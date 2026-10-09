package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.ConduitFrameBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft blocks (the reinforced prismarine family) be part of a conduit's frame, like NeoForge's patched
 * ConduitBlockEntity#updateShape, which asks IBlockExtension#isConduitFrame. The block state read in updateShape is only
 * compared against vanilla's frame blocks, so a SecurityCraft frame block is reported as prismarine there, which adds its
 * position to the frame exactly once.
 */
@Mixin(ConduitBlockEntity.class)
public abstract class ConduitBlockEntityMixin {
	@WrapOperation(method = "updateShape", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private static BlockState securitycraft$isConduitFrame(Level level, BlockPos pos, Operation<BlockState> original, @Local(argsOnly = true) BlockPos conduitPos) {
		BlockState state = original.call(level, pos);

		if (state.getBlock() instanceof ConduitFrameBlockHook hook && hook.isConduitFrame(state, level, pos, conduitPos))
			return Blocks.PRISMARINE.defaultBlockState();

		return state;
	}
}

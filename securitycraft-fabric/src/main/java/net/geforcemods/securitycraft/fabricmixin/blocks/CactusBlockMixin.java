package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.geforcemods.securitycraft.fabric.block.SoilBlockHook;
import net.geforcemods.securitycraft.fabric.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft's soil blocks decide whether a cactus can stay on them (reinforced sand), like NeoForge's patched
 * CactusBlock#canSurvive. The decision is asked at the same point as on NeoForge: after vanilla checked the blocks next to
 * the cactus, right before it checks the block below (the first BlockState#is(Block) call, which tests for a cactus below).
 */
@Mixin(CactusBlock.class)
public abstract class CactusBlockMixin {
	@Inject(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 0), cancellable = true)
	private void securitycraft$canSustainPlant(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockPos soilPos = pos.below();
		TriState soilDecision = SoilBlockHook.getSoilDecision(level.getBlockState(soilPos), level, soilPos, Direction.UP, state);

		if (!soilDecision.isDefault())
			cir.setReturnValue(soilDecision.isTrue());
	}
}

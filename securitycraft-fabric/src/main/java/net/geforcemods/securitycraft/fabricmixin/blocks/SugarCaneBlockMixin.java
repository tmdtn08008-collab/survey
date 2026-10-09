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
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft's soil blocks decide whether sugar cane can stay on them (reinforced dirt and sand next to water), like
 * NeoForge's patched SugarCaneBlock#canSurvive. The decision is asked at the same point as on NeoForge: after vanilla
 * checked for sugar cane below, right before it checks for dirt or sand (the first BlockState#is(TagKey) call).
 */
@Mixin(SugarCaneBlock.class)
public abstract class SugarCaneBlockMixin {
	@Inject(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0), cancellable = true)
	private void securitycraft$canSustainPlant(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockPos soilPos = pos.below();
		TriState soilDecision = SoilBlockHook.getSoilDecision(level.getBlockState(soilPos), level, soilPos, Direction.UP, state);

		if (!soilDecision.isDefault())
			cir.setReturnValue(soilDecision.isTrue());
	}
}

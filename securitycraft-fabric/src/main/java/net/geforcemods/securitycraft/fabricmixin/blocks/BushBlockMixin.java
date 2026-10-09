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
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft's soil blocks decide whether a bush-like plant (flowers, saplings, fungi, roots, lily pads, wither
 * roses, nether wart, seagrass, crops...) can stay on them, like NeoForge's patched BushBlock#canSurvive. Plants on other
 * blocks behave like in vanilla.
 */
@Mixin(BushBlock.class)
public abstract class BushBlockMixin {
	@Inject(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
	private void securitycraft$canSustainPlant(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockPos soilPos = pos.below();
		TriState soilDecision = SoilBlockHook.getSoilDecision(level.getBlockState(soilPos), level, soilPos, Direction.UP, state);

		if (!soilDecision.isDefault())
			cir.setReturnValue(soilDecision.isTrue());
	}
}

package net.geforcemods.securitycraft.fabricmixin.blocks;

import java.util.function.BiConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.block.SoilBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;

/**
 * Asks {@link SoilBlockHook#onTreeGrow} before a growing tree turns the block below its trunk into dirt, like NeoForge's
 * patched TrunkPlacer#setDirtAt. SecurityCraft's reinforced blocks return true, so a tree growing on them cannot replace
 * them (reinforced dirt is not in #minecraft:dirt, so vanilla would replace it).
 */
@Mixin(TrunkPlacer.class)
public abstract class TrunkPlacerMixin {
	@Inject(method = "setDirtAt", at = @At("HEAD"), cancellable = true)
	private static void securitycraft$callOnTreeGrow(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> placeFunction, RandomSource random, BlockPos pos, TreeConfiguration config, CallbackInfo ci) {
		//tree features always run in a WorldGenLevel, which is a LevelReader (NeoForge casts unconditionally)
		if (level instanceof LevelReader levelReader) {
			BlockState state = levelReader.getBlockState(pos);

			if (state.getBlock() instanceof SoilBlockHook hook && hook.onTreeGrow(state, levelReader, placeFunction, random, pos, config))
				ci.cancel();
		}
	}
}

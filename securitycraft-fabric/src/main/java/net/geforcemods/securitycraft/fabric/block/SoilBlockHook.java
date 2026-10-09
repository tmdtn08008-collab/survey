package net.geforcemods.securitycraft.fabric.block;

import java.util.function.BiConsumer;

import net.geforcemods.securitycraft.fabric.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

/**
 * Fabric replacement for NeoForge's IBlockExtension#canSustainPlant and #onTreeGrow, implemented only by SecurityCraft's
 * reinforced soil blocks.
 * <ul>
 * <li>{@link #canSustainPlant} is consulted by SecurityCraft's mixins in BushBlock#canSurvive, CactusBlock#canSurvive and
 * SugarCaneBlock#canSurvive at the same points NeoForge consults it. NeoForge also asks it for mushrooms, crops, bamboo,
 * cocoa, chorus plants, dripleaves, pitcher crops and hanging mangrove propagules, but SecurityCraft's soil blocks return
 * {@link TriState#DEFAULT} for all of those (they are covered by the generated vanilla tags instead), so those sites are not
 * hooked.</li>
 * <li>{@link #onTreeGrow} is consulted by SecurityCraft's TrunkPlacerMixin before TrunkPlacer#setDirtAt replaces the block
 * under a growing tree with dirt.</li>
 * </ul>
 */
public interface SoilBlockHook {
	/**
	 * @param soilState The state of this (soil) block
	 * @param level The level the soil is in
	 * @param soilPos The position of the soil
	 * @param facing The side of the soil the plant is on
	 * @param plant The state of the plant
	 * @return TRUE or FALSE to allow or deny the plant on this soil, DEFAULT to let the plant decide like in vanilla
	 */
	default TriState canSustainPlant(BlockState soilState, BlockGetter level, BlockPos soilPos, Direction facing, BlockState plant) {
		return TriState.DEFAULT;
	}

	/**
	 * Called when a tree grows on top of this block, before the trunk placer turns it into dirt.
	 *
	 * @param state The state of this block
	 * @param level The level the tree grows in (this is called during world generation too)
	 * @param placeFunction The function the trunk placer uses to place blocks
	 * @param randomSource The random source of the tree feature
	 * @param pos The position of this block
	 * @param config The configuration of the tree
	 * @return true to stop the trunk placer from replacing this block (the block is then responsible for any changes itself)
	 */
	default boolean onTreeGrow(BlockState state, LevelReader level, BiConsumer<BlockPos, BlockState> placeFunction, RandomSource randomSource, BlockPos pos, TreeConfiguration config) {
		return false;
	}

	/**
	 * Asks the soil at the given position whether it can sustain the plant, like NeoForge's
	 * IBlockStateExtension#canSustainPlant.
	 *
	 * @return The soil's decision, or DEFAULT if the soil is not a SecurityCraft soil block
	 */
	static TriState getSoilDecision(BlockState soilState, BlockGetter level, BlockPos soilPos, Direction facing, BlockState plant) {
		if (soilState.getBlock() instanceof SoilBlockHook hook)
			return hook.canSustainPlant(soilState, level, soilPos, facing, plant);

		return TriState.DEFAULT;
	}
}

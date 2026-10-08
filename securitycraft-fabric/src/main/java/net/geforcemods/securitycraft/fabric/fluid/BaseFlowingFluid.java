package net.geforcemods.securitycraft.fabric.fluid;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Fabric stand-in for NeoForge's BaseFlowingFluid, without NeoForge's FluidType (vanilla fluid behavior on Fabric is driven by
 * fluid tags instead).
 */
public abstract class BaseFlowingFluid extends FlowingFluid {
	private final Supplier<? extends Fluid> flowing;
	private final Supplier<? extends Fluid> still;
	private final Supplier<? extends Item> bucket;
	private final Supplier<? extends LiquidBlock> block;
	private final int slopeFindDistance;
	private final int levelDecreasePerBlock;
	private final float explosionResistance;
	private final int tickRate;

	protected BaseFlowingFluid(Properties properties) {
		flowing = properties.flowing;
		still = properties.still;
		bucket = properties.bucket;
		block = properties.block;
		slopeFindDistance = properties.slopeFindDistance;
		levelDecreasePerBlock = properties.levelDecreasePerBlock;
		explosionResistance = properties.explosionResistance;
		tickRate = properties.tickRate;
	}

	@Override
	public Fluid getFlowing() {
		return flowing.get();
	}

	@Override
	public Fluid getSource() {
		return still.get();
	}

	@Override
	protected boolean canConvertToSource(Level level) {
		return false;
	}

	/**
	 * NeoForge's position-aware variant. Vanilla only calls {@link #canConvertToSource(Level)}.
	 */
	public boolean canConvertToSource(FluidState state, Level level, BlockPos pos) {
		return canConvertToSource(level);
	}

	@Override
	protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
		BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

		Block.dropResources(state, level, pos, be);
	}

	@Override
	protected int getSlopeFindDistance(LevelReader level) {
		return slopeFindDistance;
	}

	@Override
	protected int getDropOff(LevelReader level) {
		return levelDecreasePerBlock;
	}

	@Override
	public Item getBucket() {
		return bucket != null ? bucket.get() : Items.AIR;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
		return direction == Direction.DOWN && !isSame(fluid);
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return tickRate;
	}

	@Override
	protected float getExplosionResistance() {
		return explosionResistance;
	}

	@Override
	protected BlockState createLegacyBlock(FluidState state) {
		if (block != null)
			return block.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));

		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public boolean isSame(Fluid fluid) {
		return fluid == getSource() || fluid == getFlowing();
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		return Optional.of(SoundEvents.BUCKET_FILL);
	}

	public static class Properties {
		private final Supplier<? extends Fluid> still;
		private final Supplier<? extends Fluid> flowing;
		private Supplier<? extends Item> bucket;
		private Supplier<? extends LiquidBlock> block;
		private int slopeFindDistance = 4;
		private int levelDecreasePerBlock = 1;
		private float explosionResistance = 1;
		private int tickRate = 5;

		public Properties(Supplier<? extends Fluid> still, Supplier<? extends Fluid> flowing) {
			this.still = still;
			this.flowing = flowing;
		}

		public Properties bucket(Supplier<? extends Item> bucket) {
			this.bucket = bucket;
			return this;
		}

		public Properties block(Supplier<? extends LiquidBlock> block) {
			this.block = block;
			return this;
		}

		public Properties slopeFindDistance(int slopeFindDistance) {
			this.slopeFindDistance = slopeFindDistance;
			return this;
		}

		public Properties levelDecreasePerBlock(int levelDecreasePerBlock) {
			this.levelDecreasePerBlock = levelDecreasePerBlock;
			return this;
		}

		public Properties explosionResistance(float explosionResistance) {
			this.explosionResistance = explosionResistance;
			return this;
		}

		public Properties tickRate(int tickRate) {
			this.tickRate = tickRate;
			return this;
		}
	}
}

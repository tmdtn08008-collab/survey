package net.geforcemods.securitycraft.blocks;

import java.util.function.BiConsumer;

import net.geforcemods.securitycraft.api.OwnableBlockEntity;
import net.geforcemods.securitycraft.fabricmixin.blocks.BlockBehaviourPropertiesAccessor;
import net.geforcemods.securitycraft.misc.OwnershipEvent;
import net.geforcemods.securitycraft.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.geforcemods.securitycraft.fabric.event.NeoForge;

public class OwnableFenceGateBlock extends FenceGateBlock implements EntityBlock {
	//PORT-NOTE: NeoForge adds these fields to FenceGateBlock itself. Vanilla only has the private WoodType they are taken from
	protected final SoundEvent openSound, closeSound;
	private final float destroyTimeForOwner;

	public OwnableFenceGateBlock(BlockBehaviour.Properties properties, WoodType woodType) {
		super(woodType, OwnableBlock.withReinforcedDestroyTime(properties));
		destroyTimeForOwner = OwnableBlock.getStoredDestroyTime();
		openSound = woodType.fenceGateOpen();
		closeSound = woodType.fenceGateClose();
	}

	public OwnableFenceGateBlock(BlockBehaviour.Properties properties, SoundEvent openSound, SoundEvent closeSound) {
		//PORT-NOTE: Vanilla's FenceGateBlock has no constructor that takes sounds (NeoForge adds one) and always plays the sounds of its WoodType, so an unregistered WoodType carrying these sounds is passed instead. Its sound type is the one already set in the properties, because vanilla applies the WoodType's sound type to the properties.
		this(properties, new WoodType("securitycraft:fence_gate_sounds", BlockSetType.IRON, ((BlockBehaviourPropertiesAccessor) properties).securitycraft$getSoundType(), SoundType.HANGING_SIGN, closeSound, openSound));
	}

	@Override
	public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return BlockUtils.getDestroyProgress(super::getDestroyProgress, destroyTimeForOwner, state, player, level, pos);
	}

	@Override
	public void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
		if (!explosion.canTriggerBlocks())
			super.onExplosionHit(state, level, pos, explosion, dropConsumer);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		if (placer instanceof Player player)
			NeoForge.EVENT_BUS.post(new OwnershipEvent(level, pos, player));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean hasActiveSCBlock = BlockUtils.hasActiveSCBlockNextTo(context.getLevel(), context.getClickedPos());

		return super.getStateForPlacement(context).setValue(OPEN, hasActiveSCBlock).setValue(POWERED, hasActiveSCBlock);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		if (!level.isClientSide) {
			boolean isPoweredSCBlock = BlockUtils.hasActiveSCBlockNextTo(level, pos);

			if (state.getValue(POWERED) != isPoweredSCBlock) {
				level.setBlock(pos, state.setValue(POWERED, isPoweredSCBlock).setValue(OPEN, isPoweredSCBlock), 2);

				if (state.getValue(OPEN) != isPoweredSCBlock) {
					level.playSound(null, pos, isPoweredSCBlock ? openSound : closeSound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
					level.gameEvent(null, isPoweredSCBlock ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
				}
			}
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new OwnableBlockEntity(pos, state);
	}
}

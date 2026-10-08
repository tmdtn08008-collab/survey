package net.geforcemods.securitycraft.fabric.event;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric stand-ins for NeoForge's PlayerEvent subclasses that SecurityCraft listens to.
 */
public abstract class PlayerEvent {
	private final Player player;

	protected PlayerEvent(Player player) {
		this.player = player;
	}

	public Player getEntity() {
		return player;
	}

	/**
	 * Fired when it is checked whether a player can harvest (get the drops of) a block: when the block is broken by the
	 * player on the server, and when the mining speed is calculated on both sides. See
	 * {@link EventHooks#doPlayerHarvestCheck(Player, BlockState, BlockGetter, BlockPos)}.
	 */
	public static class HarvestCheck extends PlayerEvent {
		private final BlockState state;
		private final BlockGetter level;
		private final BlockPos pos;
		private final boolean blockEntityKnown;
		@Nullable
		private final BlockEntity blockEntity;
		private boolean success;

		public HarvestCheck(Player player, BlockState state, BlockGetter level, BlockPos pos, boolean success, boolean blockEntityKnown, @Nullable BlockEntity blockEntity) {
			super(player);
			this.state = state;
			this.level = level;
			this.pos = pos;
			this.success = success;
			this.blockEntityKnown = blockEntityKnown;
			this.blockEntity = blockEntity;
		}

		public BlockState getTargetBlock() {
			return state;
		}

		public BlockGetter getLevel() {
			return level;
		}

		public BlockPos getPos() {
			return pos;
		}

		/**
		 * Returns the block entity of the block being checked. NeoForge checks harvestability while the block is still in the
		 * level, but vanilla's ServerPlayerGameMode#destroyBlock (which Fabric does not patch) only checks it after the block
		 * has been removed, so the block entity captured before the removal is handed to this event there.
		 */
		@Nullable
		public BlockEntity getBlockEntity() {
			return blockEntityKnown ? blockEntity : level.getBlockEntity(pos);
		}

		public boolean canHarvest() {
			return success;
		}

		public void setCanHarvest(boolean success) {
			this.success = success;
		}
	}
}

package net.geforcemods.securitycraft.fabric.event;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric stand-ins for NeoForge's BlockEvent subclasses that SecurityCraft listens to.
 */
public abstract class BlockEvent {
	private final LevelAccessor level;
	private final BlockPos pos;
	private final BlockState state;

	protected BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
		this.level = level;
		this.pos = pos;
		this.state = state;
	}

	public LevelAccessor getLevel() {
		return level;
	}

	public BlockPos getPos() {
		return pos;
	}

	public BlockState getState() {
		return state;
	}

	/**
	 * Fired on the server before a player breaks a block (from Fabric's PlayerBlockBreakEvents.BEFORE). Canceling it
	 * prevents the block from being broken.
	 */
	public static class BreakEvent extends BlockEvent {
		private final Player player;
		private boolean canceled;

		public BreakEvent(Level level, BlockPos pos, BlockState state, Player player) {
			super(level, pos, state);
			this.player = player;
		}

		public Player getPlayer() {
			return player;
		}

		public boolean isCanceled() {
			return canceled;
		}

		public void setCanceled(boolean canceled) {
			this.canceled = canceled;
		}
	}

	/**
	 * Fired on the server after an entity placed a block. Unlike NeoForge's version, this event cannot be canceled, because
	 * SecurityCraft's Fabric port records the block changes instead of capturing restorable snapshots.
	 */
	public static class EntityPlaceEvent extends BlockEvent {
		@Nullable
		private final Entity entity;
		private final BlockState placedAgainst;

		public EntityPlaceEvent(LevelAccessor level, BlockPos pos, BlockState placedBlock, BlockState placedAgainst, @Nullable Entity entity) {
			super(level, pos, placedBlock);
			this.entity = entity;
			this.placedAgainst = placedAgainst;
		}

		@Nullable
		public Entity getEntity() {
			return entity;
		}

		public BlockState getPlacedBlock() {
			return getState();
		}

		public BlockState getPlacedAgainst() {
			return placedAgainst;
		}
	}
}

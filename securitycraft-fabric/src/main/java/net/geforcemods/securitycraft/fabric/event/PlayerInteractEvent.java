package net.geforcemods.securitycraft.fabric.event;

import javax.annotation.Nullable;

import net.geforcemods.securitycraft.fabric.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fabric stand-ins for NeoForge's PlayerInteractEvent subclasses that SecurityCraft listens to. They are plain objects,
 * created by {@link EventHooks} (from SecurityCraft's own mixins or from Fabric API callbacks) and handed to
 * SCEventHandler.
 */
public abstract class PlayerInteractEvent extends CancellableEvent {
	private final Player player;
	private final InteractionHand hand;
	private final BlockPos pos;
	@Nullable
	private final Direction face;

	protected PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos, @Nullable Direction face) {
		this.player = player;
		this.hand = hand;
		this.pos = pos;
		this.face = face;
	}

	public Player getEntity() {
		return player;
	}

	public InteractionHand getHand() {
		return hand;
	}

	public ItemStack getItemStack() {
		return player.getItemInHand(hand);
	}

	public BlockPos getPos() {
		return pos;
	}

	@Nullable
	public Direction getFace() {
		return face;
	}

	public Level getLevel() {
		return player.level();
	}

	/**
	 * Fired on both sides when a player right clicks a block, before the block or the held item are used. Canceling it
	 * makes the interaction return {@link #getCancellationResult()} (PASS by default, which lets the client go on to use the
	 * item in the air). {@link #setUseBlock} and {@link #setUseItem} control whether the block and the item are used: TRUE
	 * forces the use, FALSE prevents it and DEFAULT keeps vanilla behavior.
	 */
	public static class RightClickBlock extends PlayerInteractEvent {
		private final BlockHitResult hitVec;
		private InteractionResult cancellationResult = InteractionResult.PASS;
		private TriState useBlock = TriState.DEFAULT;
		private TriState useItem = TriState.DEFAULT;

		public RightClickBlock(Player player, InteractionHand hand, BlockPos pos, BlockHitResult hitVec) {
			super(player, hand, pos, hitVec.getDirection());
			this.hitVec = hitVec;
		}

		public BlockHitResult getHitVec() {
			return hitVec;
		}

		public TriState getUseBlock() {
			return useBlock;
		}

		public TriState getUseItem() {
			return useItem;
		}

		public void setUseBlock(TriState useBlock) {
			this.useBlock = useBlock;
		}

		public void setUseItem(TriState useItem) {
			this.useItem = useItem;
		}

		@Override
		public void setCanceled(boolean canceled) {
			super.setCanceled(canceled);

			if (canceled) {
				useBlock = TriState.FALSE;
				useItem = TriState.FALSE;
			}
		}

		public InteractionResult getCancellationResult() {
			return cancellationResult;
		}

		public void setCancellationResult(InteractionResult cancellationResult) {
			this.cancellationResult = cancellationResult;
		}
	}

	/**
	 * Fired on both sides when a player left clicks a block.
	 */
	public static class LeftClickBlock extends PlayerInteractEvent {
		private final Action action;
		private TriState useBlock = TriState.DEFAULT;
		private TriState useItem = TriState.DEFAULT;

		public LeftClickBlock(Player player, BlockPos pos, Direction face, Action action) {
			super(player, InteractionHand.MAIN_HAND, pos, face);
			this.action = action;
		}

		public Action getAction() {
			return action;
		}

		public TriState getUseBlock() {
			return useBlock;
		}

		public TriState getUseItem() {
			return useItem;
		}

		public void setUseBlock(TriState useBlock) {
			this.useBlock = useBlock;
		}

		public void setUseItem(TriState useItem) {
			this.useItem = useItem;
		}

		@Override
		public void setCanceled(boolean canceled) {
			super.setCanceled(canceled);

			if (canceled) {
				useBlock = TriState.FALSE;
				useItem = TriState.FALSE;
			}
		}

		public enum Action {
			START,
			STOP,
			ABORT,
			CLIENT_HOLD;

			public static Action convert(ServerboundPlayerActionPacket.Action action) {
				return switch (action) {
					case STOP_DESTROY_BLOCK -> STOP;
					case ABORT_DESTROY_BLOCK -> ABORT;
					default -> START;
				};
			}
		}
	}

	/**
	 * Fired on both sides when a player right clicks with an item (not targeting a block or an entity).
	 */
	public static class RightClickItem extends PlayerInteractEvent {
		private InteractionResult cancellationResult = InteractionResult.PASS;

		public RightClickItem(Player player, InteractionHand hand) {
			super(player, hand, player.blockPosition(), null);
		}

		public InteractionResult getCancellationResult() {
			return cancellationResult;
		}

		public void setCancellationResult(InteractionResult cancellationResult) {
			this.cancellationResult = cancellationResult;
		}
	}
}

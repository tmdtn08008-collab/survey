package net.geforcemods.securitycraft.fabric.event;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Fabric stand-in for NeoForge's UseItemOnBlockEvent. SecurityCraft only fires the {@link UsePhase#ITEM_AFTER_BLOCK}
 * phase (from the ItemStack#useOn mixin), which is the only phase SecurityCraft listens to. Canceling it makes
 * ItemStack#useOn return the {@link #getCancellationResult() cancellation result}.
 */
public class UseItemOnBlockEvent extends CancellableEvent {
	private final Level level;
	@Nullable
	private final Player player;
	private final InteractionHand hand;
	private final ItemStack heldItem;
	private final BlockPos pos;
	@Nullable
	private final Direction face;
	private final UseOnContext context;
	private final UsePhase usePhase;
	private ItemInteractionResult cancellationResult = ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

	public UseItemOnBlockEvent(UseOnContext context, UsePhase usePhase) {
		level = context.getLevel();
		player = context.getPlayer();
		hand = context.getHand();
		heldItem = context.getItemInHand();
		pos = context.getClickedPos();
		face = context.getClickedFace();
		this.context = context;
		this.usePhase = usePhase;
	}

	@Nullable
	public Player getPlayer() {
		return player;
	}

	public InteractionHand getHand() {
		return hand;
	}

	public ItemStack getItemStack() {
		return heldItem;
	}

	public BlockPos getPos() {
		return pos;
	}

	@Nullable
	public Direction getFace() {
		return face;
	}

	public Level getLevel() {
		return level;
	}

	public UseOnContext getUseOnContext() {
		return context;
	}

	public UsePhase getUsePhase() {
		return usePhase;
	}

	public void cancelWithResult(ItemInteractionResult result) {
		setCancellationResult(result);
		setCanceled(true);
	}

	public ItemInteractionResult getCancellationResult() {
		return cancellationResult;
	}

	public void setCancellationResult(ItemInteractionResult cancellationResult) {
		this.cancellationResult = cancellationResult;
	}

	public enum UsePhase {
		ITEM_BEFORE_BLOCK,
		BLOCK,
		ITEM_AFTER_BLOCK
	}
}

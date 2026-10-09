package net.geforcemods.securitycraft.fabricmixin.events.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.geforcemods.securitycraft.fabric.event.PlayerInteractEvent.RightClickBlock;
import net.geforcemods.securitycraft.fabric.util.TriState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fires SecurityCraft's PlayerInteractEvent.RightClickBlock stand-in on the client at the start of
 * MultiPlayerGameMode#performUseItemOn, like NeoForge, and applies its result the same way as on the server, so that the
 * client's prediction matches the server. performUseItemOn runs inside the prediction of useItemOn, so the use packet is
 * still sent to the server when the event is canceled, like on NeoForge.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Shadow
	private GameType localPlayerMode;

	@Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true)
	private void securitycraft$fireRightClickBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = EventHooks.onRightClickBlock(player, hand, hitResult.getBlockPos(), hitResult);

		eventRef.set(event);

		if (event.isCanceled())
			cir.setReturnValue(event.getCancellationResult());
		else if (localPlayerMode != GameType.SPECTATOR && event.getUseItem() != TriState.FALSE) {
			InteractionResult result = EventHooks.onItemUseFirst(player.getItemInHand(hand), new UseOnContext(player, hand, hitResult));

			if (result != InteractionResult.PASS)
				cir.setReturnValue(result);
		}
	}

	/**
	 * useBlock TRUE: the block is used even if the player is sneaking with an item in their hand.
	 */
	@ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"))
	private boolean securitycraft$applyUseBlockTrue(boolean isSecondaryUseActive, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = eventRef.get();

		return isSecondaryUseActive && (event == null || !event.getUseBlock().isTrue());
	}

	/**
	 * useBlock FALSE: the block is not used, see ServerPlayerGameModeMixin.
	 */
	@WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;"))
	private ItemInteractionResult securitycraft$applyUseBlockFalse(BlockState state, ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hitResult, Operation<ItemInteractionResult> original, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = eventRef.get();

		if (event != null && event.getUseBlock().isFalse())
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

		return original.call(state, stack, level, player, hand, hitResult);
	}

	/**
	 * useItem TRUE: the item is used even if the stack is empty or on cooldown. useItem FALSE: the item is not used and PASS
	 * is returned.
	 */
	@ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 0), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z")))
	private boolean securitycraft$applyUseItem(boolean isEmpty, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = eventRef.get();

		if (event != null) {
			if (event.getUseItem().isTrue())
				return false;
			else if (event.getUseItem().isFalse())
				return true;
		}

		return isEmpty;
	}

	@ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;isOnCooldown(Lnet/minecraft/world/item/Item;)Z"))
	private boolean securitycraft$applyUseItemTrue(boolean isOnCooldown, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = eventRef.get();

		return isOnCooldown && (event == null || !event.getUseItem().isTrue());
	}
}

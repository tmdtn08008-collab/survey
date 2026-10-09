package net.geforcemods.securitycraft.fabricmixin.events;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.geforcemods.securitycraft.fabric.event.PlayerInteractEvent.LeftClickBlock;
import net.geforcemods.securitycraft.fabric.event.PlayerInteractEvent.RightClickBlock;
import net.geforcemods.securitycraft.fabric.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fires SecurityCraft's PlayerInteractEvent.RightClickBlock, PlayerInteractEvent.LeftClickBlock and PlayerEvent.HarvestCheck
 * stand-ins on the server, at the places NeoForge patched into ServerPlayerGameMode, and applies the results like NeoForge
 * does.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow
	protected ServerLevel level;
	@Shadow
	@Final
	protected ServerPlayer player;
	@Shadow
	private GameType gameModeForPlayer;

	/**
	 * Fires RightClickBlock after the check whether the block is enabled and before the spectator check, like NeoForge. A
	 * canceled event ends the interaction with its cancellation result. Afterwards, NeoForge's onItemUseFirst is called for
	 * non-spectators, unless the event denied using the item.
	 */
	@Inject(method = "useItemOn", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;gameModeForPlayer:Lnet/minecraft/world/level/GameType;", opcode = Opcodes.GETFIELD, ordinal = 0), cancellable = true)
	private void securitycraft$fireRightClickBlock(ServerPlayer serverPlayer, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = EventHooks.onRightClickBlock(serverPlayer, hand, hitResult.getBlockPos(), hitResult);

		eventRef.set(event);

		if (event.isCanceled())
			cir.setReturnValue(event.getCancellationResult());
		else if (gameModeForPlayer != GameType.SPECTATOR && event.getUseItem() != TriState.FALSE) {
			InteractionResult result = EventHooks.onItemUseFirst(stack, new UseOnContext(serverPlayer, hand, hitResult));

			if (result != InteractionResult.PASS)
				cir.setReturnValue(result);
		}
	}

	/**
	 * useBlock TRUE: the block is used even if the player is sneaking with an item in their hand.
	 */
	//PORT-NOTE: NeoForge also lets sneaking players use the block if both held items return true from IItemExtension#doesSneakBypassUse. No SecurityCraft item overrides it, so that is not ported
	@ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
	private boolean securitycraft$applyUseBlockTrue(boolean isSecondaryUseActive, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = eventRef.get();

		return isSecondaryUseActive && (event == null || !event.getUseBlock().isTrue());
	}

	/**
	 * useBlock FALSE: the block is not used. Returning SKIP_DEFAULT_BLOCK_INTERACTION skips both BlockState#useItemOn and
	 * BlockState#useWithoutItem, so vanilla continues with using the item, exactly as if the block use branch was skipped.
	 */
	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;"))
	private ItemInteractionResult securitycraft$applyUseBlockFalse(BlockState state, ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hitResult, Operation<ItemInteractionResult> original, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = eventRef.get();

		if (event != null && event.getUseBlock().isFalse())
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

		return original.call(state, stack, level, player, hand, hitResult);
	}

	/**
	 * useItem TRUE: the item is used even if the stack is empty or on cooldown. useItem FALSE: the item is not used and PASS
	 * is returned (the else branch of the vanilla check).
	 */
	@ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 0), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z")))
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

	@ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;isOnCooldown(Lnet/minecraft/world/item/Item;)Z"))
	private boolean securitycraft$applyUseItemTrue(boolean isOnCooldown, @Share("rightClickBlock") LocalRef<RightClickBlock> eventRef) {
		RightClickBlock event = eventRef.get();

		return isOnCooldown && (event == null || !event.getUseItem().isTrue());
	}

	/**
	 * Fires LeftClickBlock at the start of handleBlockBreakAction for every action, like NeoForge. A canceled event stops the
	 * action from being processed.
	 */
	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
	private void securitycraft$fireLeftClickBlock(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face, int maxBuildHeight, int sequence, CallbackInfo ci) {
		if (EventHooks.onLeftClickBlock(player, pos, face, LeftClickBlock.Action.convert(action)).isCanceled()) {
			//NeoForge only returns here. Additionally, the client is told about the actual block, in case it predicted something different
			if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
				player.connection.send(new ClientboundBlockUpdatePacket(level, pos));

				BlockEntity be = level.getBlockEntity(pos);

				if (be != null) {
					Packet<ClientGamePacketListener> updatePacket = be.getUpdatePacket();

					if (updatePacket != null)
						player.connection.send(updatePacket);
				}
			}

			ci.cancel();
		}
	}

	/**
	 * NeoForge replaces this check with BlockState#canHarvestBlock, which fires PlayerEvent.HarvestCheck. Vanilla checks this
	 * after the block has been removed, so the block entity captured before the removal is handed to the event.
	 */
	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean securitycraft$doPlayerHarvestCheck(ServerPlayer serverPlayer, BlockState state, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos, @Local BlockEntity blockEntity) {
		return EventHooks.doPlayerHarvestCheck(serverPlayer, state, level, pos, original.call(serverPlayer, state), true, blockEntity);
	}
}

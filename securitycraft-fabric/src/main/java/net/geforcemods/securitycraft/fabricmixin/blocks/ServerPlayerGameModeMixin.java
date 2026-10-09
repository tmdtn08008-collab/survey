package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.PlayerDestroyBlockHook;
import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Calls {@link PlayerDestroyBlockHook#onDestroyedByPlayer} instead of removing the block directly when a player breaks a
 * SecurityCraft block that implements it, like NeoForge's patched ServerPlayerGameMode#destroyBlock does (mines and
 * claymores explode, the cage trap disassembles its bars). Vanilla blocks are removed exactly like in vanilla.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow
	@Final
	protected ServerPlayer player;

	@Shadow
	public abstract boolean isCreative();

	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private boolean securitycraft$callOnDestroyedByPlayer(ServerLevel level, BlockPos pos, boolean isMoving, Operation<Boolean> original, @Local(ordinal = 1) BlockState stateAfterPlayerWillDestroy) {
		if (stateAfterPlayerWillDestroy.getBlock() instanceof PlayerDestroyBlockHook hook) {
			//like NeoForge, the harvest check happens while the block is still in the level, and is always false in creative mode
			boolean willHarvest = !isCreative() && EventHooks.doPlayerHarvestCheck(player, stateAfterPlayerWillDestroy, level, pos);

			return hook.onDestroyedByPlayer(stateAfterPlayerWillDestroy, level, pos, player, willHarvest, level.getFluidState(pos));
		}

		return original.call(level, pos, isMoving);
	}
}

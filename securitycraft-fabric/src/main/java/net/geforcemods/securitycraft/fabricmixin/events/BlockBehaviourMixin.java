package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge fires PlayerEvent.HarvestCheck here (EventHooks#doPlayerHarvestCheck) to decide whether the mining speed is
 * divided by 30 or by 100.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
	@WrapOperation(method = "getDestroyProgress(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean securitycraft$doPlayerHarvestCheck(Player player, BlockState state, Operation<Boolean> original, @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos) {
		return EventHooks.doPlayerHarvestCheck(player, state, level, pos, original.call(player, state), false, null);
	}
}

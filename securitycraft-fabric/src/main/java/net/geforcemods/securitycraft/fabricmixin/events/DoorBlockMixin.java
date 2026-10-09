package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge replaces this check with Player#hasCorrectToolForDrops(BlockState, Level, BlockPos), which fires
 * PlayerEvent.HarvestCheck. It decides whether breaking the top half of a door drops the door. This matters for
 * SecurityCraft's keypad and scanner doors, which extend DoorBlock.
 */
@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {
	@WrapOperation(method = "playerWillDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean securitycraft$doPlayerHarvestCheck(Player player, BlockState state, Operation<Boolean> original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
		return EventHooks.doPlayerHarvestCheck(player, state, level, pos, original.call(player, state), false, null);
	}
}

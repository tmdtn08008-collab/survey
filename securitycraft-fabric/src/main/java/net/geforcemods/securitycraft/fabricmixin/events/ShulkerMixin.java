package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;

/**
 * Fires SecurityCraft's EntityTeleportEvent.EnderEntity stand-in when a shulker found a position to teleport to, like
 * NeoForge. A canceled event makes the shulker try the next position, as if it could not attach to the found one.
 */
@Mixin(Shulker.class)
public abstract class ShulkerMixin {
	//PORT-NOTE: NeoForge also teleports to a target changed by the event. SecurityCraft's handler only cancels, so the target is not replaced here
	@WrapOperation(method = "teleportSomewhere", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Shulker;findAttachableSurface(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"))
	private Direction securitycraft$fireEnderTeleport(Shulker shulker, BlockPos pos, Operation<Direction> original) {
		Direction direction = original.call(shulker, pos);

		if (direction != null && EventHooks.onEnderTeleport(shulker, pos.getX(), pos.getY(), pos.getZ()).isCanceled())
			return null;

		return direction;
	}
}

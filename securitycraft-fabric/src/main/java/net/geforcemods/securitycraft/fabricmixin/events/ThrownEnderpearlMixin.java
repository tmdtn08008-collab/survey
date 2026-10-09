package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;

/**
 * Fires SecurityCraft's EntityTeleportEvent.EnderPearl stand-in before a player is teleported by their ender pearl, at the
 * same place as NeoForge. A canceled event skips the endermite spawn, the teleport, the fall damage and the sound, like on
 * NeoForge. The pearl is discarded either way.
 */
@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
	//PORT-NOTE: NeoForge also uses a target and an attack damage changed by the event. SecurityCraft's handler only cancels, so they are not replaced here
	@WrapOperation(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isAcceptingMessages()Z"))
	private boolean securitycraft$fireEnderPearlTeleport(ServerGamePacketListenerImpl connection, Operation<Boolean> original, @Local(argsOnly = true) HitResult hitResult) {
		if (!original.call(connection))
			return false;

		ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;

		return !EventHooks.onEnderPearlLand(connection.player, pearl.getX(), pearl.getY(), pearl.getZ(), pearl, 5.0F, hitResult).isCanceled();
	}
}

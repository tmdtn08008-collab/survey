package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.event.EntityTeleportEvent;
import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * Fires SecurityCraft's EntityTeleportEvent.EnderEntity stand-in before an enderman teleports, like NeoForge. A canceled
 * event makes the teleport fail.
 */
@Mixin(EnderMan.class)
public abstract class EnderManMixin {
	@WrapOperation(method = "teleport(DDD)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/EnderMan;randomTeleport(DDDZ)Z"))
	private boolean securitycraft$fireEnderTeleport(EnderMan enderMan, double x, double y, double z, boolean broadcastTeleport, Operation<Boolean> original) {
		EntityTeleportEvent.EnderEntity event = EventHooks.onEnderTeleport(enderMan, x, y, z);

		if (event.isCanceled())
			return false;

		return original.call(enderMan, event.getTargetX(), event.getTargetY(), event.getTargetZ(), broadcastTeleport);
	}
}

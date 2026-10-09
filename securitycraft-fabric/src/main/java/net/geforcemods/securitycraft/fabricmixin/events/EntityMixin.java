package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.world.entity.Entity;

/**
 * Fires SecurityCraft's EntityMountEvent stand-in when an entity dismounts, where NeoForge fires EntityMountEvent (on both
 * sides). Canceling it keeps the entity mounted (LivingEntity#stopRiding then sees that the vehicle did not change).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Shadow
	private Entity vehicle;

	//PORT-NOTE: NeoForge also fires EntityMountEvent when an entity starts riding (Entity#startRiding). SecurityCraft's handler only acts on dismounting, so it is not fired there
	@Inject(method = "removeVehicle", at = @At("HEAD"), cancellable = true)
	private void securitycraft$fireDismount(CallbackInfo ci) {
		if (vehicle != null && !EventHooks.canMountEntity((Entity) (Object) this, vehicle, false))
			ci.cancel();
	}
}

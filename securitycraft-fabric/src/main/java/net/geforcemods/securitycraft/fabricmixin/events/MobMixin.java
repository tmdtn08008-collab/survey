package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.geforcemods.securitycraft.fabric.event.LivingChangeTargetEvent;
import net.geforcemods.securitycraft.fabric.event.LivingChangeTargetEvent.LivingTargetType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Fires SecurityCraft's LivingChangeTargetEvent stand-in (MOB_TARGET) in Mob#setTarget, like NeoForge. A canceled event keeps
 * the old target.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Shadow
	private LivingEntity target;

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void securitycraft$fireChangeTarget(LivingEntity newTarget, CallbackInfo ci) {
		LivingChangeTargetEvent event = EventHooks.onLivingChangeTarget((Mob) (Object) this, newTarget, LivingTargetType.MOB_TARGET);

		if (event.isCanceled())
			ci.cancel();
		else if (event.getNewAboutToBeSetTarget() != newTarget) {
			target = event.getNewAboutToBeSetTarget();
			ci.cancel();
		}
	}
}

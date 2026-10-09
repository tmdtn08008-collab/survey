package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.geforcemods.securitycraft.fabric.event.LivingChangeTargetEvent;
import net.geforcemods.securitycraft.fabric.event.LivingChangeTargetEvent.LivingTargetType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;

/**
 * Fires SecurityCraft's LivingChangeTargetEvent stand-in (BEHAVIOR_TARGET) where NeoForge does: in the trigger lambda of
 * StartAttacking#create(Predicate, Function), after Mob#canAttack allowed the target and before the attack target memory is
 * set. A canceled event makes the behavior not start, which is what NeoForge does. method_47123 is that lambda; lambdas
 * have no Mojang name, so their intermediary name is used in development and production alike.
 */
@Mixin(StartAttacking.class)
public abstract class StartAttackingMixin {
	@WrapOperation(method = "method_47123", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z"))
	private static boolean securitycraft$fireChangeTarget(Mob mob, LivingEntity target, Operation<Boolean> original) {
		if (!original.call(mob, target))
			return false;

		LivingChangeTargetEvent event = EventHooks.onLivingChangeTarget(mob, target, LivingTargetType.BEHAVIOR_TARGET);

		//PORT-NOTE: NeoForge would set a target changed by the event as the attack target. SecurityCraft's handler only cancels, so the target is not replaced here
		return !event.isCanceled() && event.getNewAboutToBeSetTarget() != null;
	}
}

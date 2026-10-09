package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fires SecurityCraft's LivingDamageEvent.Post stand-in at the end of LivingEntity#actuallyHurt when the entity is not
 * invulnerable to the damage, like NeoForge. Unlike Fabric's ServerLivingEntityEvents.AFTER_DAMAGE, this also fires for
 * lethal damage.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "actuallyHurt", at = @At("TAIL"))
	private void securitycraft$fireLivingDamagePost(DamageSource source, float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (!self.isInvulnerableTo(source))
			EventHooks.onLivingDamagePost(self, source);
	}
}

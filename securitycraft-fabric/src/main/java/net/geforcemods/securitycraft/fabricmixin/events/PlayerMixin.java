package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Player#actuallyHurt does not call the LivingEntity version, so NeoForge fires LivingDamageEvent.Post at its end as well.
 * See LivingEntityMixin.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	// RETURN, not TAIL: actuallyHurt also returns early when the damage left after absorption is 0, and NeoForge fires
	// LivingDamageEvent.Post in that case too
	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	private void securitycraft$fireLivingDamagePost(DamageSource source, float amount, CallbackInfo ci) {
		Player self = (Player) (Object) this;

		if (!self.isInvulnerableTo(source))
			EventHooks.onLivingDamagePost(self, source);
	}
}

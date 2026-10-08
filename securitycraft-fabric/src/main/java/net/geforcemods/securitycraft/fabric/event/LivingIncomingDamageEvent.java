package net.geforcemods.securitycraft.fabric.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fabric stand-in for NeoForge's LivingIncomingDamageEvent, created from Fabric's ServerLivingEntityEvents.ALLOW_DAMAGE,
 * which fires at the same place in LivingEntity#hurt. Canceling it makes the entity take no damage.
 */
public class LivingIncomingDamageEvent extends CancellableEvent {
	private final LivingEntity entity;
	private final DamageSource source;
	private final float amount;

	public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
		this.entity = entity;
		this.source = source;
		this.amount = amount;
	}

	public LivingEntity getEntity() {
		return entity;
	}

	public DamageSource getSource() {
		return source;
	}

	public float getAmount() {
		return amount;
	}
}

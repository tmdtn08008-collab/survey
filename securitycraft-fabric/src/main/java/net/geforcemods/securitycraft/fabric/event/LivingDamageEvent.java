package net.geforcemods.securitycraft.fabric.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fabric stand-ins for NeoForge's LivingDamageEvent subclasses that SecurityCraft listens to.
 */
public abstract class LivingDamageEvent {
	private final LivingEntity entity;
	private final DamageSource source;

	protected LivingDamageEvent(LivingEntity entity, DamageSource source) {
		this.entity = entity;
		this.source = source;
	}

	public LivingEntity getEntity() {
		return entity;
	}

	public DamageSource getSource() {
		return source;
	}

	/**
	 * Fired on the server at the end of LivingEntity#actuallyHurt and Player#actuallyHurt whenever the entity is not
	 * invulnerable to the damage source, exactly like NeoForge. Unlike Fabric's ServerLivingEntityEvents.AFTER_DAMAGE, this
	 * also fires for lethal damage, and it fires for fully shield-blocked damage outside of the invulnerability frames (in
	 * which case vanilla calls actuallyHurt with 0 damage).
	 */
	public static class Post extends LivingDamageEvent {
		public Post(LivingEntity entity, DamageSource source) {
			super(entity, source);
		}
	}
}

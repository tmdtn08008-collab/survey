package net.geforcemods.securitycraft.fabric.event;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;

/**
 * Fabric stand-in for NeoForge's LivingChangeTargetEvent, fired from Mob#setTarget ({@link LivingTargetType#MOB_TARGET})
 * and from the StartAttacking brain behavior ({@link LivingTargetType#BEHAVIOR_TARGET}). Canceling it keeps the old
 * target.
 */
public class LivingChangeTargetEvent extends CancellableEvent {
	private final LivingEntity entity;
	private final LivingTargetType targetType;
	@Nullable
	private final LivingEntity originalTarget;
	@Nullable
	private LivingEntity newTarget;

	public LivingChangeTargetEvent(LivingEntity entity, @Nullable LivingEntity originalTarget, LivingTargetType targetType) {
		this.entity = entity;
		this.originalTarget = originalTarget;
		this.newTarget = originalTarget;
		this.targetType = targetType;
	}

	public LivingEntity getEntity() {
		return entity;
	}

	@Nullable
	public LivingEntity getNewAboutToBeSetTarget() {
		return newTarget;
	}

	public void setNewAboutToBeSetTarget(@Nullable LivingEntity newTarget) {
		this.newTarget = newTarget;
	}

	public LivingTargetType getTargetType() {
		return targetType;
	}

	@Nullable
	public LivingEntity getOriginalAboutToBeSetTarget() {
		return originalTarget;
	}

	public enum LivingTargetType {
		MOB_TARGET,
		BEHAVIOR_TARGET
	}
}

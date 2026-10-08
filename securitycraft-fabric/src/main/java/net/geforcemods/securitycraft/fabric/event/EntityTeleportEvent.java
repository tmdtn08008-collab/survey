package net.geforcemods.securitycraft.fabric.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fabric stand-in for NeoForge's EntityTeleportEvent. Instances are created by SecurityCraft's own mixins at the vanilla
 * teleport sites and handed to SCEventHandler#onEntityTeleport.
 */
public class EntityTeleportEvent {
	private final Entity entity;
	private double targetX;
	private double targetY;
	private double targetZ;
	private boolean canceled;

	public EntityTeleportEvent(Entity entity, double targetX, double targetY, double targetZ) {
		this.entity = entity;
		this.targetX = targetX;
		this.targetY = targetY;
		this.targetZ = targetZ;
	}

	public Entity getEntity() {
		return entity;
	}

	public double getTargetX() {
		return targetX;
	}

	public void setTargetX(double targetX) {
		this.targetX = targetX;
	}

	public double getTargetY() {
		return targetY;
	}

	public void setTargetY(double targetY) {
		this.targetY = targetY;
	}

	public double getTargetZ() {
		return targetZ;
	}

	public void setTargetZ(double targetZ) {
		this.targetZ = targetZ;
	}

	public Vec3 getTarget() {
		return new Vec3(targetX, targetY, targetZ);
	}

	public double getPrevX() {
		return entity.getX();
	}

	public double getPrevY() {
		return entity.getY();
	}

	public double getPrevZ() {
		return entity.getZ();
	}

	public Vec3 getPrev() {
		return entity.position();
	}

	public boolean isCanceled() {
		return canceled;
	}

	public void setCanceled(boolean canceled) {
		this.canceled = canceled;
	}

	public static class TeleportCommand extends EntityTeleportEvent {
		public TeleportCommand(Entity entity, double targetX, double targetY, double targetZ) {
			super(entity, targetX, targetY, targetZ);
		}
	}

	public static class SpreadPlayersCommand extends EntityTeleportEvent {
		public SpreadPlayersCommand(Entity entity, double targetX, double targetY, double targetZ) {
			super(entity, targetX, targetY, targetZ);
		}
	}

	public static class EnderEntity extends EntityTeleportEvent {
		private final LivingEntity entityLiving;

		public EnderEntity(LivingEntity entity, double targetX, double targetY, double targetZ) {
			super(entity, targetX, targetY, targetZ);
			entityLiving = entity;
		}

		public LivingEntity getEntityLiving() {
			return entityLiving;
		}
	}

	public static class EnderPearl extends EntityTeleportEvent {
		private final ServerPlayer player;
		private final ThrownEnderpearl pearlEntity;
		private float attackDamage;
		private final HitResult hitResult;

		public EnderPearl(ServerPlayer entity, double targetX, double targetY, double targetZ, ThrownEnderpearl pearlEntity, float attackDamage, HitResult hitResult) {
			super(entity, targetX, targetY, targetZ);
			player = entity;
			this.pearlEntity = pearlEntity;
			this.attackDamage = attackDamage;
			this.hitResult = hitResult;
		}

		public ThrownEnderpearl getPearlEntity() {
			return pearlEntity;
		}

		public ServerPlayer getPlayer() {
			return player;
		}

		public HitResult getHitResult() {
			return hitResult;
		}

		public float getAttackDamage() {
			return attackDamage;
		}

		public void setAttackDamage(float attackDamage) {
			this.attackDamage = attackDamage;
		}
	}

	public static class ChorusFruit extends EntityTeleportEvent {
		private final LivingEntity entityLiving;

		public ChorusFruit(LivingEntity entity, double targetX, double targetY, double targetZ) {
			super(entity, targetX, targetY, targetZ);
			entityLiving = entity;
		}

		public LivingEntity getEntityLiving() {
			return entityLiving;
		}
	}
}

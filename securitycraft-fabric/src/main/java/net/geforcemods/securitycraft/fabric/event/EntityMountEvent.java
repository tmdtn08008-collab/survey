package net.geforcemods.securitycraft.fabric.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Fabric stand-in for NeoForge's EntityMountEvent. SecurityCraft only fires it when an entity dismounts (from
 * Entity#removeVehicle), since that is the only case SecurityCraft listens to. Canceling it keeps the entity mounted.
 */
public class EntityMountEvent extends CancellableEvent {
	private final Entity entityMounting;
	private final Entity entityBeingMounted;
	private final Level level;
	private final boolean isMounting;

	public EntityMountEvent(Entity entityMounting, Entity entityBeingMounted, Level level, boolean isMounting) {
		this.entityMounting = entityMounting;
		this.entityBeingMounted = entityBeingMounted;
		this.level = level;
		this.isMounting = isMounting;
	}

	public Entity getEntity() {
		return entityMounting;
	}

	public boolean isMounting() {
		return isMounting;
	}

	public boolean isDismounting() {
		return !isMounting;
	}

	public Entity getEntityMounting() {
		return entityMounting;
	}

	public Entity getEntityBeingMounted() {
		return entityBeingMounted;
	}

	public Level getLevel() {
		return level;
	}
}

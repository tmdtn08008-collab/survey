package net.geforcemods.securitycraft.api;

import net.minecraft.world.entity.Entity;

/**
 * Defines a callback that the Sentry checks before trying to attack an entity. Call
 *
 * <pre>
 * SecurityCraftAPI.registerSentryAttackTargetCheck(new ClassThatImplementsIAttackTargetCheck());
 * </pre>
 *
 * in the {@link SecurityCraftPlugin#register()} method of a "securitycraft" entrypoint to register this with SecurityCraft (on
 * NeoForge, this was done with an InterModComms message).
 *
 * @author bl4ckscor3
 */
@FunctionalInterface
public interface IAttackTargetCheck {
	/**
	 * Checks if the Sentry is allowed to attack the given entity. Returning false does not guarantee that the Sentry will not
	 * attack this entity.
	 *
	 * @param potentialTarget The entity that the Sentry wants to attack
	 * @return true if the Sentry is allowed to attack this entity
	 */
	public boolean canAttack(Entity potentialTarget);
}

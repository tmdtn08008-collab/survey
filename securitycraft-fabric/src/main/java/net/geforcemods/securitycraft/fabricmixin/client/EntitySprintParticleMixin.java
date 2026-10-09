package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.client.DisguiseParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * NeoForge's Entity patch tells the sprinting particle which block it comes from, so that the particle of a disguised block
 * can look like the disguise. Fabric's particle has no such information, so the position is remembered while the particle
 * is created, see {@link DisguiseParticles}. Only applied on the client, where the particles are visible.
 */
@Mixin(Entity.class)
public class EntitySprintParticleMixin {
	@WrapOperation(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private void securitycraft$rememberSprintParticleSource(Level level, ParticleOptions particle, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, Operation<Void> original, @Local(ordinal = 0) BlockPos sourcePos) {
		if (!level.isClientSide) {
			original.call(level, particle, x, y, z, xSpeed, ySpeed, zSpeed);
			return;
		}

		DisguiseParticles.setSourcePos(sourcePos);

		try {
			original.call(level, particle, x, y, z, xSpeed, ySpeed, zSpeed);
		}
		finally {
			DisguiseParticles.setSourcePos(null);
		}
	}
}

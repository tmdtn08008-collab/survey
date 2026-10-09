package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.client.DisguiseParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gives block particles of disguised blocks the texture of the disguise, like NeoForge's TerrainParticle#updateSprite. All
 * terrain particles, including the 8-argument constructor's, go through this constructor.
 */
@Mixin(TerrainParticle.class)
public class TerrainParticleMixin {
	@WrapOperation(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;getParticleIcon(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite securitycraft$useDisguisedParticleIcon(BlockModelShaper modelShaper, BlockState state, Operation<TextureAtlasSprite> original, @Local(argsOnly = true) ClientLevel level, @Local(argsOnly = true) BlockPos pos) {
		return DisguiseParticles.getParticleIcon(modelShaper, state, level, pos, original.call(modelShaper, state));
	}
}

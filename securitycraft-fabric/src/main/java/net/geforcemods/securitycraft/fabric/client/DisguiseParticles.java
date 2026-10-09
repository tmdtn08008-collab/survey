package net.geforcemods.securitycraft.fabric.client;

import net.geforcemods.securitycraft.models.DisguisableDynamicBakedModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes block particles (breaking, hitting and sprinting) and the in-wall screen overlay of disguised blocks look like the
 * disguise. Replaces NeoForge's BlockModelShaper#getTexture(BlockState, Level, BlockPos), which TerrainParticle#updateSprite
 * and ScreenEffectRenderer use to ask the block's model for its particle icon with the model data of the block entity at
 * the block's position.
 */
public final class DisguiseParticles {
	/**
	 * The position of the block a block particle is currently being created for, if vanilla does not tell the particle
	 * itself (sprinting particles, see EntitySprintParticleMixin). Only accessed on the client thread.
	 */
	private static BlockPos sourcePos;

	private DisguiseParticles() {}

	public static void setSourcePos(BlockPos pos) {
		sourcePos = pos;
	}

	/**
	 * @param pos The position the particle was created for
	 * @param original The vanilla particle icon lookup
	 * @return The particle icon of the block's disguise if the block at the source position is disguised, the vanilla particle
	 *         icon otherwise
	 */
	public static TextureAtlasSprite getParticleIcon(BlockModelShaper modelShaper, BlockState state, ClientLevel level, BlockPos pos, TextureAtlasSprite original) {
		return getTexture(modelShaper, state, level, sourcePos != null ? sourcePos : pos, original);
	}

	/**
	 * @param pos The position of the block
	 * @param original The vanilla particle icon lookup
	 * @return The particle icon of the block's disguise if the block at the given position is disguised, the vanilla particle
	 *         icon otherwise
	 */
	public static TextureAtlasSprite getTexture(BlockModelShaper modelShaper, BlockState state, BlockAndTintGetter level, BlockPos pos, TextureAtlasSprite original) {
		BakedModel model = modelShaper.getBlockModel(state);

		if (model instanceof DisguisableDynamicBakedModel disguisableModel && level != null && pos != null)
			return disguisableModel.getParticleIcon(DisguisableDynamicBakedModel.getModelData(level, pos));

		return original;
	}
}

package net.geforcemods.securitycraft.models;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;

public class BlockMineModel implements BakedModel {
	private final BakedModel defaultModel;
	private final BakedModel guiModel;

	public BlockMineModel(BakedModel defaultModel, BakedModel guiModel) {
		this.defaultModel = defaultModel;
		this.guiModel = guiModel;
	}

	/**
	 * PORT-NOTE: Replaces the override of NeoForge's IBakedModelExtension#applyTransform. SecurityCraft's ItemRenderer mixin
	 * swaps this model for the returned one before vanilla applies the model's transforms and renders its quads, which is what
	 * applyTransform did on NeoForge (apply the chosen model's transforms and render it).
	 *
	 * @param displayContext The context the item is rendered in
	 * @return The model to render the mine with in the given context
	 */
	public BakedModel getModelFor(ItemDisplayContext displayContext) {
		if (displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
			return guiModel;
		else
			return defaultModel;
	}

	@Override
	public ItemTransforms getTransforms() {
		return defaultModel == null ? ItemTransforms.NO_TRANSFORMS : defaultModel.getTransforms();
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
		return defaultModel == null ? new ArrayList<>() : defaultModel.getQuads(state, side, rand);
	}

	@Override
	public boolean useAmbientOcclusion() {
		return defaultModel == null || defaultModel.useAmbientOcclusion();
	}

	@Override
	public boolean isGui3d() {
		return defaultModel != null && defaultModel.isGui3d();
	}

	@Override
	public boolean usesBlockLight() {
		return defaultModel.usesBlockLight();
	}

	@Override
	public boolean isCustomRenderer() {
		return defaultModel != null && defaultModel.isCustomRenderer();
	}

	@Override
	public TextureAtlasSprite getParticleIcon() {
		return defaultModel == null ? Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(MissingTextureAtlasSprite.getLocation()) : defaultModel.getParticleIcon();
	}

	@Override
	public ItemOverrides getOverrides() {
		return defaultModel == null ? ItemOverrides.EMPTY : defaultModel.getOverrides();
	}
}

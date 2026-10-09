package net.geforcemods.securitycraft.models;

import java.util.List;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.geforcemods.securitycraft.fabric.model.ModelData;
import net.geforcemods.securitycraft.fabric.model.ModelProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The model of every disguisable block. When the block's block entity has a disguise, the disguised block state's model is
 * rendered in place of the block's own model.
 * <p>
 * PORT-NOTE: On NeoForge this is an IDynamicBakedModel that receives the block entity's ModelData and returns the disguised
 * model's quads per render type. On Fabric it is a FabricBakedModel (Fabric Rendering API, implemented by Indigo and Sodium)
 * that reads the same ModelData from the block entity's render data. The disguised model's quads are put into the render
 * layer of the disguised block (for example translucent for stained glass) instead of the layer of the disguisable block
 * itself, which is what NeoForge's getRenderTypes override does. Without a Fabric Rendering API implementation, disguises are
 * not rendered (ClientHandler#init logs a warning in that case).
 */
public class DisguisableDynamicBakedModel implements BakedModel {
	public static final ModelProperty<BlockState> DISGUISED_STATE = new ModelProperty<>();
	private final BakedModel oldModel;

	public DisguisableDynamicBakedModel(BakedModel oldModel) {
		this.oldModel = oldModel;
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
		ModelData modelData = getModelData(level, pos);
		BlockState disguisedState = modelData.get(DISGUISED_STATE);

		if (disguisedState != null) {
			Block block = disguisedState.getBlock();

			if (block != Blocks.AIR) {
				BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(disguisedState);

				if (model != null && model != this) {
					emitDisguisedQuads(model, disguisedState, level, pos, randomSupplier, context);
					return;
				}
			}
		}

		emitOldQuads(level, state, pos, randomSupplier, context, modelData);
	}

	/**
	 * Emits the quads of the disguised block's model, with each quad that does not choose a render layer itself put into the
	 * disguised block's render layer
	 */
	private static void emitDisguisedQuads(BakedModel model, BlockState disguisedState, BlockAndTintGetter level, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
		Renderer renderer = RendererAccess.INSTANCE.getRenderer();
		BlendMode disguisedBlendMode = BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(disguisedState));

		if (renderer == null || disguisedBlendMode == BlendMode.DEFAULT) {
			model.emitBlockQuads(level, disguisedState, pos, randomSupplier, context);
			return;
		}

		MaterialFinder finder = renderer.materialFinder();

		context.pushTransform(quad -> {
			if (quad.material().blendMode() == BlendMode.DEFAULT)
				quad.material(finder.copyFrom(quad.material()).blendMode(disguisedBlendMode).find());

			return true;
		});
		model.emitBlockQuads(level, disguisedState, pos, randomSupplier, context);
		context.popTransform();
	}

	/**
	 * Emits the quads of this block's own model, used when the block is not disguised
	 */
	public void emitOldQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context, ModelData modelData) {
		oldModel.emitBlockQuads(level, state, pos, randomSupplier, context);
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
		return getOldQuads(state, side, rand);
	}

	public List<BakedQuad> getOldQuads(BlockState state, Direction side, RandomSource rand) {
		return oldModel.getQuads(state, side, rand);
	}

	/**
	 * Replaces NeoForge's IBakedModelExtension#getParticleIcon(ModelData). Called by SecurityCraft's TerrainParticle mixin, so
	 * that breaking and hitting a disguised block shows particles of the disguise.
	 */
	public TextureAtlasSprite getParticleIcon(ModelData modelData) {
		BlockState disguisedState = modelData.get(DISGUISED_STATE);

		if (disguisedState != null) {
			Block block = disguisedState.getBlock();

			if (block != Blocks.AIR) {
				BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(disguisedState);

				if (model != null && model != this)
					return model instanceof DisguisableDynamicBakedModel disguisableModel ? disguisableModel.getParticleIcon(modelData) : model.getParticleIcon();
			}
		}

		return getOldParticleIcon(modelData);
	}

	public TextureAtlasSprite getOldParticleIcon(ModelData modelData) {
		return oldModel.getParticleIcon();
	}

	@Override
	public TextureAtlasSprite getParticleIcon() {
		return oldModel.getParticleIcon();
	}

	@Override
	public boolean isGui3d() {
		return false;
	}

	@Override
	public boolean isCustomRenderer() {
		return false;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return true;
	}

	@Override
	public ItemOverrides getOverrides() {
		return null;
	}

	@Override
	public ItemTransforms getTransforms() {
		return ItemTransforms.NO_TRANSFORMS;
	}

	@Override
	public boolean usesBlockLight() {
		return false;
	}

	/**
	 * @return The model data of the block entity at the given position, as provided by its render data
	 */
	public static ModelData getModelData(BlockAndTintGetter level, BlockPos pos) {
		if (level != null && pos != null && level.getBlockEntityRenderData(pos) instanceof ModelData modelData)
			return modelData;

		return ModelData.EMPTY;
	}
}

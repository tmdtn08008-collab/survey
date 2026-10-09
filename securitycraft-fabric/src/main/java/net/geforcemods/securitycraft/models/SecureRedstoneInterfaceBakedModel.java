package net.geforcemods.securitycraft.models;

import java.util.function.Supplier;

import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.geforcemods.securitycraft.fabric.model.ModelData;
import net.geforcemods.securitycraft.fabric.model.ModelProperty;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public class SecureRedstoneInterfaceBakedModel extends DisguisableDynamicBakedModel {
	public static final ModelProperty<Boolean> POWERED = new ModelProperty<>();
	private final BakedModel poweredModel;

	public SecureRedstoneInterfaceBakedModel(BakedModel poweredModel, BakedModel oldModel) {
		super(oldModel);
		this.poweredModel = poweredModel;
	}

	@Override
	public void emitOldQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context, ModelData modelData) {
		Boolean powered = modelData.get(POWERED);

		if (powered != null && powered && poweredModel != null) {
			poweredModel.emitBlockQuads(level, state, pos, randomSupplier, context);
			return;
		}

		super.emitOldQuads(level, state, pos, randomSupplier, context, modelData);
	}

	@Override
	public TextureAtlasSprite getOldParticleIcon(ModelData modelData) {
		Boolean powered = modelData.get(POWERED);

		if (powered != null && powered && poweredModel != null)
			return poweredModel.getParticleIcon();

		return super.getOldParticleIcon(modelData);
	}
}

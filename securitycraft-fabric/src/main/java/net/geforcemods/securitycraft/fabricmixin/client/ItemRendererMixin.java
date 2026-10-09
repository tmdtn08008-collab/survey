package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.models.BlockMineModel;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Replaces NeoForge's IBakedModelExtension#applyTransform hook (ClientHooks#handleCameraTransforms in ItemRenderer#render)
 * for {@link BlockMineModel}: the mine is rendered with the model of the block it looks like, or with its own model in the
 * GUI and first person, including that model's transforms.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
	@ModifyVariable(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("HEAD"), argsOnly = true)
	private BakedModel securitycraft$selectBlockMineModel(BakedModel model, @Local(argsOnly = true) ItemDisplayContext displayContext) {
		if (model instanceof BlockMineModel blockMineModel) {
			BakedModel selectedModel = blockMineModel.getModelFor(displayContext);

			if (selectedModel != null)
				return selectedModel;
		}

		return model;
	}
}

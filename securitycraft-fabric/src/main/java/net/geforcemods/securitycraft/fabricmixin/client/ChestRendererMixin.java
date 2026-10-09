package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.renderers.KeypadChestRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * NeoForge's ChestRenderer patch adds an overridable getMaterial method, which {@link KeypadChestRenderer} overrides to use
 * its own textures. This calls it in place of vanilla's texture lookup.
 */
@Mixin(ChestRenderer.class)
public class ChestRendererMixin {
	@WrapOperation(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Sheets;chooseMaterial(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/level/block/state/properties/ChestType;Z)Lnet/minecraft/client/resources/model/Material;"))
	private Material securitycraft$getKeypadChestMaterial(BlockEntity be, ChestType chestType, boolean christmas, Operation<Material> original) {
		if ((Object) this instanceof KeypadChestRenderer keypadChestRenderer && be instanceof ChestBlockEntity chest)
			return keypadChestRenderer.getMaterial(chest, chestType);

		return original.call(be, chestType, christmas);
	}
}

package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import net.geforcemods.securitycraft.ClientHandler;
import net.geforcemods.securitycraft.SCClientEventHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int packedLight);

	/**
	 * Replaces NeoForge's RenderHandEvent (fired per hand from renderHandsWithItems), which SecurityCraft cancels so that no hands
	 * are shown while viewing through a security camera
	 */
	@WrapWithCondition(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private boolean securitycraft$shouldRenderHand(ItemInHandRenderer renderer, AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		return SCClientEventHandler.shouldRenderHand();
	}

	/**
	 * Replaces the taser's IClientItemExtensions#applyForgeHandTransform, which NeoForge calls right before the transforms of the
	 * branch for regular items. When it applies, vanilla's arm transforms are skipped and the item is rendered right away.
	 */
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z", ordinal = 1), cancellable = true)
	private void securitycraft$applyTaserHandTransform(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci, @Local HumanoidArm arm) {
		if (ClientHandler.applyTaserHandTransform(poseStack, stack, swingProgress)) {
			boolean rightArm = arm == HumanoidArm.RIGHT;

			renderItem(player, stack, rightArm ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !rightArm, poseStack, buffer, packedLight);
			poseStack.popPose();
			ci.cancel();
		}
	}
}

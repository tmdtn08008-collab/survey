package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.ClientHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the taser's third person arm pose, which is a NeoForge enum extension of HumanoidModel.ArmPose with a transformer
 * (applied by NeoForge's poseRightArm/poseLeftArm patch). Fabric cannot add arm poses, so the transformer is applied right
 * after vanilla posed the arms, which is where the extended pose would have been applied as well.
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/world/entity/LivingEntity;F)V"))
	private void securitycraft$applyTaserArmPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (ClientHandler.hasTaserArmPose(entity)) {
			HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
			ModelPart leftArm = model.leftArm;
			ModelPart rightArm = model.rightArm;

			leftArm.yRot = 0.5F;
			rightArm.yRot = -0.5F;
			leftArm.xRot = rightArm.xRot = -1.5F;
		}
	}
}

package com.abilitywar.client.anim;

import com.abilitywar.client.fx.FpEffects;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 능력 사용 중(맨손)일 때 1인칭 양팔을 직접 그린다.
 */
public final class FirstPersonRenderer {
    /** 마지막 프레임의 주먹 위치 (손 렌더 공간). */
    public static final Vector3f[] FIST_VIEW = {new Vector3f(0.25f, -0.3f, -0.6f), new Vector3f(-0.25f, -0.3f, -0.6f)};
    public static FpPose lastPose;

    public static boolean shouldTakeOver(ClientPlayerEntity p) {
        if (p == null) return false;
        String key = ((AWPlayer) p).aw$getAbilityKey();
        if (key.isEmpty()) return false;
        if (((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_SHADOW) || ((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_SWARM)) return true;
        return p.getMainHandStack().isEmpty() && !p.isUsingItem();
    }

    public static void render(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player, int light) {
        // 그림자 상태에서는 팔이 보이지 않음
        if (((AWPlayer) player).aw$hasFlag(AWPlayer.FLAG_SWARM)) return;
        if (((AWPlayer) player).aw$hasFlag(AWPlayer.FLAG_SHADOW)) {
            FpEffects.renderHandless(tickDelta, matrices, consumers, player, light);
            consumers.draw();
            return;
        }
        FpPose pose = PlayerAnimator.computeFirstPerson(player, tickDelta);
        lastPose = pose;
        // 시선 관성 (바닐라와 같은 흔들림)
        float h = net.minecraft.util.math.MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
        float i = net.minecraft.util.math.MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - h) * 0.1F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta) - i) * 0.1F));

        PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
        PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
        boolean slim = model.thinArms;
        PlayerAnimator.renderingFirstPersonArms = true;
        try {
            for (int arm = 0; arm < 2; arm++) {
                if (!pose.visible[arm]) continue;
                renderArm(matrices, consumers, light, player, model, arm, slim, pose);
            }
        } finally {
            PlayerAnimator.renderingFirstPersonArms = false;
        }
        FpEffects.render(tickDelta, matrices, consumers, player, light, pose);
        matrices.pop();
        consumers.draw();
    }

    private static void renderArm(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
                                  PlayerEntityModel<AbstractClientPlayerEntity> model, int arm, boolean slim, FpPose pose) {
        boolean right = arm == 0;
        ModelPart part = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        float[] savedA = save(part);
        float[] savedS = save(sleeve);
        boolean visA = part.visible, visS = sleeve.visible;
        part.setPivot(0, 0, 0);
        part.setAngles(0, 0, 0);
        part.xScale = part.yScale = part.zScale = 1;
        sleeve.copyTransform(part);
        part.visible = true;
        sleeve.visible = player.isPartVisible(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);

        float[] p = pose.pos[arm];
        float[] r = pose.rot[arm];
        matrices.push();
        matrices.translate(p[0] / 16f, p[1] / 16f, -p[2] / 16f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(r[1]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(r[0]));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(r[2]));
        // 주먹 위치 기록 (어깨에서 앞으로 0.75)
        Matrix4f m = matrices.peek().getPositionMatrix();
        Vector3f fist = new Vector3f(0, 0, -0.72f);
        m.transformPosition(fist);
        FIST_VIEW[arm].set(fist);
        com.abilitywar.client.fx.dragon.DragonParts.fpClaws(matrices, consumers, player, arm);
        com.abilitywar.client.fx.blood.BloodParts.fpClaws(matrices, consumers, player, arm);
        com.abilitywar.client.fx.cantor.CantorParts.fpBaton(matrices, consumers, player, arm);
        com.abilitywar.client.fx.warlock.WarlockParts.fpFlame(matrices, consumers, player, arm);
        if (arm == 0) com.abilitywar.client.fx.blade.BladeParts.fpSword(matrices, consumers, light, player, pose.rot[AnimClip.FP_ITEM], pose.pos[AnimClip.FP_ITEM]);
        // 팔 모델 방향 맞추기: 모델 +Y(손 방향) -> -Z, 모델 앞면 -> 위
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        float cx = (slim ? 0.5f : 1f) * (right ? 1 : -1);
        matrices.translate(cx / 16f, 2f / 16f, 0);
        part.render(matrices, consumers.getBuffer(RenderLayer.getEntitySolid(player.getSkinTexture())), light, OverlayTexture.DEFAULT_UV);
        if (sleeve.visible) {
            sleeve.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(player.getSkinTexture())), light, OverlayTexture.DEFAULT_UV);
        }
        com.abilitywar.client.fx.dragon.DragonParts.fpArmOverlay(matrices, consumers, light, player, part);
        com.abilitywar.client.fx.blood.BloodParts.fpArmOverlay(matrices, consumers, light, player, part);
        matrices.pop();

        restore(part, savedA);
        restore(sleeve, savedS);
        part.visible = visA;
        sleeve.visible = visS;
    }

    private static float[] save(ModelPart p) {
        return new float[]{p.pivotX, p.pivotY, p.pivotZ, p.pitch, p.yaw, p.roll, p.xScale, p.yScale, p.zScale};
    }

    private static void restore(ModelPart p, float[] s) {
        p.pivotX = s[0];
        p.pivotY = s[1];
        p.pivotZ = s[2];
        p.pitch = s[3];
        p.yaw = s[4];
        p.roll = s[5];
        p.xScale = s[6];
        p.yScale = s[7];
        p.zScale = s[8];
    }

    private FirstPersonRenderer() {
    }
}

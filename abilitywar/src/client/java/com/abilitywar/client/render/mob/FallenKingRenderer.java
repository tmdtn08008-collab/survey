package com.abilitywar.client.render.mob;

import com.abilitywar.mob.FallenKingEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** 타락한 기사왕: 2.4배 크기의 갑옷 기사 + 대검 + 붉은 눈. 공격마다 자세가 바뀜. */
public class FallenKingRenderer extends BipedEntityRenderer<FallenKingEntity, FallenKingRenderer.KnightModel> {
    static final Identifier SKIN = MobRenderers.tex("fallen_king");
    static final RenderLayer EYES = RenderLayer.getEyes(MobRenderers.tex("fallen_king_eyes"));

    public FallenKingRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new KnightModel(ctx.getPart(EntityModelLayers.ZOMBIE)), 1.2f);
        addFeature(new EyesFeatureRenderer<>(this) {
            @Override
            public RenderLayer getEyesTexture() {
                return EYES;
            }
        });
        addFeature(new Sword(this));
    }

    @Override
    protected void scale(FallenKingEntity e, MatrixStack ms, float td) {
        ms.scale(2.35f, 2.35f, 2.35f);
    }

    @Override
    protected void setupTransforms(FallenKingEntity e, MatrixStack ms, float anim, float bodyYaw, float td) {
        super.setupTransforms(e, ms, anim, bodyYaw, td);
        // 회전베기: 몸 전체가 돎
        if (e.anim() == FallenKingEntity.SPIN) {
            float t = e.animTime(td);
            if (t > 5 && t < 20) ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((t - 5) / 15f * 720f));
        }
    }

    @Override
    public Identifier getTexture(FallenKingEntity e) {
        return SKIN;
    }

    public static class KnightModel extends BipedEntityModel<FallenKingEntity> {
        public KnightModel(net.minecraft.client.model.ModelPart root) {
            super(root);
        }

        @Override
        public void setAngles(FallenKingEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, age, headYaw, headPitch);
            float td = age - e.age;
            float t = e.animTime(td);
            // 기본: 대검을 앞으로 비스듬히 듦
            rightArm.pitch = rightArm.pitch * 0.4f - 0.55f;
            leftArm.pitch = leftArm.pitch * 0.5f - 0.2f;
            switch (e.anim()) {
                case FallenKingEntity.SLAM, FallenKingEntity.WAVE -> {
                    int hit = e.anim() == FallenKingEntity.SLAM ? (e.enraged() ? 13 : 16) : 15;
                    float up = MathHelper.clamp(t / (hit - 2f), 0, 1), down = MathHelper.clamp((t - hit + 2) / 3f, 0, 1);
                    float rec = MathHelper.clamp((t - hit - 6) / 8f, 0, 1);
                    float p = MathHelper.lerp(down, MathHelper.lerp(up, -0.55f, -3.0f), -0.6f);
                    p = MathHelper.lerp(rec, p, -0.55f);
                    rightArm.pitch = p;
                    leftArm.pitch = p;
                    rightArm.yaw = -0.3f;
                    leftArm.yaw = 0.3f;
                    body.pitch = MathHelper.lerp(down, -0.2f * up, 0.45f) * (1 - rec);
                    head.pitch += body.pitch * 0.5f;
                }
                case FallenKingEntity.SPIN -> {
                    rightArm.pitch = -1.5f;
                    rightArm.roll = 0.6f;
                    leftArm.pitch = -0.4f;
                    leftArm.roll = -0.8f;
                }
                case FallenKingEntity.CHARGE -> {
                    float lean = MathHelper.clamp((t - 6) / 4f, 0, 1);
                    body.pitch = 0.5f * lean;
                    rightArm.pitch = -0.2f + 0.6f * lean;
                    leftArm.pitch = -1.4f * lean;
                }
                case FallenKingEntity.SUMMON -> {
                    float up = MathHelper.clamp(t / 8f, 0, 1);
                    rightArm.pitch = -2.6f * up;
                    leftArm.pitch = -2.6f * up;
                    rightArm.roll = 0.5f * up;
                    leftArm.roll = -0.5f * up;
                    head.pitch = -0.4f * up;
                }
                default -> {
                }
            }
            hat.copyTransform(head);
        }
    }

    static final class Sword extends FeatureRenderer<FallenKingEntity, KnightModel> {
        Sword(FeatureRendererContext<FallenKingEntity, KnightModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, FallenKingEntity e, float limbAngle, float limbDistance, float td, float anim, float headYaw, float headPitch) {
            ms.push();
            getContextModel().setArmAngle(Arm.RIGHT, ms);
            ms.translate(-0.0625f, 0.62f, -0.02f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(80));
            float glow = e.enraged() ? 0.8f + 0.2f * MathHelper.sin((e.age + td) * 0.3f) : 0.35f;
            MobRenderers.bossSword(ms, vcp, glow);
            ms.pop();
        }
    }
}

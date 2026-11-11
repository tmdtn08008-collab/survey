package com.abilitywar.client.render.mob;

import com.abilitywar.mob.EmberColossusEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** 화염 거인: 거인 골렘 뼈대 (바닐라 철 골렘 모양) 를 1.85배로 + 용암 바위 그림 + 빛나는 틈. */
public class ColossusRenderer extends MobEntityRenderer<EmberColossusEntity, ColossusRenderer.ColossusModel> {
    static final Identifier SKIN = MobRenderers.tex("ember_colossus");
    static final RenderLayer GLOW = RenderLayer.getEyes(MobRenderers.tex("ember_colossus_glow"));

    public ColossusRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ColossusModel(ctx.getPart(EntityModelLayers.IRON_GOLEM)), 2.2f);
        addFeature(new EyesFeatureRenderer<>(this) {
            @Override
            public RenderLayer getEyesTexture() {
                return GLOW;
            }
        });
    }

    @Override
    protected void scale(EmberColossusEntity e, MatrixStack ms, float td) {
        ms.scale(1.85f, 1.85f, 1.85f);
    }

    @Override
    public Identifier getTexture(EmberColossusEntity e) {
        return SKIN;
    }

    public static class ColossusModel extends SinglePartEntityModel<EmberColossusEntity> {
        final ModelPart root, head, rightArm, leftArm, rightLeg, leftLeg;

        public ColossusModel(ModelPart root) {
            this.root = root;
            this.head = root.getChild("head");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        @Override
        public ModelPart getPart() {
            return root;
        }

        @Override
        public void setAngles(EmberColossusEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            head.yaw = headYaw * 0.017453292f;
            head.pitch = headPitch * 0.017453292f;
            rightLeg.pitch = -1.5f * MathHelper.wrap(limbAngle, 13f) * limbDistance;
            leftLeg.pitch = 1.5f * MathHelper.wrap(limbAngle, 13f) * limbDistance;
            rightLeg.yaw = leftLeg.yaw = 0;
            rightArm.pitch = (-0.2f + 1.5f * MathHelper.wrap(limbAngle, 13f)) * limbDistance;
            leftArm.pitch = (-0.2f - 1.5f * MathHelper.wrap(limbAngle, 13f)) * limbDistance;
            rightArm.roll = leftArm.roll = 0;
            float td = age - e.age;
            float t = e.animTime(td);
            switch (e.anim()) {
                case EmberColossusEntity.POUND -> {
                    float up = MathHelper.clamp(t / 14f, 0, 1), down = MathHelper.clamp((t - 14) / 3f, 0, 1);
                    float p = MathHelper.lerp(down, -2.8f * up, -0.5f);
                    rightArm.pitch = p;
                    leftArm.pitch = p;
                    head.pitch = -0.3f * up + 0.4f * down;
                }
                case EmberColossusEntity.METEOR -> {
                    float up = MathHelper.clamp(t / 6f, 0, 1) * (1 - MathHelper.clamp((t - 22) / 8f, 0, 1));
                    rightArm.pitch = -2.9f * up;
                    leftArm.pitch = -2.9f * up;
                    rightArm.roll = 0.4f * up;
                    leftArm.roll = -0.4f * up;
                    head.pitch = -0.6f * up;
                }
                case EmberColossusEntity.THROW -> {
                    float ph = (t % 5) / 5f;
                    rightArm.pitch = MathHelper.lerp(ph, -2.6f, -0.6f);
                }
                case EmberColossusEntity.BREATH -> {
                    head.pitch = -0.2f;
                    rightArm.pitch = -0.8f;
                    leftArm.pitch = -0.8f;
                    rightArm.roll = -0.4f;
                    leftArm.roll = 0.4f;
                }
                default -> {
                }
            }
        }
    }
}

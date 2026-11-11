package com.abilitywar.client.render.mob;

import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.mob.FrostWitchEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/** 서리 마녀: 떠 있는 가녀린 마녀 + 얼음 왕관 + 몸 둘레를 도는 얼음 조각. 주문을 걸 때 두 팔을 듦. */
public class FrostWitchRenderer extends MobEntityRenderer<FrostWitchEntity, PlayerEntityModel<FrostWitchEntity>> {
    static final Identifier SKIN = MobRenderers.tex("frost_witch");

    public FrostWitchRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new WitchModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM)), 0.6f);
        addFeature(new Crown(this));
    }

    @Override
    protected void scale(FrostWitchEntity e, MatrixStack ms, float td) {
        ms.scale(1.32f, 1.32f, 1.32f);
    }

    @Override
    public Identifier getTexture(FrostWitchEntity e) {
        return SKIN;
    }

    static final class WitchModel extends PlayerEntityModel<FrostWitchEntity> {
        WitchModel(net.minecraft.client.model.ModelPart root) {
            super(root, true);
        }

        @Override
        public void setAngles(FrostWitchEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            super.setAngles(e, 0, 0, age, headYaw, headPitch);
            // 떠 있음: 다리는 모으고 살짝 흔들림
            rightLeg.pitch = 0.15f + 0.05f * MathHelper.sin(age * 0.08f);
            leftLeg.pitch = 0.1f + 0.05f * MathHelper.sin(age * 0.08f + 1);
            float idle = MathHelper.sin(age * 0.06f);
            rightArm.pitch = -0.3f + 0.05f * idle;
            leftArm.pitch = -0.3f - 0.05f * idle;
            rightArm.roll = 0.25f;
            leftArm.roll = -0.25f;
            if (e.anim() != 0) {
                float t = e.animTime(age - e.age);
                float up = MathHelper.clamp(t / 6f, 0, 1);
                rightArm.pitch = -2.5f * up - 0.3f * (1 - up);
                leftArm.pitch = -2.5f * up - 0.3f * (1 - up);
                rightArm.roll = 0.25f + 0.35f * up;
                leftArm.roll = -0.25f - 0.35f * up;
                head.pitch = -0.25f * up;
            }
            hat.copyTransform(head);
            rightSleeve.copyTransform(rightArm);
            leftSleeve.copyTransform(leftArm);
            rightPants.copyTransform(rightLeg);
            leftPants.copyTransform(leftLeg);
        }
    }

    static final class Crown extends FeatureRenderer<FrostWitchEntity, PlayerEntityModel<FrostWitchEntity>> {
        Crown(FeatureRendererContext<FrostWitchEntity, PlayerEntityModel<FrostWitchEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, FrostWitchEntity e, float limbAngle, float limbDistance, float td, float anim, float headYaw, float headPitch) {
            float t = e.age + td;
            ms.push();
            getContextModel().head.rotate(ms);
            Matrix4f m = ms.peek().getPositionMatrix();
            VertexConsumer g = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            // 얼음 왕관: 뾰족한 얼음 가시 9개 (가운데가 가장 김)
            for (int i = 0; i < 9; i++) {
                double a = Math.PI * 2 * i / 9;
                float r = 0.29f, h = i == 0 ? 0.42f : (i % 2 == 0 ? 0.26f : 0.18f);
                float x = (float) -Math.sin(a) * r, z = (float) -Math.cos(a) * r;
                float sx = (float) Math.cos(a) * 0.05f, sz = (float) -Math.sin(a) * 0.05f;
                spike(g, m, x, -0.5f, z, sx, sz, h, 0.7f, 0.95f, 1f, 0.85f);
            }
            ms.pop();
            // 몸 둘레를 도는 얼음 조각
            ms.push();
            getContextModel().body.rotate(ms);
            m = ms.peek().getPositionMatrix();
            for (int i = 0; i < 6; i++) {
                double a = t * 0.05 + i * Math.PI / 3;
                float x = (float) Math.cos(a) * 0.75f, z = (float) Math.sin(a) * 0.75f, y = 0.45f + 0.12f * MathHelper.sin(t * 0.1f + i);
                spike(g, m, x, y + 0.12f, z, 0.05f, 0.05f, 0.24f, 0.6f, 0.9f, 1f, 0.7f);
                spike(g, m, x, y + 0.12f, z, 0.05f, 0.05f, -0.16f, 0.6f, 0.9f, 1f, 0.7f);
            }
            ms.pop();
        }

        /** 밑면 (x,y,z) ± (sx, sz), 높이 h (+ 는 위, 모델 공간은 -Y 가 위). */
        static void spike(VertexConsumer vc, Matrix4f m, float x, float y, float z, float sx, float sz, float h, float r, float g, float b, float a) {
            float ty = y - h;
            Draw.vc(vc, m, x - sx, y, z - sz, r, g, b, a * 0.4f);
            Draw.vc(vc, m, x + sx, y, z + sz, r, g, b, a * 0.4f);
            Draw.vc(vc, m, x, ty, z, 1, 1, 1, a);
            Draw.vc(vc, m, x, ty, z, 1, 1, 1, a);
            Draw.vc(vc, m, x - sz, y, z + sx, r, g, b, a * 0.4f);
            Draw.vc(vc, m, x + sz, y, z - sx, r, g, b, a * 0.4f);
            Draw.vc(vc, m, x, ty, z, 1, 1, 1, a);
            Draw.vc(vc, m, x, ty, z, 1, 1, 1, a);
        }
    }
}

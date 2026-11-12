package com.abilitywar.client.render.mob;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.mob.AWMobs;
import com.abilitywar.mob.DireWolfEntity;
import com.abilitywar.mob.FairyEntity;
import com.abilitywar.mob.GolemLike;
import com.abilitywar.mob.MermaidEntity;
import com.abilitywar.mob.WraithEntity;
import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** 2.9 야생 생명체 렌더러: 사람 모양 6 · 유령 (반투명) · 요정 (빛 날개) · 인어 (꼬리 모델) · 흉포한 늑대 · 골렘 둘. 비룡 · 용왕은 BossFx 가 고룡 모델로. */
public final class WildRenderers {
    public static final EntityModelLayer MERMAID_LAYER = new EntityModelLayer(AbilityWar.id("mermaid"), "main");

    public static void init() {
        EntityModelLayerRegistry.registerModelLayer(MERMAID_LAYER, MermaidModel::getTexturedModelData);
        EntityRendererRegistry.register(AWMobs.GOBLIN_LORD, ctx -> new MobRenderers.Biped<>(ctx, "goblin_lord", 1.05f));
        EntityRendererRegistry.register(AWMobs.ORC_LORD, ctx -> new MobRenderers.Biped<>(ctx, "orc_lord", 1.45f));
        EntityRendererRegistry.register(AWMobs.BANDIT, ctx -> new MobRenderers.Biped<>(ctx, "bandit", 1.0f));
        EntityRendererRegistry.register(AWMobs.THIEF, ctx -> new MobRenderers.Biped<>(ctx, "thief", 0.97f));
        EntityRendererRegistry.register(AWMobs.PIRATE, ctx -> new MobRenderers.Biped<>(ctx, "pirate", 1.0f));
        EntityRendererRegistry.register(AWMobs.CULTIST, ctx -> new MobRenderers.Biped<>(ctx, "cultist", 1.0f));
        EntityRendererRegistry.register(AWMobs.WRAITH, WraithRenderer::new);
        EntityRendererRegistry.register(AWMobs.FAIRY, FairyRenderer::new);
        EntityRendererRegistry.register(AWMobs.MERMAID, MermaidRenderer::new);
        EntityRendererRegistry.register(AWMobs.DIRE_WOLF, DireWolfRenderer::new);
        EntityRendererRegistry.register(AWMobs.ANCIENT_GOLEM, ctx -> new GolemRenderer<>(ctx, "ancient_golem", 1.0f));
        EntityRendererRegistry.register(AWMobs.ENHANCED_GOLEM, ctx -> new GolemRenderer<>(ctx, "enhanced_golem", 1.22f));
        EntityRendererRegistry.register(AWMobs.DRAKE, MobRenderers.Invisible::new);
        EntityRendererRegistry.register(AWMobs.DRAGON_KING, MobRenderers.Invisible::new);
    }

    // ================================================================ 유령: 반투명 + 늘 밝음 + 둥실둥실
    static final class WraithRenderer extends MobRenderers.Biped<WraithEntity> {
        WraithRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, "wraith", 1.0f);
        }

        @Nullable
        @Override
        protected RenderLayer getRenderLayer(WraithEntity e, boolean showBody, boolean translucent, boolean showOutline) {
            return RenderLayer.getEntityTranslucent(getTexture(e));
        }

        @Override
        protected int getBlockLight(WraithEntity e, BlockPos pos) {
            return 12;
        }

        @Override
        protected void scale(WraithEntity e, MatrixStack ms, float td) {
            super.scale(e, ms, td);
            ms.translate(0, -0.12 * MathHelper.sin((e.age + td) * 0.1f) - 0.1, 0);
        }

        @Override
        public void render(WraithEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
            getModel().leftArmPose = BipedEntityModel.ArmPose.EMPTY;
            super.render(e, yaw, td, ms, vcp, light);
        }
    }

    // ================================================================ 요정: 작은 사람 + 빛 날개 넷
    static final class FairyRenderer extends MobRenderers.Biped<FairyEntity> {
        FairyRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, "fairy", 0.3f);
        }

        @Override
        protected int getBlockLight(FairyEntity e, BlockPos pos) {
            return 15;
        }

        @Override
        public void render(FairyEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
            super.render(e, yaw, td, ms, vcp, light);
            float body = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
            float t = e.age + td;
            float flap = 30 + 32 * MathHelper.sin(t * 1.4f);
            ms.push();
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - body));
            ms.translate(0, 0.42, 0.06);
            VertexConsumer vc = vcp.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < 2; k++) {
                    ms.push();
                    ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (flap + k * 8)));
                    ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * (k == 0 ? -20 : 25)));
                    Matrix4f m = ms.peek().getPositionMatrix();
                    float w = k == 0 ? 0.32f : 0.22f, h = k == 0 ? 0.22f : 0.16f, y0 = k == 0 ? 0.02f : -0.1f;
                    float r = k == 0 ? 1f : 0.6f, g = k == 0 ? 0.6f : 0.95f, b = 1f;
                    Draw.v(vc, m, 0, y0, 0, r, g, b, 0.85f, 0, 1);
                    Draw.v(vc, m, side * w, y0 + h, 0, r, g, b, 0.85f, 0, 0);
                    Draw.v(vc, m, side * w * 1.1f, y0 + h * 0.2f, 0, r, g, b, 0.85f, 1, 0);
                    Draw.v(vc, m, side * w * 0.4f, y0 - h * 0.3f, 0, r, g, b, 0.85f, 1, 1);
                    ms.pop();
                }
            }
            ms.pop();
        }
    }

    // ================================================================ 인어: 사람 윗몸 + 비늘 꼬리 (다리 대신)
    public static class MermaidModel extends BipedEntityModel<MermaidEntity> {
        final ModelPart tail1, tail2, fin;

        public MermaidModel(ModelPart root) {
            super(root);
            tail1 = root.getChild("body").getChild("tail1");
            tail2 = tail1.getChild("tail2");
            fin = tail2.getChild("fin");
        }

        public static TexturedModelData getTexturedModelData() {
            ModelData md = BipedEntityModel.getModelData(Dilation.NONE, 0f);
            ModelPartData root = md.getRoot();
            root.addChild("right_leg", ModelPartBuilder.create(), ModelTransform.pivot(-1.9f, 12f, 0));
            root.addChild("left_leg", ModelPartBuilder.create(), ModelTransform.pivot(1.9f, 12f, 0));
            ModelPartData body = root.getChild("body");
            ModelPartData t1 = body.addChild("tail1", ModelPartBuilder.create().uv(0, 32).cuboid(-3.5f, 0f, -2f, 7, 6, 4), ModelTransform.pivot(0, 12, 0));
            ModelPartData t2 = t1.addChild("tail2", ModelPartBuilder.create().uv(0, 44).cuboid(-2.5f, 0f, -1.5f, 5, 6, 3), ModelTransform.pivot(0, 5.5f, 0));
            t2.addChild("fin", ModelPartBuilder.create().uv(24, 44).cuboid(-5f, 0f, -0.5f, 10, 5, 1), ModelTransform.pivot(0, 5.5f, 0));
            return TexturedModelData.of(md, 64, 64);
        }

        @Override
        public void animateModel(MermaidEntity e, float limbAngle, float limbDistance, float td) {
            rightArmPose = ArmPose.EMPTY;
            leftArmPose = ArmPose.EMPTY;
            if (e.getMainHandStack().isOf(Items.TRIDENT) && e.isAttacking()) rightArmPose = ArmPose.THROW_SPEAR;
            super.animateModel(e, limbAngle, limbDistance, td);
        }

        @Override
        public void setAngles(MermaidEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, age, headYaw, headPitch);
            float sw = e.isTouchingWater() ? 1f : 0.35f;
            float sp = 0.18f + limbDistance * 0.4f;
            tail1.pitch = MathHelper.sin(age * sp) * 0.25f * sw + 0.1f;
            tail2.pitch = MathHelper.sin(age * sp - 0.9f) * 0.4f * sw + 0.15f;
            fin.pitch = MathHelper.sin(age * sp - 1.8f) * 0.5f * sw + 0.3f;
            tail1.yaw = MathHelper.sin(age * 0.07f) * 0.08f;
        }
    }

    static final class MermaidRenderer extends MobEntityRenderer<MermaidEntity, MermaidModel> {
        static final Identifier SKIN = MobRenderers.tex("mermaid");

        MermaidRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, new MermaidModel(ctx.getPart(MERMAID_LAYER)), 0.5f);
            addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
        }

        @Override
        public Identifier getTexture(MermaidEntity e) {
            return SKIN;
        }

        /** 헤엄칠 때 눕는 자세 (물에 빠진 자와 같음). */
        @Override
        protected void setupTransforms(MermaidEntity e, MatrixStack ms, float animationProgress, float bodyYaw, float td) {
            super.setupTransforms(e, ms, animationProgress, bodyYaw, td);
            float f = e.getLeaningPitch(td);
            if (f > 0) ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(f, e.getPitch(), -10f - e.getPitch())));
        }
    }

    // ================================================================ 흉포한 늑대 (늑대 뼈대 1.4배)
    static final class DireWolfModel extends AnimalModel<DireWolfEntity> {
        final ModelPart head, realHead, torso, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg, tail, neck;

        DireWolfModel(ModelPart root) {
            head = root.getChild("head");
            realHead = head.getChild("real_head");
            torso = root.getChild("body");
            neck = root.getChild("upper_body");
            rightHindLeg = root.getChild("right_hind_leg");
            leftHindLeg = root.getChild("left_hind_leg");
            rightFrontLeg = root.getChild("right_front_leg");
            leftFrontLeg = root.getChild("left_front_leg");
            tail = root.getChild("tail");
        }

        @Override
        protected Iterable<ModelPart> getHeadParts() {
            return ImmutableList.of(head);
        }

        @Override
        protected Iterable<ModelPart> getBodyParts() {
            return ImmutableList.of(torso, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg, tail, neck);
        }

        @Override
        public void setAngles(DireWolfEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            head.pitch = headPitch * 0.017453292f;
            head.yaw = headYaw * 0.017453292f;
            tail.pitch = 1.1f;
            tail.yaw = MathHelper.cos(limbAngle * 0.6662f) * 0.6f * limbDistance;
            rightHindLeg.pitch = MathHelper.cos(limbAngle * 0.6662f) * 1.4f * limbDistance;
            leftHindLeg.pitch = MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * 1.4f * limbDistance;
            rightFrontLeg.pitch = MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * 1.4f * limbDistance;
            leftFrontLeg.pitch = MathHelper.cos(limbAngle * 0.6662f) * 1.4f * limbDistance;
            // 으르렁: 고개를 낮춤
            if (e.isAttacking()) {
                head.pitch += 0.25f;
                realHead.roll = MathHelper.sin(age * 0.6f) * 0.05f;
            } else realHead.roll = 0;
        }
    }

    static final class DireWolfRenderer extends MobEntityRenderer<DireWolfEntity, DireWolfModel> {
        static final Identifier SKIN = MobRenderers.tex("dire_wolf");

        DireWolfRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, new DireWolfModel(ctx.getPart(EntityModelLayers.WOLF)), 0.7f);
        }

        @Override
        protected void scale(DireWolfEntity e, MatrixStack ms, float td) {
            ms.scale(1.4f, 1.4f, 1.4f);
        }

        @Override
        public Identifier getTexture(DireWolfEntity e) {
            return SKIN;
        }
    }

    // ================================================================ 골렘 (철 골렘 뼈대 + 빛나는 룬)
    static final class GolemModel<T extends MobEntity & GolemLike> extends SinglePartEntityModel<T> {
        final ModelPart root, head, rightArm, leftArm, rightLeg, leftLeg;

        GolemModel(ModelPart root) {
            this.root = root;
            head = root.getChild("head");
            rightArm = root.getChild("right_arm");
            leftArm = root.getChild("left_arm");
            rightLeg = root.getChild("right_leg");
            leftLeg = root.getChild("left_leg");
        }

        @Override
        public ModelPart getPart() {
            return root;
        }

        @Override
        public void setAngles(T e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            head.yaw = headYaw * 0.017453292f;
            head.pitch = headPitch * 0.017453292f;
            rightLeg.pitch = -1.5f * MathHelper.wrap(limbAngle, 13f) * limbDistance;
            leftLeg.pitch = 1.5f * MathHelper.wrap(limbAngle, 13f) * limbDistance;
            rightLeg.yaw = leftLeg.yaw = 0;
        }

        @Override
        public void animateModel(T e, float limbAngle, float limbDistance, float td) {
            int a = e.attackTicks();
            if (a > 0) {
                rightArm.pitch = -2f + 1.5f * MathHelper.wrap(a - td, 10f);
                leftArm.pitch = -2f + 1.5f * MathHelper.wrap(a - td, 10f);
            } else {
                rightArm.pitch = (-0.2f + 1.5f * MathHelper.wrap(limbAngle, 13f)) * limbDistance;
                leftArm.pitch = (-0.2f - 1.5f * MathHelper.wrap(limbAngle, 13f)) * limbDistance;
            }
        }
    }

    static final class GolemRenderer<T extends MobEntity & GolemLike> extends MobEntityRenderer<T, GolemModel<T>> {
        final Identifier skin;
        final float size;

        GolemRenderer(EntityRendererFactory.Context ctx, String name, float size) {
            super(ctx, new GolemModel<>(ctx.getPart(EntityModelLayers.IRON_GOLEM)), 0.9f * size);
            this.skin = MobRenderers.tex(name);
            this.size = size;
            RenderLayer glow = RenderLayer.getEyes(MobRenderers.tex(name + "_glow"));
            addFeature(new EyesFeatureRenderer<>(this) {
                @Override
                public RenderLayer getEyesTexture() {
                    return glow;
                }
            });
        }

        @Override
        protected void scale(T e, MatrixStack ms, float td) {
            ms.scale(size, size, size);
        }

        @Override
        public Identifier getTexture(T e) {
            return skin;
        }
    }

    private WildRenderers() {
    }
}

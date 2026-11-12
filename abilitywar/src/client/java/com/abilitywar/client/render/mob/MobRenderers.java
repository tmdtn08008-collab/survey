package com.abilitywar.client.render.mob;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.client.fx.Fx;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.fx.paladin.PaladinParts;
import com.abilitywar.mob.AWMobs;
import com.abilitywar.mob.WispEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/** 몹 · 보스 렌더러 등록. */
public final class MobRenderers {
    public static void init() {
        EntityRendererRegistry.register(AWMobs.GOBLIN, ctx -> new Biped<>(ctx, "goblin", 0.72f));
        EntityRendererRegistry.register(AWMobs.ORC, ctx -> new Biped<>(ctx, "orc", 1.2f));
        EntityRendererRegistry.register(AWMobs.SKELETON_KNIGHT, ctx -> new Biped<>(ctx, "skeleton_knight", 1.02f));
        EntityRendererRegistry.register(AWMobs.WISP, WispRenderer::new);
        EntityRendererRegistry.register(AWMobs.FALLEN_KING, FallenKingRenderer::new);
        EntityRendererRegistry.register(AWMobs.EMBER_COLOSSUS, ColossusRenderer::new);
        EntityRendererRegistry.register(AWMobs.FROST_WITCH, FrostWitchRenderer::new);
        EntityRendererRegistry.register(AWMobs.ANCIENT_WYVERN, Invisible::new);
        WildRenderers.init();
    }

    static Identifier tex(String name) {
        return AbilityWar.id("textures/entity/" + name + ".png");
    }

    /** 사람 모양 몹 (64x64 피부) + 크기. 손에 든 물건은 그대로 보임. */
    public static class Biped<T extends MobEntity> extends BipedEntityRenderer<T, BipedEntityModel<T>> {
        final Identifier skin;
        final float size;

        public Biped(EntityRendererFactory.Context ctx, String name, float size) {
            super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.5f * size);
            this.skin = tex(name);
            this.size = size;
        }

        @Override
        protected void scale(T entity, MatrixStack ms, float td) {
            ms.scale(size, size, size);
        }

        @Override
        public Identifier getTexture(T entity) {
            return skin;
        }
    }

    /** 도깨비불: 빛나는 구슬 + 일렁이는 불꽃. */
    static final class WispRenderer extends EntityRenderer<WispEntity> {
        WispRenderer(EntityRendererFactory.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(WispEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
            float t = e.age + td;
            ms.push();
            ms.translate(0, 0.25 + 0.06 * MathHelper.sin(t * 0.15f), 0);
            ms.multiply(dispatcher.getRotation());
            Matrix4f m = ms.peek().getPositionMatrix();
            int c = WispEntity.COLOR;
            float pulse = 0.85f + 0.15f * MathHelper.sin(t * 0.4f);
            VertexConsumer g = vcp.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            quad(g, m, 0.9f * pulse, Fx.r(c), Fx.g(c), Fx.b(c), 0.85f);
            quad(g, m, 0.35f, 1, 1, 1, 1f);
            VertexConsumer f = vcp.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            quad(f, m, 0.6f, Fx.r(c), Fx.g(c), Fx.b(c), 0.6f);
            ms.pop();
            super.render(e, yaw, td, ms, vcp, light);
        }

        static void quad(VertexConsumer vc, Matrix4f m, float s, float r, float g, float b, float a) {
            Draw.v(vc, m, -s, -s, 0, r, g, b, a, 0, 1);
            Draw.v(vc, m, -s, s, 0, r, g, b, a, 0, 0);
            Draw.v(vc, m, s, s, 0, r, g, b, a, 1, 0);
            Draw.v(vc, m, s, -s, 0, r, g, b, a, 1, 1);
        }

        @Override
        public Identifier getTexture(WispEntity e) {
            return Tex.GLOW;
        }
    }

    /** 고대 비룡: 몸은 BossFx 가 고룡 모델로 그림. */
    public static final class Invisible<T extends Entity> extends EntityRenderer<T> {
        Invisible(EntityRendererFactory.Context ctx) {
            super(ctx);
        }

        @Override
        public Identifier getTexture(T e) {
            return Tex.GLOW;
        }
    }

    static final float[] DARK_BLADE = {0.32f, 0.3f, 0.36f}, DARK_TRIM = {0.55f, 0.12f, 0.16f}, RED_GLOW = {1f, 0.18f, 0.12f}, RED_GEM = {1f, 0.2f, 0.25f};

    static void bossSword(MatrixStack ms, VertexConsumerProvider vcp, float glow) {
        PaladinParts.renderSword(ms, vcp, 1.25f, 0.16f, glow, DARK_BLADE, DARK_TRIM, RED_GLOW, RED_GEM);
    }

    private MobRenderers() {
    }
}

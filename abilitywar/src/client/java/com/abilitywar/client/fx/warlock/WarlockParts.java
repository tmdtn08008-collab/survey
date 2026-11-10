package com.abilitywar.client.fx.warlock;

import com.abilitywar.client.anim.AnimState;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.fx.dragon.DragonParts;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** 흑마법사의 몸: 녹색으로 빛나는 눈, 왼쪽에 떠 있는 흑마법서, 두 손의 영혼 불꽃. */
public final class WarlockParts {
    static final Identifier GLOW = Tex.fx("warlock_glow_skin");
    static final Identifier BOOK = Tex.fx("warlock_book");

    public static boolean isWarlock(PlayerEntity p) {
        return "warlock".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    /** 주문을 거는 중 (스킬/궁극기): 책이 앞으로 오고 손 불꽃이 커짐. */
    static float casting(PlayerEntity p, float now) {
        AnimState st = PlayerAnimator.peek(p);
        AnimState.Action a = st == null ? null : st.current();
        if (a == null || !a.clip.id.startsWith("warlock.")) return 0;
        String id = a.clip.id;
        if (id.startsWith("warlock.skill") || id.startsWith("warlock.ult") || id.equals("warlock.collect") || id.startsWith("warlock.attack"))
            return DragonParts.env(a.time(now), 0, 2, a.clip.length - 3, a.clip.length);
        return 0;
    }

    /** 손 끝의 영혼 불꽃 (손 공간). */
    static void flame(MatrixStack ms, VertexConsumerProvider vcp, float now, float size, float phase) {
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer g = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float fl = 0.85f + 0.15f * MathHelper.sin(now * 0.9f + phase) + 0.08f * MathHelper.sin(now * 2.3f + phase);
        float s = size * fl;
        for (int k = 0; k < 3; k++) {
            float a = (float) (k * Math.PI / 3 + now * 0.05f);
            float cx = MathHelper.cos(a) * s * 0.6f, cz = MathHelper.sin(a) * s * 0.6f;
            // 위로 길쭉한 마름모 (아래가 손)
            g.vertex(m, -cx, 0, -cz).color(0.15f, 0.6f, 0.38f, 0f).next();
            g.vertex(m, 0, -s * 0.4f, 0).color(0.3f, 0.95f, 0.6f, 0.9f).next();
            g.vertex(m, cx, 0, cz).color(0.15f, 0.6f, 0.38f, 0f).next();
            g.vertex(m, 0, s * 1.8f, 0).color(0.1f, 0.5f, 0.3f, 0f).next();
        }
    }

    // ------------------------------------------------------------------ 흑마법서 (몸통 공간)
    static void renderBook(MatrixStack ms, VertexConsumerProvider vcp, int light, float now, float cast) {
        ms.push();
        float bob = 0.04f * MathHelper.sin(now * 0.11f);
        ms.translate(MathHelper.lerp(cast, 0.55f, 0.3f), 0.3f + bob - 0.05f * cast, MathHelper.lerp(cast, -0.2f, -0.45f));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(cast, -28, -10) + 5 * MathHelper.sin(now * 0.07f)));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(cast, 58, 35)));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        float hw = 0.16f, hh = 0.2f;
        float open = MathHelper.lerp(cast, 0.6f, 1f);
        float ang = (float) Math.toRadians(70 * (1 - open) + 6);
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        VertexConsumer page = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(BOOK));
        pv(page, m, n, 0, 0, -hh, 0.5f, 1, 0xF000F0);
        pv(page, m, n, -hw * cs, hw * sn, -hh, 0f, 1, 0xF000F0);
        pv(page, m, n, -hw * cs, hw * sn, hh, 0f, 0, 0xF000F0);
        pv(page, m, n, 0, 0, hh, 0.5f, 0, 0xF000F0);
        pv(page, m, n, 0, 0, -hh, 0.5f, 1, 0xF000F0);
        pv(page, m, n, hw * cs, hw * sn, -hh, 1f, 1, 0xF000F0);
        pv(page, m, n, hw * cs, hw * sn, hh, 1f, 0, 0xF000F0);
        pv(page, m, n, 0, 0, hh, 0.5f, 0, 0xF000F0);
        // 검은 표지 + 녹색 빛
        VertexConsumer solid = vcp.getBuffer(AWRenderLayers.SOLID_COLOR);
        float ex = 0.012f;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (hw + ex) * cs, z = (hw + ex) * sn;
            solid.vertex(m, 0, 0.004f, -hh - ex).color(0.06f, 0.04f, 0.08f, 1).next();
            solid.vertex(m, x, -(z) + 0.004f, -hh - ex).color(0.06f, 0.04f, 0.08f, 1).next();
            solid.vertex(m, x, -(z) + 0.004f, hh + ex).color(0.06f, 0.04f, 0.08f, 1).next();
            solid.vertex(m, 0, 0.004f, hh + ex).color(0.06f, 0.04f, 0.08f, 1).next();
        }
        VertexConsumer glow = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float a = 0.25f + 0.45f * cast;
        float s = 0.34f;
        glow.vertex(m, -s, -0.06f, 0).color(0.2f, 0.9f, 0.55f, 0).next();
        glow.vertex(m, 0, -0.06f, -s).color(0.2f, 0.9f, 0.55f, a).next();
        glow.vertex(m, s, -0.06f, 0).color(0.2f, 0.9f, 0.55f, 0).next();
        glow.vertex(m, 0, -0.06f, s).color(0.2f, 0.9f, 0.55f, a).next();
        ms.pop();
    }

    static void pv(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light) {
        vc.vertex(m, x, -y, z).color(1f, 1f, 1f, 1f).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, -1, 0).next();
    }

    // ------------------------------------------------------------------ 1인칭: 손 끝 불꽃
    public static void fpFlame(MatrixStack ms, VertexConsumerProvider vcp, PlayerEntity p, int arm) {
        if (!isWarlock(p)) return;
        float now = ClientClock.now();
        ms.push();
        ms.translate(0, 0.06f, -0.72f);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
        flame(ms, vcp, now, 0.09f + 0.08f * casting(p, now), arm * 2.1f);
        ms.pop();
    }

    // ------------------------------------------------------------------ 피처 렌더러
    public static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        public Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!isWarlock(p) || p.isInvisible()) return;
            float now = ClientClock.now();
            PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
            // 빛나는 눈
            float pulse = 0.75f + 0.25f * MathHelper.sin(now * 0.15f);
            model.render(ms, vcp.getBuffer(RenderLayer.getEyes(GLOW)), light, OverlayTexture.DEFAULT_UV, pulse, pulse, pulse, 1);
            float cast = casting(p, now);
            ms.push();
            model.body.rotate(ms);
            renderBook(ms, vcp, light, now, cast);
            ms.pop();
            if (!p.getMainHandStack().isEmpty()) return;
            for (int arm = 0; arm < 2; arm++) {
                ms.push();
                model.setArmAngle(arm == 0 ? Arm.RIGHT : Arm.LEFT, ms);
                ms.translate(arm == 0 ? -0.0625f : 0.0625f, 0.66f, 0);
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                flame(ms, vcp, now, 0.07f + 0.08f * cast, arm * 2.1f);
                ms.pop();
            }
        }
    }

    private WarlockParts() {
    }
}

package com.abilitywar.client.fx.cantor;

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

/** 성가사의 몸: 머리 위 광륜, 오른손의 지휘봉, 왼쪽에 떠 있는 성가집, (궁극기) 빛의 날개. */
public final class CantorParts {
    static final Identifier WING = Tex.fx("cantor_wing");
    static final Identifier WING_GLOW = Tex.fx("cantor_wing_glow");
    static final Identifier BOOK = Tex.fx("cantor_book");
    public static final int GOLD = 0xFFD86A;
    /** 선율 색: 용맹 / 수호 / 신속. */
    public static final int[] MODE_COLOR = {0xFFA040, 0x7FD4FF, 0x8CFF8C};

    public static boolean isCantor(PlayerEntity p) {
        return "cantor".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    static AnimState.Action action(PlayerEntity p) {
        AnimState st = PlayerAnimator.peek(p);
        return st == null ? null : st.current();
    }

    /** 노래하는 중 (스킬/궁극기 동작): 성가집이 펼쳐지고 페이지가 넘어감. */
    static float singing(PlayerEntity p, float now) {
        AnimState.Action a = action(p);
        if (a == null || !a.clip.id.startsWith("cantor.")) return 0;
        String id = a.clip.id;
        if (id.startsWith("cantor.skill") || id.startsWith("cantor.ult") || id.equals("cantor.switch") || id.equals("cantor.select"))
            return DragonParts.env(a.time(now), 0, 3, a.clip.length - 4, a.clip.length);
        return 0;
    }

    static float wings(PlayerEntity p, float now) {
        AnimState.Action a = action(p);
        if (a == null) return 0;
        float t = a.time(now);
        if (a.clip.id.equals("cantor.ult")) return DragonParts.env(t, 10, 20, 62, 72);
        if (a.clip.id.equals("cantor.select")) return DragonParts.env(t, 4, 9, 20, 26);
        return 0;
    }

    static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    /** 광륜 색 = 금빛 + 선율 색. */
    public static float[] haloColor(PlayerEntity p) {
        int mode = CantorFx.mode(p.getId());
        float[] g = rgb(GOLD), m = rgb(MODE_COLOR[mode]);
        return new float[]{g[0] * 0.55f + m[0] * 0.45f, g[1] * 0.55f + m[1] * 0.45f, g[2] * 0.55f + m[2] * 0.45f};
    }

    // ------------------------------------------------------------------ 광륜 (머리 공간, +Y 아래)
    public static void renderHalo(MatrixStack ms, VertexConsumerProvider vcp, float[] col, float now, float boost) {
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float y = -0.66f + 0.015f * MathHelper.sin(now * 0.12f);
        float r = 0.27f, w = 0.035f;
        int seg = 40;
        float pulse = (0.8f + 0.2f * MathHelper.sin(now * 0.2f)) * (1 + boost);
        for (int band = 0; band < 3; band++) {
            // 0: 바깥 번짐, 1: 심, 2: 안쪽 번짐
            float r0 = band == 0 ? r + w * 0.5f : band == 1 ? r - w * 0.5f : r - w * 2.2f;
            float r1 = band == 0 ? r + w * 2.6f : band == 1 ? r + w * 0.5f : r - w * 0.5f;
            float a0 = band == 0 ? 0.5f : band == 1 ? 1f : 0f, a1 = band == 0 ? 0f : band == 1 ? 1f : 0.5f;
            float br = band == 1 ? 1f : 0.6f;
            for (int i = 0; i < seg; i++) {
                double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
                float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
                float cr = Math.min(1, col[0] * br * pulse + (band == 1 ? 0.1f : 0)), cg = Math.min(1, col[1] * br * pulse * 0.92f + (band == 1 ? 0.05f : 0)), cb = Math.min(1, col[2] * br * pulse * 0.75f);
                vc.vertex(m, c0 * r0, y, s0 * r0).color(cr, cg, cb, a0).next();
                vc.vertex(m, c1 * r0, y, s1 * r0).color(cr, cg, cb, a0).next();
                vc.vertex(m, c1 * r1, y, s1 * r1).color(cr, cg, cb, a1).next();
                vc.vertex(m, c0 * r1, y, s0 * r1).color(cr, cg, cb, a1).next();
            }
        }
    }

    // ------------------------------------------------------------------ 지휘봉 (손 공간: 손 끝 기준, -Z = 앞)
    static void renderBaton(MatrixStack ms, VertexConsumerProvider vcp, float now, float[] col, boolean fp) {
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer solid = vcp.getBuffer(AWRenderLayers.SOLID_COLOR);
        float L = fp ? 0.62f : 0.52f, w = fp ? 0.02f : 0.016f;
        // 손잡이 (짙은 나무색) → 금빛 대 → 흰 끝
        box(solid, m, -0.03f, 0.03f, -w * 1.8f, w * 1.8f, -w * 1.8f, w * 1.8f, 0.24f, 0.13f, 0.06f);
        box(solid, m, -L * 0.1f, -0.03f, -w * 2.2f, w * 2.2f, -w * 2.2f, w * 2.2f, 0.95f, 0.75f, 0.3f);
        box(solid, m, -L, -L * 0.1f, -w, w, -w, w, 0.98f, 0.92f, 0.7f);
        VertexConsumer glow = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float p = 0.7f + 0.3f * MathHelper.sin(now * 0.3f);
        // 끝의 빛 (작은 십자 판 두 장)
        float s = (fp ? 0.09f : 0.07f) * p;
        float z = -L;
        quadXY(glow, m, 0, 0, z, s, col[0], col[1], col[2], 0.9f);
        quadZY(glow, m, 0, 0, z, s, col[0], col[1], col[2], 0.9f);
        box(glow, m, -L, -L * 0.1f, -w * 2.5f, w * 2.5f, -w * 2.5f, w * 2.5f, col[0] * 0.25f * p, col[1] * 0.22f * p, col[2] * 0.15f * p);
    }

    /** z 범위가 -Z 방향 (z0 < z1). */
    static void box(VertexConsumer vc, Matrix4f m, float z0, float z1, float x0, float x1, float y0, float y1, float r, float g, float b) {
        float[][] f = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
                {x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1},
                {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1},
                {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}};
        float[] shade = {0.8f, 0.8f, 0.65f, 0.65f, 1f, 0.55f};
        for (int i = 0; i < 6; i++) {
            float k = shade[i];
            for (int v = 0; v < 4; v++) vc.vertex(m, f[i][v * 3], f[i][v * 3 + 1], f[i][v * 3 + 2]).color(r * k, g * k, b * k, 1).next();
        }
    }

    static void quadXY(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, float r, float g, float b, float a) {
        vc.vertex(m, x - s, y, z).color(r, g, b, 0).next();
        vc.vertex(m, x, y - s, z).color(r, g, b, a).next();
        vc.vertex(m, x + s, y, z).color(r, g, b, 0).next();
        vc.vertex(m, x, y + s, z).color(r, g, b, a).next();
    }

    static void quadZY(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z - s).color(r, g, b, 0).next();
        vc.vertex(m, x, y - s, z).color(r, g, b, a).next();
        vc.vertex(m, x, y, z + s).color(r, g, b, 0).next();
        vc.vertex(m, x, y + s, z).color(r, g, b, a).next();
    }

    // ------------------------------------------------------------------ 성가집 (몸통 공간)
    static void renderBook(MatrixStack ms, VertexConsumerProvider vcp, int light, float now, float sing) {
        ms.push();
        float bob = 0.04f * MathHelper.sin(now * 0.1f);
        // 몸 왼쪽 앞, 노래할 때는 가슴 앞으로
        ms.translate(MathHelper.lerp(sing, 0.52f, 0.3f), 0.32f + bob - 0.06f * sing, MathHelper.lerp(sing, -0.22f, -0.45f));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(sing, -25, -10) + 4 * MathHelper.sin(now * 0.07f)));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(sing, 55, 35)));
        float open = MathHelper.lerp(sing, 0.55f, 1f);
        float hw = 0.16f, hh = 0.2f;
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        VertexConsumer page = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(BOOK));
        float ang = (float) Math.toRadians(70 * (1 - open) + 6);
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        // 왼쪽 페이지 (u 0~0.5) / 오른쪽 페이지 (u 0.5~1): 책등(x=0)에서 위로 접힘
        pageQuad(page, m, n, -hw * cs, -hw * sn * -1, hh, 0, 0.5f, light, true, cs, sn);
        pageQuad(page, m, n, hw * cs, hw * sn, hh, 0.5f, 1f, light, false, cs, sn);
        // 넘어가는 페이지
        float flip = (now * (0.02f + 0.06f * sing)) % 1f;
        float fa = (float) Math.PI * flip;
        float fx = (float) Math.cos(fa), fz = (float) Math.sin(fa);
        flipQuad(page, m, n, fx * hw * 0.98f, fz * hw * 0.9f + 0.005f, hh * 0.98f, light);
        // 표지 (짙은 붉은색 + 금 테두리)
        VertexConsumer solid = vcp.getBuffer(AWRenderLayers.SOLID_COLOR);
        float ex = 0.012f;
        coverQuad(solid, m, -(hw + ex) * cs, (hw + ex) * sn, hh + ex, -0.004f);
        coverQuad(solid, m, (hw + ex) * cs, (hw + ex) * sn, hh + ex, -0.004f);
        // 책 위의 은은한 빛
        VertexConsumer glow = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float a = 0.18f + 0.35f * sing;
        quadXZ(glow, m, 0, 0.06f, 0, 0.32f, 1f, 0.85f, 0.45f, a);
        ms.pop();
    }

    /** 책등(0,0,y)에서 (ex, ez) 로 뻗은 페이지. y 는 세로 (±hh). */
    static void pageQuad(VertexConsumer vc, Matrix4f m, Matrix3f n, float ex, float ez, float hh, float u0, float u1, int light, boolean left, float cs, float sn) {
        float ua = left ? u1 : u0, ub = left ? u0 : u1;
        pv(vc, m, n, 0, 0, -hh, ua, 1, light);
        pv(vc, m, n, ex, Math.abs(ez), -hh, ub, 1, light);
        pv(vc, m, n, ex, Math.abs(ez), hh, ub, 0, light);
        pv(vc, m, n, 0, 0, hh, ua, 0, light);
    }

    static void flipQuad(VertexConsumer vc, Matrix4f m, Matrix3f n, float ex, float ez, float hh, int light) {
        pv(vc, m, n, 0, 0.004f, -hh, 0.5f, 1, light);
        pv(vc, m, n, ex, ez, -hh, 0.95f, 1, light);
        pv(vc, m, n, ex, ez, hh, 0.95f, 0, light);
        pv(vc, m, n, 0, 0.004f, hh, 0.5f, 0, light);
    }

    static void pv(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light) {
        // 책 공간: x = 좌우, y = 페이지 면에서 위, z = 세로
        vc.vertex(m, x, -y, z).color(1f, 1f, 1f, 1f).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, -1, 0).next();
    }

    static void coverQuad(VertexConsumer vc, Matrix4f m, float ex, float ez, float hh, float dy) {
        float r = 0.45f, g = 0.08f, b = 0.1f;
        vc.vertex(m, 0, -dy, -hh).color(r, g, b, 1).next();
        vc.vertex(m, ex, -(ez + dy), -hh).color(r, g, b, 1).next();
        vc.vertex(m, ex, -(ez + dy), hh).color(r, g, b, 1).next();
        vc.vertex(m, 0, -dy, hh).color(r, g, b, 1).next();
    }

    static void quadXZ(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, float r, float g, float b, float a) {
        vc.vertex(m, x - s, -y, z).color(r, g, b, 0).next();
        vc.vertex(m, x, -y, z - s).color(r, g, b, a).next();
        vc.vertex(m, x + s, -y, z).color(r, g, b, 0).next();
        vc.vertex(m, x, -y, z + s).color(r, g, b, a).next();
    }

    // ------------------------------------------------------------------ 빛의 날개
    static void renderWings(MatrixStack ms, VertexConsumerProvider vcp, int light, float amount, float now) {
        if (amount <= 0.01f) return;
        float size = 2.4f * (0.3f + 0.7f * amount);
        float flap = 8 * MathHelper.sin(now * 0.18f);
        for (int pair = 0; pair < 2; pair++) {
            for (int side = 0; side < 2; side++) {
                boolean left = side == 1;
                float sx = left ? 1 : -1;
                ms.push();
                ms.translate(sx * 1.4f / 16f, (3.0f + pair * 3.5f) / 16f, 2.4f / 16f);
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12 + pair * 6));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((left ? -10 : 10) * (1 - amount) + (left ? 1 : -1) * pair * 12));
                float roll = (pair == 0 ? 28 : -18) + flap * (pair == 0 ? 1 : 0.6f);
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(left ? -roll : roll));
                float sz = pair == 0 ? size : size * 0.7f;
                DragonParts.wingQuad(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(WING)), 0xF000F0, sz, 1, 1, 1, amount * (pair == 0 ? 1 : 0.8f), !left, 14);
                float gl = 0.8f * amount;
                DragonParts.wingQuad(ms, vcp.getBuffer(RenderLayer.getEyes(WING_GLOW)), light, sz, gl, gl, gl, 1, !left, 14);
                ms.pop();
            }
        }
    }

    // ------------------------------------------------------------------ 1인칭
    public static void fpBaton(MatrixStack ms, VertexConsumerProvider vcp, PlayerEntity p, int arm) {
        if (!isCantor(p) || arm != 0) return;
        float now = ClientClock.now();
        ms.push();
        ms.translate(0, 0.02f, -0.66f);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28));
        renderBaton(ms, vcp, now, haloColor(p), true);
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
            if (!isCantor(p) || p.isInvisible()) return;
            float now = ClientClock.now();
            PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
            float[] col = haloColor(p);
            float sing = singing(p, now);
            // 광륜
            ms.push();
            model.head.rotate(ms);
            renderHalo(ms, vcp, col, now, sing * 0.4f);
            ms.pop();
            // 날개
            float w = wings(p, now);
            if (w > 0.01f) {
                ms.push();
                model.body.rotate(ms);
                renderWings(ms, vcp, light, w, now);
                ms.pop();
            }
            // 성가집
            ms.push();
            model.body.rotate(ms);
            renderBook(ms, vcp, light, now, sing);
            ms.pop();
            // 지휘봉 (맨손일 때)
            if (!p.getMainHandStack().isEmpty()) return;
            ms.push();
            model.setArmAngle(Arm.RIGHT, ms);
            ms.translate(-0.0625f, 0.62f, -0.02f);
            // 지휘봉: 주먹에서 팔 연장선 쪽(+Y)으로, 살짝 앞으로 기울여
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(70));
            renderBaton(ms, vcp, now, col, false);
            ms.pop();
        }
    }

    private CantorParts() {
    }
}

package com.abilitywar.client.fx;

import com.abilitywar.client.anim.FirstPersonRenderer;
import com.abilitywar.client.anim.FpPose;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 1인칭 손 공간에 그리는 이펙트 (주먹 오라, 충전 구체, 방패). */
public final class FpEffects {
    private static final Matrix4f ID = new Matrix4f();
    private static final Quaternionf NO_ROT = new Quaternionf();
    private static float counterFlash;

    public static void counterFlash() {
        counterFlash = 1f;
    }

    public static void render(float td, MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity p, int light, FpPose pose) {
        float time = p.age + td;
        Vector3f fr = FirstPersonRenderer.FIST_VIEW[0], fl = FirstPersonRenderer.FIST_VIEW[1];
        VertexConsumer g = consumers.getBuffer(AWRenderLayers.additive(Tex.GLOW));
        int col = Effects.abilityColor(p), col2 = col == Fx.PURPLE ? Fx.PINK : Effects.mixWhite(col, 0.4f);
        // 주먹 주변 은은한 기운 (능력 색)
        for (int arm = 0; arm < 2; arm++) {
            if (!pose.visible[arm]) continue;
            Vector3f f = FirstPersonRenderer.FIST_VIEW[arm];
            float flick = 0.75f + 0.25f * (float) Math.sin(time * 0.35 + arm * 2);
            Draw.billboard(g, ID, NO_ROT, f.x, f.y, f.z, 0.09f * flick, 0, Fx.r(col), Fx.g(col), Fx.b(col), 0.35f);
        }
        // 충전 (공허 파동)
        for (Effects.Charge c : VfxManager.find(Effects.Charge.class)) {
            if (c.caster() != p.getId()) continue;
            float t = c.progress(td);
            VertexConsumer dk = consumers.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.billboard(dk, ID, NO_ROT, fr.x, fr.y, fr.z, 0.05f + t * 0.1f, 0, 0, 0, 0, 0.9f);
            VertexConsumer gg = consumers.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(gg, ID, NO_ROT, fr.x, fr.y, fr.z, 0.12f + t * 0.28f, 0, Fx.r(Fx.PURPLE), Fx.g(Fx.PURPLE), Fx.b(Fx.PURPLE), 0.9f);
            VertexConsumer fx = consumers.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(fx, ID, NO_ROT, fr.x, fr.y, fr.z, 0.2f + t * 0.3f, time * 0.25f, Fx.r(Fx.PINK), Fx.g(Fx.PINK), Fx.b(Fx.PINK), 0.7f);
            VertexConsumer ring = consumers.getBuffer(AWRenderLayers.additive(Tex.RING));
            float rt = (time % 5) / 5f;
            Draw.billboard(ring, ID, NO_ROT, fr.x, fr.y, fr.z, 0.45f * (1 - rt) + 0.05f, 0, Fx.r(Fx.PURPLE), Fx.g(Fx.PURPLE), Fx.b(Fx.PURPLE), rt * 0.8f);
        }
        // 반격 자세 / 막기 방패
        for (Effects.Shield s : VfxManager.find(Effects.Shield.class)) {
            if (s.caster() != p.getId()) continue;
            float vis = s.visibility(td);
            if (vis <= 0.01f) continue;
            boolean counter = s.kind() == 0;
            Vec3d c = counter ? new Vec3d((fl.x + fr.x) * 0.5 - 0.05, (fl.y + fr.y) * 0.5 + 0.08, Math.min(fl.z, fr.z) - 0.18)
                    : new Vec3d((fl.x + fr.x) * 0.5, (fl.y + fr.y) * 0.5 + 0.05, Math.min(fl.z, fr.z) - 0.1);
            float size = (counter ? 0.42f : 0.3f) * vis * s.scaleMul;
            double ang = time * (counter ? 0.08 : 0.03);
            Vec3d ax = new Vec3d(Math.cos(ang), Math.sin(ang), 0).multiply(size);
            Vec3d ay = new Vec3d(-Math.sin(ang), Math.cos(ang), 0).multiply(size);
            VertexConsumer dk = consumers.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.quad(dk, ID, c, new Vec3d(size * 1.1, 0, 0), new Vec3d(0, size * 1.1, 0), 0.02f, 0, 0.05f, 0.45f * vis);
            VertexConsumer rs = consumers.getBuffer(AWRenderLayers.additive(Tex.RUNE_SHIELD));
            float pulse = counter ? 0.75f + 0.25f * (float) Math.sin(time * 1.2) : 0.55f;
            Draw.quad(rs, ID, c, ax, ay, Fx.r(col), Fx.g(col), Fx.b(col), vis * pulse);
            VertexConsumer ring = consumers.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.quad(ring, ID, c, new Vec3d(size * 1.15, 0, 0), new Vec3d(0, size * 1.15, 0), Fx.r(col2), Fx.g(col2), Fx.b(col2), vis * 0.6f);
        }
        // 용기사 역린 반격 자세: 왼팔 앞의 비늘 방패
        float sh = com.abilitywar.client.fx.dragon.DragonParts.fpShieldAmount(p, com.abilitywar.client.anim.ClientClock.now());
        if (sh > 0.01f) {
            Vec3d c = new Vec3d(fl.x * 0.6 + fr.x * 0.4, fl.y + 0.12, fl.z - 0.2);
            float size = 0.36f * (0.7f + 0.3f * sh);
            double ang = time * 0.05;
            Vec3d ax = new Vec3d(Math.cos(ang), Math.sin(ang), 0).multiply(size);
            Vec3d ay = new Vec3d(-Math.sin(ang), Math.cos(ang), 0).multiply(size);
            VertexConsumer v = consumers.getBuffer(AWRenderLayers.additive(com.abilitywar.client.fx.dragon.DragonParts.SHIELD));
            float a = sh * (0.7f + 0.3f * (float) Math.sin(time * 0.8));
            Draw.quad(v, ID, c, ax, ay, 1f, 0.5f, 0.15f, a);
        }
        if (counterFlash > 0) {
            VertexConsumer fx = consumers.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(fx, ID, NO_ROT, 0, -0.05, -0.6, 0.9f * (1.4f - counterFlash), 0, 1, 1, 1, counterFlash);
            counterFlash = Math.max(0, counterFlash - 0.08f);
        }
    }

    public static void renderHandless(float td, MatrixStack matrices, VertexConsumerProvider consumers, ClientPlayerEntity p, int light) {
    }

    private FpEffects() {
    }
}

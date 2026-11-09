package com.abilitywar.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대시 잔상: 그 순간 플레이어의 자세를 그대로 찍어 두고, 빛나는 실루엣으로 잠깐 남긴다.
 */
public final class AfterImages {
    private static final class Req {
        int remaining, interval, cooldown, color, life;
        /** 이 자리(월드)에 찍기 — 순간이동하기 전 자리에 잔상을 남길 때. */
        Vec3d at;
    }

    private static final class Snap {
        PlayerEntityRenderer renderer;
        Identifier skin;
        final ModelTransform[] parts = new ModelTransform[12];
        final boolean[] vis = new boolean[12];
        Matrix4f mw;
        Vec3d cam;
        int age, life, color;
    }

    private static final Map<Integer, Req> REQ = new HashMap<>();
    private static final List<Snap> SNAPS = new ArrayList<>();

    public static void request(int entityId, int count, int interval, int color, int life) {
        Req r = new Req();
        r.remaining = count;
        r.interval = interval;
        r.color = color;
        r.life = life;
        REQ.put(entityId, r);
    }

    private record AtReq(int id, Vec3d at, int color, int life) {
    }

    private static final List<AtReq> AT = new ArrayList<>();

    /** 지정한 자리에 잔상 하나 (다음 렌더의 자세를 그 자리로 옮겨 찍음). 여러 개 쌓을 수 있음. */
    public static void requestAt(int entityId, Vec3d at, int color, int life) {
        AT.add(new AtReq(entityId, at, color, life));
    }

    public static void tick() {
        SNAPS.removeIf(s -> ++s.age >= s.life);
        REQ.values().removeIf(r -> {
            r.cooldown--;
            return r.remaining <= 0;
        });
    }

    public static void clear() {
        SNAPS.clear();
        REQ.clear();
        AT.clear();
    }

    private static ModelPart[] parts(PlayerEntityModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg, m.rightPants, m.leftPants};
    }

    /** 플레이어 렌더 중(모델 공간 행렬) 호출. */
    static void capture(AbstractClientPlayerEntity e, MatrixStack ms, PlayerEntityModel<?> model) {
        if (!AT.isEmpty()) {
            for (java.util.Iterator<AtReq> it = AT.iterator(); it.hasNext(); ) {
                AtReq a = it.next();
                if (a.id() != e.getId()) continue;
                it.remove();
                Req r = new Req();
                r.color = a.color();
                r.life = a.life();
                r.at = a.at();
                snap(e, ms, model, r);
            }
        }
        Req r = REQ.get(e.getId());
        if (r == null || r.cooldown > 0 || r.remaining <= 0) return;
        r.remaining--;
        r.cooldown = r.interval;
        snap(e, ms, model, r);
    }

    private static void snap(AbstractClientPlayerEntity e, MatrixStack ms, PlayerEntityModel<?> model, Req r) {
        Snap s = new Snap();
        s.renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(e);
        s.skin = e.getSkinTexture();
        ModelPart[] ps = parts(model);
        for (int i = 0; i < ps.length; i++) {
            s.parts[i] = ps[i].getTransform();
            s.vis[i] = ps[i].visible;
        }
        s.mw = new Matrix4f(HandTracker.invView()).mul(ms.peek().getPositionMatrix());
        if (r.at != null) {
            Vec3d off = r.at.subtract(e.getLerpedPos(MinecraftClient.getInstance().getTickDelta()));
            s.mw = new Matrix4f().translation((float) off.x, (float) off.y, (float) off.z).mul(s.mw);
        }
        s.cam = HandTracker.cameraPos();
        s.life = r.life;
        s.color = r.color;
        SNAPS.add(s);
    }

    static void render(Fx.Ctx c) {
        if (SNAPS.isEmpty()) return;
        for (Snap s : SNAPS) {
            float fade = 1f - (s.age + c.tickDelta) / s.life;
            if (fade <= 0) continue;
            PlayerEntityModel<?> model = s.renderer.getModel();
            ModelPart[] ps = parts(model);
            ModelTransform[] saved = new ModelTransform[ps.length];
            boolean[] savedVis = new boolean[ps.length];
            for (int i = 0; i < ps.length; i++) {
                saved[i] = ps[i].getTransform();
                savedVis[i] = ps[i].visible;
                ps[i].setTransform(s.parts[i]);
                ps[i].visible = s.vis[i];
            }
            Matrix4f m = new Matrix4f(c.m)
                    .translate((float) (s.cam.x - c.cam.x), (float) (s.cam.y - c.cam.y), (float) (s.cam.z - c.cam.z))
                    .mul(s.mw);
            MatrixStack ms = new MatrixStack();
            ms.peek().getPositionMatrix().set(m);
            VertexConsumer vc = c.imm.getBuffer(RenderLayer.getEyes(s.skin));
            float k = fade * fade * 0.9f;
            float r = Fx.r(s.color) * k, g = Fx.g(s.color) * k, b = Fx.b(s.color) * k;
            for (int i = 0; i < ps.length; i++) {
                if (s.vis[i]) ps[i].render(ms, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, r, g, b, 1f);
            }
            c.imm.draw();
            for (int i = 0; i < ps.length; i++) {
                ps[i].setTransform(saved[i]);
                ps[i].visible = savedVis[i];
            }
        }
    }

    private AfterImages() {
    }
}

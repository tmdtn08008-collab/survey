package com.abilitywar.client.fx.dragon;

import com.abilitywar.client.fx.Fx;
import com.abilitywar.client.fx.Tex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 고룡의 환영: 바닐라 엔더 드래곤 모델을 반투명한 몸체 + 흐르는 불꽃 무늬로 그린다
 * (월드에는 존재하지 않는 가짜 개체).
 */
public final class Phantom {
    private static final Identifier TEX = new Identifier("textures/entity/enderdragon/dragon.png");
    private static final Identifier EYES = new Identifier("textures/entity/enderdragon/dragon_eyes.png");
    private static final Identifier SWIRL = Tex.fx("dragon_swirl");
    private static EnderDragonEntity fake;
    private static ClientWorld fakeWorld;

    static EnderDragonEntity dragon(ClientWorld w) {
        if (fake == null || fakeWorld != w) {
            fake = new EnderDragonEntity(EntityType.ENDER_DRAGON, w);
            fake.setSilent(true);
            fakeWorld = w;
        }
        return fake;
    }

    /** 목을 든 정도에 따른 몸 전체의 들림 (렌더러: (seg5.y - seg10.y) * 10도). */
    static float bodyPitch(float neck) {
        return neck * 2.25f * 10f;
    }

    /**
     * 입 위치 (월드). 바닐라 모델: 목 5마디 * 10px + 머리 → 원점 기준 약 (0, 2.25, -7.4) 블록,
     * 그 뒤 몸 전체를 bodyPitch 만큼 들고 yaw 로 돌린다. 엔더 드래곤 모델은 -Z 를 보므로 yaw+180.
     */
    public static Vec3d mouth(Vec3d pos, float yaw, float scale, float neck) {
        double p = Math.toRadians(bodyPitch(neck));
        double y = 2.25, z = -7.4;
        double y1 = y * Math.cos(p) - z * Math.sin(p);
        double z1 = y * Math.sin(p) + z * Math.cos(p);
        double th = Math.toRadians(-(yaw + 180));
        double x2 = z1 * Math.sin(th);
        double z2 = z1 * Math.cos(th);
        return pos.add(x2 * scale, y1 * scale, z2 * scale);
    }

    /**
     * @param yaw   바라보는 방향 (도, 마인크래프트 기준: 0 = +Z)
     * @param neck  0 = 수평, 1 = 고개를 들어 올림
     * @param wing  날개 위치 (0..1 반복: 날갯짓 + 입 벌림, 0.25 에서 입을 가장 크게 벌림)
     * @param alpha 전체 투명도
     */
    public static void render(Fx.Ctx c, Vec3d pos, float yaw, float scale, float neck, float wing, float alpha) {
        if (alpha <= 0.01f) return;
        EnderDragonEntity d = dragon(c.world);
        d.prevWingPosition = wing;
        d.wingPosition = wing;
        d.latestSegment = 63;
        float dyaw = yaw + 180;
        for (int i = 0; i < 64; i++) {
            int age = (63 - i); // 0 = 최신
            d.segmentCircularBuffer[i][0] = dyaw;
            d.segmentCircularBuffer[i][1] = neck * Math.max(0, 12 - age) * 0.45;
            d.segmentCircularBuffer[i][2] = 0;
        }
        d.hurtTime = 0;
        d.setPosition(pos.x, pos.y, pos.z);
        @SuppressWarnings("unchecked")
        EntityRenderer<EnderDragonEntity> r = (EntityRenderer<EnderDragonEntity>) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(d);
        float time = (c.world.getTime() + c.tickDelta);
        c.matrices.push();
        c.matrices.translate(pos.x - c.cam.x, pos.y - c.cam.y, pos.z - c.cam.z);
        c.matrices.scale(scale, scale, scale);
        // 1. 어두운 붉은 반투명 몸체
        r.render(d, 0, 1f, c.matrices, new TintProvider(c.imm, RenderLayer.getEntityTranslucent(TEX), 0.5f, 0.14f, 0.06f, 0.5f * alpha), 0xF000F0);
        c.imm.draw();
        // 2. 몸을 따라 흐르는 불꽃 (가산 합성: 색으로 세기 조절)
        float k = alpha;
        r.render(d, 0, 1f, c.matrices, new TintProvider(c.imm, RenderLayer.getEnergySwirl(SWIRL, (time * 0.004f) % 1f, (time * 0.011f) % 1f), 1.0f * k, 0.55f * k, 0.25f * k, 1f), 0xF000F0);
        c.imm.draw();
        // 3. 빛나는 눈
        r.render(d, 0, 1f, c.matrices, new TintProvider(c.imm, RenderLayer.getEyes(EYES), 1.0f * k, 0.75f * k, 0.25f * k, 1f), 0xF000F0);
        c.imm.draw();
        c.matrices.pop();
    }

    public static void clear() {
        fake = null;
        fakeWorld = null;
    }

    private Phantom() {
    }
}

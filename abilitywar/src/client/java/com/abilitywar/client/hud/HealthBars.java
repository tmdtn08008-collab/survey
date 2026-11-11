package com.abilitywar.client.hud;

import com.abilitywar.client.camera.CameraController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 몹 · 보스 머리 위 HP 막대 + 숫자 (다쳤거나, 바라보거나, 보스일 때).
 * 깎인 만큼은 흰 막대로 잠깐 남았다가 줄어듦. 가까이 (24 칸, 보스 48 칸) 있는 몹만.
 */
public final class HealthBars {
    /** 엔티티 id → 천천히 따라 내려가는 HP (흰 막대). */
    static final Map<Integer, Float> LAG = new HashMap<>();
    /** 바라보는 대상 (먼 거리까지, 화면 위 패널 · 막대 표시용). */
    public static LivingEntity target;
    public static int targetTicks;

    public static boolean isBoss(LivingEntity e) {
        return com.abilitywar.rpg.RpgManager.isBoss(e);
    }

    static boolean eligible(LivingEntity e) {
        if (e instanceof PlayerEntity || e instanceof ArmorStandEntity || e instanceof com.abilitywar.rpg.JobMasterEntity) return false;
        return e instanceof MobEntity && e.isAlive() && !e.isInvisible();
    }

    /** 매 틱: 바라보는 대상 찾기 (최대 32 칸), 흰 막대 줄이기. */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        Entity cam = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
        Vec3d s = cam.getCameraPosVec(1f), d = cam.getRotationVec(1f);
        Vec3d e = s.add(d.multiply(32));
        double max = 32 * 32;
        var bh = mc.world.raycast(new net.minecraft.world.RaycastContext(s, e, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, cam));
        if (bh.getType() != net.minecraft.util.hit.HitResult.Type.MISS) max = bh.getPos().squaredDistanceTo(s);
        EntityHitResult hit = ProjectileUtil.raycast(cam, s, e, cam.getBoundingBox().stretch(d.multiply(32)).expand(1), x -> x instanceof LivingEntity && x != mc.player && !x.isSpectator(), max);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            target = le;
            targetTicks = 40;
        } else if (targetTicks > 0 && --targetTicks == 0) target = null;
        if (target != null && (!target.isAlive() || target.isRemoved())) {
            target = null;
            targetTicks = 0;
        }
        LAG.entrySet().removeIf(en -> {
            Entity x = mc.world.getEntityById(en.getKey());
            if (!(x instanceof LivingEntity le) || !le.isAlive()) return true;
            float h = le.getHealth(), v = en.getValue();
            en.setValue(v > h ? Math.max(h, v - Math.max(0.15f, (v - h) * 0.12f)) : h);
            return false;
        });
    }

    public static void render(LivingEntity e, float td, MatrixStack ms, VertexConsumerProvider vcp, int light, EntityRenderDispatcher disp) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || CameraController.isCinematic() || !eligible(e)) return;
        boolean boss = isBoss(e);
        double dist = disp.getSquaredDistanceToCamera(e);
        if (dist > (boss ? 48 * 48 : 24 * 24)) return;
        float hp = Math.max(0, e.getHealth()), maxHp = Math.max(1, e.getMaxHealth());
        boolean show = boss || hp < maxHp - 0.01f || e == target || e.hurtTime > 0;
        if (!show) return;
        float lag = LAG.computeIfAbsent(e.getId(), k -> hp);
        float f = MathHelper.clamp(hp / maxHp, 0, 1), fl = MathHelper.clamp(Math.max(lag, hp) / maxHp, 0, 1);
        int w = boss ? 60 : 32, h = boss ? 4 : 3;
        float y = e.getHeight() + 0.5f + (e.hasCustomName() || boss ? 0.28f : 0f);
        ms.push();
        ms.translate(0, y, 0);
        ms.multiply(disp.getRotation());
        float sc = 0.025f;
        ms.scale(-sc, -sc, sc);
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getTextBackground());
        int L = 0xF000F0;
        float x0 = -w / 2f, x1 = w / 2f;
        quad(vc, m, x0 - 1, -1, x1 + 1, h + 1, 0.02f, 0xC0000000, L);
        quad(vc, m, x0, 0, x1, h, 0.015f, 0xFF2A2A2A, L);
        if (fl > f) quad(vc, m, x0 + w * f, 0, x0 + w * fl, h, 0.01f, 0xFFF0F0F0, L);
        int col = f > 0.5f ? lerp(0xF0E040, 0x50E050, (f - 0.5f) * 2) : lerp(0xE03030, 0xF0E040, f * 2);
        if (boss) col = lerp(0xC02020, 0xFF6A3A, f);
        quad(vc, m, x0, 0, x0 + w * f, h, 0.005f, 0xFF000000 | col, L);
        quad(vc, m, x0, 0, x0 + w * f, 1, 0.003f, 0x40FFFFFF, L);
        // 숫자: "34 / 40"
        TextRenderer tr = mc.textRenderer;
        String t = fmt(hp) + " / " + fmt(maxHp);
        ms.push();
        ms.translate(0, -7.5f, 0);
        ms.scale(0.75f, 0.75f, 1);
        Matrix4f tm = ms.peek().getPositionMatrix();
        float tx = -tr.getWidth(t) / 2f;
        tr.draw(t, tx, 0, 0xFFFFFFFF, true, tm, vcp, TextRenderer.TextLayerType.NORMAL, 0, L);
        ms.pop();
        ms.pop();
    }

    static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        vc.vertex(m, x0, y0, z).color(argb).light(light).next();
        vc.vertex(m, x0, y1, z).color(argb).light(light).next();
        vc.vertex(m, x1, y1, z).color(argb).light(light).next();
        vc.vertex(m, x1, y0, z).color(argb).light(light).next();
    }

    public static String fmt(float v) {
        if (v >= 100 || v == Math.floor(v)) return String.valueOf(Math.round(v));
        return String.valueOf(Math.round(v * 10) / 10f);
    }

    public static int lerp(int a, int b, float k) {
        k = MathHelper.clamp(k, 0, 1);
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    public static void reset() {
        LAG.clear();
        target = null;
        targetTicks = 0;
    }

    private HealthBars() {
    }
}

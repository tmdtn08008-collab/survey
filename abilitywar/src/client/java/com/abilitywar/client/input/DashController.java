package com.abilitywar.client.input;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 공통 대시 (Q). 누르고 있는 이동키 방향(없으면 앞)으로 짧게 돌진한다.
 * 이동은 본인 클라이언트가 바로 처리해서 입력 지연이 없다.
 */
public final class DashController {
    public static final String[] DIRS = {"forward", "back", "left", "right"};
    private static final float[] SPEED = {1.35f, 1.3f, 1.2f, 1.05f, 0.85f};
    private static int ticks = -1;
    private static Vec3d dir = Vec3d.ZERO;
    private static int kind;
    private static float fovKick, prevFovKick;

    public static void tryDash() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || ClientState.ability.isEmpty()) return;
        if (InputHandler.locked(p) || ClientState.shadowForm || ClientState.busy > 0 || ticks >= 0 || !ClientState.ready(SkillSlot.DASH)) {
            InputHandler.deny();
            return;
        }
        float f = p.input.movementForward, s = p.input.movementSideways;
        if (Math.abs(f) < 0.01f && Math.abs(s) < 0.01f) f = 1;
        kind = Math.abs(f) >= Math.abs(s) ? (f > 0 ? 0 : 1) : (s > 0 ? 2 : 3);
        float yaw = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d fwd = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        Vec3d left = new Vec3d(MathHelper.cos(yaw), 0, MathHelper.sin(yaw));
        dir = fwd.multiply(f).add(left.multiply(s)).normalize();

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(SkillSlot.DASH.ordinal());
        buf.writeFloat((float) dir.x);
        buf.writeFloat((float) dir.z);
        buf.writeByte(kind);
        ClientPlayNetworking.send(AWNet.C2S_SKILL, buf);

        // 즉시 반응 (서버 동기화가 곧 덮어씀)
        int i = SkillSlot.DASH.ordinal();
        ClientState.remaining[i] = 60;
        ClientState.total[i] = 60;
        ClientState.pressFlash[i] = 1f;
        PlayerAnimator.play(p.getId(), ClientState.ability + ".dash_" + DIRS[kind], 1f);
        ticks = 0;
        fovKick = kind == 1 ? -0.6f : 1f;
        p.setSprinting(kind == 0);
    }

    /** START_CLIENT_TICK: 이동 적용. */
    public static void tick(MinecraftClient mc) {
        prevFovKick = fovKick;
        fovKick *= 0.78f;
        if (Math.abs(fovKick) < 0.01f) fovKick = 0;
        ClientPlayerEntity p = mc.player;
        if (ticks < 0 || p == null || mc.isPaused()) return;
        if (ticks >= SPEED.length || (p.horizontalCollision && ticks > 1) || p.isTouchingWater() || p.hasVehicle()) {
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x * 0.45, v.y, v.z * 0.45);
            ticks = -1;
            return;
        }
        double sp = SPEED[ticks];
        double vy = p.getVelocity().y;
        if (ticks == 0 && p.isOnGround()) vy = 0.12;
        else vy = Math.max(vy, -0.08);
        p.setVelocity(dir.x * sp, vy, dir.z * sp);
        p.fallDistance = 0;
        ticks++;
    }

    public static boolean dashing() {
        return ticks >= 0;
    }

    /** 시야각 변화 (도). */
    public static double fov(float td) {
        return MathHelper.lerp(td, prevFovKick, fovKick) * 13.0;
    }

    public static void reset() {
        ticks = -1;
        fovKick = prevFovKick = 0;
    }

    private DashController() {
    }
}

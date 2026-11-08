package com.abilitywar.client.fx;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 날아다니는 가짜 블록 조각 (월드는 바뀌지 않음). */
public final class Debris {
    static final class D {
        BlockState state;
        Vec3d pos, prev, vel;
        Vector3f axis;
        float angle, prevAngle, angV, scale, age, life;
        @Nullable
        Vec3d attract;
        float attractK;
        boolean gravity;
    }

    private static final List<D> LIST = new ArrayList<>();
    private static final Random RND = new Random();

    public static void spawn(BlockState state, Vec3d pos, Vec3d vel, float scale, float life, @Nullable Vec3d attract, float attractK, boolean gravity) {
        if (state == null || state.isAir() || LIST.size() > 400) return;
        D d = new D();
        d.state = state;
        d.pos = d.prev = pos;
        d.vel = vel;
        d.axis = new Vector3f(RND.nextFloat() - 0.5f, RND.nextFloat() - 0.5f, RND.nextFloat() - 0.5f).normalize();
        d.angV = (RND.nextFloat() * 18 + 6) * (RND.nextBoolean() ? 1 : -1);
        d.angle = RND.nextFloat() * 360;
        d.scale = scale;
        d.life = life;
        d.attract = attract;
        d.attractK = attractK;
        d.gravity = gravity;
        LIST.add(d);
    }

    public static void tick() {
        for (int i = LIST.size() - 1; i >= 0; i--) {
            D d = LIST.get(i);
            d.prev = d.pos;
            d.prevAngle = d.angle;
            d.age++;
            if (d.age > d.life) {
                LIST.remove(i);
                continue;
            }
            if (d.attract != null) {
                Vec3d to = d.attract.subtract(d.pos);
                double dist = to.length();
                if (dist < 0.6) {
                    LIST.remove(i);
                    continue;
                }
                Vec3d dir = to.normalize();
                Vec3d swirl = dir.crossProduct(new Vec3d(0, 1, 0)).multiply(0.35);
                d.vel = d.vel.multiply(0.93).add(dir.add(swirl).multiply(d.attractK / Math.max(0.8, dist * 0.35)));
                if (dist < 2.5) d.scale *= 0.9f;
            }
            if (d.gravity) d.vel = d.vel.add(0, -0.06, 0).multiply(0.98);
            d.pos = d.pos.add(d.vel);
            d.angle += d.angV;
        }
    }

    public static void render(Fx.Ctx c) {
        if (LIST.isEmpty()) return;
        MinecraftClient mc = c.mc;
        var brm = mc.getBlockRenderManager();
        for (D d : LIST) {
            Vec3d p = d.prev.lerp(d.pos, c.tickDelta);
            float fade = Math.min(1f, (d.life - d.age) / 6f);
            float s = d.scale * Math.max(0.05f, fade);
            c.matrices.push();
            c.matrices.translate(p.x - c.cam.x, p.y - c.cam.y, p.z - c.cam.z);
            c.matrices.multiply(RotationAxis.of(d.axis).rotationDegrees(MathHelper.lerp(c.tickDelta, d.prevAngle, d.angle)));
            c.matrices.scale(s, s, s);
            c.matrices.translate(-0.5, -0.5, -0.5);
            int light = WorldRenderer.getLightmapCoordinates(c.world, BlockPos.ofFloored(p));
            brm.renderBlockAsEntity(d.state, c.matrices, c.imm, light, OverlayTexture.DEFAULT_UV);
            c.matrices.pop();
        }
    }

    public static void clear() {
        LIST.clear();
    }

    private Debris() {
    }
}

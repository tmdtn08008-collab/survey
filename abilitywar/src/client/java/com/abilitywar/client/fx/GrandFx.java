package com.abilitywar.client.fx;

import com.abilitywar.data.AWPlayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/**
 * 3차 전직 ("대") 의 화려함: 늘 몸에 두르는 오라 (발밑 이중 문장 + 금빛 왕관 고리 + 떠오르는 불티),
 * 스킬을 쓸 때마다 터지는 금빛 폭발 (모든 직업 공통).
 */
public final class GrandFx {
    public static final int GOLD = 0xFFD86A;
    static final Map<Integer, Long> LAST_BURST = new HashMap<>();
    /** 시전 폭발을 붙이지 않는 이펙트 (자주 오거나 피격 쪽 이펙트). */
    static final Set<String> SKIP = Set.of("shake", "level_up", "block_hit", "basic_taunted", "guardian_intercept", "angel_heal", "shield_fade",
            "capture", "dash");

    public static boolean isGrand(Entity e) {
        return e instanceof AWPlayer p && p.aw$hasFlag2(AWPlayer.FLAG2_GRAND);
    }

    /** 이펙트가 생길 때 (VfxManager.spawn): 시전자가 3차면 금빛 폭발을 덧붙임. */
    static void onFx(String type, NbtCompound n, ClientWorld w) {
        if (SKIP.contains(type) || !n.contains("c")) return;
        if (n.getBoolean("q")) return;
        Entity e = w.getEntityById(n.getInt("c"));
        if (!(e instanceof PlayerEntity) || !isGrand(e)) return;
        long now = w.getTime();
        Long last = LAST_BURST.get(e.getId());
        if (last != null && now - last < 6) return;
        LAST_BURST.put(e.getId(), now);
        int col = Effects.abilityColor(e);
        VfxManager.add(new Burst(e.getId(), col));
        Vec3d at = e.getPos().add(0, 1.1, 0);
        VfxManager.add(new Effects.Flash(at, 2.4f, GOLD, 8, Tex.FLARE));
        Effects.sparks(at, 14, 0.35f, GOLD, 0.07f);
        Effects.sparks(at, 10, 0.25f, col, 0.06f);
    }

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        for (PlayerEntity p : w.getPlayers()) {
            if (!isGrand(p) || p.isInvisible() || p.isSpectator()) continue;
            int col = Effects.abilityColor(p);
            boolean fp = Effects.isLocalFp(p);
            if ((now + p.getId()) % 3 == 0) {
                double a = R.nextDouble() * Math.PI * 2, rr = rf(0.35f, 0.75f);
                Particles.P pp = Particles.spawn(Kind.GLOW, p.getPos().add(Math.cos(a) * rr, rf(0.05f, fp ? 0.3f : 1.6f), Math.sin(a) * rr),
                        new Vec3d(0, rf(0.03f, 0.08f), 0), rf(14, 24), rf(0.04f, 0.07f));
                pp.color(R.nextInt(3) == 0 ? col : GOLD, 0.95f);
                pp.drag = 0.96f;
            }
            if (!fp && (now + p.getId()) % 11 == 0) {
                double a = R.nextDouble() * Math.PI * 2;
                Particles.P pp = Particles.spawn(Kind.STREAK, p.getPos().add(Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5), new Vec3d(0, 0.22, 0), 10, 0.05f);
                pp.color(GOLD, 1f);
            }
        }
        LAST_BURST.keySet().removeIf(id -> w.getEntityById(id) == null);
    }

    public static void clear() {
        LAST_BURST.clear();
    }

    public static void render(Fx.Ctx c) {
        float now = c.world.getTime() + c.tickDelta;
        for (PlayerEntity p : c.world.getPlayers()) {
            if (!isGrand(p) || p.isInvisible() || p.isSpectator()) continue;
            int col = Effects.abilityColor(p);
            Vec3d pos = p.getLerpedPos(c.tickDelta);
            Vec3d g = Effects.ground(c.world, pos.add(0, 0.3, 0), 3);
            Vec3d base = (g != null ? g : pos).add(0, 0.03, 0).subtract(c.cam);
            float pulse = 0.8f + 0.2f * MathHelper.sin(now * 0.12f + p.getId());
            // 발밑: 금빛 문장 + 직업 색 문장이 서로 반대로 돎
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y, base.z, 1.15f * pulse, now * 0.015f, Fx.r(GOLD), Fx.g(GOLD), Fx.b(GOLD), 0.5f);
            VertexConsumer c2 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
            Draw.flat(c2, c.m, base.x, base.y + 0.005, base.z, 0.8f, -now * 0.03f, Fx.r(col), Fx.g(col), Fx.b(col), 0.45f);
            if (Effects.isLocalFp(p)) continue;
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            // 몸을 감싸는 옅은 빛 기둥
            ClassFx.wall(gl, c.m, base, 0.55f, 2.1f, col, 0.1f * pulse, 32);
            // 머리 위 금빛 왕관 고리 (뾰족한 빛 8개)
            Vec3d head = pos.add(0, p.getHeight() + 0.35 + 0.04 * MathHelper.sin(now * 0.1f), 0).subtract(c.cam);
            ClassFx.ring(gl, c.m, head, 0.28f, 0.035f, GOLD, 0.85f, 40);
            float rot = now * 0.03f;
            for (int i = 0; i < 8; i++) {
                double a0 = rot + i * Math.PI / 4;
                float h = i % 2 == 0 ? 0.16f : 0.1f;
                Vec3d b0 = head.add(Math.cos(a0 - 0.12) * 0.28, 0, Math.sin(a0 - 0.12) * 0.28);
                Vec3d b1 = head.add(Math.cos(a0 + 0.12) * 0.28, 0, Math.sin(a0 + 0.12) * 0.28);
                Vec3d tip = head.add(Math.cos(a0) * 0.3, h, Math.sin(a0) * 0.3);
                ClassFx.quad4(gl, c.m, b0, b1, tip, tip, GOLD, 0.9f, 0.3f);
            }
        }
    }

    /** 스킬을 쓸 때: 발밑에서 퍼지는 이중 고리 + 솟는 빛기둥. */
    static final class Burst extends Fx {
        final int id, col;

        Burst(int id, int col) {
            super(14);
            this.id = id;
            this.col = col;
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta, t = a / life;
            Vec3d base = e.getLerpedPos(c.tickDelta).add(0, 0.06, 0).subtract(c.cam);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            float ee = 1 - (1 - t) * (1 - t);
            ClassFx.ring(gl, c.m, base, 0.6f + 3.2f * ee, 0.18f, GOLD, (1 - t) * 0.9f, 56);
            ClassFx.ring(gl, c.m, base.add(0, 0.02, 0), 0.4f + 2.2f * ee, 0.12f, col, (1 - t) * 0.8f, 56);
            if (!Effects.isLocalFp(e)) ClassFx.wall(gl, c.m, base, 0.6f + 0.4f * ee, 3.0f * (1 - t), GOLD, (1 - t) * 0.35f, 40);
        }
    }

    private GrandFx() {
    }
}

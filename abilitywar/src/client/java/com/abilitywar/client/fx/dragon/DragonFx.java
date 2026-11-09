package com.abilitywar.client.fx.dragon;

import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.AfterImages;
import com.abilitywar.client.fx.Debris;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.client.fx.Effects;
import com.abilitywar.client.fx.Fx;
import com.abilitywar.client.fx.HandTracker;
import com.abilitywar.client.fx.Particles;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.fx.VfxManager;
import com.abilitywar.data.AWPlayer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.randDir;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/** 용기사 이펙트. */
public final class DragonFx {
    public static final int FIRE = 0xFF7A2E;
    public static final int GOLD = 0xFFC060;
    public static final int EMBER = 0xFF4A12;
    static final Identifier FIRE_STRIP = Tex.fx("fire_strip");
    static final Identifier FLAME = Tex.fx("flame");

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "claw_slash" -> VfxManager.add(new Slash(w, n.getInt("e"), n.getFloat("yaw"), n.getFloat("tilt"), n.getFloat("s"), n.getBoolean("fire"), Slash.CLAW));
            case "wing_slash" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) VfxManager.add(new Slash(w, e.getId(), e.getYaw(), n.getBoolean("second") ? 25 : -10, 2.0f, false, Slash.WING));
            }
            case "tail_whip" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) VfxManager.add(new Slash(w, e.getId(), e.getYaw() + 180, n.getBoolean("down") ? -90 : 0, 1.7f, true, Slash.TAIL));
            }
            case "dragon_rush" -> rush(w, n.getInt("e"));
            case "dragon_mark" -> {
                MarkTracker.mark(n.getInt("e"), n.getInt("d"));
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) VfxManager.add(new Burst(e.getPos().add(0, e.getHeight() + 0.55, 0), 0.6f, 8, false));
            }
            case "mark_burst" -> {
                MarkTracker.unmark(n.getInt("e"));
                VfxManager.add(new Burst(p, 1.4f, 14, true));
            }
            case "wing_gust" -> VfxManager.add(new Gust(w, n.getInt("e"), p));
            case "dragon_scales" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) Effects.sparks(e.getPos().add(0, 1.1, 0), 14, 0.3f, GOLD, 0.06f);
            }
            case "scales_break" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) {
                    float yaw = (e instanceof LivingEntity le ? le.bodyYaw : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
                    Vec3d at = e.getPos().add(-MathHelper.sin(yaw) * 0.6, 1.2, MathHelper.cos(yaw) * 0.6);
                    VfxManager.add(new Burst(at, 1.6f, 12, false));
                    for (int i = 0; i < 26; i++) {
                        Particles.P pp = Particles.spawn(Kind.SPARK, at, randDir().multiply(rf(0.15f, 0.5f)), rf(10, 18), rf(0.08f, 0.14f));
                        pp.color(i % 2 == 0 ? GOLD : FIRE, 1f);
                        pp.grav = 0.02f;
                        pp.drag = 0.9f;
                    }
                    if (e == MinecraftClient.getInstance().player) com.abilitywar.client.fx.FpEffects.counterFlash();
                }
            }
            case "scales_fade" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) for (int i = 0; i < 10; i++)
                    Particles.spawn(Kind.SMOKE_ADD, e.getPos().add(rf(-0.4f, 0.4f), rf(0.4f, 1.6f), rf(-0.4f, 0.4f)), new Vec3d(0, 0.03, 0), 14, 0.25f).color(0x664433, 0.4f);
            }
            case "dragon_transform" -> VfxManager.add(new Transform(w, n.getInt("e"), p));
            case "dragon_transform_end" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                Vec3d at = e != null ? e.getPos() : p;
                for (int i = 0; i < 24; i++)
                    Particles.spawn(Kind.SMOKE_DARK, at.add(rf(-0.5f, 0.5f), rf(0.2f, 2f), rf(-0.5f, 0.5f)), randDir().multiply(0.05).add(0, 0.04, 0), rf(18, 30), rf(0.4f, 0.8f)).color(0x1A0E08, 0.55f);
                Effects.sparks(at.add(0, 1, 0), 20, 0.4f, EMBER, 0.08f);
            }
            case "dragon_explosion" -> VfxManager.add(new Explosion(w, p));
            case "dragon_ult" -> VfxManager.add(new Ult(w, n));
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 지속 효과 (용화 중)
    private static final Map<Integer, Vec3d> LAST_POS = new HashMap<>();

    public static void tick(ClientWorld w) {
        MarkTracker.tick(w);
        long now = ClientClock.ticks();
        for (PlayerEntity p : w.getPlayers()) {
            if (!((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_TRANSFORMED)) {
                LAST_POS.remove(p.getId());
                continue;
            }
            Vec3d last = LAST_POS.put(p.getId(), p.getPos());
            double moved = last == null ? 0 : p.getPos().subtract(last).horizontalLength();
            if (moved > 0.08 && now % 3 == 0) {
                AfterImages.request(p.getId(), 1, 1, FIRE, 8);
            }
            // 발 밑 불꽃 / 몸 주위 불티
            if (moved > 0.05) {
                for (int i = 0; i < 2; i++)
                    Particles.spawn(Kind.FLAME, p.getPos().add(rf(-0.3f, 0.3f), 0.1, rf(-0.3f, 0.3f)), new Vec3d(0, rf(0.02f, 0.06f), 0), rf(8, 14), rf(0.15f, 0.28f)).color(i == 0 ? FIRE : GOLD, 0.9f);
            }
            if (now % 2 == 0 && !Effects.isLocalFp(p)) {
                Particles.P pp = Particles.spawn(Kind.GLOW, p.getPos().add(rf(-0.5f, 0.5f), rf(0.4f, 1.9f), rf(-0.5f, 0.5f)), new Vec3d(0, rf(0.02f, 0.05f), 0), rf(14, 22), rf(0.04f, 0.07f));
                pp.color(EMBER, 1f);
            }
            if (Effects.isLocalFp(p) && now % 2 == 0) {
                // 1인칭: 주먹 발톱 불티
                for (int arm = 0; arm < 2; arm++) {
                    Vec3d h = HandTracker.hand(p, arm, 1f);
                    Particles.spawn(Kind.FLAME, h.add(rf(-0.05f, 0.05f), 0, rf(-0.05f, 0.05f)), new Vec3d(0, 0.03, 0), 8, 0.08f).color(FIRE, 0.7f);
                }
            }
        }
    }

    public static void render(Fx.Ctx c) {
        MarkTracker.render(c);
    }

    public static void clear() {
        MarkTracker.clear();
        LAST_POS.clear();
        ElderDragon.clear();
    }

    // ================================================================ 돌진
    static void rush(ClientWorld w, int id) {
        Entity e = w.getEntityById(id);
        if (e == null) return;
        AfterImages.request(id, 4, 1, FIRE, 10);
        Vec3d at = e.getPos();
        for (int i = 0; i < 16; i++) {
            Particles.spawn(Kind.SMOKE_ADD, at.add(rf(-0.4f, 0.4f), rf(0.1f, 0.4f), rf(-0.4f, 0.4f)), randDir().multiply(0.08).add(0, 0.03, 0), rf(10, 16), rf(0.25f, 0.45f)).color(0xAA6644, 0.25f);
            Particles.spawn(Kind.FLAME, at.add(rf(-0.3f, 0.3f), rf(0f, 1.6f), rf(-0.3f, 0.3f)), new Vec3d(0, 0.04, 0), rf(8, 14), rf(0.2f, 0.35f)).color(i % 2 == 0 ? FIRE : GOLD, 0.8f);
        }
    }

    // ================================================================ 베기 (발톱 / 날개 / 꼬리)
    static final class Slash extends Fx {
        static final int CLAW = 0, WING = 1, TAIL = 2;
        final int entity, kind;
        final float yaw, tilt, scale;
        final boolean fire;
        Vec3d center;
        final Vec3d[] pts = new Vec3d[20];
        final float[] wds = new float[20], als = new float[20];

        Slash(ClientWorld w, int entity, float yaw, float tilt, float scale, boolean fire, int kind) {
            super(kind == CLAW ? 8 : 10);
            this.entity = entity;
            this.yaw = yaw;
            this.tilt = tilt;
            this.scale = scale;
            this.fire = fire;
            this.kind = kind;
            Entity e = w.getEntityById(entity);
            center = e != null ? e.getPos() : Vec3d.ZERO;
        }

        Vec3d point(float a, float rad, Vec3d c, Vec3d f, Vec3d u) {
            return c.add(f.multiply(Math.cos(a) * rad * 0.62)).add(u.multiply(Math.sin(a) * rad));
        }

        Vec3d[] basis() {
            float y = yaw * MathHelper.RADIANS_PER_DEGREE;
            Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
            Vec3d right = new Vec3d(-f.z, 0, f.x);
            double tr = Math.toRadians(tilt);
            Vec3d u = right.multiply(Math.cos(tr)).add(new Vec3d(0, 1, 0).multiply(Math.sin(tr)));
            Vec3d nrm = u.crossProduct(f).normalize();
            return new Vec3d[]{f, u, nrm};
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(entity);
            if (e != null) center = e.getPos();
            if (age == 1 && (fire || kind == TAIL)) {
                Vec3d[] b = basis();
                Vec3d c = center.add(0, kind == TAIL ? 0.7 : 1.15, 0).add(b[0].multiply(0.4));
                for (int i = 0; i < 10; i++) {
                    float a = rf(-1.3f, 1.3f);
                    Vec3d p = point(a, 1.3f * scale, c, b[0], b[1]);
                    Particles.spawn(Kind.FLAME, p, b[1].multiply(-Math.signum(a) * 0.05).add(0, 0.03, 0), rf(8, 14), rf(0.18f, 0.3f) * scale).color(i % 2 == 0 ? FIRE : GOLD, 0.9f);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(entity);
            Vec3d base = e != null ? e.getLerpedPos(c.tickDelta) : center;
            Vec3d[] b = basis();
            float t = t(c);
            float head = Math.min(1, t * 3.2f), tail = Math.max(0, (t - 0.12f) * 1.6f);
            if (head <= tail) return;
            boolean fp = e != null && Effects.isLocalFp(e);
            Vec3d cc = base.add(0, kind == TAIL ? 0.7 : 1.15, 0).add(b[0].multiply(kind == WING ? (fp ? 1.1 : 0.2) : 0.35));
            float fpA = fp && kind != CLAW ? 0.45f : 1f;
            int lines = kind == CLAW ? 3 : 1;
            float rad = (kind == WING ? 1.4f : kind == TAIL ? 1.25f : 1.05f) * scale;
            float a0 = -1.45f, a1 = 1.45f;
            int col = kind == TAIL ? EMBER : kind == WING ? GOLD : FIRE;
            for (int l = 0; l < lines; l++) {
                float off = (l - (lines - 1) / 2f) * 0.14f;
                float rr = rad * (1 + off * 0.35f);
                int n = pts.length;
                for (int i = 0; i < n; i++) {
                    float k = tail + (head - tail) * i / (n - 1);
                    float a = a1 + (a0 - a1) * k;
                    pts[i] = point(a, rr, cc.add(b[2].multiply(off)), b[0], b[1]).subtract(c.cam);
                    float env = (float) Math.sin(Math.PI * i / (n - 1));
                    float w0 = (kind == WING ? 0.22f : kind == TAIL ? 0.16f : 0.07f) * scale;
                    wds[i] = w0 * (0.25f + 0.75f * env);
                    als[i] = env * (1 - t * t) * fpA;
                }
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                for (int i = 0; i < n; i++) wds[i] *= 2.4f;
                Draw.ribbon(g, c.m, pts, n, wds, r(col), g(col), b(col), als, 0.6f, 0);
                for (int i = 0; i < n; i++) wds[i] /= 2.4f * 2.2f;
                Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.95f, 0.8f, als, 0.6f, 0);
                if (kind == TAIL) {
                    VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
                    for (int i = 0; i < n; i++) wds[i] *= 4f;
                    Draw.ribbon(dk, c.m, pts, n, wds, 0.12f, 0.03f, 0.02f, als, 0.6f, 0);
                }
            }
        }
    }

    // ================================================================ 작은 불꽃 폭발 (표식 등)
    static final class Burst extends Fx {
        final Vec3d p;
        final float s;
        final boolean fireball;

        Burst(Vec3d p, float s, int life, boolean fireball) {
            super(life);
            this.p = p;
            this.s = s;
            this.fireball = fireball;
            int n = fireball ? 18 : 8;
            for (int i = 0; i < n; i++) {
                Particles.spawn(Kind.FLAME, p, randDir().multiply(rf(0.05f, 0.18f) * s), rf(8, 16), rf(0.2f, 0.4f) * s).color(i % 3 == 0 ? GOLD : FIRE, 0.9f);
                Particles.spawn(Kind.GLOW, p, randDir().multiply(rf(0.1f, 0.35f) * s), rf(10, 18), rf(0.05f, 0.09f)).color(EMBER, 1f);
            }
            if (fireball) {
                for (int i = 0; i < 6; i++)
                    Particles.spawn(Kind.SMOKE_DARK, p, randDir().multiply(0.05).add(0, 0.04, 0), rf(16, 26), rf(0.4f, 0.7f) * s).color(0x1A0E08, 0.5f).size1 = 1.2f * s;
            }
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float nf = nearFade(rp, s * 2);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, rp.x, rp.y, rp.z, s * 1.6f * (0.4f + t), 0, 1, 0.75f, 0.4f, (1 - t) * (1 - t) * nf);
            VertexConsumer r = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.billboard(r, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.3f + t * 1.5f), 0, r(FIRE), g(FIRE), b(FIRE), (1 - t) * 0.8f * nf);
            if (fireball) {
                VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(FLAME));
                Draw.billboard(fl, c.m, c.camRot, rp.x, rp.y + t * 0.4, rp.z, s * (0.5f + t * 0.8f), 0, 1, 0.5f, 0.15f, (1 - t) * nf);
            }
        }
    }

    // ================================================================ 날개 돌풍
    static final class Gust extends Fx {
        final Vec3d p;

        Gust(ClientWorld w, int id, Vec3d at) {
            super(14);
            Entity ent = w.getEntityById(id);
            double r0 = ent != null && Effects.isLocalFp(ent) ? 1.8 : 0.8;
            Vec3d g = Effects.ground(w, at.add(0, 0.5, 0), 3);
            p = g != null ? g.add(0, 0.04, 0) : at;
            for (int i = 0; i < 36; i++) {
                double a = i / 36.0 * Math.PI * 2;
                Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                Particles.P s = Particles.spawn(Kind.STREAK, p.add(dir.multiply(r0)).add(0, rf(0.3f, 1.8f), 0), dir.multiply(rf(0.5f, 0.9f)), rf(6, 10), rf(0.06f, 0.1f));
                s.color(i % 2 == 0 ? GOLD : 0xFFFFFF, 0.8f);
                s.drag = 0.85f;
                Particles.spawn(Kind.SMOKE_ADD, p.add(dir.multiply(0.6)).add(0, 0.15, 0), dir.multiply(rf(0.2f, 0.35f)), rf(12, 20), rf(0.35f, 0.6f)).color(0xC0A080, 0.25f).size1 = 1.1f;
            }
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float e = 1 - (1 - t) * (1 - t);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.flat(sw, c.m, rp.x, rp.y, rp.z, 0.6f + e * 5f, 0, r(GOLD), g(GOLD), b(GOLD), (1 - t) * 0.75f);
            Draw.flat(sw, c.m, rp.x, rp.y + 1.0, rp.z, 0.4f + e * 4f, 1, 1f, 0.8f, 0.6f, (1 - t) * 0.35f);
        }
    }

    // ================================================================ 용화 (변신)
    static final class Transform extends Fx {
        final int id;
        final Vec3d p;

        Transform(ClientWorld w, int id, Vec3d at) {
            super(26);
            this.id = id;
            Vec3d g = Effects.ground(w, at.add(0, 0.5, 0), 3);
            p = g != null ? g.add(0, 0.04, 0) : at;
            for (int i = 0; i < 48; i++) {
                double a = i / 48.0 * Math.PI * 2;
                Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                Particles.spawn(Kind.FLAME, p.add(dir.multiply(0.5)).add(0, 0.2, 0), dir.multiply(rf(0.2f, 0.45f)).add(0, rf(0.02f, 0.08f), 0), rf(12, 20), rf(0.3f, 0.6f)).color(i % 3 == 0 ? GOLD : FIRE, 0.9f);
            }
            for (int i = 0; i < 40; i++)
                Particles.spawn(Kind.GLOW, p.add(rf(-0.8f, 0.8f), rf(0, 2.2f), rf(-0.8f, 0.8f)), new Vec3d(0, rf(0.05f, 0.18f), 0), rf(16, 30), rf(0.05f, 0.1f)).color(EMBER, 1f);
            for (int i = 0; i < 12; i++)
                Particles.spawn(Kind.FLAME, p.add(rf(-0.3f, 0.3f), rf(0.2f, 1.8f), rf(-0.3f, 0.3f)), new Vec3d(0, rf(0.1f, 0.25f), 0), rf(10, 18), rf(0.4f, 0.8f)).color(GOLD, 0.8f);
            CameraController.hitstop(2f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.flat(sw, c.m, rp.x, rp.y, rp.z, 0.6f + e * 6f, 0, r(FIRE), g(FIRE), b(FIRE), (1 - t) * 0.9f);
            VertexConsumer rune = c.imm.getBuffer(AWRenderLayers.additive(MarkTracker.RUNE));
            Draw.flat(rune, c.m, rp.x, rp.y + 0.01, rp.z, 1.8f + e * 0.8f, t * 2, r(GOLD), g(GOLD), b(GOLD), (1 - t) * 0.9f);
            VertexConsumer cr = c.imm.getBuffer(AWRenderLayers.translucent(Tex.CRACK));
            Draw.flat(cr, c.m, rp.x, rp.y + 0.005, rp.z, 2.2f, 0.4f, 0.05f, 0.02f, 0.01f, (1 - t) * 0.8f);
            Vec3d up = rp.add(0, 1.1, 0);
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            float ft = Math.min(1, t * 3);
            Draw.billboard(fl, c.m, c.camRot, up.x, up.y, up.z, 3.5f * (0.4f + ft), 0, 1, 0.7f, 0.35f, (1 - ft) * nearFade(up, 3));
            // 불기둥
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(FLAME));
            for (int i = 0; i < 6; i++) {
                double a = i / 6.0 * Math.PI * 2 + t * 3;
                Vec3d b = rp.add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
                Draw.billboard(st, c.m, c.camRot, b.x, b.y + 0.9 + t, b.z, 1.1f * (1 - t * 0.6f), 0, 1, 0.5f, 0.15f, (1 - t) * 0.8f);
            }
        }
    }

    // ================================================================ 브레스 폭발 + 불타는 지면
    static final class Explosion extends Fx {
        final Vec3d p;
        final float rot = R.nextFloat() * 6.28f;

        Explosion(ClientWorld w, Vec3d at) {
            super(80);
            Vec3d g = Effects.ground(w, at.add(0, 1, 0), 4);
            p = g != null ? g.add(0, 0.04, 0) : at;
            for (int i = 0; i < 60; i++) {
                Vec3d d = randDir();
                if (d.y < 0) d = new Vec3d(d.x, -d.y * 0.4, d.z);
                Particles.spawn(Kind.FLAME, p.add(0, 0.5, 0), d.multiply(rf(0.2f, 0.7f)), rf(14, 26), rf(0.6f, 1.4f)).color(i % 3 == 0 ? GOLD : FIRE, 0.95f).size1 = 2f;
            }
            for (int i = 0; i < 30; i++) {
                Vec3d d = randDir();
                Particles.spawn(Kind.SMOKE_DARK, p.add(0, 1, 0), new Vec3d(d.x * 0.25, Math.abs(d.y) * 0.25 + 0.05, d.z * 0.25), rf(30, 50), rf(0.8f, 1.5f)).color(0x140A06, 0.6f).size1 = 3f;
            }
            Effects.sparks(p.add(0, 0.5, 0), 70, 1.3f, GOLD, 0.16f);
            BlockState st = Effects.groundState(w, p);
            if (st != null) {
                BlockStateParticleEffect bp = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
                for (int i = 0; i < 16; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    Debris.spawn(st, p.add(Math.cos(a) * rf(0, 2f), 0.2, Math.sin(a) * rf(0, 2f)), new Vec3d(Math.cos(a) * rf(0.1f, 0.35f), rf(0.4f, 0.8f), Math.sin(a) * rf(0.1f, 0.35f)), rf(0.25f, 0.5f), rf(20, 34), null, 0, true);
                }
                for (int i = 0; i < 60; i++) {
                    double a = R.nextDouble() * Math.PI * 2, d = R.nextDouble() * 5;
                    w.addParticle(bp, p.x + Math.cos(a) * d, p.y + 0.1, p.z + Math.sin(a) * d, Math.cos(a) * 0.4, rf(0.2f, 0.6f), Math.sin(a) * 0.4);
                }
            }
            CameraController.hitstop(4f);
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age > 4 && age < 64) {
                for (int i = 0; i < 4; i++) {
                    double a = R.nextDouble() * Math.PI * 2, d = Math.sqrt(R.nextDouble()) * 5;
                    Particles.spawn(Kind.FLAME, p.add(Math.cos(a) * d, 0.1, Math.sin(a) * d), new Vec3d(0, rf(0.04f, 0.1f), 0), rf(10, 20), rf(0.3f, 0.6f)).color(i % 2 == 0 ? FIRE : GOLD, 0.85f);
                }
                if (age % 3 == 0)
                    Particles.spawn(Kind.SMOKE_DARK, p.add(rf(-4, 4), 0.5, rf(-4, 4)), new Vec3d(0, 0.06, 0), 30, 0.7f).color(0x140A06, 0.4f).size1 = 1.6f;
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float t = a / life;
            Vec3d rp = p.subtract(c.cam);
            float e = 1 - (float) Math.pow(1 - Math.min(1, a / 12f), 3);
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            float ft = Math.min(1, a / 8f);
            Draw.billboard(fl, c.m, c.camRot, rp.x, rp.y + 1.2, rp.z, 12 * (0.3f + ft), 0, 1, 0.8f, 0.45f, (1 - ft) * nearFade(rp, 6));
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            float gt = Math.min(1, a / 20f);
            Draw.billboard(g, c.m, c.camRot, rp.x, rp.y + 1.5, rp.z, 8 * e, 0, r(FIRE), g(FIRE), b(FIRE), (1 - gt) * 0.85f * nearFade(rp, 5));
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            float st = Math.min(1, a / 14f);
            Draw.flat(sw, c.m, rp.x, rp.y, rp.z, 1 + 11 * e, 0, r(GOLD), g(GOLD), b(GOLD), (1 - st) * 0.8f);
            // 그을린 땅 + 빛나는 균열 (불타는 동안)
            float burn = a < 64 ? 1 : Math.max(0, 1 - (a - 64) / 16f);
            VertexConsumer cr = c.imm.getBuffer(AWRenderLayers.translucent(Tex.CRACK));
            Draw.flat(cr, c.m, rp.x, rp.y + 0.005, rp.z, 5.5f, rot, 0.06f, 0.02f, 0.01f, 0.85f * burn);
            VertexConsumer cg = c.imm.getBuffer(AWRenderLayers.additive(Tex.CRACK));
            float flick = 0.7f + 0.3f * (float) Math.sin(a * 0.7);
            Draw.flat(cg, c.m, rp.x, rp.y + 0.01, rp.z, 5.5f, rot, 1f, 0.35f, 0.08f, 0.7f * burn * flick);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.flat(gl, c.m, rp.x, rp.y + 0.02, rp.z, 6f, 0, 1f, 0.3f, 0.05f, 0.35f * burn);
        }
    }

    // ================================================================ 궁극기: 고룡 강림
    static final class Ult extends Fx {
        final int caster;
        final Vec3d target, fwd, start;
        final float yaw;
        final Vec3d dragonPos;
        final Vec3d targetLocal;
        Vec3d mouth;
        static final float SCALE = 1.0f;

        Ult(ClientWorld w, NbtCompound n) {
            super(92);
            caster = n.getInt("c");
            target = new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz"));
            start = new Vec3d(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"));
            Vec3d d = target.subtract(start);
            d = new Vec3d(d.x, 0, d.z);
            if (d.lengthSquared() < 0.5) {
                float y = n.getFloat("yaw") * MathHelper.RADIANS_PER_DEGREE;
                d = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
            }
            fwd = d.normalize();
            yaw = (float) (MathHelper.atan2(fwd.z, fwd.x) * 57.2957795) - 90f;
            Vec3d back = start.subtract(fwd.multiply(10));
            Vec3d g = Effects.ground(w, back.add(0, 3, 0), 8);
            dragonPos = g != null ? g : new Vec3d(back.x, start.y, back.z);
            // 목표를 고룡의 로컬 좌표로
            Vec3d td = target.add(0, 0.6, 0).subtract(dragonPos);
            double th = Math.toRadians(-yaw), cs = Math.cos(th), sn = Math.sin(th);
            targetLocal = new Vec3d(td.x * cs - td.z * sn, td.y, td.x * sn + td.z * cs);
        }

        float alpha(float a) {
            float in = MathHelper.clamp((a - 5) / 12f, 0, 1);
            float out = 1 - MathHelper.clamp((a - 72) / 14f, 0, 1);
            return in * out;
        }

        Vec3d pos(float a) {
            float rise = MathHelper.clamp((a - 4) / 16f, 0, 1);
            rise = 1 - (1 - rise) * (1 - rise) * (1 - rise);
            return dragonPos.add(0, -11 * (1 - rise), 0);
        }

        // t, 머리(x,y,z), 시선(x,y,z), 일으킴, 날개, 날갯짓, 입, 목구멍, 앞발
        static final float[][] KEYS = {
                {0, 0, 7.0f, 6.0f, 0, 6.5f, 12, 0, 0.1f, 0.2f, 0, 0, 0},
                {18, 0, 8.5f, 6.8f, 0, 8.0f, 12, 0, 0.35f, 0, 0.1f, 0, 0},
                {24, 0, 10.5f, 5.2f, 0, 12.5f, 9, 0.25f, 0.6f, -0.3f, 0.15f, 0.2f, 0.3f},
                {28, 0, 13.0f, 9.0f, 0, 12.4f, 15, 1.0f, 1.0f, -0.55f, 1.0f, 0.4f, 1},
                {40, 0, 12.6f, 9.4f, 0, 11.8f, 15, 0.9f, 1.0f, -0.4f, 0.9f, 0.3f, 1},
                {43, 0, 11.5f, 8.6f, 0, 10.8f, 15, 0.55f, 1.0f, -0.8f, 0.4f, 0.2f, 0.6f},
                {47, 0, 9.5f, 7.5f, 0, 8.6f, 14, 0.25f, 0.95f, 0.85f, 0.2f, 0.2f, 0.2f},
                {52, 0, 11.0f, 5.0f, 0, 11.8f, 10, 0.4f, 0.9f, 0, 0.5f, 1.0f, 0.3f},
                {72, 0, 8.5f, 6.5f, 0, 8.0f, 12, 0.1f, 0.5f, 0.2f, 0.1f, 0, 0},
                {200, 0, 8.5f, 6.5f, 0, 8.0f, 12, 0, 0.3f, 0.2f, 0, 0, 0},
        };

        static float sm(float k) {
            k = MathHelper.clamp(k, 0, 1);
            return k * k * (3 - 2 * k);
        }

        ElderDragon.Pose pose(float a) {
            ElderDragon.Pose p = new ElderDragon.Pose();
            int i = 0;
            while (i < KEYS.length - 2 && a > KEYS[i + 1][0]) i++;
            float[] k0 = KEYS[i], k1 = KEYS[i + 1];
            float k = sm((a - k0[0]) / (k1[0] - k0[0]));
            float[] v = new float[k0.length];
            for (int j = 0; j < v.length; j++) v[j] = k0[j] + (k1[j] - k0[j]) * k;
            p.head = new Vec3d(v[1], v[2], v[3]);
            p.look = new Vec3d(v[4], v[5], v[6]);
            p.rear = v[7];
            p.wingSpread = v[8];
            p.wingFlap = v[9] + 0.22f * MathHelper.sin(a * 0.3f) * (a < 40 || a > 50 ? 1 : 0);
            p.jaw = v[10];
            p.throat = v[11];
            p.clawRaise = v[12];
            p.time = a;
            p.breathe = 0.5f + 0.5f * MathHelper.sin(a * 0.25f);
            p.shake = a >= 27 && a < 40 ? 1f - (a - 27) / 13f : 0;
            // 브레스: 목표를 향해 목을 뻗음
            float br = sm((a - 53) / 4f) * (1 - sm((a - 68) / 5f));
            if (br > 0) {
                Vec3d nb = new Vec3d(0, 7.0, 3.4);
                Vec3d dir = targetLocal.subtract(nb).normalize();
                Vec3d hb = nb.add(dir.multiply(7.2)).add(0, 1.4, 0);
                p.head = ElderDragon.lerp(p.head, hb, br);
                p.look = ElderDragon.lerp(p.look, targetLocal, br);
                p.jaw = MathHelper.lerp(br, p.jaw, 1f);
                p.throat = Math.max(p.throat, br);
                p.rear = MathHelper.lerp(br, p.rear, 0.3f);
                p.wingSpread = MathHelper.lerp(br, p.wingSpread, 0.95f);
                p.wingFlap = MathHelper.lerp(br, p.wingFlap, -0.25f);
            }
            return p;
        }

        boolean breathing(float a) {
            return a >= 56 && a < 69;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity c = w.getEntityById(caster);
            // 1. 손에 용의 힘을 모은다
            if (age < 24 && c != null) {
                for (int arm = 0; arm < 2; arm++) {
                    Vec3d h = HandTracker.hand(c, arm, 1f);
                    for (int i = 0; i < 3; i++) {
                        Vec3d from = h.add(randDir().multiply(rf(1.2f, 2.5f)));
                        Particles.P pp = Particles.spawn(Kind.FLAME, from, h.subtract(from).multiply(0.12), 10, rf(0.12f, 0.22f));
                        pp.color(i == 0 ? GOLD : FIRE, 0.9f);
                        pp.drag = 1f;
                    }
                }
            }
            // 땅에서 솟는 불티 + 고룡이 솟아오르는 불구덩이
            if (age < 60 && age % 2 == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = R.nextDouble() * Math.PI * 2, d = rf(0.5f, 3.5f);
                    Particles.spawn(Kind.GLOW, start.add(Math.cos(a) * d, 0.1, Math.sin(a) * d), new Vec3d(0, rf(0.08f, 0.2f), 0), rf(18, 30), rf(0.05f, 0.1f)).color(EMBER, 1f);
                }
            }
            if (age >= 3 && age < 24) {
                for (int i = 0; i < 10; i++) {
                    double a = R.nextDouble() * Math.PI * 2, d = Math.sqrt(R.nextDouble()) * 7;
                    Vec3d p0 = dragonPos.add(Math.cos(a) * d, 0.2, Math.sin(a) * d);
                    Particles.spawn(Kind.FLAME, p0, new Vec3d(0, rf(0.2f, 0.5f), 0), rf(12, 22), rf(0.8f, 1.6f)).color(i % 3 == 0 ? GOLD : FIRE, 0.9f).size1 = 2.2f;
                }
                if (age % 2 == 0) for (int i = 0; i < 3; i++)
                    Particles.spawn(Kind.SMOKE_DARK, dragonPos.add(rf(-5, 5), 0.5, rf(-5, 5)), new Vec3d(0, 0.12, 0), 34, 1.6f).color(0x160A06, 0.5f).size1 = 3f;
            }
            // 몸에서 피어오르는 불티 (환영 느낌)
            if (age >= 6 && age < 84 && !ElderDragon.SAMPLES.isEmpty()) {
                int n = age < 20 ? 14 : 6;
                for (int i = 0; i < n; i++) {
                    Vec3d sp = ElderDragon.SAMPLES.get(R.nextInt(ElderDragon.SAMPLES.size()));
                    Particles.P pp = Particles.spawn(i % 3 == 0 ? Kind.FLAME : Kind.GLOW, sp.add(rf(-0.4f, 0.4f), rf(-0.4f, 0.4f), rf(-0.4f, 0.4f)), new Vec3d(0, rf(0.04f, 0.14f), 0), rf(14, 26), i % 3 == 0 ? rf(0.3f, 0.6f) : rf(0.06f, 0.12f));
                    pp.color(i % 2 == 0 ? FIRE : EMBER, 0.9f);
                }
            }
            // 3. 포효: 입에서 터지는 충격
            if (age == 28 && mouth != null) {
                for (int i = 0; i < 70; i++)
                    Particles.spawn(Kind.STREAK, mouth, randDir().multiply(rf(0.7f, 1.6f)), rf(8, 14), rf(0.12f, 0.2f)).color(i % 2 == 0 ? GOLD : FIRE, 0.9f).drag = 0.88f;
                // 땅의 먼지 고리
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                    Particles.spawn(Kind.SMOKE_ADD, dragonPos.add(dir.multiply(3)).add(0, 0.3, 0), dir.multiply(rf(0.4f, 0.7f)), rf(16, 26), rf(0.8f, 1.4f)).color(0xC09070, 0.3f).size1 = 2f;
                }
            }
            // 날개를 내려칠 때 바람
            if (age == 45) {
                for (int i = 0; i < 50; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                    Particles.spawn(Kind.SMOKE_ADD, dragonPos.add(dir.multiply(rf(2, 8))).add(0, 0.3, 0), dir.multiply(rf(0.5f, 1.0f)), rf(14, 24), rf(0.8f, 1.6f)).color(0xB08060, 0.28f).size1 = 2.2f;
                }
            }
            // 5. 브레스
            if (breathing(age) && mouth != null) {
                Vec3d dir = target.add(0, 0.5, 0).subtract(mouth);
                for (int i = 0; i < 14; i++) {
                    double k = R.nextDouble();
                    Vec3d p = mouth.add(dir.multiply(k)).add(randDir().multiply(0.3 + k * 1.6));
                    Particles.spawn(Kind.FLAME, p, dir.normalize().multiply(rf(0.5f, 0.9f)), rf(6, 10), rf(0.6f, 1.2f) * (float) (0.6 + k)).color(i % 3 == 0 ? GOLD : FIRE, 0.95f);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            // 바닥 룬 (용기사 발밑)
            Vec3d g = Effects.ground(c.world, start.add(0, 0.5, 0), 4);
            if (g != null && a < 60) {
                Vec3d gp = g.add(0, 0.04, 0).subtract(c.cam);
                float grow = Effects.outBack(Math.min(1, a / 10f));
                float fade = a > 50 ? Math.max(0, 1 - (a - 50) / 10f) : 1;
                VertexConsumer rune = c.imm.getBuffer(AWRenderLayers.additive(MarkTracker.RUNE));
                Draw.flat(rune, c.m, gp.x, gp.y, gp.z, 3.6f * grow, a * 0.02f, r(FIRE), g(FIRE), b(FIRE), 0.9f * fade);
                VertexConsumer mc = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
                Draw.flat(mc, c.m, gp.x, gp.y + 0.01, gp.z, 5f * grow, -a * 0.015f, r(GOLD), g(GOLD), b(GOLD), 0.55f * fade);
            }
            // 고룡이 솟아오르는 불의 원
            if (a < 80) {
                Vec3d dp = dragonPos.add(0, 0.05, 0).subtract(c.cam);
                float grow = Effects.outBack(Math.min(1, a / 8f));
                float fade = a > 64 ? Math.max(0, 1 - (a - 64) / 16f) : 1;
                VertexConsumer rune = c.imm.getBuffer(AWRenderLayers.additive(MarkTracker.RUNE));
                Draw.flat(rune, c.m, dp.x, dp.y, dp.z, 9f * grow, -a * 0.01f, r(FIRE), g(FIRE), b(FIRE), 0.85f * fade);
                VertexConsumer cr = c.imm.getBuffer(AWRenderLayers.additive(Tex.CRACK));
                Draw.flat(cr, c.m, dp.x, dp.y + 0.01, dp.z, 8f * grow, 0.3f, 1f, 0.35f, 0.08f, 0.7f * fade);
            }
            // 2. 고룡
            float da = alpha(a);
            ElderDragon.Pose pose = pose(a);
            float glow = 1.0f + 0.4f * pose.throat + (a >= 27 && a < 40 ? 0.35f : 0);
            ElderDragon.render(c, pos(a), yaw, SCALE, pose, da, glow);
            mouth = ElderDragon.lastMouth;
            if (da > 0.01f) {
                // 목구멍 불빛
                if (pose.throat > 0.05f) {
                    Vec3d mm = mouth.subtract(c.cam);
                    VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
                    Draw.billboard(fl, c.m, c.camRot, mm.x, mm.y, mm.z, 2.5f * pose.throat, 0, 1f, 0.55f, 0.15f, pose.throat * da * nearFade(mm, 2));
                }
            }
            // 포효 충격파
            if (a >= 28 && a < 42 && mouth != null) {
                float k = (a - 28) / 14f;
                Vec3d m = mouth.subtract(c.cam);
                VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
                for (int i = 0; i < 3; i++) {
                    float kk = Math.min(1, k * 1.3f + i * 0.15f);
                    Draw.billboard(sw, c.m, c.camRot, m.x, m.y, m.z, 2 + kk * 12, 0, r(FIRE), g(FIRE), b(FIRE), (1 - kk) * 0.35f * nearFade(m, 4));
                }
            }
            // 5. 브레스 광선 (입에서 목표까지)
            if (a >= 55 && a < 70 && mouth != null) {
                float k = MathHelper.clamp((a - 55) / 3f, 0, 1) * (1 - MathHelper.clamp((a - 66) / 4f, 0, 1));
                Vec3d m = mouth;
                Vec3d end = target.add(0, 0.6, 0);
                int n = 18;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    double t = (double) i / (n - 1);
                    pts[i] = m.lerp(end, t).subtract(c.cam);
                    wds[i] = (float) (0.6 + t * 2.2) * k;
                    als[i] = k;
                }
                VertexConsumer fs = c.imm.getBuffer(AWRenderLayers.additive(FIRE_STRIP));
                Draw.ribbon(fs, c.m, pts, n, wds, 1, 1, 1, als, 0.25f, -a * 0.35f);
                for (int i = 0; i < n; i++) wds[i] *= 0.45f;
                Draw.ribbon(fs, c.m, pts, n, wds, 1, 0.95f, 0.8f, als, 0.4f, -a * 0.6f);
                Vec3d e = end.subtract(c.cam);
                VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
                Draw.billboard(fl, c.m, c.camRot, e.x, e.y, e.z, 5f * k, a * 0.1f, 1, 0.7f, 0.3f, k * nearFade(e, 3));
                Vec3d mm = m.subtract(c.cam);
                Draw.billboard(fl, c.m, c.camRot, mm.x, mm.y, mm.z, 3.5f * k, 0, 1, 0.8f, 0.4f, k * nearFade(mm, 2));
            }
        }
    }

    private DragonFx() {
    }
}

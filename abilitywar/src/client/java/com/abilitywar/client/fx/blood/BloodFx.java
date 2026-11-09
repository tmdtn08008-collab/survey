package com.abilitywar.client.fx.blood;

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
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.randDir;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/** 혈귀 이펙트. */
public final class BloodFx {
    public static final int CRIMSON = 0xD0102A;
    public static final int BRIGHT = 0xFF4050;
    public static final int DARK = 0x200004;
    static final Identifier SPIKE = Tex.fx("blood_spike");
    static final Identifier POOL = Tex.fx("blood_pool");
    static final Identifier BAT = Tex.fx("blood_bat");
    static final Identifier MOON = Tex.fx("blood_moon");
    static final Identifier CHAIN = Tex.fx("blood_chain");

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "blood_bleed" -> {
                int s = n.getInt("s");
                if (s <= 0) BLEED.remove(n.getInt("e"));
                else BLEED.put(n.getInt("e"), new long[]{s, w.getTime() + 160});
            }
            case "blood_burst" -> VfxManager.add(new Burst(w, p, n.contains("s") ? n.getFloat("s") : 1f));
            case "blood_heal" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) VfxManager.add(new Heal(e.getId(), Math.min(1.5f, 0.4f + n.getFloat("a") * 0.15f)));
            }
            case "blood_slash" -> VfxManager.add(new Slash(w, n.getInt("e"), n.getFloat("yaw"), n.getFloat("tilt"), n.getFloat("s")));
            case "blood_spikes" -> VfxManager.add(new Spikes(w, p, n.getFloat("r"), n.getBoolean("big")));
            case "blood_thread" -> VfxManager.add(new Thread(n.getInt("c"), n.getInt("e"), n.contains("tx") ? new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz")) : null, n.getInt("d"), n.getBoolean("enh")));
            case "blood_rush" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) {
                    AfterImages.request(e.getId(), 3, 1, CRIMSON, 10);
                    VfxManager.add(new Bats(w, e.getId(), e.getPos(), e.getPos().add(e.getRotationVec(1f).multiply(3)), 6, 10));
                }
            }
            case "blood_drain" -> VfxManager.add(new Drain(n.getInt("e"), n.getInt("c"), n.getInt("d"), n.getBoolean("big")));
            case "blood_veil" -> VfxManager.add(new Veil(n.getInt("c"), n.getInt("d")));
            case "blood_veil_burst" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) {
                    VfxManager.add(new Burst(w, e.getPos().add(0, 1, 0), 1.6f));
                    if (e == MinecraftClient.getInstance().player) com.abilitywar.client.fx.FpEffects.counterFlash();
                }
            }
            case "blood_veil_fade" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) for (int i = 0; i < 20; i++)
                    Particles.spawn(Kind.SMOKE_DARK, e.getPos().add(rf(-0.6f, 0.6f), rf(0.2f, 1.8f), rf(-0.6f, 0.6f)), randDir().multiply(0.03), rf(12, 20), rf(0.3f, 0.5f)).color(DARK, 0.5f);
            }
            case "blood_bats" -> VfxManager.add(new Bats(w, n.getInt("e"), p, new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz")), n.getInt("d"), 30));
            case "blood_ult" -> VfxManager.add(new Ult(w, n.getInt("c"), p));
            case "blood_chains" -> VfxManager.add(new Chains(n.getInt("c"), n.getIntArray("ids")));
            default -> {
                return false;
            }
        }
        return true;
    }

    static void droplets(Vec3d p, int n, float speed) {
        for (int i = 0; i < n; i++) {
            Particles.P pp = Particles.spawn(Kind.GLOW, p, randDir().multiply(rf(0.05f, speed)).add(0, rf(0.02f, speed * 0.6f), 0), rf(12, 22), rf(0.05f, 0.1f));
            pp.color(i % 3 == 0 ? BRIGHT : CRIMSON, 1f);
            pp.grav = 0.045f;
            pp.drag = 0.96f;
        }
    }

    // ================================================================ 출혈 표시 (단계 = 머리 위 핏방울 수)
    static final Map<Integer, long[]> BLEED = new HashMap<>();

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        for (Iterator<Map.Entry<Integer, long[]>> it = BLEED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, long[]> en = it.next();
            Entity e = w.getEntityById(en.getKey());
            if (e == null || !e.isAlive() || now > en.getValue()[1]) {
                it.remove();
                continue;
            }
            int s = (int) en.getValue()[0];
            if (now % Math.max(2, 8 - s) == 0) {
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.3f, 0.3f), rf(0.4f, e.getHeight() * 0.9f), rf(-0.3f, 0.3f)), new Vec3d(0, -0.02, 0), 16, 0.06f);
                pp.color(CRIMSON, 1f);
                pp.grav = 0.03f;
            }
        }
    }

    public static void render(Fx.Ctx c) {
        for (Map.Entry<Integer, long[]> en : BLEED.entrySet()) {
            Entity e = c.world.getEntityById(en.getKey());
            if (e == null) continue;
            int s = (int) en.getValue()[0];
            Vec3d p = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.5, 0).subtract(c.cam);
            float nf = Fx.nearFade(p, 0.5f);
            Vector3f right = c.camRot.transform(new Vector3f(1, 0, 0));
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (int i = 0; i < s; i++) {
                float off = (i - (s - 1) / 2f) * 0.17f;
                double x = p.x + right.x * off, y = p.y + right.y * off, z = p.z + right.z * off;
                Draw.billboard(g, c.m, c.camRot, x, y, z, 0.16f, 0, 1f, 0.12f, 0.15f, 0.9f * nf);
            }
        }
    }

    public static void clear() {
        BLEED.clear();
    }

    // ================================================================ 피 튀김 폭발
    static final class Burst extends Fx {
        final Vec3d p;
        final float s;

        Burst(ClientWorld w, Vec3d p, float s) {
            super(16);
            this.p = p;
            this.s = s;
            droplets(p, (int) (24 * s), 0.25f * s);
            for (int i = 0; i < 8 * s; i++)
                Particles.spawn(Kind.SMOKE_DARK, p, randDir().multiply(rf(0.04f, 0.12f) * s), rf(14, 24), rf(0.4f, 0.7f) * s).color(0x3A0006, 0.55f).size1 = 1.4f * s;
            if (s >= 1.4f) CameraController.hitstop(2f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float nf = nearFade(rp, s * 2);
            VertexConsumer r = c.imm.getBuffer(AWRenderLayers.translucent(Tex.SHOCKWAVE));
            Draw.billboard(r, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.4f + 2.2f * t), 0, 0.5f, 0.02f, 0.05f, (1 - t) * 0.8f * nf);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, rp.x, rp.y, rp.z, 1.4f * s * (0.3f + t), 0, 0.9f, 0.08f, 0.1f, (1 - t) * (1 - t) * 0.8f * nf);
        }
    }

    // ================================================================ 흡혈 회복 (몸으로 스며드는 피)
    static final class Heal extends Fx {
        final int id;
        final float s;

        Heal(int id, float s) {
            super(10);
            this.id = id;
            this.s = s;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e != null && age < 6) {
                Vec3d c = e.getPos().add(0, 1.1, 0);
                for (int i = 0; i < 3 * s; i++) {
                    Vec3d from = c.add(randDir().multiply(rf(0.8f, 1.4f)));
                    Particles.P pp = Particles.spawn(Kind.GLOW, from, c.subtract(from).multiply(0.15), 7, rf(0.05f, 0.08f));
                    pp.color(BRIGHT, 1f);
                    pp.drag = 1f;
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
        }
    }

    // ================================================================ 발톱 할퀴기
    static final class Slash extends Fx {
        final int entity;
        final float yaw, tilt, scale;
        final Vec3d center;

        Slash(ClientWorld w, int entity, float yaw, float tilt, float scale) {
            super(8);
            this.entity = entity;
            this.yaw = yaw;
            this.tilt = tilt;
            this.scale = scale;
            Entity e = w.getEntityById(entity);
            center = e != null ? e.getPos() : Vec3d.ZERO;
            if (e != null) {
                float y = yaw * MathHelper.RADIANS_PER_DEGREE;
                droplets(e.getPos().add(-MathHelper.sin(y) * 1.2, 1.1, MathHelper.cos(y) * 1.2), 8, 0.15f);
            }
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(entity);
            Vec3d base = e != null ? e.getLerpedPos(c.tickDelta) : center;
            boolean fp = e != null && Effects.isLocalFp(e);
            float t = t(c);
            float head = Math.min(1, t * 3.4f), tail = Math.max(0, (t - 0.1f) * 1.5f);
            if (head <= tail) return;
            float y = yaw * MathHelper.RADIANS_PER_DEGREE;
            Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
            Vec3d right = new Vec3d(-f.z, 0, f.x);
            double tr = Math.toRadians(tilt);
            Vec3d u = right.multiply(Math.cos(tr)).add(new Vec3d(0, 1, 0).multiply(Math.sin(tr)));
            Vec3d nrm = u.crossProduct(f).normalize();
            for (int line = -1; line <= 1; line++) {
                Vec3d cc = base.add(0, 1.15, 0).add(f.multiply(fp ? 0.9 : 0.4)).add(nrm.multiply(line * 0.13));
                float rad = 1.2f * scale * (1 + line * 0.05f);
                int n = 18;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    float k = tail + (head - tail) * i / (n - 1);
                    float a = 1.4f - 2.8f * k;
                    pts[i] = cc.add(f.multiply(Math.cos(a) * rad * 0.7)).add(u.multiply(Math.sin(a) * rad)).subtract(c.cam);
                    float env = (float) Math.sin(Math.PI * i / (n - 1));
                    wds[i] = 0.07f * scale * (0.25f + 0.75f * env);
                    als[i] = env * (1 - t * t) * (fp ? 0.6f : 1f);
                }
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
                for (int i = 0; i < n; i++) wds[i] *= 2.6f;
                Draw.ribbon(dk, c.m, pts, n, wds, 0.22f, 0, 0.02f, als, 0.6f, 0);
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                for (int i = 0; i < n; i++) {
                    wds[i] /= 2.6f;
                    als[i] *= 0.8f;
                }
                Draw.ribbon(g, c.m, pts, n, wds, 0.95f, 0.04f, 0.08f, als, 0.6f, 0);
            }
        }
    }

    // ================================================================ 2. 피의 창 (땅에서 솟음)
    static final class Spikes extends Fx {
        final Vec3d p;
        final float r;
        final boolean big;
        final float[][] spikes;
        final float rot = R.nextFloat() * 6.28f;

        Spikes(ClientWorld w, Vec3d p, float r, boolean big) {
            super(30);
            this.p = p.add(0, 0.02, 0);
            this.r = r;
            this.big = big;
            int n = big ? 11 : 8;
            spikes = new float[n][];
            for (int i = 0; i < n; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                double d = i == 0 ? 0 : Math.sqrt(R.nextDouble()) * r;
                float h = (float) ((i == 0 ? (big ? 3.4 : 2.4) : rf(1.0f, big ? 2.6f : 1.9f)) * (1 - d / (r * 1.6)));
                float lean = (float) (d / r) * 0.5f;
                spikes[i] = new float[]{(float) (Math.cos(a) * d), (float) (Math.sin(a) * d), h, (float) a, lean, rf(0.18f, 0.32f) * (big ? 1.3f : 1f), rf(0, 2)};
            }
            droplets(p.add(0, 0.5, 0), big ? 40 : 24, 0.35f);
            BlockState st = Effects.groundState(w, p);
            if (st != null) for (int i = 0; i < (big ? 10 : 6); i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Debris.spawn(st, p.add(Math.cos(a) * r * 0.6, 0.2, Math.sin(a) * r * 0.6), new Vec3d(Math.cos(a) * 0.2, rf(0.3f, 0.6f), Math.sin(a) * 0.2), rf(0.2f, 0.4f), rf(18, 28), null, 0, true);
            }
            if (big) CameraController.hitstop(2f);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Vec3d rp = p.subtract(c.cam);
            float fade = a < 20 ? 1 : Math.max(0, 1 - (a - 20) / 10f);
            // 핏자국
            VertexConsumer pool = c.imm.getBuffer(AWRenderLayers.translucent(POOL));
            Draw.flat(pool, c.m, rp.x, rp.y + 0.01, rp.z, r * 1.3f * Math.min(1, a / 3f), rot, 1, 1, 1, 0.9f * fade);
            // 창: 3틱에 솟고 18틱부터 가라앉음
            VertexConsumer vc = c.imm.getBuffer(RenderLayer.getEntityTranslucent(SPIKE));
            Matrix4f m = c.m;
            Matrix3f nm = c.matrices.peek().getNormalMatrix();
            for (float[] s : spikes) {
                float grow = MathHelper.clamp((a - s[6]) / 3f, 0, 1);
                grow = 1 - (1 - grow) * (1 - grow) * (1 - grow);
                float sink = a > 18 ? MathHelper.clamp((a - 18) / 10f, 0, 1) : 0;
                float h = s[2] * grow * (1 - sink);
                if (h < 0.02f) continue;
                Vec3d base = rp.add(s[0], -0.2 - sink * s[2] * 0.3, s[1]);
                Vec3d dir = new Vec3d(Math.cos(s[3]) * s[4], 1, Math.sin(s[3]) * s[4]).normalize();
                Vec3d tip = base.add(dir.multiply(h));
                float b = s[5];
                Vec3d ax = dir.crossProduct(new Vec3d(0, 0, 1)).normalize().multiply(b);
                Vec3d az = dir.crossProduct(ax).normalize().multiply(b);
                Vec3d[] cs = {base.add(ax), base.add(az), base.subtract(ax), base.subtract(az)};
                for (int i = 0; i < 4; i++) {
                    Vec3d q0 = cs[i], q1 = cs[(i + 1) % 4];
                    Vec3d nrm = q1.subtract(q0).crossProduct(tip.subtract(q0)).normalize();
                    ev(vc, m, nm, q0, 0, 1, nrm);
                    ev(vc, m, nm, q1, 1, 1, nrm);
                    ev(vc, m, nm, tip, 1, 0, nrm);
                    ev(vc, m, nm, tip, 0, 0, nrm);
                }
            }
            // 빛 (번들거림)
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.flat(g, c.m, rp.x, rp.y + 0.05, rp.z, r * 1.6f, 0, 0.9f, 0.05f, 0.08f, 0.5f * fade);
        }

        static void ev(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3d p, float u, float v, Vec3d nrm) {
            vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1f, 1f, 1f, 1f).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
                    .normal(n, (float) nrm.x, (float) nrm.y, (float) nrm.z).next();
        }
    }

    // ================================================================ 1. 피의 실 (손 → 적)
    static final class Thread extends Fx {
        final int caster, target;
        final Vec3d point;
        final boolean enh;

        Thread(int caster, int target, Vec3d point, int d, boolean enh) {
            super(d);
            this.caster = caster;
            this.target = target;
            this.point = point;
            this.enh = enh;
        }

        Vec3d end(ClientWorld w, float td) {
            Entity t = target >= 0 ? w.getEntityById(target) : null;
            if (t != null) return Effects.chest(t, td);
            return point;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity c = w.getEntityById(caster);
            Vec3d e = end(w, 1f);
            if (c != null && e != null && age % 2 == 0) {
                Vec3d h = HandTracker.hand(c, 0, 1f);
                for (int i = 0; i < 3; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, h.lerp(e, R.nextDouble()), new Vec3d(0, -0.02, 0), 14, 0.06f);
                    pp.color(CRIMSON, 1f);
                    pp.grav = 0.04f;
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity ce = c.world.getEntityById(caster);
            Vec3d e = end(c.world, c.tickDelta);
            if (ce == null || e == null) return;
            float a = age + c.tickDelta;
            float out = Math.min(1, a / 2.5f);
            float fade = Math.min(1, (life - a) / 3f);
            Vec3d h = HandTracker.hand(ce, 0, c.tickDelta);
            Vec3d d = e.subtract(h);
            Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            int strands = enh ? 3 : 2;
            for (int sIdx = 0; sIdx < strands; sIdx++) {
                int n = 16;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    double k = (double) i / (n - 1) * out;
                    double wav = Math.sin(k * 9 + a * 0.9 + sIdx * 2) * 0.18 * (1 - k) * (1 - out * 0.6);
                    pts[i] = h.add(d.multiply(k)).add(side.multiply(wav + (sIdx - (strands - 1) / 2.0) * 0.06)).add(0, Math.sin(k * Math.PI) * -0.15, 0).subtract(c.cam);
                    wds[i] = 0.05f + 0.03f * (float) Math.sin(k * Math.PI);
                    als[i] = fade;
                }
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
                Draw.ribbon(dk, c.m, pts, n, wds, 0.25f, 0.0f, 0.02f, als, 0.8f, -a * 0.3f);
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                for (int i = 0; i < n; i++) wds[i] *= 0.6f;
                Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.1f, 0.12f, als, 0.8f, -a * 0.3f);
            }
        }
    }

    /** 흡혈 줄기가 들어가는 입 위치 (1인칭 본인이면 화면 아래 가운데에서 끝나게). */
    static Vec3d mouth(Entity b, float td) {
        if (Effects.isLocalFp(b)) {
            Vec3d eye = b.getCameraPosVec(td);
            return eye.add(b.getRotationVec(td).multiply(0.9)).add(0, -0.55, 0);
        }
        if (b instanceof net.minecraft.entity.player.PlayerEntity pl && biting(pl)) {
            // 흡혈난무의 물어뜯기: 몸을 숙여 머리가 앞으로 내려가 있음
            float yaw = MathHelper.lerp(td, pl.prevBodyYaw, pl.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
            return b.getLerpedPos(td).add(-MathHelper.sin(yaw) * 0.55, 1.05, MathHelper.cos(yaw) * 0.55);
        }
        return b.getLerpedPos(td).add(0, b.getStandingEyeHeight() - 0.15, 0);
    }

    static boolean biting(net.minecraft.entity.player.PlayerEntity p) {
        com.abilitywar.client.anim.AnimState st = com.abilitywar.client.anim.PlayerAnimator.peek(p);
        return st != null && st.current() != null && st.current().clip.id.equals("blood.skill3_drain");
    }

    // ================================================================ 흡혈 (적 → 시전자 입)
    static final class Drain extends Fx {
        final int from, to;
        final boolean big;

        Drain(int from, int to, int d, boolean big) {
            super(d);
            this.from = from;
            this.to = to;
            this.big = big;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity a = w.getEntityById(from), b = w.getEntityById(to);
            if (a != null && b != null) {
                Vec3d s = Effects.chest(a, 1f), e = mouth(b, 1f);
                for (int i = 0; i < (big ? 5 : 3); i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, s.add(randDir().multiply(0.2)), e.subtract(s).multiply(rf(0.12f, 0.2f)), 8, rf(0.05f, 0.09f));
                    pp.color(i % 2 == 0 ? BRIGHT : CRIMSON, 1f);
                    pp.drag = 1f;
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity a = c.world.getEntityById(from), b = c.world.getEntityById(to);
            if (a == null || b == null) return;
            float t = age + c.tickDelta;
            float fade = Math.min(1, t / 2f) * Math.min(1, (life - t) / 3f);
            Vec3d s = Effects.chest(a, c.tickDelta), e = mouth(b, c.tickDelta);
            boolean fp = Effects.isLocalFp(b);
            if (fp) fade *= 0.55f;
            Vec3d d = e.subtract(s);
            Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            int streams = big ? 5 : 3;
            for (int k = 0; k < streams; k++) {
                int n = 14;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    double q = (double) i / (n - 1);
                    double wav = Math.sin(q * 7 + t * 0.7 + k * 1.7) * 0.22 * Math.sin(q * Math.PI);
                    double up = Math.sin(q * Math.PI) * (0.25 + 0.1 * k) * (d.length() < 1.6 ? 0.4 : 1);
                    pts[i] = s.add(d.multiply(q)).add(side.multiply(wav)).add(0, up, 0).subtract(c.cam);
                    wds[i] = (big ? 0.07f : 0.05f) * (0.5f + 0.5f * (float) Math.sin(q * Math.PI)) * (fp ? 0.6f : 1f);
                    als[i] = fade;
                }
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.08f, 0.1f, als, 1.2f, t * 0.5f);
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
                for (int i = 0; i < n; i++) wds[i] *= 1.8f;
                Draw.ribbon(dk, c.m, pts, n, wds, 0.3f, 0, 0.02f, als, 1.2f, t * 0.5f);
            }
        }
    }

    // ================================================================ 4. 피의 막
    static final class Veil extends Fx {
        final int id;

        Veil(int id, int d) {
            super(d);
            this.id = id;
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            float vis = Math.min(1, a / 3f) * Math.min(1, (life - a) / 3f);
            Vec3d cp = e.getLerpedPos(c.tickDelta).add(0, 0.95, 0).subtract(c.cam);
            boolean fp = Effects.isLocalFp(e);
            float rad = (fp ? 1.6f : 1.25f) * (0.85f + 0.15f * vis) + 0.04f * MathHelper.sin(a * 0.9f);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.DARK_COLOR);
            Draw.sphereColor(dk, c.m, cp, rad, 10, 16, 0.3f, 0.0f, 0.02f, (fp ? 0.08f : 0.14f) * vis);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            Draw.sphereColor(g, c.m, cp, rad * 1.03f, 10, 16, 0.55f, 0.02f, 0.05f, (fp ? 0.1f : 0.15f) * vis);
            if (age % 2 == 0) {
                Vec3d p = cp.add(c.cam).add(randDir().multiply(rad));
                Particles.spawn(Kind.GLOW, p, new Vec3d(0, -0.03, 0), 10, 0.06f).color(CRIMSON, 1f);
            }
        }
    }

    // ================================================================ 박쥐 떼
    static final class Bats extends Fx {
        final Vec3d from, to;
        final int travel;
        final float[][] bats;
        final int owner;

        Bats(ClientWorld w, int owner, Vec3d from, Vec3d to, int travel, int count) {
            super(travel + 14);
            this.owner = owner;
            this.from = from.add(0, 1.0, 0);
            this.to = to.add(0, 1.0, 0);
            this.travel = Math.max(1, travel);
            bats = new float[count][];
            for (int i = 0; i < count; i++)
                bats[i] = new float[]{rf(0, 6.28f), rf(0.3f, 1.1f), rf(-0.8f, 0.8f), rf(0.8f, 1.4f), rf(0, 6.28f), rf(-1.5f, 1.5f)};
            if (count > 12) {
                for (int i = 0; i < 18; i++)
                    Particles.spawn(Kind.SMOKE_DARK, this.from.add(randDir().multiply(0.6)), randDir().multiply(0.05), rf(12, 20), rf(0.4f, 0.7f)).color(DARK, 0.5f);
                droplets(this.from, 14, 0.2f);
            }
        }

        Vec3d pos(float[] b, float a) {
            float k = MathHelper.clamp(a / travel, 0, 1);
            Vec3d center = from.lerp(to, k * k * (3 - 2 * k));
            float spread = a < travel ? 0.9f : 0.9f + (a - travel) * 0.35f;
            double ang = b[0] + a * 0.5 * b[3];
            return center.add(Math.cos(ang) * b[1] * spread, b[2] * spread * 0.6 + Math.sin(a * 0.8 + b[4]) * 0.15 + (a > travel ? (a - travel) * 0.06 * b[5] : 0), Math.sin(ang) * b[1] * spread);
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == travel && bats.length > 12) {
                for (int i = 0; i < 18; i++)
                    Particles.spawn(Kind.SMOKE_DARK, to.add(randDir().multiply(0.6)), randDir().multiply(0.05), rf(12, 20), rf(0.4f, 0.7f)).color(DARK, 0.5f);
                droplets(to, 16, 0.2f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float fade = a < travel ? 1 : Math.max(0, 1 - (a - travel) / 12f);
            if (fade <= 0) return;
            Vector3f rv = c.camRot.transform(new Vector3f(1, 0, 0)), uv = c.camRot.transform(new Vector3f(0, 1, 0));
            Vec3d right = new Vec3d(rv.x, rv.y, rv.z), up = new Vec3d(uv.x, uv.y, uv.z);
            VertexConsumer vc = c.imm.getBuffer(AWRenderLayers.translucent(BAT));
            for (int i = 0; i < bats.length; i++) {
                float[] b = bats[i];
                Vec3d p = pos(b, a).subtract(c.cam);
                float s = 0.26f + 0.08f * (i % 3);
                boolean frame = ((int) (a * 1.6f + b[4] * 3)) % 2 == 0;
                float u0 = frame ? 0 : 0.5f, u1 = frame ? 0.5f : 1;
                Draw.quadUV(vc, c.m, p, right.multiply(s), up.multiply(s * 0.5), 1, 1, 1, fade, u0, 0, u1, 1);
            }
        }
    }

    // ================================================================ R. 피의 사슬 → 흡혈 → 폭발 (서버 t16 에 시작)
    static final class Chains extends Fx {
        final int caster;
        final int[] ids;

        Chains(int caster, int[] ids) {
            super(46);
            this.caster = caster;
            this.ids = ids;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == 42) {
                Entity c = w.getEntityById(caster);
                for (int id : ids) {
                    Entity e = w.getEntityById(id);
                    if (e != null) VfxManager.add(new Burst(w, Effects.chest(e, 1f), 1.8f));
                }
                if (c != null) VfxManager.add(new Nova(c.getPos()));
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity ce = c.world.getEntityById(caster);
            if (ce == null) return;
            float a = age + c.tickDelta;
            float out = Math.min(1, a / 4f);
            float fade = a < 42 ? 1 : Math.max(0, 1 - (a - 42) / 3f);
            Vec3d s = Effects.chest(ce, c.tickDelta);
            for (int id : ids) {
                Entity e = c.world.getEntityById(id);
                if (e == null) continue;
                Vec3d t = Effects.chest(e, c.tickDelta);
                Vec3d d = t.subtract(s);
                double len = d.length() * out;
                int n = 12;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    double k = (double) i / (n - 1) * out;
                    pts[i] = s.add(d.multiply(k)).add(0, -Math.sin(k * Math.PI) * 0.6 * (a < 18 ? 1 : 0.2), 0).subtract(c.cam);
                    wds[i] = 0.12f;
                    als[i] = fade;
                }
                VertexConsumer ch = c.imm.getBuffer(AWRenderLayers.translucent(CHAIN));
                Draw.ribbon(ch, c.m, pts, n, wds, 1, 1, 1, als, (float) (len / 0.5 / (n - 1)) * 1.0f, 0);
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(CHAIN));
                Draw.ribbon(g, c.m, pts, n, wds, 0.8f, 0.05f, 0.08f, als, (float) (len / 0.5 / (n - 1)), 0);
            }
            // 흡혈 단계 (서버 t34~56 = 이 이펙트 18~40)
            if (a >= 18 && a < 41) {
                for (int id : ids) {
                    Entity e = c.world.getEntityById(id);
                    if (e == null) continue;
                    Vec3d t = Effects.chest(e, c.tickDelta);
                    Vec3d d = s.subtract(t);
                    Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
                    side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
                    int n = 12;
                    Vec3d[] pts = new Vec3d[n];
                    float[] wds = new float[n], als = new float[n];
                    for (int i = 0; i < n; i++) {
                        double q = (double) i / (n - 1);
                        pts[i] = t.add(d.multiply(q)).add(side.multiply(Math.sin(q * 6 + a * 0.6 + id) * 0.25 * Math.sin(q * Math.PI))).add(0, Math.sin(q * Math.PI) * 0.6, 0).subtract(c.cam);
                        wds[i] = 0.08f * (float) (0.4 + 0.6 * Math.sin(q * Math.PI));
                        als[i] = 1;
                    }
                    VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                    Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.08f, 0.1f, als, 1.2f, a * 0.5f);
                }
            }
        }
    }

    /** 혈월만개 마무리: 시전자 중심의 피의 파동. */
    static final class Nova extends Fx {
        final Vec3d p;

        Nova(Vec3d p) {
            super(22);
            this.p = p;
            droplets(p.add(0, 2, 0), 60, 0.6f);
            CameraController.hitstop(4f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            float e = 1 - (1 - t) * (1 - t);
            Vec3d rp = p.add(0, 0.1, 0).subtract(c.cam);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.translucent(Tex.SHOCKWAVE));
            Draw.flat(sw, c.m, rp.x, rp.y, rp.z, 2 + e * 16, 0, 0.55f, 0.02f, 0.05f, (1 - t) * 0.95f);
            Draw.flat(sw, c.m, rp.x, rp.y + 2, rp.z, 1 + e * 12, 0, 0.45f, 0.01f, 0.04f, (1 - t) * 0.6f);
            VertexConsumer sa = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.flat(sa, c.m, rp.x, rp.y + 0.02, rp.z, 2 + e * 16, 0, 0.6f, 0.02f, 0.05f, (1 - t) * 0.5f);
            Vec3d up = rp.add(0, 3, 0);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, up.x, up.y, up.z, 7 * (0.3f + e), 0, 0.8f, 0.05f, 0.08f, (1 - t) * 0.7f * nearFade(up, 5));
        }
    }

    // ================================================================ R. 혈월만개: 하늘의 핏빛 달, 피의 비, 박쥐 무리
    static final class Ult extends Fx {
        final int caster;
        final Vec3d start, moon;
        final List<float[]> orbit = new ArrayList<>();

        Ult(ClientWorld w, int caster, Vec3d start) {
            super(84);
            this.caster = caster;
            this.start = start;
            Entity c = w.getEntityById(caster);
            float yaw = c != null ? c.getYaw() : 0;
            Vec3d f = new Vec3d(-MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE), 0, MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE));
            moon = start.subtract(f.multiply(46)).add(0, 30, 0);
            for (int i = 0; i < 16; i++) orbit.add(new float[]{rf(0, 6.28f), rf(2.5f, 4.5f), rf(1.0f, 4.0f), rf(0.7f, 1.3f)});
        }

        @Override
        public boolean tick(ClientWorld w) {
            // 피의 비
            if (age > 8 && age < 70) {
                for (int i = 0; i < 6; i++) {
                    Vec3d p = start.add(rf(-14, 14), rf(10, 16), rf(-14, 14));
                    Particles.P pp = Particles.spawn(Kind.STREAK, p, new Vec3d(0, -0.9, 0), 18, 0.05f);
                    pp.color(i % 2 == 0 ? CRIMSON : 0x80000C, 0.8f);
                    pp.drag = 1f;
                }
            }
            if (age < 14 && age % 2 == 0) {
                Entity c = w.getEntityById(caster);
                if (c != null) droplets(c.getPos().add(0, 1, 0), 6, 0.15f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float rise = MathHelper.clamp(a / 18f, 0, 1);
            float fade = 1 - MathHelper.clamp((a - 70) / 14f, 0, 1);
            // 핏빛 달
            Vec3d mp = moon.add(0, -12 * (1 - rise * rise), 0).subtract(c.cam);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.billboard(gl, c.m, c.camRot, mp.x, mp.y, mp.z, 52f, 0, 0.35f, 0.0f, 0.02f, 0.32f * rise * fade);
            VertexConsumer mv = c.imm.getBuffer(AWRenderLayers.translucent(MOON));
            Draw.billboard(mv, c.m, c.camRot, mp.x, mp.y, mp.z, 24f, 0, 1, 1, 1, rise * fade);
            VertexConsumer ga = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(ga, c.m, c.camRot, mp.x, mp.y, mp.z, 34f, 0, 0.5f, 0.02f, 0.03f, 0.3f * rise * fade);
            // 바닥 핏자국 + 마법진
            Vec3d gp = start.add(0, 0.03, 0).subtract(c.cam);
            float grow = Effects.outBack(Math.min(1, a / 12f));
            Draw.flat(c.imm.getBuffer(AWRenderLayers.translucent(POOL)), c.m, gp.x, gp.y, gp.z, 6f * grow, 0.4f, 1, 1, 1, 0.85f * fade);
            Draw.flat(c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE)), c.m, gp.x, gp.y + 0.01, gp.z, 7f * grow, a * 0.02f, r(CRIMSON), g(CRIMSON), b(CRIMSON), 0.8f * fade);
            // 시전자 주위를 도는 박쥐
            Entity ce = c.world.getEntityById(caster);
            if (ce != null && a > 8 && a < 66) {
                Vec3d center = ce.getLerpedPos(c.tickDelta);
                Vector3f rv = c.camRot.transform(new Vector3f(1, 0, 0)), uv = c.camRot.transform(new Vector3f(0, 1, 0));
                Vec3d right = new Vec3d(rv.x, rv.y, rv.z), up = new Vec3d(uv.x, uv.y, uv.z);
                VertexConsumer vc = c.imm.getBuffer(AWRenderLayers.translucent(BAT));
                float bf = Math.min(1, (a - 8) / 6f) * Math.min(1, (66 - a) / 6f);
                for (float[] b : orbit) {
                    double ang = b[0] + a * 0.12 * b[3];
                    Vec3d p = center.add(Math.cos(ang) * b[1], b[2] + Math.sin(a * 0.2 + b[0]) * 0.4, Math.sin(ang) * b[1]).subtract(c.cam);
                    boolean frame = ((int) (a * 1.5f + b[0] * 3)) % 2 == 0;
                    Draw.quadUV(vc, c.m, p, right.multiply(0.32), up.multiply(0.16), 1, 1, 1, bf, frame ? 0 : 0.5f, 0, frame ? 0.5f : 1, 1);
                }
            }
        }
    }

    private BloodFx() {
    }
}

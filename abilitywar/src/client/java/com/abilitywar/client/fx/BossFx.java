package com.abilitywar.client.fx;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.dragon.ElderDragon;
import com.abilitywar.mob.AncientWyvernEntity;
import com.abilitywar.mob.BossEntity;
import com.abilitywar.mob.EmberColossusEntity;
import com.abilitywar.mob.FallenKingEntity;
import com.abilitywar.mob.FrostWitchEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.randDir;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/** 보스 이펙트: 경고 원, 내려찍기, 회전베기, 먼지, 분노, 얼음 가시, 서리 폭발, 운석, 숨결, 눈보라, 죽음 + 고대 비룡 몸 + 등장 제목. */
public final class BossFx {
    public static final int ICE = 0x9FE8FF;

    /** 등장 제목 (HUD 가 읽음). */
    public static Text introName, introSub;
    public static int introTicks;
    public static final int INTRO_LEN = 90;

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "boss_intro" -> intro(w, n, p);
            case "boss_warn" -> VfxManager.add(new Warn(w, p, n.getFloat("r"), n.getInt("d"), n.getInt("col") & 0xFFFFFF));
            case "boss_slam" -> slam(w, p, n.getFloat("r"), n.getInt("col") & 0xFFFFFF);
            case "boss_spin" -> VfxManager.add(new ClassFx.Slash(w, n.getInt("c"), 0, n.getInt("col") & 0xFFFFFF, true));
            case "boss_dust" -> ClassFx.dust(w, p, n.getFloat("r"), 6);
            case "boss_enrage" -> enrage(w, n.getInt("e"));
            case "boss_spike" -> VfxManager.add(new Spikes(w, p, n.contains("s") ? n.getFloat("s") : 1.5f));
            case "boss_nova" -> nova(w, p, n.getFloat("r"));
            case "boss_meteor" -> VfxManager.add(new Meteor(w, p, n.getInt("t"), n.getInt("col") & 0xFFFFFF));
            case "boss_breath" -> VfxManager.add(new Breath(n.getInt("c"), n.getInt("d"), n.getInt("col") & 0xFFFFFF, n.contains("len") ? n.getFloat("len") : 10));
            case "boss_blizzard" -> VfxManager.add(new Blizzard(n.getInt("e"), n.getInt("d")));
            case "boss_death" -> death(w, p, n.contains("s") ? n.getFloat("s") : 2f);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 등장
    static void intro(ClientWorld w, NbtCompound n, Vec3d p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.squaredDistanceTo(p) > 64 * 64) return;
        introName = Text.translatable(n.getString("name"));
        introSub = Text.translatable(n.getString("sub"));
        introTicks = INTRO_LEN;
        VfxManager.add(new Effects.Flash(p.add(0, 2, 0), 6f, 0xFFE0C0, 16, Tex.FLARE));
        VfxManager.add(new ClassFx.Ring(w, p, 10f, 0xFF4040, 4));
        CameraController.addShake(0.4f, 14);
    }

    public static void tickHud() {
        if (introTicks > 0) introTicks--;
    }

    // ================================================================ 공통 그리기
    static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int col, float a0, float a1) {
        ClassFx.quad4(vc, m, a, b, c, d, col, a0, a1);
    }

    // ================================================================ 경고 원
    static final class Warn extends Fx {
        final Vec3d p;
        final float rad;
        final int col;

        Warn(ClientWorld w, Vec3d at, float rad, int dur, int col) {
            super(Math.max(4, dur));
            Vec3d g = Effects.ground(w, at.add(0, 1.0, 0), 4);
            this.p = (g != null ? g : at).add(0, 0.04, 0);
            this.rad = rad;
            this.col = col;
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta, t = Math.min(1, a / life);
            Vec3d base = p.subtract(c.cam);
            float blink = 0.6f + 0.4f * MathHelper.sin(a * (0.5f + t));
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            ClassFx.ring(gl, c.m, base, rad, 0.12f, col, 0.9f * blink, 64);
            // 안쪽이 바깥으로 차오름 (다 차면 공격이 옴)
            ClassFx.ring(gl, c.m, base.add(0, 0.005, 0), rad * t, rad * t * 0.5f + 0.05f, col, 0.35f, 64);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            Draw.flat(sw, c.m, base.x, base.y + 0.01, base.z, rad * 1.05f, 0, Fx.r(col), Fx.g(col), Fx.b(col), 0.25f + 0.25f * t);
        }
    }

    // ================================================================ 내려찍기 (먼지 + 균열 + 충격파)
    static void slam(ClientWorld w, Vec3d p, float r, int col) {
        VfxManager.add(new ClassFx.Ring(w, p, r, col, 0));
        BlockState st = Effects.groundState(w, p);
        if (st != null) for (int i = 0; i < (int) (8 + r * 4); i++) {
            double a = R.nextDouble() * Math.PI * 2, rr = rf(0.3f, r);
            Debris.spawn(st, p.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), new Vec3d(Math.cos(a) * 0.12, rf(0.25f, 0.55f), Math.sin(a) * 0.12), rf(0.12f, 0.28f), rf(16, 28), null, 0, true);
        }
        ClassFx.dust(w, p, r, (int) (8 + r * 3));
        Effects.sparks(p.add(0, 0.4, 0), (int) (10 + r * 4), 0.4f, col, 0.07f);
        VfxManager.add(new Effects.Flash(p.add(0, 0.6, 0), r * 0.9f, col, 8, Tex.FLARE));
    }

    // ================================================================ 분노 (2페이즈)
    static void enrage(ClientWorld w, int id) {
        Entity e = w.getEntityById(id);
        if (e == null) return;
        VfxManager.add(new ClassFx.Ring(w, e.getPos(), 8f, 0xFF2020, 4));
        VfxManager.add(new Effects.Flash(e.getPos().add(0, e.getHeight() * 0.6, 0), 6f, 0xFF3030, 14, Tex.FLARE));
        for (int i = 0; i < 40; i++) {
            Vec3d d = randDir();
            Particles.spawn(Kind.SMOKE_DARK, e.getPos().add(0, e.getHeight() * 0.5, 0).add(d.multiply(0.6)), d.multiply(0.25), rf(20, 34), rf(0.6f, 1.1f)).color(0x200508, 0.6f);
        }
        CameraController.addShake(0.5f, 12);
    }

    // ================================================================ 얼음 가시
    static final class Spikes extends Fx {
        final Vec3d p;
        final float s;
        final double[][] spikes;

        Spikes(ClientWorld w, Vec3d at, float s) {
            super(36);
            Vec3d g = Effects.ground(w, at.add(0, 1, 0), 4);
            this.p = g != null ? g : at;
            this.s = s;
            int n = 5 + (int) (s * 3);
            spikes = new double[n][];
            for (int i = 0; i < n; i++) {
                double a = R.nextDouble() * Math.PI * 2, rr = i == 0 ? 0 : rf(0.2f, s);
                spikes[i] = new double[]{Math.cos(a) * rr, Math.sin(a) * rr, (i == 0 ? 2.6 : rf(1.0f, 2.0f)) * s / 1.6, rf(0.12f, 0.26f), rf(-0.3f, 0.3f), rf(-0.3f, 0.3f)};
            }
            for (int i = 0; i < 18; i++) {
                Vec3d d = randDir();
                Particles.P pp = Particles.spawn(Kind.SPARK, p.add(0, 0.5, 0), new Vec3d(d.x * 0.2, Math.abs(d.y) * 0.4, d.z * 0.2), rf(14, 22), 0.07f);
                pp.color(i % 2 == 0 ? 0xFFFFFF : ICE, 1f);
                pp.grav = 0.02f;
            }
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float grow = Effects.outBack(Math.min(1, a / 3f)), fade = Math.min(1, (life - a) / 8f);
            Vec3d base = p.subtract(c.cam);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (double[] sp : spikes) {
                Vec3d b = base.add(sp[0], -0.1, sp[1]);
                Vec3d tip = b.add(sp[4], sp[2] * grow * fade, sp[5]);
                float w = (float) sp[3];
                for (int k = 0; k < 4; k++) {
                    double a0 = k * Math.PI / 2, a1 = (k + 1) * Math.PI / 2;
                    Vec3d q0 = b.add(Math.cos(a0) * w, 0, Math.sin(a0) * w), q1 = b.add(Math.cos(a1) * w, 0, Math.sin(a1) * w);
                    quad(gl, c.m, tip, tip, q1, q0, k % 2 == 0 ? 0xFFFFFF : ICE, 0.85f * fade, 0.35f * fade);
                }
            }
            VertexConsumer fr = c.imm.getBuffer(AWRenderLayers.additive(Tex.CRACK));
            Draw.flat(fr, c.m, base.x, base.y + 0.02, base.z, s * 1.3f, 0.4f, Fx.r(ICE), Fx.g(ICE), Fx.b(ICE), 0.6f * fade);
        }
    }

    // ================================================================ 서리 폭발
    static void nova(ClientWorld w, Vec3d p, float r) {
        VfxManager.add(new ClassFx.Ring(w, p, r, ICE, 1));
        VfxManager.add(new Effects.Flash(p.add(0, 1, 0), r * 0.8f, 0xE8FBFF, 12, Tex.FLARE));
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            Particles.P pp = Particles.spawn(Kind.SPARK, p.add(0, 1, 0), new Vec3d(Math.cos(a) * 0.55, rf(0f, 0.15f), Math.sin(a) * 0.55), rf(12, 20), 0.08f);
            pp.color(i % 2 == 0 ? 0xFFFFFF : ICE, 1f);
            pp.drag = 0.88f;
        }
        CameraController.addShake(0.3f, 8);
    }

    // ================================================================ 운석 (하늘에서 떨어지는 불덩이)
    static final class Meteor extends Fx {
        final Vec3d to, from;
        final int travel, col;

        Meteor(ClientWorld w, Vec3d at, int travel, int col) {
            super(Math.max(4, travel) + 1);
            Vec3d g = Effects.ground(w, at.add(0, 1, 0), 4);
            this.to = g != null ? g : at;
            this.from = to.add(rf(-6, 6), 26, rf(-6, 6));
            this.travel = Math.max(4, travel);
            this.col = col;
        }

        Vec3d pos(float a) {
            float k = MathHelper.clamp(a / travel, 0, 1);
            return from.lerp(to, k * k);
        }

        @Override
        public boolean tick(ClientWorld w) {
            Vec3d p = pos(age);
            for (int i = 0; i < 3; i++)
                Particles.spawn(Kind.FLAME, p.add(rf(-0.4f, 0.4f), rf(-0.4f, 0.4f), rf(-0.4f, 0.4f)), new Vec3d(0, 0.05, 0), rf(10, 18), rf(0.3f, 0.6f)).color(i == 0 ? 0xFFE0A0 : col, 1f);
            Particles.spawn(Kind.SMOKE_DARK, p, new Vec3d(0, 0.02, 0), rf(20, 30), rf(0.5f, 0.9f)).color(0x201008, 0.5f);
            if (age + 1 >= travel) VfxManager.add(new Effects.Flash(to.add(0, 0.8, 0), 4f, col, 10, Tex.FLARE));
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Vec3d p = pos(a).subtract(c.cam);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, p.x, p.y, p.z, 1.3f, 0, Fx.r(col), Fx.g(col), Fx.b(col), 0.9f * Fx.nearFade(p, 1));
            Draw.billboard(g, c.m, c.camRot, p.x, p.y, p.z, 0.6f, 0, 1, 0.95f, 0.8f, Fx.nearFade(p, 1));
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            Vec3d dir = to.subtract(from).normalize();
            Draw.streak(st, c.m, p, dir, 5f, 0.6f, Fx.r(col), Fx.g(col), Fx.b(col), 0.8f);
        }
    }

    // ================================================================ 숨결 (불/얼음 줄기)
    static final class Breath extends Fx {
        final int caster, col;
        final float len;

        Breath(int caster, int dur, int col, float len) {
            super(dur);
            this.caster = caster;
            this.col = col;
            this.len = len;
        }

        static Vec3d mouth(Entity e) {
            if (e instanceof AncientWyvernEntity) return lastMouth.containsKey(e.getId()) ? lastMouth.get(e.getId()) : e.getPos().add(0, 1.5, 0);
            float yaw = (e instanceof LivingEntity le ? le.bodyYaw : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            return e.getPos().add(-MathHelper.sin(yaw) * e.getWidth() * 0.6, e.getHeight() * 0.78, MathHelper.cos(yaw) * e.getWidth() * 0.6);
        }

        static Vec3d dir(Entity e) {
            float yaw = (e instanceof LivingEntity le ? le.headYaw : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            return new Vec3d(-MathHelper.sin(yaw), e instanceof AncientWyvernEntity ? -0.35 : -0.2, MathHelper.cos(yaw)).normalize();
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(caster);
            if (e == null || !e.isAlive()) return true;
            Vec3d m = mouth(e), d = dir(e);
            for (int i = 0; i < 8; i++) {
                Vec3d v = d.multiply(rf(0.6f, 1.0f) * len / 14).add(randDir().multiply(0.08 * len / 10));
                Particles.P pp = Particles.spawn(Kind.FLAME, m, v, rf(12, 20), rf(0.35f, 0.8f));
                pp.color(i % 3 == 0 ? 0xFFF0B0 : col, 1f);
                pp.drag = 0.96f;
            }
            if (age % 2 == 0) Particles.spawn(Kind.SMOKE_DARK, m.add(d.multiply(len * 0.8)), new Vec3d(0, 0.06, 0), rf(20, 30), rf(0.8f, 1.4f)).color(0x1A0A04, 0.45f);
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(caster);
            if (e == null) return;
            Vec3d m = mouth(e).subtract(c.cam), d = dir(e);
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            float flick = 0.8f + 0.2f * MathHelper.sin((age + c.tickDelta) * 1.3f);
            Draw.streak(st, c.m, m.add(d.multiply(len)), d, len, 1.4f * flick, Fx.r(col), Fx.g(col), Fx.b(col), 0.7f);
            Draw.streak(st, c.m, m.add(d.multiply(len * 0.8)), d, len * 0.8f, 0.6f, 1, 0.95f, 0.8f, 0.8f);
        }
    }

    // ================================================================ 눈보라 (서리 마녀 주위)
    static final class Blizzard extends Fx {
        final int id;

        Blizzard(int id, int dur) {
            super(dur);
            this.id = id;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e == null) return true;
            for (int i = 0; i < 14; i++) {
                double a = R.nextDouble() * Math.PI * 2, rr = rf(2, 18);
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(Math.cos(a) * rr, rf(4, 10), Math.sin(a) * rr), new Vec3d(rf(0.1f, 0.3f), rf(-0.35f, -0.2f), rf(-0.1f, 0.1f)), rf(30, 50), rf(0.05f, 0.1f));
                pp.color(0xF4FCFF, 0.9f);
                pp.drag = 1f;
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
        }
    }

    // ================================================================ 죽음
    static void death(ClientWorld w, Vec3d p, float s) {
        VfxManager.add(new Effects.Flash(p, 8f * Math.max(1, s / 2), 0xFFF0D0, 20, Tex.FLARE));
        VfxManager.add(new ClassFx.Ring(w, p.add(0, -1, 0), 10f, 0xFFD86A, 0));
        VfxManager.add(new com.abilitywar.client.fx.paladin.PaladinFx.Pillar(w, p.add(0, -1, 0), 2.5f));
        for (int i = 0; i < 80; i++) {
            Vec3d d = randDir();
            Particles.P pp = Particles.spawn(i % 3 == 0 ? Kind.STREAK : Kind.GLOW, p, d.multiply(rf(0.2f, 0.8f)), rf(20, 40), rf(0.06f, 0.14f));
            pp.color(i % 2 == 0 ? 0xFFD86A : 0xFFFFFF, 1f);
            pp.drag = 0.94f;
        }
        CameraController.addShake(0.7f, 20);
    }

    // ================================================================ 계속: 보스 몸 꾸미기 + 고대 비룡
    static final java.util.Map<Integer, Vec3d> lastMouth = new java.util.HashMap<>();

    public static void tick(ClientWorld w) {
        for (Entity e : w.getEntities()) {
            if (!(e instanceof BossEntity b) || !e.isAlive()) continue;
            long t = w.getTime() + e.getId();
            if (b.enraged() && t % 2 == 0) {
                Vec3d at = e.getPos().add(rf(-1, 1) * e.getWidth(), rf(0, e.getHeight()), rf(-1, 1) * e.getWidth());
                Particles.spawn(Kind.SMOKE_DARK, at, new Vec3d(0, 0.06, 0), rf(16, 26), rf(0.3f, 0.6f)).color(0x300608, 0.5f);
                if (t % 6 == 0) Particles.spawn(Kind.GLOW, at, new Vec3d(0, 0.05, 0), 16, 0.07f).color(0xFF3030, 1f);
            }
            if (e instanceof EmberColossusEntity && t % 3 == 0) {
                Vec3d at = e.getPos().add(rf(-1, 1) * e.getWidth() * 0.5, rf(0.5f, e.getHeight()), rf(-1, 1) * e.getWidth() * 0.5);
                Particles.spawn(Kind.FLAME, at, new Vec3d(0, 0.06, 0), rf(10, 18), rf(0.2f, 0.4f)).color(0xFF8A30, 1f);
            }
            if (e instanceof FrostWitchEntity && t % 3 == 0) {
                Vec3d at = e.getPos().add(rf(-1, 1), rf(0, 3f), rf(-1, 1));
                Particles.spawn(Kind.GLOW, at, new Vec3d(0, -0.03, 0), rf(20, 30), rf(0.04f, 0.07f)).color(ICE, 0.9f);
            }
            if (e instanceof FallenKingEntity && t % 5 == 0) {
                Vec3d at = e.getPos().add(rf(-0.6f, 0.6f), 0.1, rf(-0.6f, 0.6f));
                Particles.spawn(Kind.WISP_DARK, at, new Vec3d(0, 0.03, 0), rf(20, 30), rf(0.4f, 0.7f)).color(0x0A0408, 0.6f);
            }
        }
        lastMouth.keySet().removeIf(id -> w.getEntityById(id) == null);
    }

    public static void clear() {
        lastMouth.clear();
        introTicks = 0;
    }

    public static void render(Fx.Ctx c) {
        for (Entity e : c.world.getEntities()) {
            if (!(e instanceof AncientWyvernEntity wy) || !e.isAlive() && wy.deathTime > 18) continue;
            float td = c.tickDelta;
            float time = e.age + td;
            ElderDragon.Pose p = new ElderDragon.Pose();
            p.time = time;
            Vec3d v = e.getVelocity();
            double sp = v.horizontalLength();
            p.wingSpread = 1f;
            p.wingFlap = MathHelper.sin(time * (sp > 0.4 ? 0.35f : 0.22f));
            p.tailSway = 1;
            int anim = wy.anim();
            float at = wy.animTime(td);
            if (anim == AncientWyvernEntity.BREATH) {
                p.throat = MathHelper.clamp((at - 6) / 8f, 0, 1);
                p.jaw = MathHelper.clamp((at - 12) / 3f, 0, 1) * (1 - MathHelper.clamp((at - 50) / 4f, 0, 1));
                p.rear = 0.4f * MathHelper.clamp(at / 10f, 0, 1);
                p.wingFlap = MathHelper.sin(time * 0.3f) * 0.6f;
            } else if (anim == AncientWyvernEntity.GUST) {
                float k = MathHelper.clamp(at / 10f, 0, 1);
                p.rear = 0.6f * k;
                p.wingFlap = at < 10 ? -k : 1f;
            } else if (anim == AncientWyvernEntity.SWOOP && at > 12 && at < 26) {
                p.wingSpread = 0.55f;
                p.jaw = 0.6f;
            }
            if (wy.hurtTime > 0) p.shake = wy.hurtTime / 10f;
            float yaw = MathHelper.lerpAngleDegrees(td, wy.prevBodyYaw, wy.bodyYaw);
            boolean king = e instanceof com.abilitywar.mob.DragonKingEntity, drake = e instanceof com.abilitywar.mob.DrakeEntity;
            float scale = king ? 0.5f : drake ? 0.15f : 0.32f;
            Vec3d pos = e.getLerpedPos(td).subtract(0, 4.4 * scale - (king ? 1.6 : drake ? 0.5 : 1.0), 0);
            float alpha = e.isAlive() ? 1f : 1f - wy.deathTime / 20f;
            ElderDragon.render(c, pos, yaw, scale, p, alpha, wy.enraged() ? 1.3f : king ? 1.1f : 0.9f,
                    king ? ElderDragon.AZURE : drake ? ElderDragon.JADE : ElderDragon.RED);
            lastMouth.put(e.getId(), ElderDragon.lastMouth);
            c.imm.draw();
        }
    }

    private BossFx() {
    }
}

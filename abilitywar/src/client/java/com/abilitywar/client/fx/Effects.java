package com.abilitywar.client.fx;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.data.AWPlayer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

import static com.abilitywar.client.fx.Particles.Kind;
import static com.abilitywar.client.fx.Particles.spawn;

/** 공허 능력 이펙트 모음. */
public final class Effects {
    public static final Random R = new Random();

    public static float rf(float a, float b) {
        return a + R.nextFloat() * (b - a);
    }

    public static Vec3d randDir() {
        double u = R.nextDouble() * 2 - 1, th = R.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new Vec3d(s * Math.cos(th), u, s * Math.sin(th));
    }

    public static Vec3d pos(NbtCompound n) {
        return new Vec3d(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"));
    }

    public static Vec3d chest(Entity e, float td) {
        return e.getLerpedPos(td).add(0, e.getHeight() * 0.62, 0);
    }

    public static boolean isLocalFp(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return e == mc.player && mc.options.getPerspective().isFirstPerson() && !CameraController.isCinematic();
    }

    @Nullable
    public static Vec3d ground(ClientWorld w, Vec3d p, int depth) {
        BlockPos.Mutable m = BlockPos.ofFloored(p).mutableCopy();
        for (int i = 0; i < depth; i++) {
            BlockState s = w.getBlockState(m);
            if (!s.isAir() && !s.getCollisionShape(w, m).isEmpty()) {
                double top = m.getY() + s.getCollisionShape(w, m).getMax(net.minecraft.util.math.Direction.Axis.Y);
                return new Vec3d(p.x, top, p.z);
            }
            m.move(0, -1, 0);
        }
        return null;
    }

    public static BlockState groundState(ClientWorld w, Vec3d p) {
        BlockPos.Mutable m = BlockPos.ofFloored(p.add(0, 0.2, 0)).mutableCopy();
        for (int i = 0; i < 4; i++) {
            BlockState s = w.getBlockState(m);
            if (!s.isAir()) return s;
            m.move(0, -1, 0);
        }
        return null;
    }

    public static void sparks(Vec3d p, int n, float speed, int color, float size) {
        for (int i = 0; i < n; i++) {
            Vec3d d = randDir().multiply(rf(0.3f, 1f) * speed);
            spawn(Kind.STREAK, p, d, rf(5, 11), size * rf(0.6f, 1.2f)).color(color, 1f).drag = 0.82f;
        }
    }

    // ================================================================ TENDRIL
    public static final class Tendril extends Fx {
        final int caster, target;
        final Vec3d end;
        final int dur;
        final Vec3d[] pts = new Vec3d[28];
        final float[] widths = new float[28];
        final float[] alphas = new float[28];

        Tendril(NbtCompound n) {
            super(n.getInt("d") + 2);
            caster = n.getInt("c");
            target = n.getInt("t");
            end = target < 0 ? new Vec3d(n.getDouble("ex"), n.getDouble("ey"), n.getDouble("ez")) : null;
            dur = n.getInt("d");
        }

        Vec3d tip(ClientWorld w, float td, Vec3d hand) {
            Entity t = target >= 0 ? w.getEntityById(target) : null;
            Vec3d goal = t != null ? chest(t, td) : (end != null ? end : hand);
            float a = age + td;
            float ext = target >= 0 ? 4f : 5f;
            float k;
            if (a < ext) k = a / ext;
            else if (target < 0) k = Math.max(0, 1 - (a - ext) / 5f);
            else if (a > dur - 3) k = Math.max(0, 1 - (a - (dur - 3)) / 3f);
            else k = 1;
            k = 1 - (1 - k) * (1 - k);
            return hand.lerp(goal, k);
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity c = w.getEntityById(caster);
            if (c == null) return true;
            Vec3d hand = HandTracker.hand(c, 0, 1f);
            Vec3d tip = tip(w, 1f, hand);
            if (age == 4 && target >= 0) {
                sparks(tip, 14, 0.5f, PINK, 0.12f);
                spawn(Kind.FLARE, tip, Vec3d.ZERO, 6, 0.9f).color(PALE, 1f);
            }
            for (int i = 0; i < 3; i++) {
                Vec3d p = hand.lerp(tip, R.nextDouble());
                spawn(i == 0 ? Kind.SMOKE_DARK : Kind.GLOW, p, randDir().multiply(0.02), rf(8, 14), rf(0.08f, 0.2f))
                        .color(i == 0 ? 0x05000A : PURPLE, i == 0 ? 0.5f : 0.8f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity ce = c.world.getEntityById(caster);
            if (ce == null) return;
            Vec3d hand = HandTracker.hand(ce, 0, c.tickDelta);
            Vec3d tip = tip(c.world, c.tickDelta, hand);
            Vec3d d = tip.subtract(hand);
            double len = d.length();
            if (len < 0.05) return;
            Vec3d dir = d.normalize();
            Vec3d up = Math.abs(dir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            Vec3d s1 = dir.crossProduct(up).normalize();
            Vec3d s2 = dir.crossProduct(s1).normalize();
            float time = age + c.tickDelta;
            int n = pts.length;
            for (int i = 0; i < n; i++) {
                double t = (double) i / (n - 1);
                double env = Math.sin(t * Math.PI) * Math.min(1.0, len / 4);
                double w1 = Math.sin(t * 9 - time * 0.9) * 0.18 * env;
                double w2 = Math.cos(t * 7 + time * 0.7) * 0.14 * env;
                pts[i] = hand.add(d.multiply(t)).add(s1.multiply(w1)).add(s2.multiply(w2)).subtract(c.cam);
                double camD = pts[i].length();
                float near = (float) Math.max(0.12, Math.min(1, camD / 3.0));
                widths[i] = (float) (0.13 * (1 - t * 0.55)) * near;
                alphas[i] = (float) Math.min(1, t * 8) * (float) Math.min(1, camD / 1.2);
            }
            VertexConsumer dark = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
            Draw.ribbon(dark, c.m, pts, n, widths, 0.02f, 0f, 0.05f, alphas, 1.2f, -time * 0.15f);
            for (int i = 0; i < n; i++) widths[i] *= 2.2f;
            VertexConsumer glow = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
            Draw.ribbon(glow, c.m, pts, n, widths, r(PURPLE), g(PURPLE), b(PURPLE), alphas, 0.8f, -time * 0.25f);
            for (int i = 0; i < n; i++) widths[i] *= 0.25f;
            Draw.ribbon(glow, c.m, pts, n, widths, r(PALE), g(PALE), b(PALE), alphas, 1.5f, -time * 0.4f);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Vec3d tr = tip.subtract(c.cam);
            Draw.billboard(g, c.m, c.camRot, tr.x, tr.y, tr.z, 0.45f, 0, r(PURPLE), g(PURPLE), b(PURPLE), 0.9f);
            if (!isLocalFp(ce)) {
                Vec3d hr = hand.subtract(c.cam);
                Draw.billboard(g, c.m, c.camRot, hr.x, hr.y, hr.z, 0.35f, 0, r(PURPLE), g(PURPLE), b(PURPLE), 0.7f);
            }
        }
    }

    // ================================================================ IMPACT
    public static final class Impact extends Fx {
        final Vec3d p;
        final String kind;
        final float scale;
        final float rot = R.nextFloat() * 6.28f;

        final int c1, c2;

        public Impact(Vec3d p, String kind, float scale) {
            this(p, kind, scale, -1);
        }

        public Impact(Vec3d p, String kind, float scale, int col) {
            super(kind.equals("punch") ? 7 : 10);
            this.p = p;
            this.kind = kind;
            this.scale = scale;
            this.c1 = col < 0 ? PURPLE : col;
            this.c2 = col < 0 ? PINK : mixWhite(col, 0.45f);
            boolean big = !kind.equals("punch");
            sparks(p, big ? 22 : 10, big ? 0.75f : 0.45f, big ? c2 : c1, big ? 0.13f : 0.09f);
            for (int i = 0; i < (big ? 6 : 2); i++) {
                spawn(Kind.SMOKE_DARK, p, randDir().multiply(0.06), rf(10, 18), rf(0.3f, 0.6f) * scale).color(0x0A0012, 0.55f).size1 = 0.9f * scale;
            }
            for (int i = 0; i < (big ? 10 : 4); i++) {
                spawn(Kind.GLOW, p, randDir().multiply(rf(0.05f, 0.25f)), rf(6, 12), rf(0.08f, 0.16f)).color(PALE, 1f);
            }
            if (big) CameraController.hitstop(kind.equals("crit") ? 2.5f : 1.5f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float s = scale;
            float nf = nearFade(rp, s * 2f);
            if (nf <= 0.01f) return;
            t = 1 - (1 - t) * nf;
            VertexConsumer imp = c.imm.getBuffer(AWRenderLayers.additive(Tex.IMPACT));
            float fs = s * (0.6f + 1.4f * (1 - (1 - t) * (1 - t)));
            Draw.billboard(imp, c.m, c.camRot, rp.x, rp.y, rp.z, fs, rot, r(PALE), g(PALE), b(PALE), (1 - t));
            Draw.billboard(imp, c.m, c.camRot, rp.x, rp.y, rp.z, fs * 1.3f, rot + 0.7f, r(c1), g(c1), b(c1), (1 - t) * 0.8f);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.billboard(ring, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.3f + t * 1.8f), 0, r(c2), g(c2), b(c2), (1 - t) * 0.9f);
            if (!kind.equals("punch")) {
                VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
                Draw.billboard(fl, c.m, c.camRot, rp.x, rp.y, rp.z, s * 2.6f * (1 - t * 0.5f), 0, 1, 1, 1, (1 - t) * (1 - t));
                VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
                Draw.billboard(sw, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.4f + t * 3.2f), rot, r(c1), g(c1), b(c1), (1 - t) * 0.7f);
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
                Draw.billboard(dk, c.m, c.camRot, rp.x, rp.y, rp.z, s * 0.7f * (1 - t), 0, 0.02f, 0, 0.05f, (1 - t) * 0.8f);
            }
        }
    }

    // ================================================================ CHARGE (공허 파동 준비)
    public static final class Charge extends Fx {
        final int caster;

        Charge(NbtCompound n) {
            super(n.getInt("d") + 1);
            caster = n.getInt("c");
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity c = w.getEntityById(caster);
            if (c == null) return true;
            Vec3d hand = HandTracker.hand(c, 0, 1f);
            for (int i = 0; i < 6; i++) {
                Vec3d from = hand.add(randDir().multiply(rf(1.2f, 2.4f)));
                Particles.P p = spawn(Kind.STREAK, from, hand.subtract(from).multiply(0.14), 10, rf(0.06f, 0.11f));
                p.color(R.nextBoolean() ? PURPLE : PINK, 1f);
                p.drag = 1.0f;
            }
            if (age % 2 == 0) spawn(Kind.SMOKE_DARK, hand, randDir().multiply(0.03), 10, 0.25f).color(0x050008, 0.6f);
            return super.tick(w);
        }

        public float progress(float td) {
            return Math.min(1, (age + td) / life);
        }

        public int caster() {
            return caster;
        }

        @Override
        public void render(Ctx c) {
            Entity ce = c.world.getEntityById(caster);
            if (ce == null || isLocalFp(ce)) return;
            Vec3d h = HandTracker.hand(ce, 0, c.tickDelta).subtract(c.cam);
            float t = progress(c.tickDelta);
            float time = age + c.tickDelta;
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.billboard(dk, c.m, c.camRot, h.x, h.y, h.z, 0.12f + t * 0.3f, 0, 0, 0, 0, 0.95f);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, h.x, h.y, h.z, 0.3f + t * 0.7f, 0, r(PURPLE), g(PURPLE), b(PURPLE), 0.9f);
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(fl, c.m, c.camRot, h.x, h.y, h.z, 0.5f + t * 0.8f, time * 0.2f, r(PINK), g(PINK), b(PINK), 0.6f);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            float rt = (time % 5) / 5f;
            Draw.billboard(ring, c.m, c.camRot, h.x, h.y, h.z, 1.2f * (1 - rt) + 0.15f, 0, r(PURPLE), g(PURPLE), b(PURPLE), rt * 0.8f);
        }
    }

    // ================================================================ BURST (공허 파동)
    public static final class Burst extends Fx {
        final Vec3d p, dir, ax, ay;

        Burst(Vec3d p, Vec3d dir) {
            super(16);
            this.p = p;
            this.dir = dir.normalize();
            Vec3d up = Math.abs(this.dir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            ax = this.dir.crossProduct(up).normalize();
            ay = this.dir.crossProduct(ax).normalize();
            for (int i = 0; i < 60; i++) {
                Vec3d d = this.dir.add(randDir().multiply(0.55)).normalize().multiply(rf(0.6f, 1.6f));
                spawn(Kind.STREAK, p, d, rf(6, 12), rf(0.08f, 0.16f)).color(i % 3 == 0 ? PALE : (i % 3 == 1 ? PINK : PURPLE), 1f).drag = 0.86f;
            }
            for (int i = 0; i < 16; i++) {
                Vec3d d = this.dir.add(randDir().multiply(0.7)).normalize().multiply(rf(0.1f, 0.45f));
                spawn(Kind.SMOKE_DARK, p.add(this.dir.multiply(rf(0, 2))), d, rf(14, 26), rf(0.4f, 0.8f)).color(0x07000E, 0.6f).size1 = 1.6f;
            }
            CameraController.hitstop(2f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            for (int i = 0; i < 4; i++) {
                float dist = (0.3f + i * 1.4f) * e;
                float rad = (0.5f + i * 0.75f) * (0.3f + e * 0.9f);
                Vec3d cc = p.add(dir.multiply(dist)).subtract(c.cam);
                float a = (1 - t) * (1 - i * 0.18f) * nearFade(cc, rad * 1.5f);
                Draw.quad(sw, c.m, cc, ax.multiply(rad), ay.multiply(rad), r(i == 0 ? PALE : PURPLE), g(i == 0 ? PALE : PURPLE), b(i == 0 ? PALE : PURPLE), a);
            }
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.RING_SOFT));
            for (int i = 0; i < 3; i++) {
                float dist = (0.8f + i * 1.6f) * e;
                float rad = (0.7f + i * 0.9f) * (0.3f + e);
                Vec3d cc = p.add(dir.multiply(dist)).subtract(c.cam);
                Draw.quad(dk, c.m, cc, ax.multiply(rad), ay.multiply(rad), 0.02f, 0, 0.05f, (1 - t) * 0.55f * nearFade(cc, rad * 1.5f));
            }
            Vec3d rp = p.subtract(c.cam);
            float nf = nearFade(rp, 3f);
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(fl, c.m, c.camRot, rp.x, rp.y, rp.z, 2.6f * (1 - t * 0.6f), 0, 1, 1, 1, (1 - t) * (1 - t) * nf);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, rp.x, rp.y, rp.z, 2.0f, 0, r(PURPLE), g(PURPLE), b(PURPLE), (1 - t) * 0.7f * nf);
        }
    }

    // ================================================================ TRAIL
    public static final class Trail extends Fx {
        final int entity;
        final Vec3d[] hist = new Vec3d[16];
        int count;
        final Vec3d[] pts = new Vec3d[16];
        final float[] w = new float[16], a = new float[16];

        Trail(NbtCompound n) {
            super(n.getInt("d") + 10);
            entity = n.getInt("e");
        }

        @Override
        public boolean tick(ClientWorld world) {
            Entity e = world.getEntityById(entity);
            if (e != null && age < life - 10) {
                System.arraycopy(hist, 0, hist, 1, hist.length - 1);
                hist[0] = e.getPos().add(0, e.getHeight() * 0.5, 0);
                count = Math.min(hist.length, count + 1);
                spawn(Kind.SMOKE_DARK, hist[0], randDir().multiply(0.03), 14, 0.35f).color(0x08000F, 0.5f);
                spawn(Kind.GLOW, hist[0].add(randDir().multiply(0.3)), Vec3d.ZERO, 10, 0.12f).color(PINK, 1f);
            } else if (count > 0) {
                count--;
            }
            return super.tick(world) || (count == 0 && age > 3);
        }

        @Override
        public void render(Ctx c) {
            if (count < 2) return;
            Entity e = c.world.getEntityById(entity);
            int n = count;
            for (int i = 0; i < n; i++) {
                Vec3d p = hist[i];
                if (i == 0 && e != null && age < life - 10) p = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() * 0.5, 0);
                pts[i] = p.subtract(c.cam);
                float k = 1f - (float) i / (n - 1);
                w[i] = 0.45f * k;
                a[i] = k * 0.9f;
            }
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
            Draw.ribbon(dk, c.m, pts, n, w, 0.02f, 0, 0.05f, a, 0.5f, 0);
            for (int i = 0; i < n; i++) w[i] *= 1.8f;
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
            Draw.ribbon(g, c.m, pts, n, w, r(PURPLE), g(PURPLE), b(PURPLE), a, 0.5f, (age + c.tickDelta) * 0.1f);
        }
    }

    // ================================================================ RIFT (순간이동 균열)
    public static final class Rift extends Fx {
        final Vec3d p;
        final float scale;

        Rift(Vec3d p, float scale) {
            super(16);
            this.p = p.add(0, 1.0 * scale, 0);
            this.scale = scale;
            for (int i = 0; i < 24; i++) {
                Vec3d off = new Vec3d(rf(-0.2f, 0.2f), rf(-1f, 1f) * scale, rf(-0.2f, 0.2f));
                spawn(i % 2 == 0 ? Kind.SMOKE_DARK : Kind.STREAK, this.p.add(off), randDir().multiply(0.12), rf(8, 16), rf(0.1f, 0.3f))
                        .color(i % 2 == 0 ? 0x05000A : PINK, i % 2 == 0 ? 0.6f : 1f);
            }
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float open = a < 3 ? a / 3f : (a > life - 5 ? Math.max(0, (life - a) / 5f) : 1f);
            open = 1 - (1 - open) * (1 - open);
            Vec3d rp = p.subtract(c.cam);
            Vec3d toCam = rp.multiply(-1);
            Vec3d side = new Vec3d(toCam.z, 0, -toCam.x);
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            float h = 1.25f * scale;
            float wdt = 0.55f * scale * open;
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.RIFT));
            Draw.quad(g, c.m, rp, side.multiply(wdt * 1.6), new Vec3d(0, h * 1.15, 0), 1, 1, 1, open);
            // 균열 내부: 공허(엔드 포털)
            VertexConsumer ep = c.imm.getBuffer(RenderLayer.getEndPortal());
            int seg = 10;
            for (int i = 0; i < seg; i++) {
                double y0 = -1 + 2.0 * i / seg, y1 = -1 + 2.0 * (i + 1) / seg;
                double w0 = Math.sqrt(Math.max(0, 1 - y0 * y0)) * wdt * 0.35, w1 = Math.sqrt(Math.max(0, 1 - y1 * y1)) * wdt * 0.35;
                Vec3d a0 = rp.add(side.multiply(-w0)).add(0, y0 * h, 0), b0 = rp.add(side.multiply(w0)).add(0, y0 * h, 0);
                Vec3d a1 = rp.add(side.multiply(-w1)).add(0, y1 * h, 0), b1 = rp.add(side.multiply(w1)).add(0, y1 * h, 0);
                ep.vertex(c.m, (float) a0.x, (float) a0.y, (float) a0.z).next();
                ep.vertex(c.m, (float) b0.x, (float) b0.y, (float) b0.z).next();
                ep.vertex(c.m, (float) b1.x, (float) b1.y, (float) b1.z).next();
                ep.vertex(c.m, (float) a1.x, (float) a1.y, (float) a1.z).next();
            }
        }
    }

    // ================================================================ CRATER (내리꽂기)
    public static final class Crater extends Fx {
        final Vec3d p;
        final float radius;
        final float rot = R.nextFloat() * 6.28f;

        int col = PURPLE, col2 = PINK;

        Crater(ClientWorld w, Vec3d at, float radius) {
            this(w, at, radius, -1);
        }

        Crater(ClientWorld w, Vec3d at, float radius, int color) {
            super(40);
            if (color >= 0) {
                col = color;
                col2 = mixWhite(color, 0.4f);
            }
            Vec3d gnd = ground(w, at.add(0, 0.5, 0), 4);
            this.p = gnd != null ? gnd.add(0, 0.03, 0) : at;
            this.radius = radius;
            BlockState st = groundState(w, this.p);
            int nDebris = (int) (radius * 3);
            for (int i = 0; i < nDebris && st != null; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                double d = R.nextDouble() * radius * 0.6;
                Vec3d sp = p.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
                Vec3d vel = new Vec3d(Math.cos(a) * rf(0.08f, 0.25f), rf(0.3f, 0.65f) * Math.min(1.5, radius / 3), Math.sin(a) * rf(0.08f, 0.25f));
                Debris.spawn(st, sp, vel, rf(0.2f, 0.45f), rf(18, 30), null, 0, true);
            }
            if (st != null) {
                BlockStateParticleEffect bp = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
                for (int i = 0; i < radius * 18; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double d = R.nextDouble() * radius;
                    w.addParticle(bp, p.x + Math.cos(a) * d, p.y + 0.1, p.z + Math.sin(a) * d, Math.cos(a) * 0.3, rf(0.1f, 0.5f), Math.sin(a) * 0.3);
                }
            }
            for (int i = 0; i < 28; i++) {
                double a = i / 28.0 * Math.PI * 2;
                Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                spawn(Kind.SMOKE_DARK, p.add(dir.multiply(0.4)).add(0, 0.2, 0), dir.multiply(rf(0.15f, 0.3f) * radius / 3).add(0, 0.03, 0), rf(18, 30), rf(0.5f, 0.9f))
                        .color(col == PURPLE ? 0x0B0612 : 0x140A06, 0.55f).size1 = 1.4f;
            }
            sparks(p.add(0, 0.3, 0), (int) (radius * 8), 0.8f, col2, 0.14f);
            for (int i = 0; i < 6; i++) {
                spawn(Kind.WISP_ADD, p.add(rf(-1, 1) * radius * 0.4, 0.2, rf(-1, 1) * radius * 0.4), new Vec3d(0, rf(0.1f, 0.25f), 0), rf(10, 18), rf(0.4f, 0.8f)).color(col, 0.8f);
            }
            CameraController.hitstop(3f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float e = 1 - (1 - Math.min(1, t * 2.5f)) * (1 - Math.min(1, t * 2.5f));
            VertexConsumer crack = c.imm.getBuffer(AWRenderLayers.translucent(Tex.CRACK));
            float ca = t < 0.7f ? 1 : (1 - t) / 0.3f;
            Draw.flat(crack, c.m, rp.x, rp.y, rp.z, radius * 0.9f, rot, 0.03f, 0.0f, 0.06f, ca * 0.9f);
            VertexConsumer crackGlow = c.imm.getBuffer(AWRenderLayers.additive(Tex.CRACK));
            Draw.flat(crackGlow, c.m, rp.x, rp.y + 0.01, rp.z, radius * 0.9f, rot, r(col), g(col), b(col), ca * 0.6f * (1 - t));
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.flat(sw, c.m, rp.x, rp.y + 0.05, rp.z, radius * (0.3f + 1.6f * e), 0, r(col2), g(col2), b(col2), (1 - Math.min(1, t * 2f)) * 0.9f);
            VertexConsumer mc = c.imm.getBuffer(AWRenderLayers.additive(col == PURPLE ? Tex.MAGIC_CIRCLE2 : Tex.fx("dragon_rune")));
            float mt = Math.min(1, t * 3);
            Draw.flat(mc, c.m, rp.x, rp.y + 0.04, rp.z, radius * (0.6f + 0.6f * mt), rot + t * 1.5f, r(col), g(col), b(col), (1 - mt) * 0.9f);
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            float ft = Math.min(1, t * 4);
            Draw.billboard(fl, c.m, c.camRot, rp.x, rp.y + 0.5, rp.z, radius * 1.6f * (1 - ft * 0.5f), 0, 1, 1, 1, (1 - ft));
            // 수직 충격파
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            Vec3d toCam = rp.multiply(-1);
            Vec3d side = new Vec3d(toCam.z, 0, -toCam.x);
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            float rr = radius * (0.2f + e * 0.8f);
            Draw.quad(ring, c.m, rp.add(0, rr * 0.3, 0), side.multiply(rr), new Vec3d(0, rr * 0.35, 0), r(col), g(col), b(col), (1 - Math.min(1, t * 3)) * 0.8f);
        }
    }

    // ================================================================ SHIELD (반격/막기)
    public static class Shield extends Fx {
        final int caster;
        final int kind;
        float scaleMul = 1f;
        int fadeAge = -1;
        boolean broken;

        Shield(int caster, int dur, int kind) {
            super(dur);
            this.caster = caster;
            this.kind = kind;
        }

        public int caster() {
            return caster;
        }

        public int kind() {
            return kind;
        }

        public float visibility(float td) {
            float a = age + td;
            float in = Math.min(1, a / 3f);
            in = Effects.outBack(in);
            float out = fadeAge >= 0 ? Math.max(0, 1 - (a - fadeAge) / 6f) : 1;
            return in * out;
        }

        public void fade() {
            if (fadeAge < 0) {
                fadeAge = age;
                life = age + 6;
            }
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(caster);
            if (e == null || isLocalFp(e)) return;
            float vis = visibility(c.tickDelta);
            if (vis <= 0.01f) return;
            float yaw = (e instanceof LivingEntity le ? MathHelper.lerpAngleDegrees(c.tickDelta, le.prevBodyYaw, le.bodyYaw) : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
            Vec3d center = e.getLerpedPos(c.tickDelta).add(0, kind == 0 ? 1.25 : 1.15, 0).add(fwd.multiply(kind == 0 ? 0.75 : 0.55)).subtract(c.cam);
            float size = (kind == 0 ? 0.95f : 0.7f) * vis * scaleMul;
            float time = age + c.tickDelta;
            double ang = time * (kind == 0 ? 0.08 : 0.03);
            Vec3d ax = right.multiply(Math.cos(ang)).add(new Vec3d(0, Math.sin(ang), 0)).multiply(size);
            Vec3d ay = right.multiply(-Math.sin(ang)).add(new Vec3d(0, Math.cos(ang), 0)).multiply(size);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.quad(dk, c.m, center, right.multiply(size * 1.1), new Vec3d(0, size * 1.1, 0), 0.02f, 0, 0.05f, 0.55f * vis);
            VertexConsumer rs = c.imm.getBuffer(AWRenderLayers.additive(Tex.RUNE_SHIELD));
            float pulse = kind == 0 ? 0.75f + 0.25f * (float) Math.sin(time * 1.2) : 0.6f;
            int col = abilityColor(e), col2 = col == PURPLE ? PINK : mixWhite(col, 0.4f);
            Draw.quad(rs, c.m, center, ax, ay, r(col), g(col), b(col), vis * pulse);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.quad(ring, c.m, center, right.multiply(size * 1.15), new Vec3d(0, size * 1.15, 0), r(col2), g(col2), b(col2), vis * 0.7f);
        }
    }

    public static final class BlockShield extends Shield {
        BlockShield(int caster) {
            super(caster, 100000, 1);
        }

        @Override
        public boolean tick(ClientWorld w) {
            age++;
            Entity e = w.getEntityById(caster);
            boolean on = e instanceof AWPlayer p && p.aw$hasFlag(AWPlayer.FLAG_BLOCKING);
            if (!on) fade();
            return age >= life;
        }
    }

    /** 능력 고유 색 (막기 방패, 주먹 기운 등). 능력이 없으면 공허 보라. */
    public static int abilityColor(Entity e) {
        if (e instanceof AWPlayer p) {
            com.abilitywar.ability.Ability a = com.abilitywar.ability.Abilities.byString(p.aw$getAbilityKey());
            if (a != null) return a.color;
        }
        return Fx.PURPLE;
    }

    public static int mixWhite(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * k);
        g = (int) (g + (255 - g) * k);
        b = (int) (b + (255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    public static float outBack(float x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
    }

    // ================================================================ SHADOW POOL
    public static final class ShadowPool extends Fx {
        final Vec3d p;
        final int kind;
        final float rot = R.nextFloat() * 6.28f;

        ShadowPool(ClientWorld w, Vec3d at, int kind) {
            super(kind == 0 ? 26 : kind == 1 ? 22 : kind == 2 ? 14 : 14);
            Vec3d g = ground(w, at.add(0, 0.4, 0), 3);
            this.p = g != null ? g.add(0, 0.02 + R.nextFloat() * 0.005, 0) : at.add(0, 0.02, 0);
            this.kind = kind;
            int n = kind == 3 ? 1 : 14;
            for (int i = 0; i < n; i++) {
                Vec3d off = new Vec3d(rf(-0.6f, 0.6f), 0.05, rf(-0.6f, 0.6f));
                spawn(Kind.WISP_DARK, p.add(off), new Vec3d(0, rf(0.03f, kind == 1 ? 0.18f : 0.08f), 0), rf(12, 22), rf(0.25f, 0.5f)).color(0x050008, 0.75f);
                if (kind != 3 && i % 2 == 0)
                    spawn(Kind.GLOW, p.add(off), new Vec3d(0, rf(0.02f, 0.12f), 0), rf(10, 18), rf(0.05f, 0.1f)).color(PURPLE, 1f);
            }
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            float s;
            float a;
            switch (kind) {
                case 0 -> { s = Math.min(1, t * 4) * 1.3f; a = t > 0.75f ? (1 - t) / 0.25f : 1f; }
                case 1 -> { s = 1.4f * (1 - t * 0.6f); a = 1 - t; }
                case 2 -> { s = 1.2f * (1 - t); a = 1 - t; }
                default -> { s = 0.9f; a = (1 - t) * 0.7f; }
            }
            Vec3d rp = p.subtract(c.cam);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.SHADOW_POOL));
            Draw.flat(dk, c.m, rp.x, rp.y, rp.z, s, rot + t * 0.6f, 1, 1, 1, a * 0.95f);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            Draw.flat(ring, c.m, rp.x, rp.y + 0.005, rp.z, s * 0.85f, 0, r(PURPLE), g(PURPLE), b(PURPLE), a * 0.35f);
        }
    }

    // ================================================================ ULT CAST
    public static final class UltCast extends Fx {
        final int caster;
        final Vec3d target;
        final int dur;
        Vec3d groundPos;
        Vec3d orbPos = null, orbPrev = null;
        final Vec3d[] trail = new Vec3d[12];
        int trailN;

        UltCast(ClientWorld w, NbtCompound n) {
            super(n.getInt("d") + 4);
            caster = n.getInt("c");
            target = new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz"));
            dur = n.getInt("d");
            Vec3d p = pos(n);
            Vec3d g = ground(w, p.add(0, 0.5, 0), 4);
            groundPos = (g != null ? g : p).add(0, 0.04, 0);
        }

        Vec3d orb(ClientWorld w, float td) {
            Entity e = w.getEntityById(caster);
            Vec3d hand = e != null ? HandTracker.hand(e, 0, td) : groundPos.add(0, 2.5, 0);
            Vec3d above = hand.add(0, 0.55, 0);
            float a = age + td;
            if (a < dur - 20) return above;
            float k = Math.min(1, (a - (dur - 20)) / 20f);
            k = k * k * (3 - 2 * k);
            Vec3d mid = above.lerp(target, 0.5).add(0, 3.0, 0);
            Vec3d p1 = above.lerp(mid, k), p2 = mid.lerp(target, k);
            return p1.lerp(p2, k);
        }

        @Override
        public boolean tick(ClientWorld w) {
            Vec3d o = orb(w, 1f);
            orbPrev = orbPos;
            orbPos = o;
            System.arraycopy(trail, 0, trail, 1, trail.length - 1);
            trail[0] = o;
            trailN = Math.min(trail.length, trailN + 1);
            float prog = (float) age / dur;
            // 마법진에서 올라가는 빛
            int n = age < 10 ? 2 : 6;
            for (int i = 0; i < n; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                double rr = rf(1f, 4.8f);
                Vec3d sp = groundPos.add(Math.cos(a) * rr, 0.05, Math.sin(a) * rr);
                Particles.P p = spawn(i % 3 == 0 ? Kind.WISP_DARK : Kind.STREAK, sp, new Vec3d(0, rf(0.08f, 0.22f), 0), rf(16, 30), rf(0.06f, 0.14f));
                if (i % 3 == 0) {
                    p.color(0x060010, 0.6f);
                    p.size0 = 0.4f;
                } else {
                    p.color(R.nextBoolean() ? PURPLE : PINK, 1f);
                    p.attract = orbPos;
                    p.attractStrength = 0.01f + prog * 0.03f;
                    p.drag = 0.95f;
                }
            }
            if (age > 12 && age < dur - 18) {
                for (int i = 0; i < 5; i++) {
                    Vec3d from = o.add(randDir().multiply(rf(1.5f, 3.5f)));
                    Particles.P p = spawn(Kind.STREAK, from, o.subtract(from).multiply(0.1), 12, rf(0.05f, 0.1f));
                    p.color(PALE, 1f);
                    p.drag = 1f;
                }
            }
            if (age >= dur - 20 && age < dur) {
                spawn(Kind.SMOKE_DARK, o, randDir().multiply(0.05), 20, 0.7f).color(0x05000A, 0.7f);
                sparks(o, 3, 0.3f, PINK, 0.1f);
            }
            if (age == dur) {
                sparks(target, 60, 1.2f, PALE, 0.18f);
            }
            return super.tick(w);
        }

        public float darkness(float td) {
            float a = age + td;
            float in = Math.min(1, a / 12f);
            float out = a > dur ? Math.max(0, 1 - (a - dur) / 4f) : 1;
            return in * out;
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float time = a;
            Vec3d gp = groundPos.subtract(c.cam);
            float grow = outBack(Math.min(1, a / 12f));
            float fade = a > dur ? Math.max(0, 1 - (a - dur) / 4f) : 1;
            float R0 = 5.0f * grow;
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.flat(dk, c.m, gp.x, gp.y - 0.01, gp.z, R0 * 1.3f, 0, 0.02f, 0, 0.04f, 0.7f * fade);
            VertexConsumer mc1 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            Draw.flat(mc1, c.m, gp.x, gp.y, gp.z, R0, time * 0.012f, r(PURPLE), g(PURPLE), b(PURPLE), 0.95f * fade);
            VertexConsumer mc2 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
            Draw.flat(mc2, c.m, gp.x, gp.y + 0.01, gp.z, R0 * 0.62f, -time * 0.025f, r(PINK), g(PINK), b(PINK), 0.85f * fade);
            // 빛기둥
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            if (a > 8) {
                float pa = Math.min(1, (a - 8) / 8f) * fade;
                for (int i = 0; i < 8; i++) {
                    double ang = i / 8.0 * Math.PI * 2 + time * 0.012;
                    Vec3d base = gp.add(Math.cos(ang) * R0 * 0.92, 0, Math.sin(ang) * R0 * 0.92);
                    float hgt = 3.5f + (float) Math.sin(time * 0.3 + i) * 0.8f;
                    Draw.streak(st, c.m, base.add(0, hgt, 0), new Vec3d(0, 1, 0), hgt, 0.18f, r(PURPLE), g(PURPLE), b(PURPLE), pa * 0.8f);
                }
            }
            // 오브
            if (orbPos != null && a > 10) {
                Vec3d o = (orbPrev != null ? orbPrev.lerp(orbPos, c.tickDelta) : orbPos).subtract(c.cam);
                float os = Math.min(1, (a - 10) / 30f);
                os = os * os * (3 - 2 * os);
                float rad = 0.15f + os * 0.6f;
                VertexConsumer ep = c.imm.getBuffer(RenderLayer.getEndPortal());
                Draw.spherePos(ep, c.m, o, rad * 0.7f, 8, 12);
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
                Draw.billboard(g, c.m, c.camRot, o.x, o.y, o.z, rad * 3.2f, 0, r(PURPLE), g(PURPLE), b(PURPLE), 0.85f * fade);
                VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
                Draw.billboard(ring, c.m, c.camRot, o.x, o.y, o.z, rad * 1.05f, 0, r(PALE), g(PALE), b(PALE), 0.95f * fade);
                VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SWIRL));
                Draw.annulus(sw, c.m, o, new Vec3d(1, 0, 0), new Vec3d(0, 0.25, 0.97).normalize(), rad * 1.1f, rad * 2.6f, 24, -time * 0.03f, 2, 1, 1, 1, 0.9f * fade, 0);
                if (age % 2 == 0 || true) {
                    VertexConsumer lt = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
                    long seed = (long) (time / 2);
                    Random rr = new Random(seed * 31);
                    for (int i = 0; i < 3; i++) {
                        double ang = rr.nextDouble() * Math.PI * 2;
                        Vec3d end = gp.add(Math.cos(ang) * R0 * 0.9, 0.05, Math.sin(ang) * R0 * 0.9);
                        if (a < dur - 20) Draw.lightning(lt, c.m, o, end, seed * 7 + i, 0.025f, 10, 0.7f, r(PINK), g(PINK), b(PINK), 0.8f * fade);
                    }
                }
                if (trailN > 2 && a > dur - 20) {
                    Vec3d[] pts = new Vec3d[trailN];
                    float[] w = new float[trailN], al = new float[trailN];
                    for (int i = 0; i < trailN; i++) {
                        pts[i] = trail[i].subtract(c.cam);
                        float k = 1 - (float) i / (trailN - 1);
                        w[i] = rad * 0.9f * k;
                        al[i] = k;
                    }
                    pts[0] = o;
                    VertexConsumer tr = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                    Draw.ribbon(tr, c.m, pts, trailN, w, r(PURPLE), g(PURPLE), b(PURPLE), al, 0.4f, time * 0.1f);
                }
            }
        }
    }

    // ================================================================ ULT AWAKEN (모든 궁극기 앞 2초 각성)
    /**
     * 발밑에 두 겹 마법진이 펼쳐지고, 둘레에서 빛줄기가 솟고, 빛 입자 · 번개가 몸으로 빨려 들며 몸의 빛이 커짐 →
     * 끝에 한 점으로 압축됐다가 빛기둥 + 충격파 고리 + 불꽃으로 터짐. 색은 능력 색 (n "col").
     */
    public static final class UltAwaken extends Fx {
        final int caster, dur, col, light;
        final String key;
        final Vec3d groundPos;

        UltAwaken(ClientWorld w, NbtCompound n) {
            super(n.getInt("d") + 14);
            key = n.getString("k");
            caster = n.getInt("c");
            dur = n.getInt("d");
            col = n.getInt("col");
            light = lighten(col, 0.55f);
            Vec3d p = pos(n);
            Vec3d g = ground(w, p.add(0, 0.5, 0), 4);
            groundPos = (g != null ? g : p).add(0, 0.05, 0);
        }

        static int lighten(int c, float k) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            return ((int) (r + (255 - r) * k) << 16) | ((int) (g + (255 - g) * k) << 8) | (int) (b + (255 - b) * k);
        }

        Vec3d core(ClientWorld w, float td) {
            Entity e = w.getEntityById(caster);
            return e != null ? chest(e, td) : groundPos.add(0, 1.2, 0);
        }

        @Override
        public boolean tick(ClientWorld w) {
            Vec3d o = core(w, 1f);
            if (age < dur - 4) {
                // 둘레에서 솟는 빛 + 몸으로 빨려드는 빛 입자
                for (int i = 0; i < 5; i++) {
                    double a = R.nextDouble() * Math.PI * 2, rr = rf(3.0f, 5.5f);
                    Vec3d sp = groundPos.add(Math.cos(a) * rr, rf(0.1f, 2.5f), Math.sin(a) * rr);
                    Particles.P p = spawn(Kind.STREAK, sp, o.subtract(sp).multiply(0.02), rf(14, 22), rf(0.06f, 0.12f));
                    p.color(i % 2 == 0 ? col : light, 1f);
                    p.attract = o;
                    p.attractStrength = 0.02f + 0.05f * age / dur;
                    p.drag = 0.92f;
                }
                for (int i = 0; i < 2; i++) {
                    double a = R.nextDouble() * Math.PI * 2, rr = rf(1.5f, 4.5f);
                    spawn(Kind.GLOW, groundPos.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), new Vec3d(0, rf(0.12f, 0.3f), 0), rf(12, 20), rf(0.08f, 0.16f)).color(light, 0.9f);
                }
            }
            if (age < dur) classParticles(w, o);
            if (age == dur - 1) {
                // 터짐: 사방으로 불꽃 + 연기 고리
                sparks(o, 70, 1.4f, light, 0.16f);
                sparks(o, 40, 0.8f, col, 0.2f);
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    spawn(Kind.SMOKE_ADD, groundPos.add(0, 0.3, 0), new Vec3d(Math.cos(a) * 0.55, 0.02, Math.sin(a) * 0.55), 24, 0.9f).color(col, 0.5f);
                }
            }
            return super.tick(w);
        }

        /** 직업마다 다른 입자. */
        void classParticles(ClientWorld w, Vec3d o) {
            double ang = R.nextDouble() * Math.PI * 2, rr = rf(0.6f, 2.2f);
            Vec3d around = o.add(Math.cos(ang) * rr, rf(-1.0f, 0.8f), Math.sin(ang) * rr);
            switch (key) {
                case "dragon" -> { // 솟구치는 불티 + 불꽃
                    for (int i = 0; i < 3; i++) spawn(Kind.FLAME, groundPos.add(rf(-2.5f, 2.5f), 0.2, rf(-2.5f, 2.5f)), new Vec3d(0, rf(0.1f, 0.25f), 0), rf(14, 22), rf(0.25f, 0.45f)).color(0xFFA040, 1f);
                    spawn(Kind.SPARK, around, new Vec3d(0, rf(0.05f, 0.15f), 0), 16, 0.08f).color(0xFFD080, 1f);
                }
                case "blood" -> { // 몸을 감도는 핏빛 안개 + 핏방울
                    Particles.P p = spawn(Kind.SMOKE_ADD, around, new Vec3d(-Math.sin(ang) * 0.12, 0.02, Math.cos(ang) * 0.12), 24, 0.6f);
                    p.color(0xB0101E, 0.55f);
                    spawn(Kind.GLOW, o.add(rf(-0.5f, 0.5f), rf(0.5f, 1.2f), rf(-0.5f, 0.5f)), new Vec3d(0, -0.12, 0), 14, 0.07f).color(0xFF2030, 1f);
                }
                case "cantor", "angel", "paladin" -> { // 내려앉는 금빛 깃털빛 + 반짝임
                    spawn(Kind.WISP_ADD, o.add(rf(-2.5f, 2.5f), rf(2.0f, 3.5f), rf(-2.5f, 2.5f)), new Vec3d(rf(-0.02f, 0.02f), -0.05, rf(-0.02f, 0.02f)), 40, rf(0.12f, 0.2f)).color(0xFFF0C0, 0.9f);
                    spawn(Kind.SPARK, around, Vec3d.ZERO, 12, 0.06f).color(0xFFE080, 1f);
                }
                case "warlock" -> { // 땅에서 올라와 몸으로 모이는 초록 영혼
                    Particles.P p = spawn(Kind.WISP_ADD, groundPos.add(rf(-3.5f, 3.5f), 0.2, rf(-3.5f, 3.5f)), new Vec3d(0, 0.06, 0), 34, rf(0.18f, 0.3f));
                    p.color(0x48E8A0, 0.8f);
                    p.attract = o;
                    p.attractStrength = 0.02f;
                }
                case "guardian" -> { // 금빛 방패 고리 조각
                    spawn(Kind.RING, o, Vec3d.ZERO, 10, rf(0.8f, 1.6f)).color(0xF0C860, 0.5f);
                    spawn(Kind.SPARK, around, new Vec3d(0, 0.08, 0), 14, 0.07f).color(0xFFE6A0, 1f);
                }
                case "blade" -> { // 보랏빛 검기 잔상
                    Vec3d d = randDir().multiply(0.4);
                    Particles.P p = spawn(Kind.STREAK, around, d, 8, 0.12f);
                    p.color(0xC870FF, 1f);
                    p.drag = 1f;
                }
                default -> { // 공허: 빨려드는 검은 연기
                    Particles.P p = spawn(Kind.SMOKE_DARK, around.add(Math.cos(ang) * 2, 0, Math.sin(ang) * 2), Vec3d.ZERO, 26, 0.7f);
                    p.color(0x06000C, 0.6f);
                    p.attract = o;
                    p.attractStrength = 0.03f;
                }
            }
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Vec3d gp = groundPos.subtract(c.cam);
            Vec3d o = core(c.world, c.tickDelta).subtract(c.cam);
            float fade = a > dur ? Math.max(0, 1 - (a - dur) / 10f) : 1;
            float grow = outBack(Math.min(1, a / 10f));
            float pulse = a > 28 && a < dur ? 1 + 0.12f * (float) Math.sin(a * 1.6f) : 1;
            float R0 = 4.6f * grow * pulse;
            // 바닥: 어둠 + 두 겹 마법진
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.flat(dk, c.m, gp.x, gp.y - 0.01, gp.z, R0 * 1.4f, 0, 0.02f, 0.0f, 0.04f, 0.65f * fade);
            VertexConsumer mc1 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            Draw.flat(mc1, c.m, gp.x, gp.y, gp.z, R0, a * 0.02f, r(col), g(col), b(col), 0.95f * fade);
            VertexConsumer mc2 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
            Draw.flat(mc2, c.m, gp.x, gp.y + 0.02, gp.z, R0 * 0.6f, -a * 0.04f, r(light), g(light), b(light), 0.9f * fade);
            // 둘레의 빛줄기
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            if (a > 6) {
                float pa = Math.min(1, (a - 6) / 6f) * fade;
                for (int i = 0; i < 10; i++) {
                    double ang = i / 10.0 * Math.PI * 2 + a * 0.02;
                    Vec3d base = gp.add(Math.cos(ang) * R0 * 0.95, 0, Math.sin(ang) * R0 * 0.95);
                    float hgt = 2.5f + 1.5f * (float) Math.sin(a * 0.35 + i * 1.7) + (a > 30 ? (a - 30) * 0.4f : 0);
                    Draw.streak(st, c.m, base.add(0, hgt, 0), new Vec3d(0, 1, 0), hgt, 0.16f, r(col), g(col), b(col), pa * 0.85f);
                }
            }
            // 몸의 빛: 커지다가 (30~38) 한 점으로 압축
            float core;
            if (a < 30) core = 0.4f + 1.6f * Math.min(1, a / 30f);
            else if (a < dur - 2) core = 2.0f * (1 - (a - 30) / (dur - 32f)) + 0.25f;
            else core = 0.25f + (a - (dur - 2)) * 1.4f;
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            float nf = nearFade(o, core * 2.5f);
            Draw.billboard(gl, c.m, c.camRot, o.x, o.y, o.z, core * 2.5f, 0, r(col), g(col), b(col), 0.75f * fade * nf);
            Draw.billboard(gl, c.m, c.camRot, o.x, o.y, o.z, core * 1.1f, 0, r(light), g(light), b(light), 0.9f * fade * nf);
            // 번개: 마법진 둘레 → 몸
            if (a > 12 && a < dur - 2) {
                VertexConsumer lt = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
                long seed = (long) (a / 2);
                java.util.Random rr = new java.util.Random(seed * 131);
                for (int i = 0; i < 4; i++) {
                    double ang = rr.nextDouble() * Math.PI * 2;
                    Vec3d from = gp.add(Math.cos(ang) * R0 * 0.9, 0.05, Math.sin(ang) * R0 * 0.9);
                    Draw.lightning(lt, c.m, from, o, seed * 7 + i, 0.03f, 12, 0.8f, r(light), g(light), b(light), 0.85f * fade);
                }
            }
            // 끝: 하늘로 솟는 빛기둥 + 바닥으로 퍼지는 충격파 고리
            if (a > dur - 4) {
                float k = Math.min(1, (a - (dur - 4)) / 4f);
                float hf = fade * (a < dur ? k : 1);
                Draw.streak(st, c.m, gp.add(0, 18, 0), new Vec3d(0, 1, 0), 18, 0.9f + 0.6f * k, r(light), g(light), b(light), 0.9f * hf);
                Draw.streak(st, c.m, gp.add(0, 18, 0), new Vec3d(0, 1, 0), 18, 2.2f, r(col), g(col), b(col), 0.5f * hf);
            }
            if (a > dur - 1) {
                float k = (a - (dur - 1)) / 12f;
                float rad = 1 + 14 * (1 - (1 - k) * (1 - k));
                VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
                Draw.flat(ring, c.m, gp.x, gp.y + 0.1, gp.z, rad, 0, r(light), g(light), b(light), Math.max(0, 1 - k) * 0.95f);
                Draw.flat(ring, c.m, gp.x, gp.y + 0.12, gp.z, rad * 0.7f, 0, r(col), g(col), b(col), Math.max(0, 1 - k) * 0.8f);
            }
        }
    }

    // ================================================================ DASH (공통 대시)
    public static final class DashTrail extends Fx {
        final int entity, color;
        final boolean dark;
        final Vec3d dir, start;
        final float rot = R.nextFloat() * 6.28f;

        DashTrail(ClientWorld w, int entity, int color, String ability, Vec3d dir, Vec3d start) {
            super(10);
            this.entity = entity;
            this.color = color;
            this.dark = "void".equals(ability);
            this.dir = dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : dir.normalize();
            Vec3d g = ground(w, start.add(0, 0.4, 0), 3);
            this.start = g != null ? g.add(0, 0.03, 0) : start;
            // 출발 지점: 바람 고리 + 먼지
            for (int i = 0; i < 14; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Vec3d out = new Vec3d(Math.cos(a), 0, Math.sin(a)).multiply(rf(0.08f, 0.2f)).subtract(this.dir.multiply(0.12));
                spawn(dark ? Kind.SMOKE_DARK : Kind.SMOKE_ADD, this.start.add(0, 0.15, 0), out.add(0, 0.02, 0), rf(10, 16), rf(0.25f, 0.45f))
                        .color(dark ? 0x0A0414 : 0xDDDDEE, dark ? 0.5f : 0.25f).size1 = 0.8f;
            }
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(entity);
            if (e != null && age < 6) {
                Vec3d back = dir.multiply(-1);
                // 1인칭 본인은 카메라에 붙지 않게 뒤쪽 아래에서만
                Vec3d c = isLocalFp(e) ? e.getPos().add(back.multiply(1.6)).add(0, 0.5, 0) : e.getPos().add(0, e.getHeight() * 0.5, 0);
                for (int i = 0; i < 6; i++) {
                    Vec3d off = new Vec3d(rf(-0.35f, 0.35f), rf(-0.8f, 0.8f), rf(-0.35f, 0.35f));
                    Particles.P p = spawn(Kind.STREAK, c.add(off), back.multiply(rf(0.3f, 0.7f)), rf(5, 9), rf(0.05f, 0.1f));
                    p.color(i % 2 == 0 ? color : PALE, 0.9f);
                    p.drag = 0.8f;
                }
                if (dark) {
                    spawn(Kind.SMOKE_DARK, c.add(rf(-0.3f, 0.3f), rf(-0.6f, 0.4f), rf(-0.3f, 0.3f)), back.multiply(0.05), 14, 0.45f).color(0x07000E, 0.55f).size1 = 0.9f;
                    spawn(Kind.WISP_DARK, e.getPos().add(0, 0.05, 0), new Vec3d(0, 0.06, 0), 12, 0.35f).color(0x050008, 0.6f);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = start.subtract(c.cam);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.flat(ring, c.m, rp.x, rp.y, rp.z, 0.4f + t * 1.8f, rot, r(color), g(color), b(color), (1 - t) * 0.7f);
            if (dark) {
                VertexConsumer pool = c.imm.getBuffer(AWRenderLayers.translucent(Tex.SHADOW_POOL));
                Draw.flat(pool, c.m, rp.x, rp.y - 0.005, rp.z, 0.9f * (1 - t * 0.5f), rot, 1, 1, 1, (1 - t) * 0.8f);
            }
        }
    }

    // ================================================================ CAPTURE / COLLAPSE / BLOCK HIT
    public static final class Flash extends Fx {
        final Vec3d p;
        final float size;
        final int color;
        final net.minecraft.util.Identifier tex;

        public Flash(Vec3d p, float size, int color, int life, net.minecraft.util.Identifier tex) {
            super(life);
            this.p = p;
            this.size = size;
            this.color = color;
            this.tex = tex;
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            VertexConsumer v = c.imm.getBuffer(AWRenderLayers.additive(tex));
            Draw.billboard(v, c.m, c.camRot, rp.x, rp.y, rp.z, size * (0.4f + t), 0, r(color), g(color), b(color), (1 - t) * (1 - t) * nearFade(rp, size));
        }
    }

    public static final class Collapse extends Fx {
        final Vec3d p;

        Collapse(Vec3d p) {
            super(40);
            this.p = p;
            CameraController.hitstop(4f);
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == 4) {
                // 폭발: 바깥으로 튀는 빛줄기 + 얇은 보라 연기
                for (int i = 0; i < 140; i++) {
                    Vec3d d = randDir().multiply(rf(0.6f, 2.0f));
                    spawn(Kind.STREAK, p, d, rf(10, 20), rf(0.1f, 0.22f)).color(i % 3 == 0 ? PALE : (i % 3 == 1 ? PINK : PURPLE), 1f).drag = 0.87f;
                }
                for (int i = 0; i < 26; i++) {
                    double a = i / 26.0 * Math.PI * 2;
                    Vec3d d = new Vec3d(Math.cos(a), rf(-0.15f, 0.25f), Math.sin(a)).multiply(rf(0.35f, 0.6f));
                    Particles.P sp = spawn(Kind.SMOKE_DARK, p.add(d.multiply(1.5)), d, rf(18, 30), rf(0.5f, 0.8f));
                    sp.color(0x1A0830, 0.45f);
                    sp.size1 = 1.3f;
                    spawn(Kind.GLOW, p.add(d.multiply(2)), d.multiply(1.3), rf(8, 14), rf(0.3f, 0.5f)).color(PURPLE, 0.8f);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Vec3d rp = p.subtract(c.cam);
            if (a < 4) {
                // 내파: 고리가 중심으로 빨려든다
                float k = a / 4f;
                VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
                Draw.billboard(ring, c.m, c.camRot, rp.x, rp.y, rp.z, 7f * (1 - k) + 0.4f, 0, r(PINK), g(PINK), b(PINK), 0.9f * nearFade(rp, 4));
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
                Draw.billboard(dk, c.m, c.camRot, rp.x, rp.y, rp.z, 2.5f * (1 - k * 0.7f), 0, 0, 0, 0, 0.9f);
                return;
            }
            float t = (a - 4) / (life - 4);
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            float ft = Math.min(1, t * 3.5f);
            Draw.billboard(fl, c.m, c.camRot, rp.x, rp.y, rp.z, 8 * (0.4f + ft), 0, 1, 1, 1, (1 - ft) * nearFade(rp, 5));
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, rp.x, rp.y, rp.z, 9 * e, 0, r(PURPLE), g(PURPLE), b(PURPLE), (1 - t) * 0.75f * nearFade(rp, 5));
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            float st = Math.min(1, t * 1.8f);
            Draw.flat(sw, c.m, rp.x, rp.y, rp.z, 14 * e, 0, r(PURPLE), g(PURPLE), b(PURPLE), (1 - st) * 0.4f);
            VertexConsumer rg = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.billboard(rg, c.m, c.camRot, rp.x, rp.y, rp.z, 1 + 9 * e, 0, r(PINK), g(PINK), b(PINK), (1 - st) * 0.6f * nearFade(rp, 5));
        }
    }

    private Effects() {
    }
}

package com.abilitywar.client.fx;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.hud.DamageNumbers;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.data.AWPlayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 클라이언트 이펙트 관리 (생성/틱/렌더). */
public final class VfxManager {
    private static final List<Fx> EFFECTS = new ArrayList<>();
    private static final Fx.Ctx CTX = new Fx.Ctx();

    public static void spawn(String type, NbtCompound n) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        if (w == null) return;
        Vec3d p = Effects.pos(n);
        try {
            switch (type) {
                case "tendril" -> add(new Effects.Tendril(n));
                case "impact" -> add(new Effects.Impact(p, n.getString("k"), n.getFloat("s"), n.contains("col") ? n.getInt("col") & 0xFFFFFF : -1));
                case "charge" -> add(new Effects.Charge(n));
                case "burst" -> add(new Effects.Burst(p, new Vec3d(n.getDouble("dx"), n.getDouble("dy"), n.getDouble("dz"))));
                case "trail" -> add(new Effects.Trail(n));
                case "rift" -> add(new Effects.Rift(p, n.contains("s") ? n.getFloat("s") : 1f));
                case "crater" -> add(new Effects.Crater(w, p, n.getFloat("r"), n.contains("col") ? n.getInt("col") & 0xFFFFFF : -1));
                case "shield" -> add(new Effects.Shield(n.getInt("c"), n.getInt("d"), n.getInt("k")));
                case "shield_break" -> shieldBreak(w, n.getInt("c"));
                case "shield_fade" -> shieldFade(n.getInt("c"));
                case "shadow_pool" -> add(new Effects.ShadowPool(w, p, n.getInt("k")));
                case "ult_cast" -> add(new Effects.UltCast(w, n));
                case "capture" -> {
                    Entity e = w.getEntityById(n.getInt("e"));
                    Vec3d at = e != null ? e.getPos().add(0, e.getHeight() * 0.5, 0) : p;
                    add(new Effects.Flash(at, 2.5f, Fx.PINK, 10, Tex.FLARE));
                    Effects.sparks(at, 30, 0.6f, Fx.PALE, 0.12f);
                }
                case "collapse" -> add(new Effects.Collapse(p));
                case "block_hit" -> {
                    Entity e = w.getEntityById(n.getInt("e"));
                    if (e != null) {
                        float yaw = (e instanceof LivingEntity le ? le.bodyYaw : e.getYaw()) * 0.017453292f;
                        Vec3d at = e.getPos().add(-Math.sin(yaw) * 0.6, 1.2, Math.cos(yaw) * 0.6);
                        add(new Effects.Flash(at, 1.4f, Fx.PURPLE, 6, Tex.RING_SOFT));
                        Effects.sparks(at, 10, 0.4f, Fx.PINK, 0.08f);
                        for (Fx f : EFFECTS) if (f instanceof Effects.Shield s && s.caster() == e.getId()) s.scaleMul = 1.25f;
                    }
                }
                case "shake" -> CameraController.addShake(n.getFloat("i"), n.getInt("d"));
                case "dash" -> {
                    int id = n.getInt("e");
                    int color = n.getInt("c") & 0xFFFFFF;
                    AfterImages.request(id, 5, 1, color, 9);
                    add(new Effects.DashTrail(w, id, color, n.getString("ab"), new Vec3d(n.getFloat("dx"), 0, n.getFloat("dz")), p));
                }
                default -> {
                    if (!com.abilitywar.client.fx.dragon.DragonFx.spawn(type, n, w, p)) AbilityWar.LOG.debug("Unknown fx {}", type);
                }
            }
        } catch (Exception ex) {
            AbilityWar.LOG.error("FX {} failed", type, ex);
        }
    }

    public static void add(Fx f) {
        EFFECTS.add(f);
    }

    static void shieldBreak(ClientWorld w, int caster) {
        Entity e = w.getEntityById(caster);
        for (Fx f : EFFECTS) if (f instanceof Effects.Shield s && s.caster() == caster && s.kind() == 0) s.fade();
        if (e != null) {
            float yaw = (e instanceof LivingEntity le ? le.bodyYaw : e.getYaw()) * 0.017453292f;
            Vec3d at = e.getPos().add(-Math.sin(yaw) * 0.75, 1.25, Math.cos(yaw) * 0.75);
            add(new Effects.Impact(at, "crit", 1.5f));
            add(new Effects.Flash(at, 3f, Fx.PALE, 8, Tex.RUNE_SHIELD));
            Effects.sparks(at, 30, 0.7f, Fx.PALE, 0.12f);
        }
        if (e == MinecraftClient.getInstance().player) FpEffects.counterFlash();
    }

    static void shieldFade(int caster) {
        for (Fx f : EFFECTS) if (f instanceof Effects.Shield s && s.caster() == caster && s.kind() == 0) s.fade();
    }

    public static <T extends Fx> List<T> find(Class<T> type) {
        List<T> list = new ArrayList<>();
        for (Fx f : EFFECTS) if (type.isInstance(f)) list.add(type.cast(f));
        return list;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        if (w == null) {
            EFFECTS.clear();
            Particles.clear();
            Debris.clear();
            return;
        }
        if (mc.isPaused()) return;
        EFFECTS.removeIf(f -> f.tick(w));
        // 막기 방패 (F) 유지
        Set<Integer> shielded = new HashSet<>();
        for (Fx f : EFFECTS) if (f instanceof Effects.BlockShield bs && bs.fadeAge < 0) shielded.add(bs.caster());
        for (PlayerEntity p : w.getPlayers()) {
            AWPlayer awp = (AWPlayer) p;
            if (awp.aw$hasFlag(AWPlayer.FLAG_BLOCKING) && !awp.aw$getAbilityKey().isEmpty() && !shielded.contains(p.getId())) {
                add(new Effects.BlockShield(p.getId()));
            }
            // 그림자 상태 흔적
            if (awp.aw$hasFlag(AWPlayer.FLAG_SHADOW) && w.getTime() % 2 == 0) {
                Vec3d at = p.getPos();
                Particles.spawn(Particles.Kind.WISP_DARK, at.add(Effects.rf(-0.4f, 0.4f), 0.05, Effects.rf(-0.4f, 0.4f)), new Vec3d(0, 0.05, 0), 14, 0.35f).color(0x040008, 0.7f);
            }
        }
        BlackHoleFx.tick(w);
        com.abilitywar.client.fx.dragon.DragonFx.tick(w);
        AfterImages.tick();
        Particles.tick();
        Debris.tick();
        HandTracker.cleanup();
    }

    public static void clear() {
        AfterImages.clear();
        com.abilitywar.client.fx.dragon.DragonFx.clear();
        EFFECTS.clear();
        Particles.clear();
        Debris.clear();
    }

    public static void beginFrame(WorldRenderContext ctx) {
        HandTracker.beginFrame(ctx.matrixStack().peek().getPositionMatrix(), ctx.camera().getPos());
    }

    public static void renderWorld(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Fx.Ctx c = CTX;
        c.mc = mc;
        c.world = mc.world;
        c.imm = mc.getBufferBuilders().getEntityVertexConsumers();
        c.matrices = ctx.matrixStack();
        c.m = c.matrices.peek().getPositionMatrix();
        c.camRot = ctx.camera().getRotation();
        c.cam = ctx.camera().getPos();
        c.tickDelta = ctx.tickDelta();
        try {
            BlackHoleFx.render(c);
            for (Fx f : EFFECTS) f.render(c);
            Debris.render(c);
            c.imm.draw();
            AfterImages.render(c);
            ShadowView.render(c);
            com.abilitywar.client.fx.dragon.DragonFx.render(c);
            Particles.render(c.imm, c.m, c.camRot, c.cam, c.tickDelta);
            c.imm.draw();
            DamageNumbers.renderWorld(c);
            c.imm.draw();
        } catch (Exception ex) {
            AbilityWar.LOG.error("VFX render failed", ex);
            c.imm.draw();
        }
    }

    /** 그림자 상태의 플레이어: 몸 대신 웅덩이 (엔티티 렌더 행렬 기준). */
    public static void renderShadowForm(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        float time = player.age + tickDelta;
        VertexConsumer dk = consumers.getBuffer(AWRenderLayers.translucent(Tex.SHADOW_POOL));
        Draw.flat(dk, m, 0, 0.03, 0, 0.95f + 0.05f * (float) Math.sin(time * 0.3), time * 0.02f, 1, 1, 1, 0.95f);
        VertexConsumer g = consumers.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
        Draw.flat(g, m, 0, 0.035, 0, 0.8f, 0, Fx.r(Fx.PURPLE), Fx.g(Fx.PURPLE), Fx.b(Fx.PURPLE), 0.35f);
    }

    /** 그림자 상태에서 탈출 위치 표시 + 엔티티 그림자 강조. */
    static final class ShadowView {
        static void render(Fx.Ctx c) {
            MinecraftClient mc = c.mc;
            if (mc.player == null || !ClientState.shadowForm) return;
            float time = mc.player.age + c.tickDelta;
            VertexConsumer pool = c.imm.getBuffer(AWRenderLayers.translucent(Tex.SHADOW_POOL));
            for (Entity e : c.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(40), e -> e instanceof LivingEntity && e.isAlive())) {
                Vec3d g = Effects.ground(c.world, e.getLerpedPos(c.tickDelta).add(0, 0.3, 0), 4);
                if (g == null) continue;
                Vec3d rp = g.add(0, 0.03, 0).subtract(c.cam);
                Draw.flat(pool, c.m, rp.x, rp.y, rp.z, e.getWidth() * 1.2f + 0.4f, time * 0.02f, 1, 1, 1, 0.9f);
            }
            InputHandler.ShadowTarget t = InputHandler.ShadowTarget.compute(mc.player);
            if (t.pos == null) return;
            Vec3d at = t.pos;
            if (t.type == 1) {
                Entity e = c.world.getEntityById(t.entityId);
                if (e != null) at = e.getLerpedPos(c.tickDelta);
            }
            Vec3d g = Effects.ground(c.world, at.add(0, 0.3, 0), 4);
            Vec3d rp = (g != null ? g : at).add(0, 0.05, 0).subtract(c.cam);
            int col = t.valid ? Fx.PURPLE : 0xFF4466;
            float pulse = 0.8f + 0.2f * (float) Math.sin(time * 0.4);
            VertexConsumer mcv = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
            Draw.flat(mcv, c.m, rp.x, rp.y, rp.z, (t.valid ? 1.1f : 0.6f) * pulse, time * 0.05f, Fx.r(col), Fx.g(col), Fx.b(col), t.valid ? 0.95f : 0.5f);
            if (t.valid) {
                VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
                Draw.streak(st, c.m, rp.add(0, 2.5, 0), new Vec3d(0, 1, 0), 2.5f, 0.25f, Fx.r(col), Fx.g(col), Fx.b(col), 0.7f);
            }
        }
    }

    private VfxManager() {
    }
}

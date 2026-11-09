package com.abilitywar.client.fx.dragon;

import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.client.fx.Fx;
import com.abilitywar.client.fx.Particles;
import com.abilitywar.client.fx.Tex;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/** 용혈 표식이 붙은 엔티티 위에 룬을 띄운다. */
public final class MarkTracker {
    static final Identifier RUNE = Tex.fx("dragon_rune");
    private static final Map<Integer, Long> MARKS = new HashMap<>();

    public static void mark(int id, int duration) {
        MARKS.put(id, ClientClock.ticks() + duration);
    }

    public static void unmark(int id) {
        MARKS.remove(id);
    }

    public static void tick(ClientWorld w) {
        long now = ClientClock.ticks();
        MARKS.entrySet().removeIf(e -> e.getValue() < now || w.getEntityById(e.getKey()) == null);
        if (now % 3 == 0) {
            for (int id : MARKS.keySet()) {
                Entity e = w.getEntityById(id);
                if (e == null) continue;
                Vec3d p = e.getPos().add((Particles.RND.nextDouble() - 0.5) * e.getWidth(), e.getHeight() * Particles.RND.nextDouble(), (Particles.RND.nextDouble() - 0.5) * e.getWidth());
                Particles.spawn(Particles.Kind.GLOW, p, new Vec3d(0, 0.04, 0), 14, 0.07f).color(0xFF6A22, 1f);
            }
        }
    }

    public static void render(Fx.Ctx c) {
        if (MARKS.isEmpty()) return;
        float t = ClientClock.now();
        for (Map.Entry<Integer, Long> m : MARKS.entrySet()) {
            Entity e = c.world.getEntityById(m.getKey());
            if (e == null) continue;
            float left = m.getValue() - t;
            float a = Math.min(1, left / 20f) * (0.75f + 0.25f * (float) Math.sin(t * 0.3));
            Vec3d p = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.55, 0).subtract(c.cam);
            VertexConsumer v = c.imm.getBuffer(AWRenderLayers.additive(RUNE));
            Draw.billboard(v, c.m, c.camRot, p.x, p.y, p.z, 0.32f, t * 0.05f, 1f, 0.45f, 0.15f, a);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, p.x, p.y, p.z, 0.5f, 0, 1f, 0.3f, 0.08f, a * 0.5f);
            Vec3d f = e.getLerpedPos(c.tickDelta).add(0, 0.04, 0).subtract(c.cam);
            Draw.flat(c.imm.getBuffer(AWRenderLayers.additive(RUNE)), c.m, f.x, f.y, f.z, e.getWidth() * 0.9f + 0.3f, -t * 0.03f, 1f, 0.4f, 0.12f, a * 0.6f);
        }
    }

    public static boolean marked(int id) {
        return MARKS.containsKey(id);
    }

    public static void clear() {
        MARKS.clear();
    }

    private MarkTracker() {
    }
}

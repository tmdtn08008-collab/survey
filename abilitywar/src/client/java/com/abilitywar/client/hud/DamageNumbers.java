package com.abilitywar.client.hud;

import com.abilitywar.client.fx.Fx;
import com.abilitywar.net.AWNet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** 피해 숫자 (때린 쪽/맞은 쪽 모두 표시). */
public final class DamageNumbers {
    static final class Entry {
        Vec3d pos;
        double vx, vy, vz;
        float age;
        int life;
        Text text;
        int color;
        float scale;
        boolean self;
        float hx, hy;
    }

    private static final List<Entry> WORLD = new ArrayList<>();
    private static final List<Entry> HUD = new ArrayList<>();
    private static final Random R = new Random();

    static String fmt(float v) {
        if (Math.abs(v - Math.round(v)) < 0.05f) return Integer.toString(Math.round(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public static void add(int entityId, float amount, byte kind, double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entry e = new Entry();
        e.life = 28;
        e.scale = 1f;
        switch (kind) {
            case AWNet.DMG_CRIT -> {
                e.text = Text.literal(fmt(amount) + "!");
                e.color = 0xFF6B4A;
                e.scale = 1.45f;
            }
            case AWNet.DMG_SKILL -> {
                e.text = Text.literal(fmt(amount));
                e.color = 0xC08CFF;
                e.scale = 1.2f;
            }
            case AWNet.DMG_ULT -> {
                e.text = Text.literal(fmt(amount));
                e.color = 0xFF4FD8;
                e.scale = 1.35f;
            }
            case AWNet.DMG_BLOCKED -> {
                e.text = Text.literal(fmt(amount));
                e.color = 0x9A9A9A;
            }
            case AWNet.DMG_TEXT_COUNTER -> {
                e.text = Text.translatable("abilitywar.dmg.counter");
                e.color = 0xFFD54F;
                e.scale = 1.6f;
                e.life = 34;
            }
            case AWNet.DMG_TEXT_BLOCK -> {
                e.text = Text.translatable("abilitywar.dmg.block", fmt(amount));
                e.color = 0x8FB8FF;
                e.scale = 0.9f;
            }
            case AWNet.DMG_TEXT_IMMUNE -> {
                e.text = Text.translatable("abilitywar.dmg.immune");
                e.color = 0xCFCFCF;
                e.scale = 0.85f;
                e.life = 18;
            }
            default -> {
                e.text = Text.literal(fmt(amount));
                e.color = 0xFFFFFF;
            }
        }
        if (mc.player != null && entityId == mc.player.getId()) {
            e.self = true;
            if (kind < 10) {
                e.text = Text.literal("-" + e.text.getString());
                e.color = kind == AWNet.DMG_ULT ? 0xFF4FD8 : 0xFF4040;
            }
            e.hx = R.nextFloat() * 30 - 15;
            e.hy = R.nextFloat() * 6;
            HUD.add(e);
            if (HUD.size() > 12) HUD.remove(0);
            return;
        }
        e.pos = new Vec3d(x + (R.nextDouble() - 0.5) * 0.6, y + R.nextDouble() * 0.3, z + (R.nextDouble() - 0.5) * 0.6);
        e.vx = (R.nextDouble() - 0.5) * 0.04;
        e.vz = (R.nextDouble() - 0.5) * 0.04;
        e.vy = 0.07;
        WORLD.add(e);
        if (WORLD.size() > 80) WORLD.remove(0);
    }

    public static void tick() {
        for (List<Entry> list : List.of(WORLD, HUD)) {
            list.removeIf(e -> {
                e.age++;
                if (e.pos != null) {
                    e.pos = e.pos.add(e.vx, e.vy, e.vz);
                    e.vy *= 0.85;
                }
                return e.age >= e.life;
            });
        }
    }

    static float popScale(float age) {
        if (age < 4) {
            float x = age / 4f;
            return 1.9f - 0.9f * (1 - (1 - x) * (1 - x));
        }
        return 1f;
    }

    public static void renderWorld(Fx.Ctx c) {
        if (WORLD.isEmpty()) return;
        MinecraftClient mc = c.mc;
        TextRenderer tr = mc.textRenderer;
        for (Entry e : WORLD) {
            float age = e.age + c.tickDelta;
            Vec3d p = e.pos.add(e.vx * c.tickDelta, e.vy * c.tickDelta, e.vz * c.tickDelta);
            double dist = p.distanceTo(c.cam);
            float alpha = age > e.life - 8 ? Math.max(0, (e.life - age) / 8f) : 1f;
            if (alpha <= 0.02f) continue;
            float s = 0.028f * e.scale * popScale(age) * (float) Math.max(1.0, dist * 0.11);
            c.matrices.push();
            c.matrices.translate(p.x - c.cam.x, p.y - c.cam.y, p.z - c.cam.z);
            c.matrices.multiply(c.camRot);
            c.matrices.scale(-s, -s, s);
            Matrix4f m = c.matrices.peek().getPositionMatrix();
            float w = tr.getWidth(e.text) / 2f;
            int a = (int) (alpha * 255) << 24;
            tr.draw(e.text, -w + 1, 1, a | darken(e.color), false, m, c.imm, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            tr.draw(e.text, -w, 0, a | e.color, false, m, c.imm, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            c.matrices.pop();
        }
    }

    static int darken(int rgb) {
        int r = ((rgb >> 16) & 255) / 4, g = ((rgb >> 8) & 255) / 4, b = (rgb & 255) / 4;
        return (r << 16) | (g << 8) | b;
    }

    /** 내가 받은 피해: 체력바 위에 떠오른다. */
    public static void renderHud(DrawContext ctx, float td) {
        if (HUD.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        for (Entry e : HUD) {
            float age = e.age + td;
            float alpha = age > e.life - 8 ? Math.max(0, (e.life - age) / 8f) : 1f;
            if (alpha <= 0.02f) continue;
            float s = e.scale * popScale(age) * 1.1f;
            float x = sw / 2f - 50 + e.hx;
            float y = sh - 52 - e.hy - (float) (1 - Math.exp(-age * 0.12)) * 18;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 300);
            ctx.getMatrices().scale(s, s, 1);
            int w = tr.getWidth(e.text);
            ctx.drawTextWithShadow(tr, e.text, -w / 2, -4, ((int) (alpha * 255) << 24) | e.color);
            ctx.getMatrices().pop();
        }
    }

    public static void clear() {
        WORLD.clear();
        HUD.clear();
    }

    private DamageNumbers() {
    }
}

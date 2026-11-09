package com.abilitywar.client.hud;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.registry.AWEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 화면 오른쪽 아래 스킬 바: [1][2][3][4] [R], 그 위에 작은 [T] 패시브.
 * 쿨타임은 숫자 없이 게이지(부채꼴 + 테두리)로 표시한다.
 */
public final class SkillHud {
    private static final int ACCENT = 0xB27BFF;
    private static final int ACCENT2 = 0xE6CCFF;

    public static void render(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || ClientState.ability.isEmpty()) return;
        String ab = ClientState.ability;
        HudLayouts.Layout art = HudLayouts.get(ab);
        if (art != null) {
            ArtSkillBar.render(ctx, td, art);
            return;
        }
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int size = 22, gap = 3, ultSize = 30;
        int totalW = size * 4 + gap * 3 + 6 + ultSize;
        int x0 = sw - totalW - 8;
        int yRow = sh - ultSize - 8;
        boolean locked = mc.player.hasStatusEffect(AWEffects.STUN) || ((AWPlayer) mc.player).aw$hasFlag(AWPlayer.FLAG_CINEMATIC) || ClientState.busy > 0;
        KeyBinding[] keys = {InputHandler.SKILL1, InputHandler.SKILL2, InputHandler.SKILL3, InputHandler.SKILL4, InputHandler.ULTIMATE, InputHandler.PASSIVE};
        // 뒷판
        HudDraw.rect(ctx, x0 - 5, yRow - 22, sw - 3, sh - 3, 0x55000000);
        HudDraw.rect(ctx, x0 - 5, yRow - 22, sw - 3, yRow - 21, HudDraw.argb(0.6f, ACCENT));
        // 능력 이름
        TextRenderer tr = mc.textRenderer;
        Text name = Text.translatable("ability.abilitywar." + ab);
        ctx.drawTextWithShadow(tr, name, x0 - 1, yRow - 18, ACCENT2);
        for (int i = 0; i < 4; i++) {
            int x = x0 + i * (size + gap);
            int y = yRow + (ultSize - size);
            slot(ctx, tr, td, ab, SkillSlot.VALUES[i], x, y, size, keys[i], locked);
        }
        DashBadge.render(ctx, td, x0 - 14, yRow + (ultSize - size) + size / 2f, 18);
        int ux = x0 + 4 * (size + gap) + 6 - gap;
        ultSlot(ctx, tr, td, ab, ux, yRow, ultSize, keys[4], locked);
        // 패시브 (작게, 위쪽)
        int ps = 14;
        int px = x0 + 4 * (size + gap) - gap - ps;
        int py = yRow - 18 - 1;
        passiveSlot(ctx, tr, td, ab, px, py, ps, keys[5]);
    }

    static void icon(DrawContext ctx, Identifier tex, int x, int y, int size, float bright) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(bright, bright, bright, 1f);
        ctx.drawTexture(tex, x, y, size, size, 0, 0, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    static void frame(DrawContext ctx, int x, int y, int size, int color) {
        HudDraw.rect(ctx, x - 1, y - 1, x + size + 1, y, color);
        HudDraw.rect(ctx, x - 1, y + size, x + size + 1, y + size + 1, color);
        HudDraw.rect(ctx, x - 1, y, x, y + size, color);
        HudDraw.rect(ctx, x + size, y, x + size + 1, y + size, color);
    }

    static void keyLabel(DrawContext ctx, TextRenderer tr, KeyBinding key, int x, int y, int size, String fallback) {
        String label = key != null ? key.getBoundKeyLocalizedText().getString() : fallback;
        if (label.length() > 3) label = label.substring(0, 3);
        int w = tr.getWidth(label);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + size - w * 0.75f - 1, y + size - 7, 200);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        ctx.drawTextWithShadow(tr, label, 0, 0, 0xFFFFFF);
        ctx.getMatrices().pop();
    }

    static void slot(DrawContext ctx, TextRenderer tr, float td, String ab, SkillSlot s, int x, int y, int size, KeyBinding key, boolean locked) {
        int i = s.ordinal();
        float frac = ClientState.cooldownFraction(i, td);
        boolean ready = frac <= 0;
        float press = ClientState.pressFlash[i];
        ctx.getMatrices().push();
        if (press > 0) {
            float sc = 1 - 0.08f * press;
            ctx.getMatrices().translate(x + size / 2f, y + size / 2f, 0);
            ctx.getMatrices().scale(sc, sc, 1);
            ctx.getMatrices().translate(-(x + size / 2f), -(y + size / 2f), 0);
        }
        HudDraw.rect(ctx, x, y, x + size, y + size, 0xFF120A1E);
        icon(ctx, Tex.icon(ab, slotName(s)), x, y, size, ready && !locked ? 1f : 0.55f);
        if (!ready) {
            // 남은 쿨타임 = 어두운 부채꼴 (시계방향으로 줄어듦)
            HudDraw.squarePie(ctx, x, y, size, 1 - frac, 1f, 0xB0000000);
            HudDraw.borderProgress(ctx, x, y, size, 1 - frac, 1, HudDraw.argb(0.9f, ACCENT));
        }
        if (locked) HudDraw.rect(ctx, x, y, x + size, y + size, 0x55202020);
        frame(ctx, x, y, size, ready ? HudDraw.argb(0.95f, ACCENT) : 0xAA3A2A50);
        float flash = ClientState.readyFlash[i];
        if (flash > 0) {
            HudDraw.rect(ctx, x, y, x + size, y + size, HudDraw.argb(flash * 0.7f, 0xFFFFFF));
            int g = (int) ((1 - flash) * 5);
            frame(ctx, x - g, y - g, size + g * 2, HudDraw.argb(flash, ACCENT2));
        }
        keyLabel(ctx, tr, key, x, y, size, s.keyLabel);
        ctx.getMatrices().pop();
    }

    static void ultSlot(DrawContext ctx, TextRenderer tr, float td, String ab, int x, int y, int size, KeyBinding key, boolean locked) {
        float charge = Math.min(1f, ClientState.ultDisplay / 100f);
        boolean ready = ClientState.ult >= 99.99f;
        long t = MinecraftClient.getInstance().player.age;
        float time = t + td;
        HudDraw.rect(ctx, x, y, x + size, y + size, 0xFF0C0614);
        icon(ctx, Tex.icon(ab, "ultimate"), x, y, size, ready ? 1f : 0.4f);
        if (!ready) {
            // 아래에서 차오르는 게이지
            float fh = size * charge;
            HudDraw.rect(ctx, x, y + size - fh, x + size, y + size, 0x557A3BD6);
            float wave = (float) Math.sin(time * 0.25) * 0.8f;
            HudDraw.rect(ctx, x, y + size - fh - 1 + wave * 0.5f, x + size, y + size - fh + wave * 0.5f, HudDraw.argb(0.9f, ACCENT2));
            HudDraw.borderProgress(ctx, x, y, size, charge, 1.5f, HudDraw.argb(0.9f, ACCENT));
            frame(ctx, x, y, size, 0xAA3A2A50);
        } else {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.3);
            for (int g = 1; g <= 3; g++) frame(ctx, x - g, y - g, size + g * 2, HudDraw.argb((0.55f - g * 0.13f) * (0.6f + 0.4f * pulse), ACCENT));
            frame(ctx, x, y, size, HudDraw.argb(1f, ACCENT2));
            // 테두리를 도는 빛
            float f = (time * 0.02f) % 1f;
            HudDraw.borderProgress(ctx, x, y, size, f, 1.5f, HudDraw.argb(0.0f, ACCENT2));
            float seg0 = f;
            drawRunner(ctx, x, y, size, seg0);
        }
        if (locked) HudDraw.rect(ctx, x, y, x + size, y + size, 0x55202020);
        float flash = ClientState.readyFlash[SkillSlot.ULTIMATE.ordinal()];
        if (flash > 0) {
            HudDraw.rect(ctx, x, y, x + size, y + size, HudDraw.argb(flash * 0.8f, 0xFFFFFF));
            int g = (int) ((1 - flash) * 8);
            frame(ctx, x - g, y - g, size + g * 2, HudDraw.argb(flash, ACCENT2));
        }
        keyLabel(ctx, tr, key, x, y, size, "R");
    }

    static void drawRunner(DrawContext ctx, int x, int y, int size, float f) {
        float per = size * 4f;
        float pos = f * per;
        float[][] pts = {{x + size / 2f, y}, {x + size, y}, {x + size, y + size}, {x, y + size}, {x, y}, {x + size / 2f, y}};
        float acc = 0;
        for (int i = 0; i < pts.length - 1; i++) {
            float seg = Math.abs(pts[i + 1][0] - pts[i][0]) + Math.abs(pts[i + 1][1] - pts[i][1]);
            if (pos <= acc + seg) {
                float k = (pos - acc) / seg;
                float px = pts[i][0] + (pts[i + 1][0] - pts[i][0]) * k;
                float py = pts[i][1] + (pts[i + 1][1] - pts[i][1]) * k;
                HudDraw.rect(ctx, px - 2, py - 2, px + 2, py + 2, 0xEEFFFFFF);
                HudDraw.rect(ctx, px - 3.5f, py - 3.5f, px + 3.5f, py + 3.5f, 0x55E6CCFF);
                return;
            }
            acc += seg;
        }
    }

    static void passiveSlot(DrawContext ctx, TextRenderer tr, float td, String ab, int x, int y, int size, KeyBinding key) {
        int i = SkillSlot.PASSIVE.ordinal();
        HudDraw.rect(ctx, x, y, x + size, y + size, 0xFF120A1E);
        if (ClientState.shadowForm) {
            icon(ctx, Tex.icon(ab, "passive"), x, y, size, 1f);
            float k = Math.max(0, Math.min(1, (ClientState.shadowRemaining - td) / 80f));
            HudDraw.borderProgress(ctx, x, y, size, k, 1, HudDraw.argb(1f, 0x7CFFC4));
            frame(ctx, x, y, size, 0xAA7CFFC4);
        } else {
            float frac = ClientState.cooldownFraction(i, td);
            icon(ctx, Tex.icon(ab, "passive"), x, y, size, frac <= 0 ? 1f : 0.5f);
            if (frac > 0) {
                HudDraw.squarePie(ctx, x, y, size, 1 - frac, 1f, 0xB0000000);
                HudDraw.borderProgress(ctx, x, y, size, 1 - frac, 1, HudDraw.argb(0.9f, ACCENT));
            }
            frame(ctx, x, y, size, frac <= 0 ? HudDraw.argb(0.9f, ACCENT) : 0xAA3A2A50);
            float flash = ClientState.readyFlash[i];
            if (flash > 0) HudDraw.rect(ctx, x, y, x + size, y + size, HudDraw.argb(flash * 0.7f, 0xFFFFFF));
        }
        String label = key != null ? key.getBoundKeyLocalizedText().getString() : "T";
        if (label.length() > 3) label = label.substring(0, 3);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x - tr.getWidth(label) * 0.7f - 2, y + size / 2f - 3, 0);
        ctx.getMatrices().scale(0.7f, 0.7f, 1);
        ctx.drawTextWithShadow(tr, label, 0, 0, 0xCCCCCC);
        ctx.getMatrices().pop();
    }

    static String slotName(SkillSlot s) {
        return switch (s) {
            case SKILL1 -> "skill1";
            case SKILL2 -> "skill2";
            case SKILL3 -> "skill3";
            case SKILL4 -> "skill4";
            case ULTIMATE -> "ultimate";
            case PASSIVE -> "passive";
            case DASH -> "dash";
        };
    }

    /** 그림자 상태 안내. */
    public static void renderShadowHint(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!ClientState.shadowForm || mc.options.hudHidden) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.15f, 0.05f, 0.3f, 0.85f);
        ctx.drawTexture(Tex.VIGNETTE, 0, 0, sw, sh, 0, 0, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        Text t = Text.translatable("abilitywar.hud.shadow", InputHandler.PASSIVE.getBoundKeyLocalizedText());
        int w = mc.textRenderer.getWidth(t);
        ctx.drawTextWithShadow(mc.textRenderer, t, (sw - w) / 2, sh / 2 + 18, 0xD9C2FF);
    }

    private SkillHud() {
    }
}

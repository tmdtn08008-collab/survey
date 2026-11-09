package com.abilitywar.client.hud;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.Tex;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

/** 궁극기 연출 화면: 레터박스, 비네트, 제목. */
public final class CinematicOverlay {
    /** 능력별 연출 테마. */
    record Theme(String key, float vr, float vg, float vb, int accent, int glow, int title, int sub, int caption, int flash,
                 float cut1, float cut2, float titleAt, float captionUntil) {
    }

    static final Theme VOID = new Theme("void", 0.08f, 0.0f, 0.16f, 0x9B5CFF, 0x9B5CFF, 0xF4EAFF, 0xD9C2FF, 0xD9C2FF, 0xF4EAFF, 24, 46, 46, 44);
    static final Theme DRAGON = new Theme("dragon", 0.2f, 0.05f, 0.0f, 0xFF7A2E, 0xFF4A12, 0xFFF1DC, 0xFFD2A0, 0xFFC890, 0xFFE6C0, 26, 56, 58, 42);

    static Theme theme() {
        return CameraController.cinematicType().startsWith("dragon") ? DRAGON : VOID;
    }

    public static void render(DrawContext ctx, float td) {
        Theme th = theme();
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float t = CameraController.cinematicTime(td);
        int dur = CameraController.cinematicDuration();
        float in = Math.min(1, t / 6f);
        float out = Math.min(1, Math.max(0, (dur - t) / 6f));
        float k = Math.min(in, out);
        k = k * k * (3 - 2 * k);
        // 비네트
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(th.vr(), th.vg(), th.vb(), 0.95f * k);
        ctx.drawTexture(Tex.VIGNETTE, 0, 0, sw, sh, 0, 0, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // 레터박스
        float bar = sh * 0.12f * k;
        HudDraw.rect(ctx, 0, 0, sw, bar, 0xFF000000);
        HudDraw.rect(ctx, 0, sh - bar, sw, sh, 0xFF000000);
        HudDraw.rect(ctx, 0, bar, sw, bar + 1, HudDraw.argb(0.5f * k, th.accent()));
        HudDraw.rect(ctx, 0, sh - bar - 1, sw, sh - bar, HudDraw.argb(0.5f * k, th.accent()));
        // 시전자
        Entity caster = CameraController.cinematicCaster();
        if (caster != null && t < th.captionUntil()) {
            float a = Math.min(1, t / 6f) * Math.min(1, (th.captionUntil() - t) / 6f);
            Text cap = Text.translatable("abilitywar.cinematic.caster", caster.getDisplayName());
            ctx.getMatrices().push();
            ctx.getMatrices().translate(16 + (1 - a) * -10, bar + 10, 0);
            ctx.drawTextWithShadow(tr, cap, 0, 0, ((int) (a * 255) << 24) | th.caption());
            ctx.getMatrices().pop();
        }
        // 컷 전환 플래시
        float flash = 0;
        float c1 = th.cut1(), c2 = th.cut2();
        if (t >= c1 && t < c1 + 4) flash = Math.max(flash, 1 - (t - c1) / 4f) * 0.35f;
        if (t >= c2 && t < c2 + 6) flash = Math.max(flash, (1 - (t - c2) / 6f) * (th == DRAGON ? 0.55f : 1f));
        if (th == DRAGON && t >= 66 && t < 72) flash = Math.max(flash, (1 - (t - 66) / 6f) * 0.7f);
        if (flash > 0) HudDraw.rect(ctx, 0, 0, sw, sh, HudDraw.argb(flash * 0.85f, th.flash()));
        // 제목
        if (t >= th.titleAt()) {
            float tt = t - th.titleAt();
            float a = Math.min(1, tt / 5f) * out;
            Text title = Text.translatable("abilitywar.ult." + th.key() + ".title");
            Text sub = Text.translatable("abilitywar.ult." + th.key() + ".subtitle");
            float scale = 3.2f + (1 - Math.min(1, tt / 8f)) * 1.2f;
            int tw = tr.getWidth(title);
            float cy = sh - bar - 46;
            int alpha = (int) (a * 255) << 24;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, cy, 0);
            ctx.getMatrices().scale(scale, scale, 1);
            for (int i = 0; i < 4; i++) {
                float ox = (i % 2 == 0 ? 1 : -1) * 0.6f, oy = (i < 2 ? 1 : -1) * 0.6f;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(ox, oy, 0);
                ctx.drawText(tr, title, -tw / 2, -4, ((int) (a * 90) << 24) | th.glow(), false);
                ctx.getMatrices().pop();
            }
            ctx.drawText(tr, title, -tw / 2, -4, alpha | th.title(), false);
            ctx.getMatrices().pop();
            float sa = Math.min(1, Math.max(0, (tt - 3) / 5f)) * out;
            int sw2 = tr.getWidth(sub);
            float lineW = (sw2 * 1.4f + 40) * Math.min(1, tt / 10f);
            HudDraw.rect(ctx, sw / 2f - lineW, cy + 20, sw / 2f + lineW, cy + 21, HudDraw.argb(sa * 0.8f, th == DRAGON ? 0xFF9A50 : 0xB27BFF));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, cy + 27, 0);
            ctx.getMatrices().scale(1.4f, 1.4f, 1);
            ctx.drawTextWithShadow(tr, sub, -sw2 / 2, 0, ((int) (sa * 255) << 24) | th.sub());
            ctx.getMatrices().pop();
        }
    }

    private CinematicOverlay() {
    }
}

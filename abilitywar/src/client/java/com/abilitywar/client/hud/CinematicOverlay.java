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
    public static void render(DrawContext ctx, float td) {
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
        RenderSystem.setShaderColor(0.08f, 0.0f, 0.16f, 0.95f * k);
        ctx.drawTexture(Tex.VIGNETTE, 0, 0, sw, sh, 0, 0, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // 레터박스
        float bar = sh * 0.12f * k;
        HudDraw.rect(ctx, 0, 0, sw, bar, 0xFF000000);
        HudDraw.rect(ctx, 0, sh - bar, sw, sh, 0xFF000000);
        HudDraw.rect(ctx, 0, bar, sw, bar + 1, HudDraw.argb(0.5f * k, 0x9B5CFF));
        HudDraw.rect(ctx, 0, sh - bar - 1, sw, sh - bar, HudDraw.argb(0.5f * k, 0x9B5CFF));
        // 시전자
        Entity caster = CameraController.cinematicCaster();
        if (caster != null && t < 44) {
            float a = Math.min(1, t / 6f) * Math.min(1, (44 - t) / 6f);
            Text cap = Text.translatable("abilitywar.cinematic.caster", caster.getDisplayName());
            ctx.getMatrices().push();
            ctx.getMatrices().translate(16 + (1 - a) * -10, bar + 10, 0);
            ctx.drawTextWithShadow(tr, cap, 0, 0, ((int) (a * 255) << 24) | 0xD9C2FF);
            ctx.getMatrices().pop();
        }
        // 컷 전환 플래시
        float flash = 0;
        if (t >= 24 && t < 28) flash = Math.max(flash, 1 - (t - 24) / 4f) * 0.35f;
        if (t >= 46 && t < 52) flash = Math.max(flash, 1 - (t - 46) / 6f);
        if (flash > 0) HudDraw.rect(ctx, 0, 0, sw, sh, HudDraw.argb(flash * 0.85f, 0xF4EAFF));
        // 제목
        if (t >= 46) {
            float tt = t - 46;
            float a = Math.min(1, tt / 5f) * out;
            Text title = Text.translatable("abilitywar.ult.void.title");
            Text sub = Text.translatable("abilitywar.ult.void.subtitle");
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
                ctx.drawText(tr, title, -tw / 2, -4, ((int) (a * 90) << 24) | 0x9B5CFF, false);
                ctx.getMatrices().pop();
            }
            ctx.drawText(tr, title, -tw / 2, -4, alpha | 0xF4EAFF, false);
            ctx.getMatrices().pop();
            float sa = Math.min(1, Math.max(0, (tt - 3) / 5f)) * out;
            int sw2 = tr.getWidth(sub);
            float lineW = (sw2 * 1.4f + 40) * Math.min(1, tt / 10f);
            HudDraw.rect(ctx, sw / 2f - lineW, cy + 20, sw / 2f + lineW, cy + 21, HudDraw.argb(sa * 0.8f, 0xB27BFF));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, cy + 27, 0);
            ctx.getMatrices().scale(1.4f, 1.4f, 1);
            ctx.drawTextWithShadow(tr, sub, -sw2 / 2, 0, ((int) (sa * 255) << 24) | 0xD9C2FF);
            ctx.getMatrices().pop();
        }
    }

    private CinematicOverlay() {
    }
}

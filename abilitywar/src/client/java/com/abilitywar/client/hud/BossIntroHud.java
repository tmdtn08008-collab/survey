package com.abilitywar.client.hud;

import com.abilitywar.client.fx.BossFx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** 보스 등장: 화면 가운데 위에 붉은 띠 + 보스 이름 (크게) + 별칭. */
public final class BossIntroHud {
    public static void render(DrawContext ctx, float td) {
        if (BossFx.introTicks <= 0 || BossFx.introName == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float t = BossFx.INTRO_LEN - BossFx.introTicks + td;
        float in = MathHelper.clamp(t / 8f, 0, 1), out = MathHelper.clamp((BossFx.introTicks - td) / 15f, 0, 1);
        float a = Math.min(in, out);
        float cy = sh * 0.3f;
        float bw = sw * 0.5f * (1 - (float) Math.pow(1 - in, 3));
        // 붉은 띠 (가운데가 진함)
        for (int i = 0; i < 16; i++) {
            float k = i / 16f;
            HudDraw.rect(ctx, sw / 2f - bw * (1 - k), cy - 22 + k * 0.1f, sw / 2f + bw * (1 - k), cy + 26, HudDraw.argb(a * 0.06f, 0x200000));
        }
        HudDraw.rect(ctx, sw / 2f - bw, cy - 23, sw / 2f + bw, cy - 22, HudDraw.argb(a * 0.8f, 0xC02020));
        HudDraw.rect(ctx, sw / 2f - bw, cy + 26, sw / 2f + bw, cy + 27, HudDraw.argb(a * 0.8f, 0xC02020));
        Text name = BossFx.introName;
        float scale = 2.6f + 1.2f * (float) Math.pow(1 - in, 2);
        int alpha = (int) (a * 255) << 24;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sw / 2f, cy - 6, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        int w = tr.getWidth(name);
        ctx.drawText(tr, name, -w / 2 + 1, -4 + 1, alpha | 0x300000, false);
        ctx.drawText(tr, name, -w / 2, -4, alpha | 0xFFD0B0, false);
        ctx.getMatrices().pop();
        if (BossFx.introSub != null) {
            float sa = MathHelper.clamp((t - 6) / 8f, 0, 1) * out;
            Text sub = BossFx.introSub;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, cy + 13, 0);
            ctx.getMatrices().scale(1.15f, 1.15f, 1);
            ctx.drawTextWithShadow(tr, sub, -tr.getWidth(sub) / 2, 0, ((int) (sa * 255) << 24) | 0xE8B8A0);
            ctx.getMatrices().pop();
        }
    }

    private BossIntroHud() {
    }
}

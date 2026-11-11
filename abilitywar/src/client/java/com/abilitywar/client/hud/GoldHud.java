package com.abilitywar.client.hud;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.rpg.RpgClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** 오른쪽 위: 가진 돈 (G) + 늘고 줄 때 "+25 G" 가 떠오름. */
public final class GoldHud {
    public static final Identifier COIN = AbilityWar.id("textures/gui/coin.png");

    public static void coin(DrawContext ctx, int x, int y, int size) {
        ctx.drawTexture(COIN, x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    public static String fmt(long g) {
        return String.format("%,d", g);
    }

    public static void render(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        // 상태 효과 아이콘 (오른쪽 위) 아래로
        boolean good = false, bad = false;
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            if (!e.shouldShowIcon()) continue;
            if (e.getEffectType().isBeneficial()) good = true;
            else bad = true;
        }
        int y = 4 + (good ? 26 : 0) + (bad ? 26 : 0);
        String s = fmt(RpgClient.gold);
        int tw = tr.getWidth(s);
        int bw = tw + 30, x1 = w - 4, x0 = x1 - bw, y1 = y + 15;
        float flash = RpgClient.goldFlash > 0 ? RpgClient.goldFlash / 12f : 0;
        HudDraw.rect(ctx, x0, y, x1, y1, 0xD0140E08);
        RpgHud.frame(ctx, x0, y, x1, y1, flash > 0 ? RpgHud.lerpColor(RpgHud.GOLD_DARK, 0xFFF0A0, flash) : RpgHud.GOLD_DARK);
        HudDraw.rect(ctx, x0 + 1, y + 1, x1 - 1, y + 2, HudDraw.argb(0.25f + 0.4f * flash, RpgHud.GOLD));
        coin(ctx, x0 + 3, y + 2, 11);
        ctx.drawText(tr, s, x0 + 17, y + 4, 0xFFF6DC, true);
        ctx.drawText(tr, "G", x1 - 9, y + 4, RpgHud.GOLD, true);
        // 늘고 준 돈
        int py = y1 + 3;
        for (RpgClient.GoldPop p : RpgClient.goldPops) {
            float a = MathHelper.clamp(p.ticks / 20f, 0, 1);
            float rise = (40 - p.ticks) * 0.25f;
            String t = (p.delta > 0 ? "+" : "-") + fmt(Math.abs(p.delta)) + " G";
            int c = p.delta > 0 ? 0x7CFF8A : 0xFF7070;
            ctx.drawText(tr, t, x1 - tr.getWidth(t) - 2, (int) (py + 4 - rise * 0.3f), HudDraw.argb(a, c), true);
            py += 10;
        }
    }

    private GoldHud() {
    }
}

package com.abilitywar.client.social;

import com.abilitywar.client.hud.HealthBars;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.hud.RpgHud;
import com.abilitywar.client.input.InputHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** 받은 요청 카드 (오른쪽 가운데) · 파티원 HP (왼쪽 가운데) · 플레이어를 바라보면 "Shift + 우클릭" 안내 (조준점 아래). */
public final class SocialHud {
    public static void render(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float time = mc.player.age + td;

        // ---- 플레이어를 바라봄: 안내
        if (HealthBars.target instanceof PlayerEntity tp && tp != mc.player && HealthBars.targetTicks >= 39 && tp.squaredDistanceTo(mc.player) < 8 * 8 && mc.currentScreen == null) {
            Text hint = Text.translatable("social.abilitywar.hint");
            int hw = tr.getWidth(hint);
            int y = h / 2 + 14;
            HudDraw.rect(ctx, w / 2 - hw / 2 - 4, y - 2, w / 2 + hw / 2 + 4, y + 10, 0x90000000);
            ctx.drawText(tr, hint, w / 2 - hw / 2, y, 0xFFE9A8, true);
        }

        // ---- 받은 요청 카드
        int cy = h / 2 - 40;
        for (int i = 0; i < Math.min(3, SocialClient.requests.size()); i++) {
            SocialClient.Req r = SocialClient.requests.get(i);
            int cw = 170, x1 = w - 6, x0 = x1 - cw, y0 = cy, y1 = cy + 34;
            float pulse = i == 0 ? 0.5f + 0.5f * MathHelper.sin(time * 0.25f) : 0;
            HudDraw.rect(ctx, x0, y0, x1, y1, 0xE0140E08);
            RpgHud.frame(ctx, x0, y0, x1, y1, i == 0 ? RpgHud.lerpColor(RpgHud.GOLD_DARK, 0xFFE9A8, pulse) : RpgHud.GOLD_DARK);
            Text title = Text.translatable(r.type == 0 ? "social.abilitywar.card.party" : "social.abilitywar.card.trade", r.from);
            ctx.drawText(tr, title, x0 + 6, y0 + 5, r.type == 0 ? 0x8FE8FF : 0xFFD870, true);
            if (i == 0) {
                Text keys = Text.translatable("social.abilitywar.card.keys", InputHandler.ACCEPT.getBoundKeyLocalizedText(), InputHandler.DECLINE.getBoundKeyLocalizedText());
                ctx.drawText(tr, keys, x0 + 6, y0 + 17, 0xE8DCC0, true);
            }
            float f = (float) r.ticks / r.total;
            HudDraw.rect(ctx, x0 + 3, y1 - 4, x0 + 3 + (int) ((cw - 6) * f), y1 - 2, 0xFFC8A040);
            cy += 38;
        }

        // ---- 파티원
        if (!SocialClient.party.isEmpty()) {
            int n = SocialClient.party.size();
            int x = 6, y = h / 2 - (n * 22 + 12) / 2;
            ctx.drawText(tr, Text.translatable("social.abilitywar.party_hud", n + 1), x, y, 0x8FE8FF, true);
            y += 12;
            for (SocialClient.Member m : SocialClient.party) {
                int x1 = x + 104;
                HudDraw.rect(ctx, x, y, x1, y + 19, 0xB0100A06);
                RpgHud.frame(ctx, x, y, x1, y + 19, m.online() ? 0x3A6A7A : 0x3A3A3A);
                String lv = m.level() > 0 ? "Lv" + m.level() + " " : "";
                ctx.drawText(tr, lv + m.name(), x + 3, y + 2, m.online() ? (m.near() ? 0xFFFFFF : 0xA0A0A0) : 0x707070, true);
                if (m.online()) {
                    float f = MathHelper.clamp(m.hp() / Math.max(1, m.maxHp()), 0, 1);
                    int col = f > 0.5f ? HealthBars.lerp(0xF0E040, 0x50E050, (f - 0.5f) * 2) : HealthBars.lerp(0xE03030, 0xF0E040, f * 2);
                    HudDraw.rect(ctx, x + 3, y + 12, x1 - 3, y + 16, 0xFF2A2A2A);
                    HudDraw.rect(ctx, x + 3, y + 12, x + 3 + (int) ((x1 - x - 6) * f), y + 16, 0xFF000000 | col);
                    String hp = HealthBars.fmt(m.hp()) + "/" + HealthBars.fmt(m.maxHp());
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(x1 - 3 - tr.getWidth(hp) * 0.6f, y + 3, 0);
                    ctx.getMatrices().scale(0.6f, 0.6f, 1);
                    ctx.drawText(tr, hp, 0, 0, 0xD0D0D0, true);
                    ctx.getMatrices().pop();
                } else ctx.drawText(tr, Text.translatable("social.abilitywar.offline"), x + 3, y + 11, 0x707070, false);
                y += 22;
            }
        }
    }

    private SocialHud() {
    }
}

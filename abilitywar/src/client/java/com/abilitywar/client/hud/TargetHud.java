package com.abilitywar.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** 화면 위 가운데 (보스 막대 아래): 바라보는 몹 · 보스 · 플레이어의 이름 + HP 막대 + 숫자. */
public final class TargetHud {
    public static void render(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity t = HealthBars.target;
        if (t == null || mc.player == null || t instanceof com.abilitywar.rpg.JobMasterEntity) return;
        if (t instanceof net.minecraft.entity.decoration.ArmorStandEntity) return;
        TextRenderer tr = mc.textRenderer;
        int bars = ((com.abilitywar.client.mixin.BossBarHudAccessor) mc.inGameHud.getBossBarHud()).abilitywar$bossBars().size();
        int y = bars > 0 ? 12 + bars * 19 - 4 : 4;
        int cx = ctx.getScaledWindowWidth() / 2;
        float hp = Math.max(0, t.getHealth()), max = Math.max(1, t.getMaxHealth());
        float f = MathHelper.clamp(hp / max, 0, 1);
        float lag = Math.max(hp, HealthBars.LAG.getOrDefault(t.getId(), hp));
        float fl = MathHelper.clamp(lag / max, 0, 1);
        float a = MathHelper.clamp(HealthBars.targetTicks / 10f, 0, 1);
        boolean boss = HealthBars.isBoss(t);
        Text name = t.getDisplayName();
        String num = HealthBars.fmt(hp) + " / " + HealthBars.fmt(max);
        int w = Math.max(120, Math.max(tr.getWidth(name), tr.getWidth(num)) + 50);
        int x0 = cx - w / 2, x1 = cx + w / 2;
        HudDraw.rect(ctx, x0, y, x1, y + 24, HudDraw.argb(0.7f * a, 0x0C0904));
        RpgHud.frame(ctx, x0, y, x1, y + 24, boss ? 0xB03020 : t instanceof PlayerEntity ? 0x4FA3FF : RpgHud.GOLD_DARK);
        int nameCol = boss ? 0xFF8A6A : t instanceof PlayerEntity ? 0x8FD3FF : t instanceof net.minecraft.entity.mob.Monster ? 0xFFD8B0 : 0xD8F0C8;
        ctx.drawText(tr, name, cx - tr.getWidth(name) / 2, y + 3, HudDraw.argb(a, nameCol), true);
        int bx0 = x0 + 6, bx1 = x1 - 6, by = y + 14, bw = bx1 - bx0;
        HudDraw.rect(ctx, bx0, by, bx1, by + 6, HudDraw.argb(a, 0x2A2A2A));
        if (fl > f) HudDraw.rect(ctx, bx0 + (int) (bw * f), by, bx0 + (int) (bw * fl), by + 6, HudDraw.argb(a, 0xF0F0F0));
        int col = boss ? HealthBars.lerp(0xC02020, 0xFF6A3A, f) : f > 0.5f ? HealthBars.lerp(0xF0E040, 0x50E050, (f - 0.5f) * 2) : HealthBars.lerp(0xE03030, 0xF0E040, f * 2);
        HudDraw.rect(ctx, bx0, by, bx0 + (int) (bw * f), by + 6, HudDraw.argb(a, col));
        HudDraw.rect(ctx, bx0, by, bx0 + (int) (bw * f), by + 1, HudDraw.argb(0.3f * a, 0xFFFFFF));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, by + 1, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        ctx.drawText(tr, num, -tr.getWidth(num) / 2, 0, HudDraw.argb(a, 0xFFFFFF), true);
        ctx.getMatrices().pop();
    }

    private TargetHud() {
    }
}

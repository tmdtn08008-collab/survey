package com.abilitywar.client.screen;

import com.abilitywar.ability.Abilities;
import com.abilitywar.ability.Ability;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.hud.GoldHud;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.mixin.HandledScreenAccessor;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.gear.GearAttributes;
import com.abilitywar.gear.GearEnhance;
import com.abilitywar.gear.GearItem;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * 인벤토리 (E) 옆에 붙는 캐릭터 카드: 직업 · 레벨 · 경험치, 체력 · 방어, 공격 능력치 (능력 피해 · 치명타 · 쿨감 · 흡혈 · 피해 감소),
 * 입은 장비 등급 · 강화 · 각성, 돈 · 길드 등급 · 심연의 탑 최고 층. 인벤토리 틀에도 금테를 두름.
 */
public final class InventoryPanel {
    static final int W = 124;

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof InventoryScreen)) return;
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, td) -> frame(ctx, (HandledScreenAccessor) s));
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, td) -> render(ctx, (HandledScreenAccessor) s, s.width, s.height));
        });
    }

    /** 인벤토리 틀 둘레의 금테 (뒤에 그려서 틀 밖으로만 보임). */
    static void frame(DrawContext ctx, HandledScreenAccessor a) {
        int x = a.aw$x(), y = a.aw$y(), w = a.aw$bw(), h = a.aw$bh();
        float t = (System.currentTimeMillis() % 4000) / 4000f;
        float glow = 0.55f + 0.25f * MathHelper.sin(t * MathHelper.TAU);
        HudDraw.rect(ctx, x - 4, y - 4, x + w + 4, y + h + 4, 0xE0140E06);
        com.abilitywar.client.hud.RpgHud.frame(ctx, x - 4, y - 4, x + w + 4, y + h + 4, 0x8A6A28);
        com.abilitywar.client.hud.RpgHud.frame(ctx, x - 2, y - 2, x + w + 2, y + h + 2, com.abilitywar.client.hud.RpgHud.lerpColor(0x3A2C12, RpgUi.GOLD, glow));
    }

    static void render(DrawContext ctx, HandledScreenAccessor a, int sw, int sh) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null) return;
        TextRenderer tr = mc.textRenderer;
        int x0 = a.aw$x() + a.aw$bw() + 8, y0 = a.aw$y(), h = a.aw$bh();
        if (x0 + W > sw - 2) {
            // 오른쪽에 자리가 없으면 (레시피 책을 열었을 때 등) 왼쪽에
            x0 = a.aw$x() - W - 8;
            if (x0 < 2) return;
        }
        int x1 = x0 + W, y1 = y0 + h;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 100);
        RpgUi.panel(ctx, x0, y0, x1, y1);
        int x = x0 + 7, y = y0 + 7;
        // 이름 + 직업
        ctx.drawTextWithShadow(tr, tr.trimToWidth(p.getName().getString(), W - 14), x, y, 0xFFE9A8);
        y += 11;
        Ability ab = ClientState.ability.isEmpty() ? null : Abilities.byString(ClientState.ability);
        Text cls = ab == null ? Text.translatable("inv.abilitywar.no_class").formatted(Formatting.GRAY)
                : Text.translatable(ab.translationKey()).styled(s -> s.withColor(ab.color & 0xFFFFFF));
        if (RpgClient.grand && ab != null) cls = Text.literal("✦ ").formatted(Formatting.GOLD).append(cls);
        ctx.drawTextWithShadow(tr, cls, x, y, 0xFFFFFF);
        y += 12;
        // 레벨 + 경험치
        ctx.drawTextWithShadow(tr, Text.translatable("inv.abilitywar.level", RpgClient.level), x, y, RpgUi.TEXT);
        String pct = RpgClient.need > 0 ? (int) (100 * RpgClient.xp / Math.max(1, RpgClient.need)) + "%" : "";
        ctx.drawTextWithShadow(tr, pct, x1 - 7 - tr.getWidth(pct), y, RpgUi.DIM);
        y += 10;
        RpgUi.bar(ctx, x, y, W - 14, 3, RpgClient.need > 0 ? RpgClient.xp / (float) RpgClient.need : 1, 0x7CE87C);
        y += 8;
        HudDraw.rect(ctx, x, y, x1 - 7, y + 1, 0x403A2C12);
        y += 4;
        // 능력치
        y = stat(ctx, tr, x, x1, y, "❤", Text.translatable("inv.abilitywar.hp"), Math.round(p.getHealth()) + " / " + Math.round(p.getMaxHealth()), 0xFF6A6A);
        y = stat(ctx, tr, x, x1, y, "⛨", Text.translatable("inv.abilitywar.armor"), fmt(p.getArmor()), 0x9AD0FF);
        y = stat(ctx, tr, x, x1, y, "⚔", Text.translatable("inv.abilitywar.attack"), fmt(attr(p, EntityAttributes.GENERIC_ATTACK_DAMAGE)), 0xFFB070);
        y = stat(ctx, tr, x, x1, y, "✷", Text.translatable("inv.abilitywar.s.ability"), "+" + pct(attr(p, GearAttributes.ABILITY_DAMAGE)), 0xC07CFF);
        y = stat(ctx, tr, x, x1, y, "✦", Text.translatable("inv.abilitywar.s.crit"), pct(attr(p, GearAttributes.CRIT_CHANCE)), 0xFFE070);
        y = stat(ctx, tr, x, x1, y, "⌛", Text.translatable("inv.abilitywar.s.cooldown"), pct(attr(p, GearAttributes.COOLDOWN)), 0x8FD3FF);
        y = stat(ctx, tr, x, x1, y, "♥", Text.translatable("inv.abilitywar.s.lifesteal"), pct(attr(p, GearAttributes.LIFESTEAL)), 0xFF8AA8);
        y = stat(ctx, tr, x, x1, y, "◈", Text.translatable("inv.abilitywar.s.reduction"), pct(attr(p, GearAttributes.DAMAGE_REDUCTION)), 0xB8F0B8);
        HudDraw.rect(ctx, x, y + 1, x1 - 7, y + 2, 0x403A2C12);
        y += 5;
        // 입은 장비: 등급 색 칸 + 강화 · 각성
        int gx = x;
        for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getEquippedStack(es);
            int col = st.getItem() instanceof GearItem gi ? gi.grade().color : 0x3A3A44;
            HudDraw.rect(ctx, gx, y, gx + 20, y + 20, 0xA0000000);
            com.abilitywar.client.hud.RpgHud.frame(ctx, gx, y, gx + 20, y + 20, col);
            if (!st.isEmpty()) {
                ctx.drawItem(st, gx + 2, y + 2);
                int lv = GearEnhance.level(st), aw = GearEnhance.awaken(st);
                if (lv > 0 || aw > 0) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(gx + 1, y + 1, 200);
                    ctx.getMatrices().scale(0.55f, 0.55f, 1);
                    String t = aw > 0 ? "★" + aw : "+" + lv;
                    ctx.drawTextWithShadow(tr, t, 0, 0, aw > 0 ? GearEnhance.AWAKEN_COLOR : GearEnhance.color(lv));
                    ctx.getMatrices().pop();
                }
            }
            gx += 22;
        }
        y += 24;
        // 돈 · 길드 · 탑
        GoldHud.coin(ctx, x, y - 1, 10);
        ctx.drawTextWithShadow(tr, GoldHud.fmt(RpgClient.gold) + " G", x + 13, y, 0xFFE9A8);
        y += 11;
        String rank = RpgClient.guildRank >= 0 && RpgClient.guildRank < com.abilitywar.rpg.Guild.RANKS.length ? com.abilitywar.rpg.Guild.RANKS[RpgClient.guildRank] : "-";
        ctx.drawTextWithShadow(tr, Text.translatable("inv.abilitywar.guild", rank), x, y, 0x8FB8FF);
        Text tt = RpgClient.towerBest > 0 ? Text.translatable("inv.abilitywar.tower", RpgClient.towerBest) : Text.translatable("inv.abilitywar.tower_none");
        ctx.drawTextWithShadow(tr, tt, x1 - 7 - tr.getWidth(tt), y, 0xC07CFF);
        ctx.getMatrices().pop();
    }

    static int stat(DrawContext ctx, TextRenderer tr, int x, int x1, int y, String icon, Text name, String value, int color) {
        ctx.drawTextWithShadow(tr, icon, x, y, color);
        ctx.drawTextWithShadow(tr, tr.trimToWidth(name.getString(), 64), x + 10, y, 0xB8AC90);
        ctx.drawTextWithShadow(tr, value, x1 - 7 - tr.getWidth(value), y, 0xFFFFFF);
        return y + 10;
    }

    static double attr(PlayerEntity p, EntityAttribute a) {
        var i = p.getAttributeInstance(a);
        return i == null ? 0 : i.getValue();
    }

    static String pct(double v) {
        return Math.round(v * 1000) / 10.0 + "%";
    }

    static String fmt(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private InventoryPanel() {
    }
}

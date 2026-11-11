package com.abilitywar.gear;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

import java.util.List;
import java.util.Map;

/** 장비 툴팁: 등급 · 종류, 설명, 능력치, 특수 효과, 세트 효과. */
public final class GearTooltip {
    static final int STAT = 0x8FD3FF, STAT_MAIN = 0xE8E8E8, GOLD = 0xFFC94A, DIM = 0x6E6E78, ON = 0x7CFF8A, LORE = 0x9A9AA8;

    static MutableText c(Text t, int color) {
        return t.copy().styled(s -> s.withColor(TextColor.fromRgb(color)));
    }

    static MutableText lit(String s, int color) {
        return Text.literal(s).styled(st -> st.withColor(TextColor.fromRgb(color)));
    }

    static Text header(GearGrade g, Text kind) {
        MutableText t = Text.literal("").append(c(Text.translatable("gear.abilitywar.ui.grade_mark"), g.color));
        t.append(c(g.displayName(), g.color)).append(lit(" · ", DIM)).append(c(kind, 0xB8B8C4));
        return t;
    }

    static void levelLine(GearGrade g, List<Text> out) {
        int need = g.requiredLevel();
        int have = GearItem.CLIENT_LEVEL[0] == null ? 999 : GearItem.CLIENT_LEVEL[0].getAsInt();
        out.add(c(Text.translatable("gear.abilitywar.ui.req_level", need), have >= need ? 0xB8B8C4 : 0xFF5555));
    }

    static void lore(String key, List<Text> out) {
        if (!Language.getInstance().hasTranslation(key)) return;
        out.add(Text.translatable(key).styled(s -> s.withColor(TextColor.fromRgb(LORE)).withItalic(true)));
    }

    static void stats(Map<GearStat, Double> stats, boolean unknown, List<Text> out, int color) {
        for (Map.Entry<GearStat, Double> e : stats.entrySet()) {
            GearStat s = e.getKey();
            boolean main = s == GearStat.ATTACK_DAMAGE || s == GearStat.ATTACK_SPEED || s == GearStat.ARMOR;
            MutableText line = lit(" ", color).append(c(Text.translatable("gear.abilitywar.stat." + s.key), main ? STAT_MAIN : color)).append(lit(" ", color));
            if (unknown) line.append(Text.literal("???").styled(st -> st.withColor(TextColor.fromRgb(0xFF66F0)).withObfuscated(true)));
            else line.append(lit(s.format(e.getValue()), main ? 0xFFFFFF : color));
            out.add(line);
        }
    }

    static Text effect(GearDefs.Effect e, boolean unknown) {
        String c = String.valueOf(Math.round(e.chance() * 100));
        String sec = e.seconds() == Math.floor(e.seconds()) ? String.valueOf((long) e.seconds()) : String.valueOf(e.seconds());
        String pw = String.valueOf((long) e.power());
        MutableText t = lit(" ◆ ", GOLD).append(c(Text.translatable("gear.abilitywar.effect." + e.id()), GOLD)).append(lit(": ", GOLD));
        if (unknown) return t.append(Text.literal("??????").styled(s -> s.withColor(TextColor.fromRgb(0xFF66F0)).withObfuscated(true)));
        return t.append(c(Text.translatable("gear.abilitywar.effect." + e.id() + ".desc", c, sec, pw), 0xF0DFA8));
    }

    /** 강화 줄: "강화 +7 / 15 ★★★★★★★☆☆…" + 다음 승급 안내. */
    static void enhanceLine(ItemStack stack, GearGrade g, List<Text> out) {
        int lv = GearEnhance.level(stack);
        if (lv <= 0) return;
        MutableText stars = Text.literal("");
        for (int i = 1; i <= GearEnhance.MAX; i++) {
            int col = i <= lv ? GearEnhance.color(i) : 0x3A3A44;
            stars.append(Text.literal(i <= lv ? "★" : "☆").styled(s -> s.withColor(TextColor.fromRgb(col))));
            if (i % 5 == 0 && i < GearEnhance.MAX) stars.append(Text.literal(" "));
        }
        out.add(c(Text.translatable("gear.abilitywar.ui.enhance", lv, GearEnhance.MAX), GearEnhance.color(lv)).append(Text.literal(" ")).append(stars));
    }

    public static void weapon(GearWeaponItem item, ItemStack stack, List<Text> out) {
        GearDefs.Weapon d = item.def;
        boolean unk = d.grade() == GearGrade.UNKNOWN;
        out.add(header(d.grade(), Text.translatable("gear.abilitywar.type." + d.type())));
        levelLine(d.grade(), out);
        enhanceLine(stack, d.grade(), out);
        lore(item.getTranslationKey() + ".lore", out);
        out.add(Text.empty());
        out.add(c(Text.translatable("gear.abilitywar.ui.held"), 0xA0A0AC));
        stats(GearEnhance.weaponStats(d, GearEnhance.level(stack)), unk, out, STAT);
        if (!d.effects().isEmpty()) {
            out.add(c(Text.translatable("gear.abilitywar.ui.on_hit"), GOLD));
            for (GearDefs.Effect e : d.effects()) out.add(effect(e, false));
        }
        out.add(c(Text.translatable("gear.abilitywar.ui.ability_hint"), DIM));
    }

    public static void armor(GearArmorItem item, ItemStack stack, List<Text> out) {
        GearDefs.ArmorSet set = item.set;
        boolean unk = set.grade() == GearGrade.UNKNOWN;
        out.add(header(set.grade(), Text.translatable("gear.abilitywar.piece." + item.piece.slot())));
        levelLine(set.grade(), out);
        enhanceLine(stack, set.grade(), out);
        lore(item.getTranslationKey() + ".lore", out);
        out.add(Text.empty());
        out.add(c(Text.translatable("gear.abilitywar.ui.worn"), 0xA0A0AC));
        stats(item.piece.stats(), unk, out, STAT);
        int lv = GearEnhance.level(stack);
        if (lv > 0) out.add(lit(" ", ON).append(c(Text.translatable("gear.abilitywar.ui.enhance_armor",
                GearStat.trim(GearEnhance.armorBonus(item.getProtection(), lv)), GearStat.trim(GearEnhance.healthBonus(lv))), ON)));
        if (set.setStats().isEmpty() && set.setEffects().isEmpty()) return;
        int worn = wornCount(set.id());
        boolean active = worn >= 4;
        out.add(Text.empty());
        out.add(c(Text.translatable("gear.abilitywar.ui.set", Text.translatable("gear.abilitywar.set." + set.id()), worn), active ? GOLD : 0xB8B8C4));
        out.add(c(Text.translatable("gear.abilitywar.ui.set_bonus"), active ? ON : DIM));
        stats(set.setStats(), unk, out, active ? ON : DIM);
        for (String fx : set.setEffects()) {
            out.add(lit(" ◆ ", active ? ON : DIM).append(c(Text.translatable("gear.abilitywar.seteffect." + fx), active ? ON : DIM))
                    .append(lit(": ", active ? ON : DIM)).append(c(Text.translatable("gear.abilitywar.seteffect." + fx + ".desc"), active ? 0xCFFFD4 : DIM)));
        }
    }

    static int wornCount(String setId) {
        PlayerEntity p = GearItem.CLIENT_PLAYER[0] == null ? null : GearItem.CLIENT_PLAYER[0].get();
        if (p == null) return 0;
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getEquippedStack(s);
            if (st.getItem() instanceof GearArmorItem a && a.set.id().equals(setId)) n++;
        }
        return n;
    }

    private GearTooltip() {
    }
}

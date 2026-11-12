package com.abilitywar.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 장비 강화 (+0 ~ +15) 와 승급 (다음 등급).
 * <ul>
 *   <li>무기: 강화 1 마다 공격력 +5% (기본값 기준), 능력 피해 +2%p.</li>
 *   <li>갑옷: 강화 1 마다 방어력 +5% (부위 기본값 기준, 최소 +0.25), 최대 체력 +0.5.</li>
 *   <li>성공 확률: +0~4 → 100%, +5 → 90% … +14 → 20%. 실패하면 +0~9 는 그대로, +10 부터는 1 내려감 (보호 주문서가 있으면 그대로).</li>
 *   <li>승급: +10 이상 (시크릿 → ? 는 +15) 이면 광물 + 몬스터 · 보스 전리품으로 다음 등급의 같은 종류 장비로 바뀜. 강화 수치는 절반만 남음.</li>
 * </ul>
 * 강화 수치는 아이템 NBT "AWEnhance".
 *
 * <p>각성 (★1 ~ ★5): +15 장비를 심연의 결정 · 보스의 영혼 · 용의 심장 (★3 부터) 으로 더 강하게. ★ 하나 = 강화 2 단계 만큼의 능력치.
 * 실패해도 내려가지 않음 (재료만 사라짐). 승급해도 각성은 남음. NBT "AWAwaken".
 *
 * <p>? 장비 끝까지 (2.9): ? 장비는 +99 까지 강화 (+15 부터는 실패해도 그대로) → +99 에서 초월+ (+1+ 부터 다시, "+N+") → +99+ 에서 정제 (+1 부터 다시)
 * → 정제 +99 에서 승급하면 아바리티아 (Re:Avaritia) 인피니티 장비 (모드가 없으면 같은 종류의 인피니티 등급). 단계는 NBT "AWStage" (1 초월+, 2 정제).
 * 능력치 단계: 기본 +15 까지 1 씩, 그 뒤 0.4 씩 (+99 = 48) · 초월+ 48 + 0.5 씩 (+99+ = 97) · 정제 97 + 0.6 씩 (+99 = 156) · 인피니티 등급 156 + 0.6 씩.
 */
public final class GearEnhance {
    public static final String KEY = "AWEnhance";
    public static final int MAX = 15;
    public static final int PROMOTE_MIN = 10;
    static final int[] CHANCE = {100, 100, 100, 100, 100, 90, 80, 70, 60, 50, 40, 35, 30, 25, 20};

    // ================================================================ 각성
    public static final String AWAKEN_KEY = "AWAwaken";
    public static final int AWAKEN_MAX = 5;
    /** ★ 하나가 능력치로는 강화 몇 단계인지. */
    public static final int AWAKEN_STEP = 2;
    static final int[] AWAKEN_CHANCE = {80, 65, 50, 40, 30};
    static final int[] AWAKEN_CRYSTALS = {4, 6, 9, 12, 16};
    static final int[] AWAKEN_SOULS = {2, 3, 4, 5, 6};
    static final int[] AWAKEN_HEARTS = {0, 0, 1, 1, 2};
    static final int[] AWAKEN_GOLD = {20000, 40000, 70000, 110000, 160000};

    public static int awaken(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return n == null ? 0 : Math.max(0, Math.min(AWAKEN_MAX, n.getInt(AWAKEN_KEY)));
    }

    public static void setAwaken(ItemStack stack, int a) {
        a = Math.max(0, Math.min(AWAKEN_MAX, a));
        if (a == 0) {
            NbtCompound n = stack.getNbt();
            if (n != null) {
                n.remove(AWAKEN_KEY);
                if (n.isEmpty()) stack.setNbt(null);
            }
        } else stack.getOrCreateNbt().putInt(AWAKEN_KEY, a);
    }

    /** 각성할 수 있는지: 최대 강화 (+15, ? · 인피니티 등급은 +99) 이고 ★ 가 남았을 때. */
    public static boolean canAwaken(ItemStack stack) {
        return level(stack) >= maxLevel(stack) && awaken(stack) < AWAKEN_MAX;
    }

    /** ★a → ★a+1 성공 확률 (%). */
    public static int awakenChance(int a) {
        return a >= AWAKEN_MAX ? 0 : AWAKEN_CHANCE[a];
    }

    public static int awakenGold(int a) {
        return AWAKEN_GOLD[Math.min(a, AWAKEN_MAX - 1)];
    }

    /** ★a → ★a+1 재료: 심연의 결정 · 보스의 영혼 · (★3 부터) 용의 심장. */
    public static List<Mat> awakenMats(int a) {
        int i = Math.min(a, AWAKEN_MAX - 1);
        List<Mat> l = new ArrayList<>();
        l.add(new Mat(com.abilitywar.item.RpgItems.ABYSS_CRYSTAL, AWAKEN_CRYSTALS[i]));
        l.add(new Mat(com.abilitywar.item.RpgItems.BOSS_SOUL, AWAKEN_SOULS[i]));
        if (AWAKEN_HEARTS[i] > 0) l.add(new Mat(com.abilitywar.item.RpgItems.DRAGON_HEART, AWAKEN_HEARTS[i]));
        return l;
    }

    /** 능력치 계산에 쓰는 단계 = 강화 (초월+ · 정제 · 인피니티 등급은 위 표) + 각성 × 2. */
    public static int statLevel(ItemStack stack) {
        int lv = level(stack), base;
        if (gradeOf(stack) == GearGrade.ARVANTIA) base = INFINITY_BASE + lv * 3 / 5;
        else base = switch (stage(stack)) {
            case TRANSCEND -> 48 + lv / 2;
            case REFINE -> 97 + lv * 3 / 5;
            case SUPER_REFINE -> 156 + lv * 3 / 5;
            default -> lv <= MAX ? lv : MAX + (lv - MAX) * 2 / 5;
        };
        return base + awaken(stack) * AWAKEN_STEP;
    }

    // ================================================================ ? +99 → 초월+ → 정제 → 인피니티 (아바리티아)
    public static final int BEYOND_MAX = 99;
    public static final String STAGE_KEY = "AWStage";
    public static final int TRANSCEND = 1, REFINE = 2, SUPER_REFINE = 3;
    /** 인피니티 등급 +0 의 능력치 단계 (= 초정제 +99). */
    public static final int INFINITY_BASE = 215;
    public static final int TRANSCEND_COLOR = 0x5AE8FF, REFINE_COLOR = 0xD07CFF, SUPER_COLOR = 0xFFC83C, INFINITY_COLOR = 0xFF80E8;
    /** +15 넘은 구간 (? +15~ · 초월+ · 정제 · 초정제 · 인피니티 등급) 한 번 강화: 성공 10% · 실패 50% (보호 주문서 없으면 장비가 깨짐) · 하락 40%. */
    public static final int BEYOND_SUCCESS = 10, BEYOND_FAIL = 50, BEYOND_DROP = 40;

    @org.jetbrains.annotations.Nullable
    public static GearGrade gradeOf(ItemStack st) {
        return st.getItem() instanceof GearItem g ? g.grade() : null;
    }

    /** +15 을 넘어 +99 까지 강화되는 등급 (? · 인피니티 등급). */
    public static boolean beyond(@org.jetbrains.annotations.Nullable GearGrade g) {
        return g == GearGrade.UNKNOWN || g == GearGrade.ARVANTIA;
    }

    public static int maxLevel(ItemStack st) {
        return beyond(gradeOf(st)) ? BEYOND_MAX : MAX;
    }

    /** 0 기본 · 1 초월+ · 2 정제 · 3 초정제 (? 장비만). */
    public static int stage(ItemStack st) {
        NbtCompound n = st.getNbt();
        if (n == null || gradeOf(st) != GearGrade.UNKNOWN) return 0;
        return Math.max(0, Math.min(SUPER_REFINE, n.getInt(STAGE_KEY)));
    }

    public static void setStage(ItemStack st, int s) {
        if (s <= 0) {
            NbtCompound n = st.getNbt();
            if (n != null) {
                n.remove(STAGE_KEY);
                if (n.isEmpty()) st.setNbt(null);
            }
        } else st.getOrCreateNbt().putInt(STAGE_KEY, Math.min(SUPER_REFINE, s));
    }

    /** +15 을 넘은 강화 구간: 0 아님 = 그 구간의 단계 (1 기본 +15~ · 2 초월+ · 3 정제 · 4 초정제 · 5 인피니티 등급). */
    static int beyondTier(ItemStack st) {
        GearGrade g = gradeOf(st);
        if (g == GearGrade.ARVANTIA) return 5;
        if (g != GearGrade.UNKNOWN) return 0;
        int s = stage(st);
        if (s > 0) return s + 1;
        return level(st) >= MAX ? 1 : 0;
    }

    /** +15 넘은 구간인지 (성공 10% · 실패 50% (깨짐) · 하락 40%). */
    public static boolean beyondZone(ItemStack st) {
        return beyondTier(st) > 0 && level(st) < maxLevel(st);
    }

    /** 다음 강화 성공 확률 (%). */
    public static int chance(ItemStack st) {
        int lv = level(st);
        if (lv >= maxLevel(st)) return 0;
        return beyondTier(st) > 0 ? BEYOND_SUCCESS : chance(lv);
    }

    /** 실패하면 나빠지는지 (보호 주문서를 쓸 수 있는지): +10 ~ +14 (내려감) · +15 넘은 구간 (깨짐 · 내려감). */
    public static boolean failDrops(ItemStack st) {
        return beyondTier(st) > 0 || failDrops(level(st));
    }

    /** 내려갈 때의 바닥: 초월+ · 정제 · 초정제는 +1, 그 밖은 +0. */
    public static int dropFloor(ItemStack st) {
        return stage(st) > 0 ? 1 : 0;
    }

    public static int stoneCost(ItemStack st) {
        int k = beyondTier(st), lv = level(st);
        GearGrade g = gradeOf(st);
        return k == 0 ? stoneCost(g == null ? GearGrade.COMMON : g, lv) : k + lv / 40;
    }

    public static int goldCost(ItemStack st) {
        int k = beyondTier(st), lv = level(st);
        GearGrade g = gradeOf(st);
        return k == 0 ? goldCost(g == null ? GearGrade.COMMON : g, lv) : 50 + lv * 6 + k * 150;
    }

    /** ? 장비의 다음 큰 단계: 초월+ (기본 +99) · 정제 (초월+ +99) · 초정제 (정제 +99) · 인피니티 (아바리티아) 승급 (초정제 +99). */
    public enum Ascend {
        TRANSCEND, REFINE, SUPER_REFINE, INFINITY;

        public String key() {
            return name().toLowerCase();
        }

        /** 글자 키: 아바리티아 모드가 있으면 승급은 "infinity_avaritia". */
        public String langKey() {
            return this == INFINITY && com.abilitywar.compat.AvaritiaCompat.available() ? "infinity_avaritia" : key();
        }
    }

    @org.jetbrains.annotations.Nullable
    public static Ascend nextAscend(ItemStack st) {
        if (gradeOf(st) != GearGrade.UNKNOWN) return null;
        return switch (stage(st)) {
            case TRANSCEND -> Ascend.REFINE;
            case REFINE -> Ascend.SUPER_REFINE;
            case SUPER_REFINE -> Ascend.INFINITY;
            default -> Ascend.TRANSCEND;
        };
    }

    /**
     * 초월+ · 정제 · 초정제 · 승급 재료 (넷 다 하면 돈 115만 G + 재료).
     * 초월+ ← 골렘 핵 · 군주의 증표 · 보스의 영혼 · 심연의 결정 / 정제 ← 원혼의 정수 · 광신도의 인장 · 요정 가루 · 인어의 눈물 · 용의 심장
     * 초정제 ← 용왕의 역린 · 골렘 핵 · 군주의 증표 · 원혼의 정수 · 보스의 영혼
     * 인피니티 (아바리티아) 승급 ← 용왕의 역린 · 비룡 비늘 · 해적 금화 · 도적단의 인장 · 흉포한 송곳니 · 용의 심장
     */
    public static List<Mat> ascendMats(Ascend a) {
        return switch (a) {
            case TRANSCEND -> List.of(new Mat(com.abilitywar.item.RpgItems.GOLEM_CORE, 5), new Mat(com.abilitywar.item.RpgItems.LORD_EMBLEM, 5),
                    new Mat(com.abilitywar.item.RpgItems.BOSS_SOUL, 10), new Mat(com.abilitywar.item.RpgItems.ABYSS_CRYSTAL, 10));
            case REFINE -> List.of(new Mat(com.abilitywar.item.RpgItems.WRAITH_ESSENCE, 10), new Mat(com.abilitywar.item.RpgItems.CULT_SIGIL, 10),
                    new Mat(com.abilitywar.item.RpgItems.FAIRY_DUST, 10), new Mat(com.abilitywar.item.RpgItems.MERMAID_TEAR, 5),
                    new Mat(com.abilitywar.item.RpgItems.DRAGON_HEART, 2));
            case SUPER_REFINE -> List.of(new Mat(com.abilitywar.item.RpgItems.DRAGON_KING_SCALE, 2), new Mat(com.abilitywar.item.RpgItems.GOLEM_CORE, 10),
                    new Mat(com.abilitywar.item.RpgItems.LORD_EMBLEM, 10), new Mat(com.abilitywar.item.RpgItems.WRAITH_ESSENCE, 15),
                    new Mat(com.abilitywar.item.RpgItems.BOSS_SOUL, 20));
            case INFINITY -> List.of(new Mat(com.abilitywar.item.RpgItems.DRAGON_KING_SCALE, 3), new Mat(com.abilitywar.item.RpgItems.DRAKE_SCALE, 10),
                    new Mat(com.abilitywar.item.RpgItems.PIRATE_DOUBLOON, 15), new Mat(com.abilitywar.item.RpgItems.BANDIT_SEAL, 15),
                    new Mat(com.abilitywar.item.RpgItems.DIRE_FANG, 15), new Mat(com.abilitywar.item.RpgItems.DRAGON_HEART, 2));
        };
    }

    public static long ascendGold(Ascend a) {
        return switch (a) {
            case TRANSCEND -> 150_000;
            case REFINE -> 250_000;
            case SUPER_REFINE -> 350_000;
            case INFINITY -> 400_000;
        };
    }

    /** 승급 결과 무기: 아바리티아 (Re:Avaritia) 가 있으면 그 인피니티 무기, 없으면 같은 종류의 인피니티 등급 무기 (종류마다 하나). */
    @org.jetbrains.annotations.Nullable
    public static Item infinityWeapon(GearDefs.Weapon def, net.minecraft.util.math.random.Random r) {
        Item ava = com.abilitywar.compat.AvaritiaCompat.available() ? com.abilitywar.compat.AvaritiaCompat.weapon(def.type()) : null;
        if (ava != null) return ava;
        List<Item> all = GearItems.WEAPONS.get(GearGrade.ARVANTIA);
        if (all == null || all.isEmpty()) return null;
        for (Item i : all) if (i instanceof GearWeaponItem w && w.def.type().equals(def.type())) return i;
        return all.get(r.nextInt(all.size()));
    }

    /** 승급 결과 갑옷: 아바리티아가 있으면 인피니티 갑옷의 같은 부위, 없으면 인피니티 등급 세트 하나의 같은 부위. */
    @org.jetbrains.annotations.Nullable
    public static Item infinityArmor(GearArmorItem a, net.minecraft.util.math.random.Random r) {
        Item ava = com.abilitywar.compat.AvaritiaCompat.available() ? com.abilitywar.compat.AvaritiaCompat.armor(a.piece.slot()) : null;
        if (ava != null) return ava;
        List<Item> pool = new ArrayList<>();
        for (GearDefs.ArmorSet s : GearItems.SETS.getOrDefault(GearGrade.ARVANTIA, List.of())) {
            GearDefs.Piece p = s.pieces().get(a.piece.slot());
            if (p != null && GearItems.BY_ID.get(p.id()) != null) pool.add(GearItems.BY_ID.get(p.id()));
        }
        return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
    }

    /** 강화 수치 색 (단계에 따라: 초월+ 하늘 · 정제 보라 · 인피니티 분홍). */
    public static int colorOf(ItemStack st) {
        int lv = level(st);
        if (gradeOf(st) == GearGrade.ARVANTIA) return INFINITY_COLOR;
        return switch (stage(st)) {
            case TRANSCEND -> TRANSCEND_COLOR;
            case REFINE -> REFINE_COLOR;
            case SUPER_REFINE -> SUPER_COLOR;
            default -> lv > MAX ? GearGrade.lerp(0xFF5A3C, 0xFFE27A, Math.min(1, (lv - MAX) / 84.0)) : color(lv);
        };
    }

    /** 강화 수치 글자: "+7" · "+37+" (초월+) · "정제 +12" · "초정제 +12". */
    public static MutableText levelText(ItemStack st, int lv) {
        int col = colorOf(st);
        int s = stage(st);
        MutableText t = Text.empty();
        if (s == REFINE) t.append(Text.translatable("gear.abilitywar.ui.refined_mark").styled(x -> x.withColor(TextColor.fromRgb(REFINE_COLOR)).withBold(true))).append(" ");
        if (s == SUPER_REFINE) t.append(Text.translatable("gear.abilitywar.ui.super_mark").styled(x -> x.withColor(TextColor.fromRgb(SUPER_COLOR)).withBold(true))).append(" ");
        t.append(Text.literal("+" + lv + (s == TRANSCEND ? "+" : "")).styled(x -> x.withColor(TextColor.fromRgb(col)).withBold(lv >= 10 || s > 0)));
        return t;
    }

    public static final int AWAKEN_COLOR = 0xC07CFF;

    public static int level(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return n == null ? 0 : Math.max(0, Math.min(maxLevel(stack), n.getInt(KEY)));
    }

    public static void set(ItemStack stack, int level) {
        level = Math.max(0, Math.min(maxLevel(stack), level));
        if (level == 0) {
            NbtCompound n = stack.getNbt();
            if (n != null) {
                n.remove(KEY);
                if (n.isEmpty()) stack.setNbt(null);
            }
        } else stack.getOrCreateNbt().putInt(KEY, level);
    }

    /** level → level+1 성공 확률 (%). */
    public static int chance(int level) {
        return level >= MAX ? 0 : CHANCE[level];
    }

    /** 실패하면 내려가는지 (+10 부터). */
    public static boolean failDrops(int level) {
        return level >= 10;
    }

    public static int stoneCost(GearGrade g, int level) {
        return (1 + level / 3) * (1 + g.tier() / 2);
    }

    /** 강화 값 (G). */
    public static int goldCost(GearGrade g, int level) {
        return (2 + level) * (1 + g.tier()) * 10;
    }

    public static int promoteMin(GearGrade g) {
        return g == GearGrade.SECRET ? MAX : PROMOTE_MIN;
    }

    public static boolean canPromote(GearGrade g) {
        return g.tier() < GearGrade.UNKNOWN.tier();
    }

    /** 승급 재료 한 줄. */
    public record Mat(net.minecraft.item.Item item, int count) {
    }

    /**
     * 이 등급에서 다음 등급으로 승급할 때 드는 재료 (최대 4 가지): 광물 + 몬스터 전리품 + 보스 전리품.
     * 좋음 ← 철 · 뼈 · 썩은 살점 / 희귀 ← 금 · 거미 눈 · 화약 · 고블린의 귀 / 영웅 ← 다이아몬드 · 엔더 진주 · 오크의 송곳니 · 도깨비불 정수
     * 전설 ← 다이아몬드 · 블레이즈 막대 · 해골 기사의 문장 · 기사왕의 왕관 조각 / 신화 ← 네더라이트 파편 · 위더 스켈레톤 해골 · 얼음 심장 · 화염 거인의 핵
     * 시크릿 ← 네더라이트 주괴 · 셜커 껍데기 · 고대 비룡의 비늘 · 보스의 영혼 / ? ← 네더의 별 · 메아리 조각 · 드래곤의 숨결 · 보스의 영혼 10
     */
    public static List<Mat> promoteMats(GearGrade g) {
        return switch (next(g)) {
            case COMMON, GOOD -> List.of(new Mat(net.minecraft.item.Items.IRON_INGOT, 8), new Mat(net.minecraft.item.Items.BONE, 16), new Mat(net.minecraft.item.Items.ROTTEN_FLESH, 16));
            case RARE -> List.of(new Mat(net.minecraft.item.Items.GOLD_INGOT, 8), new Mat(net.minecraft.item.Items.SPIDER_EYE, 8), new Mat(net.minecraft.item.Items.GUNPOWDER, 8),
                    new Mat(com.abilitywar.item.RpgItems.GOBLIN_EAR, 6));
            case HERO -> List.of(new Mat(net.minecraft.item.Items.DIAMOND, 2), new Mat(net.minecraft.item.Items.ENDER_PEARL, 4), new Mat(com.abilitywar.item.RpgItems.ORC_TUSK, 6),
                    new Mat(com.abilitywar.item.RpgItems.WISP_ESSENCE, 4));
            case LEGENDARY -> List.of(new Mat(net.minecraft.item.Items.DIAMOND, 6), new Mat(net.minecraft.item.Items.BLAZE_ROD, 8), new Mat(com.abilitywar.item.RpgItems.KNIGHT_CREST, 4),
                    new Mat(com.abilitywar.item.RpgItems.CROWN_SHARD, 1));
            case MYTHIC -> List.of(new Mat(net.minecraft.item.Items.NETHERITE_SCRAP, 4), new Mat(net.minecraft.item.Items.WITHER_SKELETON_SKULL, 1),
                    new Mat(com.abilitywar.item.RpgItems.FROST_HEART, 1), new Mat(com.abilitywar.item.RpgItems.EMBER_CORE, 1));
            case SECRET -> List.of(new Mat(net.minecraft.item.Items.NETHERITE_INGOT, 1), new Mat(net.minecraft.item.Items.SHULKER_SHELL, 4),
                    new Mat(com.abilitywar.item.RpgItems.WYVERN_SCALE, 2), new Mat(com.abilitywar.item.RpgItems.BOSS_SOUL, 3));
            case UNKNOWN, ARVANTIA -> List.of(new Mat(net.minecraft.item.Items.NETHER_STAR, 2), new Mat(net.minecraft.item.Items.ECHO_SHARD, 4),
                    new Mat(net.minecraft.item.Items.DRAGON_BREATH, 4), new Mat(com.abilitywar.item.RpgItems.BOSS_SOUL, 10));
        };
    }

    /** 승급 값 (G). */
    public static int promoteGold(GearGrade g) {
        return new int[]{200, 500, 1200, 3000, 6000, 12000, 25000, 25000, 25000}[g.tier()];
    }

    public static GearGrade next(GearGrade g) {
        return GearGrade.values()[Math.min(g.tier() + 1, GearGrade.UNKNOWN.tier())];
    }

    // ================================================================ 능력치

    /** 강화가 반영된 무기 능력치 (표시값). */
    public static Map<GearStat, Double> weaponStats(GearDefs.Weapon def, int level) {
        if (level <= 0) return def.stats();
        Map<GearStat, Double> m = new LinkedHashMap<>(def.stats());
        m.computeIfPresent(GearStat.ATTACK_DAMAGE, (k, v) -> v * (1 + 0.05 * level));
        m.merge(GearStat.ABILITY_DAMAGE, 0.02 * level, Double::sum);
        return m;
    }

    /** 갑옷 강화로 더 붙는 방어력. */
    public static double armorBonus(int baseArmor, int level) {
        return level <= 0 ? 0 : Math.max(0.25 * level, baseArmor * 0.05 * level);
    }

    public static double healthBonus(int level) {
        return 0.5 * level;
    }

    static final UUID ENH_ARMOR = UUID.nameUUIDFromBytes("abilitywar_enhance:armor".getBytes());
    static final UUID ENH_HEALTH = UUID.nameUUIDFromBytes("abilitywar_enhance:health".getBytes());
    static final Map<String, Multimap<EntityAttribute, EntityAttributeModifier>> CACHE = new ConcurrentHashMap<>();

    /** 강화된 무기의 손 능력치 (강화 수치마다 한 번 만들어 둠). */
    static Multimap<EntityAttribute, EntityAttributeModifier> weaponModifiers(GearWeaponItem item, int level) {
        return CACHE.computeIfAbsent(item.def.id() + "+" + level, k -> {
            ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> b = ImmutableMultimap.builder();
            for (Map.Entry<GearStat, Double> e : weaponStats(item.def, level).entrySet()) {
                GearStat st = e.getKey();
                UUID id = st == GearStat.ATTACK_DAMAGE ? GearWeaponItem.attackDamageId() : st == GearStat.ATTACK_SPEED ? GearWeaponItem.attackSpeedId() : GearUuids.of("mainhand", st);
                b.put(st.attribute(), new EntityAttributeModifier(id, "Gear " + st.key, st.modifierValue(e.getValue()), st.operation()));
            }
            return b.build();
        });
    }

    /** 강화된 갑옷 한 부위의 능력치 = 기본 + 방어력 · 체력 추가. */
    static Multimap<EntityAttribute, EntityAttributeModifier> armorModifiers(GearArmorItem item, Multimap<EntityAttribute, EntityAttributeModifier> base, int level) {
        return CACHE.computeIfAbsent(item.piece.id() + "+" + level, k -> {
            ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(base);
            String slot = item.getSlotType().getName();
            UUID a = UUID.nameUUIDFromBytes((ENH_ARMOR + slot).getBytes()), h = UUID.nameUUIDFromBytes((ENH_HEALTH + slot).getBytes());
            b.put(EntityAttributes.GENERIC_ARMOR, new EntityAttributeModifier(a, "Gear enhance armor", armorBonus(item.getProtection(), level), EntityAttributeModifier.Operation.ADDITION));
            b.put(EntityAttributes.GENERIC_MAX_HEALTH, new EntityAttributeModifier(h, "Gear enhance health", healthBonus(level), EntityAttributeModifier.Operation.ADDITION));
            return b.build();
        });
    }

    // ================================================================ 이름

    /** "+7 이름": 강화 수치 색 (낮음 회색 → 높음 금색 → +15 무지개빛 붉은 금). 각성하면 "+15 ★3 이름". */
    public static Text name(ItemStack stack, Text base) {
        int lv = level(stack), aw = awaken(stack);
        if (lv <= 0 && aw <= 0 && stage(stack) == 0) return base;
        MutableText t = Text.empty();
        if (lv > 0 || stage(stack) > 0) t.append(levelText(stack, lv)).append(" ");
        if (aw > 0) t.append(Text.literal("★" + aw + " ").styled(s -> s.withColor(TextColor.fromRgb(AWAKEN_COLOR)).withBold(true)));
        return t.append(base);
    }

    public static int color(int lv) {
        if (lv >= 15) return 0xFF5A3C;
        if (lv >= 12) return 0xFFB020;
        if (lv >= 10) return 0xFFE070;
        if (lv >= 7) return 0x8FD3FF;
        if (lv >= 4) return 0xB8F0B8;
        return 0xD8D8D8;
    }

    // ================================================================ 승급 결과

    /** 다음 등급의 같은 종류 무기 (없으면 아무 무기). */
    public static Item promotedWeapon(GearDefs.Weapon def, net.minecraft.util.math.random.Random r) {
        GearGrade n = next(def.grade());
        List<Item> same = new ArrayList<>();
        for (Item i : GearItems.WEAPONS.get(n)) if (i instanceof GearWeaponItem w && w.def.type().equals(def.type())) same.add(i);
        List<Item> pool = same.isEmpty() ? GearItems.WEAPONS.get(n) : same;
        return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
    }

    /** 다음 등급 갑옷 세트 중 하나의 같은 부위. */
    public static Item promotedArmor(GearArmorItem a, net.minecraft.util.math.random.Random r) {
        GearGrade n = next(a.set.grade());
        List<Item> pool = new ArrayList<>();
        for (GearDefs.ArmorSet s : GearItems.SETS.get(n)) {
            GearDefs.Piece p = s.pieces().get(a.piece.slot());
            if (p != null && GearItems.BY_ID.get(p.id()) != null) pool.add(GearItems.BY_ID.get(p.id()));
        }
        return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
    }

    private GearEnhance() {
    }
}

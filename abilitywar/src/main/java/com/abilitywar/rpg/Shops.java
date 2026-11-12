package com.abilitywar.rpg;

import com.abilitywar.gear.GearArmorItem;
import com.abilitywar.gear.GearEnhance;
import com.abilitywar.gear.GearGrade;
import com.abilitywar.gear.GearItem;
import com.abilitywar.gear.GearWeaponItem;
import com.abilitywar.item.RpgItems;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 상점과 대장장이.
 * <ul>
 *   <li>물약 상인: 회복 물약 3 + 바닐라 물약 (에메랄드로 삼).</li>
 *   <li>대장장이: 강화석 · 보호 주문서를 팔고, 장비 강화 (+15 까지) · 승급 (다음 등급) 을 해 줌.</li>
 * </ul>
 * 서버가 값 · 재료를 모두 다시 확인함 (클라이언트는 고른 것만 보냄).
 */
public final class Shops {
    public static final Identifier S2C_ENHANCE = com.abilitywar.AbilityWar.id("enhance_result");

    /** 파는 물건: id (번역 · 버튼용), 물건, 값 (G). sell 이면 반대로 상인에게 이 물건을 팔고 값을 받음. */
    public record Offer(String id, Supplier<ItemStack> stack, int price, boolean sell) {
        Offer(String id, Supplier<ItemStack> stack, int price) {
            this(id, stack, price, false);
        }
    }

    /** 예전 화폐 에메랄드는 상인에게 1 개 10 G 에 팔 수 있음. */
    public static final int EMERALD_G = 10;

    static ItemStack potion(Potion p) {
        return PotionUtil.setPotion(new ItemStack(Items.POTION), p);
    }

    public static final List<Offer> POTIONS = List.of(
            new Offer("health_potion_small", () -> new ItemStack(RpgItems.HEALTH_POTION_SMALL), 30),
            new Offer("health_potion", () -> new ItemStack(RpgItems.HEALTH_POTION), 80),
            new Offer("health_potion_large", () -> new ItemStack(RpgItems.HEALTH_POTION_LARGE), 200),
            new Offer("regeneration", () -> potion(Potions.REGENERATION), 80),
            new Offer("swiftness", () -> potion(Potions.SWIFTNESS), 60),
            new Offer("strength", () -> potion(Potions.STRENGTH), 150),
            new Offer("fire_resistance", () -> potion(Potions.FIRE_RESISTANCE), 80),
            new Offer("night_vision", () -> potion(Potions.NIGHT_VISION), 40),
            new Offer("water_breathing", () -> potion(Potions.WATER_BREATHING), 50),
            new Offer("invisibility", () -> potion(Potions.INVISIBILITY), 120),
            new Offer("slow_falling", () -> potion(Potions.SLOW_FALLING), 50),
            new Offer("leaping", () -> potion(Potions.LEAPING), 40),
            new Offer("sell_emerald", () -> new ItemStack(Items.EMERALD), EMERALD_G, true));

    public static final List<Offer> SMITH = List.of(
            new Offer("enhance_stone", () -> new ItemStack(RpgItems.ENHANCE_STONE), 40),
            new Offer("protection_scroll", () -> new ItemStack(RpgItems.PROTECTION_SCROLL), 400),
            new Offer("sell_emerald", () -> new ItemStack(Items.EMERALD), EMERALD_G, true));

    public static List<Offer> offers(String kind) {
        return ShopNpcEntity.BLACKSMITH.equals(kind) ? SMITH : POTIONS;
    }

    // ================================================================ 인벤토리 도우미

    public static int count(PlayerInventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) n += inv.getStack(i).getCount();
        return n;
    }

    static void take(PlayerInventory inv, Item item, int n) {
        for (int i = 0; i < inv.size() && n > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isOf(item)) continue;
            int k = Math.min(n, s.getCount());
            s.decrement(k);
            n -= k;
        }
    }

    static void give(ServerPlayerEntity p, ItemStack st) {
        if (!p.getInventory().insertStack(st)) p.dropItem(st, false);
    }

    // ================================================================ 사기

    /** @return 오류 번역 키 (되면 null). */
    @Nullable
    public static String buy(ServerPlayerEntity p, String kind, String offerId, int amount) {
        Offer o = null;
        for (Offer x : offers(kind)) if (x.id().equals(offerId)) o = x;
        if (o == null) return "shop.abilitywar.err.unknown";
        PlayerInventory inv = p.getInventory();
        if (o.sell()) {
            // 팔기: 1 개, Shift 면 가진 것 모두
            Item it = o.stack().get().getItem();
            int have = count(inv, it);
            if (have <= 0) return "shop.abilitywar.err.nothing_to_sell";
            int n = amount > 1 ? have : 1;
            take(inv, it, n);
            RpgManager.addGold(p, (long) o.price() * n);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6f, 0.9f);
            p.sendMessage(Text.translatable("shop.abilitywar.sold", o.stack().get().getName(), n, fmt((long) o.price() * n)).formatted(Formatting.GREEN), true);
            return null;
        }
        amount = Math.max(1, Math.min(16, amount));
        long cost = (long) o.price() * amount;
        if (!RpgManager.takeGold(p, cost)) return "shop.abilitywar.err.gold";
        for (int i = 0; i < amount; i++) give(p, o.stack().get());
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.6f, 1.1f);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        p.sendMessage(Text.translatable("shop.abilitywar.bought", o.stack().get().getName(), amount, fmt(cost)).formatted(Formatting.GREEN), true);
        return null;
    }

    /** 1234567 → "1,234,567". */
    public static String fmt(long g) {
        return String.format("%,d", g);
    }

    // ================================================================ 강화 · 승급

    /** 강화 · 승급 결과 (화면 연출): 0 성공, 1 실패 (그대로), 2 실패 (내려감), 3 승급, 그리고 새 강화 수치. */
    static void result(ServerPlayerEntity p, int code, int level, int slot) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(code);
        buf.writeVarInt(level);
        buf.writeVarInt(slot);
        if (ServerPlayNetworking.canSend(p, S2C_ENHANCE)) ServerPlayNetworking.send(p, S2C_ENHANCE, buf);
    }

    static boolean validSlot(int slot) {
        return slot >= 0 && slot < 41;
    }

    @Nullable
    static GearGrade grade(ItemStack st) {
        return st.getItem() instanceof GearItem g ? g.grade() : null;
    }

    /** @return 오류 번역 키 (되면 null). */
    @Nullable
    public static String enhance(ServerPlayerEntity p, int slot, boolean protect) {
        if (!validSlot(slot)) return "smith.abilitywar.err.no_item";
        PlayerInventory inv = p.getInventory();
        ItemStack st = inv.getStack(slot);
        GearGrade g = grade(st);
        if (g == null) return "smith.abilitywar.err.no_item";
        int lv = GearEnhance.level(st);
        if (lv >= GearEnhance.MAX) return "smith.abilitywar.err.max";
        int stones = GearEnhance.stoneCost(g, lv), cost = GearEnhance.goldCost(g, lv);
        boolean useScroll = protect && GearEnhance.failDrops(lv);
        boolean free = p.getAbilities().creativeMode;
        if (!free) {
            if (count(inv, RpgItems.ENHANCE_STONE) < stones) return "smith.abilitywar.err.stones";
            if (RpgManager.gold(p) < cost) return "smith.abilitywar.err.gold";
            if (useScroll && count(inv, RpgItems.PROTECTION_SCROLL) < 1) return "smith.abilitywar.err.scroll";
            take(inv, RpgItems.ENHANCE_STONE, stones);
            RpgManager.takeGold(p, cost);
            if (useScroll) take(inv, RpgItems.PROTECTION_SCROLL, 1);
        }
        ServerWorld w = p.getServerWorld();
        boolean ok = w.getRandom().nextInt(100) < GearEnhance.chance(lv);
        int code;
        if (ok) {
            GearEnhance.set(st, lv + 1);
            code = 0;
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.8f, 1.2f + lv * 0.03f);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
            w.spawnParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.5, 0.6, 0.5, 0.8);
            w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), lv >= 9 ? 30 : 8, 0.4, 0.5, 0.4, 0.3);
            if (lv + 1 >= 12) announce(p, Text.translatable("smith.abilitywar.broadcast", p.getDisplayName(), st.getName()));
        } else if (GearEnhance.failDrops(lv) && !useScroll) {
            GearEnhance.set(st, lv - 1);
            code = 2;
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.PLAYERS, 0.7f, 0.9f);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 20, 0.4, 0.4, 0.4, 0.02);
        } else {
            code = 1;
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6f, 0.8f);
            w.spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
        }
        inv.markDirty();
        result(p, code, GearEnhance.level(st), slot);
        return null;
    }

    @Nullable
    public static String promote(ServerPlayerEntity p, int slot) {
        if (!validSlot(slot)) return "smith.abilitywar.err.no_item";
        PlayerInventory inv = p.getInventory();
        ItemStack st = inv.getStack(slot);
        GearGrade g = grade(st);
        if (g == null) return "smith.abilitywar.err.no_item";
        if (!GearEnhance.canPromote(g)) return "smith.abilitywar.err.top_grade";
        int lv = GearEnhance.level(st);
        if (lv < GearEnhance.promoteMin(g)) return "smith.abilitywar.err.promote_level";
        List<GearEnhance.Mat> mats = GearEnhance.promoteMats(g);
        int cost = GearEnhance.promoteGold(g);
        ServerWorld w = p.getServerWorld();
        Item next = st.getItem() instanceof GearWeaponItem wi ? GearEnhance.promotedWeapon(wi.def, w.getRandom())
                : st.getItem() instanceof GearArmorItem ai ? GearEnhance.promotedArmor(ai, w.getRandom()) : null;
        if (next == null) return "smith.abilitywar.err.top_grade";
        if (!p.getAbilities().creativeMode) {
            for (GearEnhance.Mat m : mats) if (count(inv, m.item()) < m.count()) return "smith.abilitywar.err.materials";
            if (RpgManager.gold(p) < cost) return "smith.abilitywar.err.gold";
            for (GearEnhance.Mat m : mats) take(inv, m.item(), m.count());
            RpgManager.takeGold(p, cost);
        }
        ItemStack out = new ItemStack(next);
        GearEnhance.set(out, lv / 2);
        inv.setStack(slot, out);
        inv.markDirty();
        w.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.3f);
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.4, 0.8, 0.4, 0.15);
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 50, 0.5, 0.6, 0.5, 0.5);
        GearGrade ng = GearEnhance.next(g);
        p.sendMessage(Text.translatable("smith.abilitywar.promoted", out.getName(), ng.displayName()).formatted(Formatting.GOLD), false);
        if (ng.tier() >= GearGrade.LEGENDARY.tier()) announce(p, Text.translatable("smith.abilitywar.broadcast_promote", p.getDisplayName(), out.getName(), ng.displayName()));
        result(p, 3, GearEnhance.level(out), slot);
        return null;
    }

    /** 각성 (+15 장비): ★ 하나 더. 실패하면 재료만 사라짐. 결과 코드 4 = 각성 성공, 5 = 각성 실패. */
    @Nullable
    public static String awaken(ServerPlayerEntity p, int slot) {
        if (!validSlot(slot)) return "smith.abilitywar.err.no_item";
        PlayerInventory inv = p.getInventory();
        ItemStack st = inv.getStack(slot);
        if (grade(st) == null) return "smith.abilitywar.err.no_item";
        if (GearEnhance.level(st) < GearEnhance.MAX) return "smith.abilitywar.err.awaken_level";
        int a = GearEnhance.awaken(st);
        if (a >= GearEnhance.AWAKEN_MAX) return "smith.abilitywar.err.awaken_max";
        List<GearEnhance.Mat> mats = GearEnhance.awakenMats(a);
        int cost = GearEnhance.awakenGold(a);
        if (!p.getAbilities().creativeMode) {
            for (GearEnhance.Mat m : mats) if (count(inv, m.item()) < m.count()) return "smith.abilitywar.err.materials";
            if (RpgManager.gold(p) < cost) return "smith.abilitywar.err.gold";
            for (GearEnhance.Mat m : mats) take(inv, m.item(), m.count());
            RpgManager.takeGold(p, cost);
        }
        ServerWorld w = p.getServerWorld();
        boolean ok = w.getRandom().nextInt(100) < GearEnhance.awakenChance(a);
        if (ok) {
            GearEnhance.setAwaken(st, a + 1);
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.5f, 1.4f);
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.2f);
            w.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.2, p.getZ(), 80, 0.5, 0.8, 0.5, 0.3);
            w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.4, 0.8, 0.4, 0.12);
            announce(p, Text.translatable("smith.abilitywar.broadcast_awaken", p.getDisplayName(), st.getName()));
        } else {
            w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.8f, 0.9f);
            w.spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
        }
        inv.markDirty();
        result(p, ok ? 4 : 5, GearEnhance.awaken(st), slot);
        return null;
    }

    static void announce(ServerPlayerEntity p, Text t) {
        if (p.getServer() == null) return;
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
            o.sendMessage(Text.literal("⚒ ").formatted(Formatting.GOLD).append(t.copy().formatted(Formatting.YELLOW)), false);
    }

    // ================================================================ 재료 얻기

    /**
     * 몬스터를 잡음 (플레이어가): 가끔 강화석 (4%), 이 모드 몬스터는 전리품 (고블린의 귀 50% · 오크의 송곳니 40% · 도깨비불 정수 40% · 해골 기사의 문장 35%).
     * 보스: 강화석 3~6 + 보스의 영혼 (위더 · 엔더 드래곤 · 워든은 3) + 이 모드 보스는 자기 전리품 1 (30% 로 하나 더).
     */
    public static void drops(net.minecraft.entity.LivingEntity dead, boolean boss) {
        if (!(dead.getWorld() instanceof ServerWorld w)) return;
        var r = w.getRandom();
        if (boss) {
            dead.dropStack(new ItemStack(RpgItems.ENHANCE_STONE, 3 + r.nextInt(4)));
            dead.dropStack(new ItemStack(RpgItems.BOSS_SOUL, endgame(dead) ? 3 : 1));
            Item trophy = dead instanceof com.abilitywar.mob.FallenKingEntity ? RpgItems.CROWN_SHARD
                    : dead instanceof com.abilitywar.mob.FrostWitchEntity ? RpgItems.FROST_HEART
                    : dead instanceof com.abilitywar.mob.EmberColossusEntity ? RpgItems.EMBER_CORE
                    : dead instanceof com.abilitywar.mob.AncientWyvernEntity ? RpgItems.WYVERN_SCALE : null;
            if (trophy != null) dead.dropStack(new ItemStack(trophy, r.nextInt(100) < 30 ? 2 : 1));
            // 심연의 탑에서 쓰러뜨린 보스: 심연의 결정
            if (dead.getCommandTags().contains(AbyssTower.MOB_TAG)) dead.dropStack(new ItemStack(RpgItems.ABYSS_CRYSTAL, 1 + r.nextInt(2)));
            return;
        }
        if (!(dead instanceof net.minecraft.entity.mob.Monster)) return;
        if (r.nextInt(100) < 4) dead.dropStack(new ItemStack(RpgItems.ENHANCE_STONE));
        if (dead instanceof com.abilitywar.mob.GoblinEntity && r.nextInt(100) < 50) dead.dropStack(new ItemStack(RpgItems.GOBLIN_EAR));
        if (dead instanceof com.abilitywar.mob.OrcEntity && r.nextInt(100) < 40) dead.dropStack(new ItemStack(RpgItems.ORC_TUSK));
        if (dead instanceof com.abilitywar.mob.WispEntity && r.nextInt(100) < 40) dead.dropStack(new ItemStack(RpgItems.WISP_ESSENCE));
        if (dead instanceof com.abilitywar.mob.SkeletonKnightEntity && r.nextInt(100) < 35) dead.dropStack(new ItemStack(RpgItems.KNIGHT_CREST));
    }

    static boolean endgame(net.minecraft.entity.LivingEntity e) {
        return e instanceof net.minecraft.entity.boss.WitherEntity || e instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity
                || e instanceof net.minecraft.entity.mob.WardenEntity;
    }

    /** 다른 모드 보스 (몸이 없음): 바로 인벤토리로. */
    /** 엔더 드래곤을 쓰러뜨림: 가까이서 싸운 모두에게 용의 심장 + 심연의 결정 (바로 인벤토리로 — 드래곤은 공중에서 죽음). */
    public static void dragonReward(ServerPlayerEntity p) {
        var r = p.getRandom();
        give(p, new ItemStack(RpgItems.DRAGON_HEART));
        give(p, new ItemStack(RpgItems.ABYSS_CRYSTAL, 3 + r.nextInt(3)));
        give(p, new ItemStack(RpgItems.BOSS_SOUL, 3));
        give(p, new ItemStack(RpgItems.ENHANCE_STONE, 8 + r.nextInt(5)));
    }

    public static void bossRewardDirect(ServerPlayerEntity p, int xp) {
        var r = p.getRandom();
        give(p, new ItemStack(RpgItems.ENHANCE_STONE, 3 + r.nextInt(4)));
        give(p, new ItemStack(RpgItems.BOSS_SOUL));
    }

    private Shops() {
    }
}

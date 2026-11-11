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
 *   <li>대장장이: 강화석 · 보호 주문서 · 승급석을 팔고, 장비 강화 (+15 까지) · 승급 (다음 등급) 을 해 줌.</li>
 * </ul>
 * 서버가 값 · 재료를 모두 다시 확인함 (클라이언트는 고른 것만 보냄).
 */
public final class Shops {
    public static final Identifier S2C_ENHANCE = com.abilitywar.AbilityWar.id("enhance_result");

    /** 파는 물건: id (번역 · 버튼용), 물건, 값 (에메랄드). */
    public record Offer(String id, Supplier<ItemStack> stack, int price) {
    }

    static ItemStack potion(Potion p) {
        return PotionUtil.setPotion(new ItemStack(Items.POTION), p);
    }

    public static final List<Offer> POTIONS = List.of(
            new Offer("health_potion_small", () -> new ItemStack(RpgItems.HEALTH_POTION_SMALL), 2),
            new Offer("health_potion", () -> new ItemStack(RpgItems.HEALTH_POTION), 5),
            new Offer("health_potion_large", () -> new ItemStack(RpgItems.HEALTH_POTION_LARGE), 12),
            new Offer("regeneration", () -> potion(Potions.REGENERATION), 6),
            new Offer("swiftness", () -> potion(Potions.SWIFTNESS), 5),
            new Offer("strength", () -> potion(Potions.STRENGTH), 10),
            new Offer("fire_resistance", () -> potion(Potions.FIRE_RESISTANCE), 6),
            new Offer("night_vision", () -> potion(Potions.NIGHT_VISION), 3),
            new Offer("water_breathing", () -> potion(Potions.WATER_BREATHING), 4),
            new Offer("invisibility", () -> potion(Potions.INVISIBILITY), 8),
            new Offer("slow_falling", () -> potion(Potions.SLOW_FALLING), 4),
            new Offer("leaping", () -> potion(Potions.LEAPING), 3));

    public static final List<Offer> SMITH = List.of(
            new Offer("enhance_stone", () -> new ItemStack(RpgItems.ENHANCE_STONE), 3),
            new Offer("protection_scroll", () -> new ItemStack(RpgItems.PROTECTION_SCROLL), 24),
            new Offer("promotion_stone", () -> new ItemStack(RpgItems.PROMOTION_STONE), 48));

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
        amount = Math.max(1, Math.min(16, amount));
        int cost = o.price() * amount;
        PlayerInventory inv = p.getInventory();
        if (!p.getAbilities().creativeMode) {
            if (count(inv, Items.EMERALD) < cost) return "shop.abilitywar.err.emeralds";
            take(inv, Items.EMERALD, cost);
        }
        for (int i = 0; i < amount; i++) give(p, o.stack().get());
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.6f, 1.1f);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        p.sendMessage(Text.translatable("shop.abilitywar.bought", o.stack().get().getName(), amount, cost).formatted(Formatting.GREEN), true);
        return null;
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
        int stones = GearEnhance.stoneCost(g, lv), ems = GearEnhance.emeraldCost(g, lv);
        boolean useScroll = protect && GearEnhance.failDrops(lv);
        boolean free = p.getAbilities().creativeMode;
        if (!free) {
            if (count(inv, RpgItems.ENHANCE_STONE) < stones) return "smith.abilitywar.err.stones";
            if (count(inv, Items.EMERALD) < ems) return "smith.abilitywar.err.emeralds";
            if (useScroll && count(inv, RpgItems.PROTECTION_SCROLL) < 1) return "smith.abilitywar.err.scroll";
            take(inv, RpgItems.ENHANCE_STONE, stones);
            take(inv, Items.EMERALD, ems);
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
        int stones = GearEnhance.promoteStones(g), ems = GearEnhance.promoteEmeralds(g);
        ServerWorld w = p.getServerWorld();
        Item next = st.getItem() instanceof GearWeaponItem wi ? GearEnhance.promotedWeapon(wi.def, w.getRandom())
                : st.getItem() instanceof GearArmorItem ai ? GearEnhance.promotedArmor(ai, w.getRandom()) : null;
        if (next == null) return "smith.abilitywar.err.top_grade";
        if (!p.getAbilities().creativeMode) {
            if (count(inv, RpgItems.PROMOTION_STONE) < stones) return "smith.abilitywar.err.promotion_stones";
            if (count(inv, Items.EMERALD) < ems) return "smith.abilitywar.err.emeralds";
            take(inv, RpgItems.PROMOTION_STONE, stones);
            take(inv, Items.EMERALD, ems);
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

    static void announce(ServerPlayerEntity p, Text t) {
        if (p.getServer() == null) return;
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
            o.sendMessage(Text.literal("⚒ ").formatted(Formatting.GOLD).append(t.copy().formatted(Formatting.YELLOW)), false);
    }

    // ================================================================ 재료 얻기

    /** 몬스터를 잡음: 가끔 강화석. 보스: 강화석 여러 개 + 승급석 (절반 확률). */
    public static void drops(net.minecraft.entity.LivingEntity dead, boolean boss) {
        if (!(dead.getWorld() instanceof ServerWorld w)) return;
        var r = w.getRandom();
        if (boss) {
            dead.dropStack(new ItemStack(RpgItems.ENHANCE_STONE, 3 + r.nextInt(4)));
            if (r.nextBoolean()) dead.dropStack(new ItemStack(RpgItems.PROMOTION_STONE));
        } else if (dead instanceof net.minecraft.entity.mob.Monster && r.nextInt(100) < 4) {
            dead.dropStack(new ItemStack(RpgItems.ENHANCE_STONE));
        }
    }

    /** 다른 모드 보스 (몸이 없음): 바로 인벤토리로. */
    public static void bossRewardDirect(ServerPlayerEntity p) {
        var r = p.getRandom();
        give(p, new ItemStack(RpgItems.ENHANCE_STONE, 3 + r.nextInt(4)));
        if (r.nextBoolean()) give(p, new ItemStack(RpgItems.PROMOTION_STONE));
    }

    private Shops() {
    }
}

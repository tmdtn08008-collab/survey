package com.abilitywar.mob;

import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * 마지막 보스 "종언의 용" 엔더 드래곤 (True Ending 이 공격 · 페이즈를 바꾸고, 여기서는 RPG 쪽을 더함).
 * <ul>
 *   <li>처음 다가가면 보스 등장 연출 (이름 · 별칭).</li>
 *   <li>RPG 장비 · 능력을 쓰는 플레이어 기준으로 더 단단함: 받는 피해 ×0.6, 같이 싸우는 사람이 늘면 더 줄어듦 (1명 더마다 ÷1.35).</li>
 *   <li>드래곤이 주는 피해 ×1.6.</li>
 *   <li>체력 50% · 20% 에서 분노의 포효: 가까운 플레이어에게 어둠 · 느려짐 + 화면 흔들림.</li>
 *   <li>쓰러뜨리면 가까이 있던 모두: 25,000 G · 경험치 6,000 · 용의 심장 · 심연의 결정 · 보스의 영혼 · 강화석 (RpgManager · Shops).</li>
 * </ul>
 * True Ending 이 만드는 가짜 드래곤 (거울 드래곤) 은 보스로 치지 않음.
 */
public final class FinalDragon {
    public static final long GOLD = 25_000;
    public static final int XP = 6_000;
    public static final float TAKEN = 0.6f, DEALT = 1.6f;
    static final String INTRO = "aw_dragon_intro", RAGE1 = "aw_dragon_rage1", RAGE2 = "aw_dragon_rage2";

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FinalDragon::tick);
    }

    /** True Ending 의 가짜 (거울) 드래곤. */
    public static boolean isDecoy(LivingEntity e) {
        if (!(e instanceof EnderDragonEntity)) return false;
        for (String t : e.getCommandTags()) if (t.startsWith("trueEnding_mirrordragon") || t.equals("trueEnding_dragon_noAI")) return true;
        return false;
    }

    /** 드래곤이 받는 피해 배율: 같이 싸우는 사람 (엔드에서 200칸 안) 이 많을수록 더 단단함. */
    public static float takenMult(LivingEntity dragon) {
        if (!(dragon.getWorld() instanceof ServerWorld w)) return TAKEN;
        int n = w.getPlayers(p -> !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(dragon) < 200 * 200).size();
        return TAKEN / (1f + 0.35f * Math.max(0, n - 1));
    }

    static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 0) return;
        ServerWorld end = server.getWorld(World.END);
        if (end == null) return;
        for (EnderDragonEntity d : end.getAliveEnderDragons()) {
            if (isDecoy(d) || d.isRemoved()) continue;
            var near = end.getPlayers(p -> !p.isSpectator() && p.squaredDistanceTo(d) < 150 * 150);
            if (near.isEmpty()) continue;
            if (d.addCommandTag(INTRO)) intro(end, d);
            float hp = d.getHealth() / Math.max(1, d.getMaxHealth());
            if (hp <= 0.5f && d.addCommandTag(RAGE1)) roar(end, d, false);
            if (hp <= 0.2f && d.addCommandTag(RAGE2)) roar(end, d, true);
        }
    }

    static void intro(ServerWorld w, EnderDragonEntity d) {
        NbtCompound n = new NbtCompound();
        n.putInt("e", d.getId());
        n.putString("name", d.getType().getTranslationKey());
        n.putString("sub", "boss.abilitywar.ender_dragon.sub");
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(d) < 200 * 200)) {
            AWNet.fx(w, p.getPos(), "boss_intro", n);
            p.sendMessage(Text.translatable("boss.abilitywar.ender_dragon.appeared").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
        }
        w.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 8f, 0.7f);
    }

    /** 분노의 포효: 엔드 섬의 플레이어 모두 (100칸) 어둠 · 느려짐 + 흔들림. */
    static void roar(ServerWorld w, EnderDragonEntity d, boolean second) {
        w.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 10f, second ? 0.5f : 0.65f);
        w.spawnParticles(ParticleTypes.DRAGON_BREATH, d.getX(), d.getY() + 2, d.getZ(), 200, 4, 3, 4, 0.08);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, d.getX(), d.getY() + 2, d.getZ(), 3, 1, 1, 1, 0);
        for (ServerPlayerEntity p : w.getPlayers(p -> !p.isSpectator() && p.squaredDistanceTo(d) < 100 * 100)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, second ? 100 : 60, 0, false, false, true), d);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, second ? 60 : 40, 1, false, false, true), d);
            if (second) p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0, false, false, true), d);
            AWNet.shake(p, second ? 1.4f : 1.0f, second ? 30 : 20);
            p.sendMessage(Text.translatable(second ? "boss.abilitywar.ender_dragon.rage2" : "boss.abilitywar.ender_dragon.rage1").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
        }
    }

    /** AbilityManager.modifyDamage: 드래곤이 받는 피해 / 드래곤이 주는 피해. */
    public static float modify(LivingEntity victim, net.minecraft.entity.Entity attacker, float mult) {
        if (victim instanceof EnderDragonEntity d && attacker instanceof PlayerEntity && !isDecoy(d)) mult *= takenMult(d);
        if (attacker instanceof EnderDragonEntity && victim instanceof PlayerEntity) mult *= DEALT;
        return mult;
    }

    private FinalDragon() {
    }
}

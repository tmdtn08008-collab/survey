package com.abilitywar.world;

import com.abilitywar.AbilityWar;
import com.abilitywar.mob.AWMobs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 테마 지역의 생물: 지역 안에 있는 플레이어 둘레 (18~34칸) 에 그 숲의 몹이 낮밤 없이 나옴 (둘레 수 제한).
 * 가끔 우두머리 (고블린 로드 · 슬라임 킹 · 거미 여왕 · 광신도 · 강화 골렘). 깊은 숲일수록 (테마 단계) 체력 · 공격력이 높음.
 * 요정의 숲 가운데 샘에서는 체력이 차오름.
 */
public final class RegionLife {
    public static final String TAG = "aw_region", ELITE_TAG = "aw_region_elite";
    static final UUID HP_ID = UUID.fromString("5e7c0a1e-2b11-4d6a-9a77-1c0ffee0b001"), DMG_ID = UUID.fromString("5e7c0a1e-2b11-4d6a-9a77-1c0ffee0b002");
    static final Map<UUID, Long> NEXT = new HashMap<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RegionLife::tick);
    }

    static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 11) return;
        long now = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !(p.getWorld() instanceof ServerWorld w)) continue;
            RpgWorld.RegionHit hit = RpgWorld.regionAt(w, p.getBlockPos());
            if (hit == null) continue;
            if (hit.theme() == Regions.Theme.FAIRY && p.getBlockPos().getSquaredDistance(hit.center()) < 10 * 10) spring(w, p);
            if (w.getDifficulty() == Difficulty.PEACEFUL || p.isCreative()) continue;
            Long next = NEXT.get(p.getUuid());
            if (next != null && now < next) continue;
            NEXT.put(p.getUuid(), now + 60 + w.getRandom().nextInt(80));
            spawnNear(w, p, hit);
        }
    }

    /** 요정의 샘: 재생 + 반짝임. */
    static void spring(ServerWorld w, ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 1, true, false, true));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 10, 0, true, false, false));
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 6, 0.6, 0.8, 0.6, 0.0);
        w.spawnParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 2.5, p.getZ(), 4, 1.2, 0.4, 1.2, 0.0);
    }

    record Pick(String id, int weight) {
    }

    static Pick[] table(Regions.Theme t, boolean night) {
        return switch (t) {
            case FAIRY -> new Pick[]{new Pick("abilitywar:fairy", 60), new Pick("abilitywar:wisp", 25), new Pick("minecraft:rabbit", 15)};
            case GOBLIN -> new Pick[]{new Pick("abilitywar:goblin", 55), new Pick("abilitywar:orc", 15), new Pick("abilitywar:dire_wolf", 15), new Pick("abilitywar:thief", 10),
                    new Pick("abilitywar:bandit", 5)};
            case SLIME -> new Pick[]{new Pick("minecraft:slime", 70), new Pick("minecraft:witch", 10), new Pick("abilitywar:goblin", 10), new Pick("minecraft:frog", 10)};
            case SPIDER -> new Pick[]{new Pick("minecraft:spider", 50), new Pick("minecraft:cave_spider", 35), new Pick(night ? "minecraft:skeleton" : "abilitywar:thief", 15)};
            case HAUNTED -> new Pick[]{new Pick("abilitywar:wraith", 30), new Pick("abilitywar:skeleton_knight", 25), new Pick("abilitywar:wisp", 15),
                    new Pick(night ? "minecraft:zombie" : "abilitywar:cultist", 15), new Pick(night ? "minecraft:skeleton" : "abilitywar:wraith", 15)};
            case CRYSTAL -> new Pick[]{new Pick("abilitywar:ancient_golem", 30), new Pick("abilitywar:wisp", 35), new Pick("abilitywar:skeleton_knight", 20), new Pick("abilitywar:fairy", 15)};
        };
    }

    static String elite(Regions.Theme t) {
        return switch (t) {
            case FAIRY -> null;
            case GOBLIN -> "abilitywar:goblin_lord";
            case SLIME -> "minecraft:slime";
            case SPIDER -> "abilitywar:spider_queen";
            case HAUNTED -> "abilitywar:cultist";
            case CRYSTAL -> "abilitywar:enhanced_golem";
        };
    }

    static String pick(Pick[] tab, Random r) {
        int total = 0;
        for (Pick p : tab) total += p.weight;
        int k = r.nextInt(total);
        for (Pick p : tab) if ((k -= p.weight) < 0) return p.id;
        return tab[0].id;
    }

    static void spawnNear(ServerWorld w, ServerPlayerEntity p, RpgWorld.RegionHit hit) {
        Regions.Theme t = hit.theme();
        int cap = 5 + t.ordinal() / 2;
        Box area = p.getBoundingBox().expand(40, 24, 40);
        int have = w.getEntitiesByClass(MobEntity.class, area, e -> e.getCommandTags().contains(TAG) && e.isAlive()).size();
        if (have >= cap) return;
        Random r = w.getRandom();
        boolean night = w.isNight();
        int group = Math.min(cap - have, 1 + r.nextInt(t == Regions.Theme.FAIRY ? 2 : 3));
        boolean eliteOk = elite(t) != null && r.nextInt(30) == 0
                && w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(96, 48, 96), e -> e.getCommandTags().contains(ELITE_TAG) && e.isAlive()).isEmpty();
        for (int i = 0; i < group; i++) {
            BlockPos at = spot(w, p, hit, r);
            if (at == null) continue;
            boolean isElite = eliteOk && i == 0;
            String id = isElite ? elite(t) : pick(table(t, night), r);
            Entity e = spawn(w, id, at, t, isElite);
            if (isElite && e != null) {
                for (ServerPlayerEntity o : w.getPlayers(o -> o.squaredDistanceTo(e) < 64 * 64))
                    o.sendMessage(Text.translatable("region.abilitywar.elite", e.getDisplayName(), Text.translatable("region.abilitywar.region." + t.key))
                            .formatted(Formatting.GOLD), false);
            }
        }
    }

    /** 플레이어에게서 18~34칸, 지역 안, 물이 아닌 땅 위. 다른 플레이어 12칸 안은 피함. */
    static BlockPos spot(ServerWorld w, ServerPlayerEntity p, RpgWorld.RegionHit hit, Random r) {
        for (int tries = 0; tries < 8; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 18 + r.nextDouble() * 16;
            int x = MathHelper.floor(p.getX() + Math.cos(a) * d), z = MathHelper.floor(p.getZ() + Math.sin(a) * d);
            double dx = x - hit.center().getX(), dz = z - hit.center().getZ();
            if (dx * dx + dz * dz > (Regions.R - 4) * (Regions.R - 4)) continue;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!w.getFluidState(top.down()).isEmpty() || !w.getFluidState(top).isEmpty()) continue;
            if (Math.abs(top.getY() - p.getY()) > 20) continue;
            boolean crowded = false;
            for (ServerPlayerEntity o : w.getPlayers()) if (o.squaredDistanceTo(top.getX(), top.getY(), top.getZ()) < 12 * 12) crowded = true;
            if (crowded) continue;
            return top;
        }
        return null;
    }

    static Entity spawn(ServerWorld w, String id, BlockPos at, Regions.Theme t, boolean elite) {
        var ident = net.minecraft.util.Identifier.tryParse(id);
        if (ident == null || !net.minecraft.registry.Registries.ENTITY_TYPE.containsId(ident)) return null;
        EntityType<?> type = net.minecraft.registry.Registries.ENTITY_TYPE.get(ident);
        Entity e = type.create(w);
        if (e == null) return null;
        e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.getRandom().nextFloat() * 360, 0);
        if (e instanceof MobEntity m) m.initialize(w, w.getLocalDifficulty(at), SpawnReason.EVENT, null, null);
        if (e instanceof SlimeEntity sl) sl.setSize(elite ? 6 : 1 + w.getRandom().nextInt(3), true);
        if (elite && e instanceof SlimeEntity) e.setCustomName(Text.translatable("region.abilitywar.slime_king").formatted(Formatting.GREEN, Formatting.BOLD));
        e.addCommandTag(TAG);
        if (elite) e.addCommandTag(ELITE_TAG);
        if (e instanceof LivingEntity le) {
            // 깊은 숲일수록 강함 (요정 1배 … 수정 3.25배 체력)
            int tier = t.ordinal();
            mod(le, EntityAttributes.GENERIC_MAX_HEALTH, HP_ID, 0.45 * tier + (elite ? 1.0 : 0));
            mod(le, EntityAttributes.GENERIC_ATTACK_DAMAGE, DMG_ID, 0.2 * tier);
            le.setHealth(le.getMaxHealth());
            if (elite) le.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 5, 0, false, false));
        }
        w.spawnEntityAndPassengers(e);
        w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        return e;
    }

    static void mod(LivingEntity e, EntityAttribute attr, UUID id, double v) {
        EntityAttributeInstance ai = e.getAttributeInstance(attr);
        if (ai == null || v <= 0) return;
        ai.removeModifier(id);
        ai.addPersistentModifier(new EntityAttributeModifier(id, AbilityWar.MOD_ID + " region", v, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private RegionLife() {
    }
}

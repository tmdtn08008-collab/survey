package com.abilitywar.mob;

import com.abilitywar.AbilityWar;
import com.abilitywar.registry.AWGameRules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 야생 보스: 던전이 아니어도 가끔 플레이어 근처 (30~45칸) 에 보스가 나타남.
 * 5분마다 플레이어마다 abilitywarWildBossChance % 확률. 바이옴에 맞는 보스를 고름.
 *
 * <p>목록은 config/abilitywar_wild_bosses.json (처음 실행 때 만들어짐). 다른 모드의 보스 id 도 넣을 수 있고,
 * 그 모드가 없으면 그 줄은 건너뜀.
 */
public final class BossSpawner {
    record Entry(Identifier id, int weight, String biome) {
    }

    static final List<Entry> ENTRIES = new ArrayList<>();
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
        ServerTickEvents.END_SERVER_TICK.register(BossSpawner::tick);
    }

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("abilitywar_wild_bosses.json");
    }

    static void load() {
        ENTRIES.clear();
        Path f = file();
        try {
            if (!Files.exists(f)) Files.writeString(f, defaults());
            JsonObject root = GSON.fromJson(Files.readString(f), JsonObject.class);
            // 예전 설정 파일: 새로 들어간 기본 항목 (다른 모드 보스) 을 덧붙임
            if (!root.has("_version") || root.get("_version").getAsInt() < CONFIG_VERSION) {
                java.util.Set<String> have = new java.util.HashSet<>();
                for (JsonElement el : root.getAsJsonArray("bosses")) have.add(el.getAsJsonObject().get("id").getAsString());
                JsonObject def = GSON.fromJson(defaults(), JsonObject.class);
                for (JsonElement el : def.getAsJsonArray("bosses"))
                    if (!have.contains(el.getAsJsonObject().get("id").getAsString())) root.getAsJsonArray("bosses").add(el);
                root.addProperty("_version", CONFIG_VERSION);
                root.add("_help", def.get("_help"));
                Files.writeString(f, GSON.toJson(root));
            }
            for (JsonElement el : root.getAsJsonArray("bosses")) {
                JsonObject o = el.getAsJsonObject();
                Identifier id = new Identifier(o.get("id").getAsString());
                int weight = o.has("weight") ? o.get("weight").getAsInt() : 10;
                String biome = o.has("biome") ? o.get("biome").getAsString() : "any";
                if (weight <= 0) continue;
                if (!Registries.ENTITY_TYPE.containsId(id)) {
                    AbilityWar.LOG.info("[wild bosses] {} is not installed, skipping", id);
                    continue;
                }
                ENTRIES.add(new Entry(id, weight, biome));
            }
        } catch (Exception e) {
            AbilityWar.LOG.error("[wild bosses] could not read {}", f, e);
            ENTRIES.add(new Entry(AbilityWar.id("fallen_king"), 10, "any"));
        }
        AbilityWar.LOG.info("[wild bosses] {} entries", ENTRIES.size());
    }

    static final int CONFIG_VERSION = 6;

    /**
     * 기본 목록: 이 모드 보스 4 + Bosses of Mass Destruction (설치돼 있으면 나옴)
     * + L_Ender's Cataclysm 의 보스들 (Forge 전용이라 무게 0 = 꺼짐).
     */
    static String defaults() {
        JsonObject root = new JsonObject();
        root.addProperty("_version", CONFIG_VERSION);
        root.addProperty("_help", "biome: any / cold / hot / mountain / forest / swamp / ocean. weight 0 = off. 다른 모드 보스 id 를 넣으면 그 모드가 있을 때만 나옴. "
                + "bosses_of_mass_destruction = Bosses of Mass Destruction (dist/boss_mods 에 같이 들어 있음), soulsweapons = Marium's Soulslike Weaponry (dist/boss_mods 에 같이 들어 있음), graveyard = The Graveyard (dist/boss_mods 에 같이 들어 있음), archon = Archon Renewed (dist/boss_mods 에 같이 들어 있음, archon:null 은 가장 센 보스라 기본 꺼짐), adventurez = AdventureZ (+ VoidZ), dungeonnowloading = Dungeon Now Loading (던전 보스라 기본 꺼짐), "
                + "mutantmonsters = Mutant Monsters, aquamirae = Aquamirae (선장 코넬리아는 난파선 보스라 기본 꺼짐), myths_of_the_sea = Myths of The Sea (바다에서 저절로 나옴, 기본 꺼짐).");
        JsonArray a = new JsonArray();
        String[][] def = {
                {"abilitywar:fallen_king", "10", "any"}, {"abilitywar:ember_colossus", "10", "hot"}, {"abilitywar:frost_witch", "10", "cold"},
                {"abilitywar:ancient_wyvern", "8", "mountain"}, {"abilitywar:ancient_wyvern", "3", "any"},
                {"bosses_of_mass_destruction:lich", "8", "cold"}, {"bosses_of_mass_destruction:lich", "3", "any"},
                {"bosses_of_mass_destruction:gauntlet", "6", "hot"}, {"bosses_of_mass_destruction:obsidilith", "5", "mountain"},
                {"bosses_of_mass_destruction:void_blossom", "6", "forest"}, {"bosses_of_mass_destruction:void_blossom", "4", "swamp"},
                {"soulsweapons:accursed_lord_boss", "4", "forest"}, {"soulsweapons:draugr_boss", "4", "cold"}, {"soulsweapons:night_shade", "3", "any"},
                {"soulsweapons:returning_knight", "3", "any"}, {"soulsweapons:chaos_monarch", "3", "hot"}, {"soulsweapons:moonknight", "3", "mountain"},
                {"soulsweapons:day_stalker", "3", "hot"}, {"soulsweapons:night_prowler", "3", "any"}, {"graveyard:lich", "5", "any"},
                {"archon:tar", "4", "any"}, {"archon:ayla", "4", "mountain"}, {"archon:leven", "4", "ocean"}, {"archon:inigo", "4", "hot"}, {"archon:null", "0", "any"},
                {"adventurez:blackstone_golem", "3", "hot"}, {"adventurez:the_eye", "0", "any"}, {"adventurez:void_shadow", "0", "any"},
                {"dungeonnowloading:chaos_spawner", "0", "any"}, {"dungeonnowloading:fairkeeper_boros", "0", "any"}, {"dungeonnowloading:fairkeeper_ouros", "0", "any"},
                {"mutantmonsters:mutant_zombie", "3", "any"}, {"mutantmonsters:mutant_skeleton", "3", "any"}, {"mutantmonsters:mutant_enderman", "2", "any"},
                {"mutantmonsters:mutant_creeper", "2", "any"}, {"aquamirae:captain_cornelia", "0", "cold"}, {"myths_of_the_sea:leviathan", "0", "ocean"},
                {"myths_of_the_sea:kraken", "0", "ocean"},
                {"cataclysm:ignis", "0", "hot"}, {"cataclysm:netherite_monstrosity", "0", "hot"}, {"cataclysm:ender_guardian", "0", "any"},
                {"cataclysm:the_harbinger", "0", "any"}, {"cataclysm:the_leviathan", "0", "ocean"}, {"cataclysm:ancient_remnant", "0", "hot"},
                {"cataclysm:maledictus", "0", "cold"}, {"cataclysm:scylla", "0", "ocean"}};
        for (String[] d : def) {
            JsonObject o = new JsonObject();
            o.addProperty("id", d[0]);
            o.addProperty("weight", Integer.parseInt(d[1]));
            o.addProperty("biome", d[2]);
            a.add(o);
        }
        root.add("bosses", a);
        return GSON.toJson(root);
    }

    static boolean biomeMatches(String want, RegistryEntry<Biome> b) {
        return switch (want) {
            case "cold" -> b.value().getTemperature() < 0.2f;
            case "hot" -> b.value().getTemperature() > 1.0f || b.isIn(BiomeTags.IS_BADLANDS) || b.isIn(BiomeTags.IS_SAVANNA);
            case "mountain" -> b.isIn(BiomeTags.IS_MOUNTAIN) || b.isIn(BiomeTags.IS_HILL);
            case "forest" -> b.isIn(BiomeTags.IS_FOREST) || b.isIn(BiomeTags.IS_TAIGA);
            case "swamp" -> b.matchesKey(net.minecraft.world.biome.BiomeKeys.SWAMP) || b.matchesKey(net.minecraft.world.biome.BiomeKeys.MANGROVE_SWAMP);
            case "ocean" -> b.isIn(BiomeTags.IS_OCEAN);
            default -> true;
        };
    }

    static void tick(MinecraftServer server) {
        if (server.getTicks() % (20 * 60 * 5) != 0 || ENTRIES.isEmpty()) return;
        ServerWorld w = server.getOverworld();
        if (!w.getGameRules().getBoolean(AWGameRules.WILD_BOSSES)) return;
        int chance = w.getGameRules().getInt(AWGameRules.WILD_BOSS_CHANCE);
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || p.isCreative() || w.getRandom().nextInt(100) >= chance) continue;
            spawnNear(w, p, null);
        }
    }

    /** 플레이어 근처에 야생 보스 하나 (id 가 null 이면 바이옴에 맞게 고름). @return 소환된 보스 */
    public static Entity spawnNear(ServerWorld w, ServerPlayerEntity p, Identifier forced) {
        // 이미 근처에 보스가 있거나 마을 (수도) 안이면 안 나옴
        if (!w.getEntitiesByClass(MobEntity.class, new Box(p.getBlockPos()).expand(128), e -> com.abilitywar.rpg.RpgManager.isBoss(e)).isEmpty()) return null;
        if (forced == null && com.abilitywar.world.RpgWorld.inSafeZone(w, p.getBlockPos())) return null;
        if (forced == null) {
            // 마을 · 던전 안 (또는 그 근처) 에서는 야생 보스가 안 나옴
            var k = com.abilitywar.world.RpgWorld.kindAt(w, p.getBlockPos());
            if (k != null) return null;
        }
        double a = w.getRandom().nextDouble() * Math.PI * 2;
        double d = 30 + w.getRandom().nextDouble() * 15;
        int x = MathHelper.floor(p.getX() + Math.cos(a) * d), z = MathHelper.floor(p.getZ() + Math.sin(a) * d);
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        Identifier id = forced;
        if (id == null) {
            RegistryEntry<Biome> b = w.getBiome(top);
            List<Entry> ok = new ArrayList<>();
            int total = 0;
            for (Entry e : ENTRIES) if (biomeMatches(e.biome, b)) {
                ok.add(e);
                total += e.weight;
            }
            if (total <= 0) return null;
            int r = w.getRandom().nextInt(total);
            for (Entry e : ok) {
                r -= e.weight;
                if (r < 0) {
                    id = e.id;
                    break;
                }
            }
        }
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        Entity e = type.create(w);
        if (e == null) return null;
        int y = top.getY() + (e instanceof AncientWyvernEntity ? 12 : 0);
        e.refreshPositionAndAngles(top.getX() + 0.5, y, top.getZ() + 0.5, w.getRandom().nextFloat() * 360, 0);
        if (e instanceof MobEntity m) m.initialize(w, w.getLocalDifficulty(top), SpawnReason.EVENT, null, null);
        if (e instanceof BossEntity boss) boss.setWild(true);
        w.spawnEntity(e);
        for (ServerPlayerEntity o : w.getPlayers(o -> o.squaredDistanceTo(e) < 160 * 160))
            o.sendMessage(Text.translatable("boss.abilitywar.wild", e.getDisplayName(), top.getX(), top.getZ()).formatted(Formatting.GOLD, Formatting.BOLD), false);
        return e;
    }

    static void onBossDeath(BossEntity boss) {
        if (boss.home != null && boss.getWorld() instanceof ServerWorld sw) BossAltar.onBossDied(sw, boss.home);
    }

    private BossSpawner() {
    }
}

package com.abilitywar.world;

import com.abilitywar.AbilityWar;
import com.mojang.serialization.Codec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테마 지역 (왕국 밖의 꾸민 숲): 수도를 둘러싼 고리 6곳 (테마마다 하나씩 꼭) + 그 밖의 세상에 흩어진 지역.
 * 지역 하나 = 지름 약 220칸. 테마는 생물군계에 맞춰 고름. 다른 구조물 (마을 · 던전 · 던전 입구) 은 지역 안에 안 생김.
 */
public final class Regions {
    /** 지역 반지름 (블록). 구조물 참조는 시작 청크에서 8청크까지만 닿으므로 R + 가장자리 ≤ 약 125. */
    public static final int R = 108;
    /** 수도에서 이 거리 (청크) 안에는 고리 지역만 (흩어진 지역 없음). */
    static final int RING_CLEAR = 50;
    /** 흩어진 지역 배치 (structure_set/regions.json 과 같아야 함). */
    static final int SPACING = 34, SEPARATION = 16, SALT = 20490;
    static final RandomSpreadStructurePlacement SPREAD = new RandomSpreadStructurePlacement(SPACING, SEPARATION, SpreadType.LINEAR, SALT);

    public enum Theme {
        FAIRY("fairy_glade", 1, 15, null, 0xFF9AE0),
        GOBLIN("goblin_woods", 10, 25, "goblin_warren", 0x8AC050),
        SLIME("slime_woods", 15, 30, "slime_hollow", 0x7CE05A),
        SPIDER("spider_woods", 28, 42, "spider_nest", 0xC8C8D8),
        HAUNTED("haunted_woods", 38, 55, "cult_crypt", 0x9A8CFF),
        CRYSTAL("crystal_woods", 52, 70, "golem_ruins", 0xD088FF);

        public final String key;
        public final int minLv, maxLv, color;
        /** 가운데 던전 입구의 인스턴스 던전 (없으면 null). */
        @Nullable
        public final String dungeon;

        Theme(String key, int minLv, int maxLv, @Nullable String dungeon, int color) {
            this.key = key;
            this.minLv = minLv;
            this.maxLv = maxLv;
            this.dungeon = dungeon;
            this.color = color;
        }

        public static Theme of(int i) {
            Theme[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    public record Spot(int cx, int cz, Theme theme) {
    }

    // ================================================================ 생물군계
    static RegistryEntry<Biome> biome(BiomeSource src, MultiNoiseUtil.MultiNoiseSampler s, int x, int z) {
        return src.getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(72), BiomeCoords.fromBlock(z), s);
    }

    static boolean wet(RegistryEntry<Biome> b) {
        return b.isIn(BiomeTags.IS_OCEAN) || b.isIn(BiomeTags.IS_DEEP_OCEAN) || b.isIn(BiomeTags.IS_RIVER) || b.matchesKey(BiomeKeys.MUSHROOM_FIELDS);
    }

    /** 가운데와 둘레 8곳 (반지름 80) 가운데 물 (바다 · 강) 이 1곳 이하. */
    static boolean land(BiomeSource src, MultiNoiseUtil.MultiNoiseSampler s, int bx, int bz) {
        if (wet(biome(src, s, bx, bz)) || biome(src, s, bx, bz).isIn(BiomeTags.IS_BEACH)) return false;
        int bad = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            if (wet(biome(src, s, bx + (int) (Math.cos(a) * 80), bz + (int) (Math.sin(a) * 80))) && ++bad > 1) return false;
        }
        return true;
    }

    /** 테마가 이 생물군계에 얼마나 어울리는지 (0..3). */
    static int score(Theme t, RegistryEntry<Biome> b) {
        boolean forest = b.isIn(BiomeTags.IS_FOREST), taiga = b.isIn(BiomeTags.IS_TAIGA), jungle = b.isIn(BiomeTags.IS_JUNGLE);
        boolean mountain = b.isIn(BiomeTags.IS_MOUNTAIN) || b.isIn(BiomeTags.IS_HILL), badlands = b.isIn(BiomeTags.IS_BADLANDS), savanna = b.isIn(BiomeTags.IS_SAVANNA);
        boolean swamp = b.matchesKey(BiomeKeys.SWAMP) || b.matchesKey(BiomeKeys.MANGROVE_SWAMP);
        boolean dark = b.matchesKey(BiomeKeys.DARK_FOREST) || b.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA) || b.matchesKey(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
        boolean flowery = b.matchesKey(BiomeKeys.FLOWER_FOREST) || b.matchesKey(BiomeKeys.MEADOW) || b.matchesKey(BiomeKeys.CHERRY_GROVE)
                || b.matchesKey(BiomeKeys.SUNFLOWER_PLAINS) || b.matchesKey(BiomeKeys.BIRCH_FOREST) || b.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST);
        boolean plains = b.matchesKey(BiomeKeys.PLAINS) || b.matchesKey(BiomeKeys.SUNFLOWER_PLAINS);
        boolean cold = b.matchesKey(BiomeKeys.SNOWY_TAIGA) || b.matchesKey(BiomeKeys.SNOWY_PLAINS) || b.matchesKey(BiomeKeys.GROVE) || b.matchesKey(BiomeKeys.SNOWY_SLOPES);
        boolean dry = b.matchesKey(BiomeKeys.DESERT) || badlands;
        return switch (t) {
            case FAIRY -> flowery ? 3 : plains || forest ? 2 : 0;
            case GOBLIN -> forest && !flowery || savanna || plains ? 3 : taiga ? 2 : jungle ? 1 : 0;
            case SLIME -> swamp || jungle ? 3 : b.matchesKey(BiomeKeys.LUSH_CAVES) || forest ? 1 : 0;
            case SPIDER -> dark || jungle ? 3 : taiga || forest ? 1 : 0;
            case HAUNTED -> dark || cold || swamp ? 3 : taiga ? 2 : 0;
            case CRYSTAL -> mountain || dry || cold || b.matchesKey(BiomeKeys.MEADOW) ? 3 : savanna ? 1 : 0;
        };
    }

    static int mix(long seed, int x, int z) {
        long h = seed * 6364136223846793005L + x * 341873128712L + z * 132897987541L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return (int) (h ^ (h >>> 32)) & 0x7fffffff;
    }

    // ================================================================ 고리 (수도 둘레 6곳)
    static final Map<Long, List<Spot>> RING = new ConcurrentHashMap<>();

    public static List<Spot> ring(long seed, BiomeSource src, MultiNoiseUtil.MultiNoiseSampler s) {
        return RING.computeIfAbsent(seed, k -> {
            ChunkPos cap = RpgStructures.CapitalPlacement.locate(seed, src, s);
            java.util.Random r = new java.util.Random(seed ^ 0x5EED2E61L);
            double a0 = r.nextDouble() * Math.PI * 2;
            List<int[]> spots = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                int[] found = null;
                search:
                for (int rad : new int[]{33, 35, 31, 37, 39, 41})
                    for (double da : new double[]{0, 0.13, -0.13, 0.26, -0.26}) {
                        double a = a0 + i * Math.PI / 3 + da;
                        int x = cap.x + (int) Math.round(Math.cos(a) * rad), z = cap.z + (int) Math.round(Math.sin(a) * rad);
                        if (land(src, s, x * 16 + 8, z * 16 + 8)) {
                            found = new int[]{x, z};
                            break search;
                        }
                    }
                if (found != null) spots.add(found);
            }
            // 테마: 남은 테마 가운데 그 자리 생물군계에 가장 어울리는 것 (같으면 섞은 순서)
            List<Theme> left = new ArrayList<>(List.of(Theme.values()));
            Collections.shuffle(left, r);
            List<Spot> out = new ArrayList<>();
            for (int[] p : spots) {
                if (left.isEmpty()) break;
                RegistryEntry<Biome> b = biome(src, s, p[0] * 16 + 8, p[1] * 16 + 8);
                Theme best = left.get(0);
                for (Theme t : left) if (score(t, b) > score(best, b)) best = t;
                left.remove(best);
                out.add(new Spot(p[0], p[1], best));
            }
            AbilityWar.LOG.info("[rpg] regions around the capital: {}", out);
            return List.copyOf(out);
        });
    }

    @Nullable
    static Spot ringAt(long seed, BiomeSource src, MultiNoiseUtil.MultiNoiseSampler s, int cx, int cz) {
        for (Spot sp : ring(seed, src, s)) if (sp.cx == cx && sp.cz == cz) return sp;
        return null;
    }

    // ================================================================ 흩어진 지역
    static final Map<Long, Optional<Theme>> SPREAD_THEME = new ConcurrentHashMap<>();

    /** 흩어진 지역 시작 청크의 테마 (못 지으면 null): 수도 고리 밖 · 땅 · 생물군계에 맞는 테마 중 하나. */
    @Nullable
    static Theme spreadTheme(long seed, BiomeSource src, MultiNoiseUtil.MultiNoiseSampler s, int cx, int cz) {
        long key = seed * 31 + ChunkPos.toLong(cx, cz);
        if (SPREAD_THEME.size() > 4096) SPREAD_THEME.clear();
        return SPREAD_THEME.computeIfAbsent(key, k -> {
            ChunkPos cap = RpgStructures.CapitalPlacement.locate(seed, src, s);
            if (Math.max(Math.abs(cap.x - cx), Math.abs(cap.z - cz)) < RING_CLEAR) return Optional.empty();
            int bx = cx * 16 + 8, bz = cz * 16 + 8;
            if (!land(src, s, bx, bz)) return Optional.empty();
            RegistryEntry<Biome> b = biome(src, s, bx, bz);
            List<Theme> good = new ArrayList<>();
            for (Theme t : Theme.values()) if (score(t, b) >= 2) good.add(t);
            if (good.isEmpty()) for (Theme t : Theme.values()) if (score(t, b) >= 1) good.add(t);
            if (good.isEmpty()) good.addAll(List.of(Theme.values()));
            return Optional.of(good.get(mix(seed, cx, cz) % good.size()));
        }).orElse(null);
    }

    /** 이 시작 청크에 지역이 생기면 그 테마 (고리 자리 → 고리 테마, 아니면 흩어진 지역 규칙). */
    @Nullable
    public static Theme themeAt(long seed, BiomeSource src, MultiNoiseUtil.MultiNoiseSampler s, int cx, int cz) {
        Spot sp = ringAt(seed, src, s, cx, cz);
        if (sp != null) return sp.theme;
        return spreadTheme(seed, src, s, cx, cz);
    }

    /** (cx, cz) 청크 가운데에서 pad 칸 안에 지역 (가장자리) 이 걸치는지. 마을 · 던전 · 던전 입구가 지역 안에 안 생기게. */
    public static boolean blocked(long seed, BiomeSource src, MultiNoiseUtil.MultiNoiseSampler s, int cx, int cz, int pad) {
        double lim = R + 12 + pad;
        for (Spot sp : ring(seed, src, s)) if (dist(sp.cx, sp.cz, cx, cz) < lim) return true;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos st = SPREAD.getStartChunk(seed, cx + dx * SPACING, cz + dz * SPACING);
                if (dist(st.x, st.z, cx, cz) < lim && spreadTheme(seed, src, s, st.x, st.z) != null) return true;
            }
        return false;
    }

    static double dist(int ax, int az, int bx, int bz) {
        return Math.hypot((ax - bx) * 16.0, (az - bz) * 16.0);
    }

    // ================================================================ 고리 배치
    public static final class RingPlacement extends StructurePlacement {
        public static final RingPlacement INSTANCE = new RingPlacement();
        public static final Codec<RingPlacement> CODEC = Codec.unit(() -> INSTANCE);

        RingPlacement() {
            super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1f, 0, Optional.empty());
        }

        @Override
        protected boolean isStartChunk(StructurePlacementCalculator calc, int chunkX, int chunkZ) {
            var src = ((com.abilitywar.mixin.StructurePlacementCalculatorAccessor) calc).abilitywar$getBiomeSource();
            return ringAt(calc.getStructureSeed(), src, calc.getNoiseConfig().getMultiNoiseSampler(), chunkX, chunkZ) != null;
        }

        @Override
        public StructurePlacementType<?> getType() {
            return RING_PLACEMENT;
        }
    }

    public static final StructurePlacementType<RingPlacement> RING_PLACEMENT = Registry.register(Registries.STRUCTURE_PLACEMENT, AbilityWar.id("region_ring"), () -> RingPlacement.CODEC);

    static void init() {
        AbilityWar.LOG.info("[rpg] region placements: {}", Registries.STRUCTURE_PLACEMENT.getId(RING_PLACEMENT));
    }

    private Regions() {
    }
}

package com.abilitywar.world;

import com.abilitywar.AbilityWar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementType;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPG 구조물: 수도 (월드 처음 땅 = 스폰), 마을, 던전 4종 (보스 제단), 고블린 야영지.
 * 구조물 하나 = 조각 하나 (RpgPiece). 조각은 종류·위치·시드만 저장하고, 설계도 (Blueprint) 는 필요할 때 다시 만든다.
 */
public final class RpgStructures {
    // ================================================================ 종류
    public enum Kind {
        CAPITAL(118, 70), TOWN(40, 34), FALLEN_FORTRESS(30, 30), EMBER_ALTAR(26, 30), FROST_TEMPLE(26, 34), WYVERN_ROOST(22, 30), GOBLIN_CAMP(14, 12);

        /** 반지름 (가로·세로), 높이 (바닥 위). */
        public final int radius, height;

        Kind(int radius, int height) {
            this.radius = radius;
            this.height = height;
        }

        static Kind of(String s) {
            return valueOf(s.toUpperCase());
        }
    }

    static final Map<String, Blueprint> CACHE = new ConcurrentHashMap<>();

    static Blueprint blueprint(Kind kind, int x, int y, int z, long seed) {
        String key = kind + ":" + x + ":" + y + ":" + z + ":" + seed;
        Blueprint b = CACHE.get(key);
        if (b != null) return b;
        if (CACHE.size() > 48) CACHE.clear();
        java.util.Random r = new java.util.Random(seed ^ ((long) x * 341873128712L + (long) z * 132897987541L));
        Blueprint nb = new Blueprint();
        switch (kind) {
            case CAPITAL -> CapitalPlan.build(nb, x, y, z, r);
            case TOWN -> TownPlan.build(nb, x, y, z, r);
            default -> DungeonPlans.build(kind, nb, x, y, z, r);
        }
        nb.finish();
        CACHE.put(key, nb);
        return nb;
    }

    // ================================================================ 조각
    public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE, AbilityWar.id("rpg_piece"), (StructurePieceType) RpgPiece::new);

    public static final class RpgPiece extends StructurePiece {
        final Kind kind;
        final int x, y, z;
        final long seed;

        RpgPiece(Kind kind, int x, int y, int z, long seed, BlockBox box) {
            super(PIECE, 0, box);
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.seed = seed;
        }

        RpgPiece(StructureContext ctx, NbtCompound nbt) {
            super(PIECE, nbt);
            this.kind = Kind.of(nbt.getString("Kind"));
            this.x = nbt.getInt("X");
            this.y = nbt.getInt("Y");
            this.z = nbt.getInt("Z");
            this.seed = nbt.getLong("Seed");
        }

        @Override
        protected void writeNbt(StructureContext context, NbtCompound nbt) {
            nbt.putString("Kind", kind.name().toLowerCase());
            nbt.putInt("X", x);
            nbt.putInt("Y", y);
            nbt.putInt("Z", z);
            nbt.putLong("Seed", seed);
        }

        @Override
        public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator gen, Random random, BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            blueprint(kind, x, y, z, seed).place(world, chunkBox, random);
        }
    }

    // ================================================================ 구조물
    public static final class RpgStructure extends Structure {
        public static final Codec<RpgStructure> CODEC = RecordCodecBuilder.create(i -> i.group(configCodecBuilder(i),
                Codec.STRING.fieldOf("kind").forGetter(s -> s.kind.name().toLowerCase())).apply(i, (c, k) -> new RpgStructure(c, Kind.of(k))));
        final Kind kind;

        public RpgStructure(Config config, Kind kind) {
            super(config);
            this.kind = kind;
        }

        @Override
        protected Optional<StructurePosition> getStructurePosition(Context ctx) {
            int cx = ctx.chunkPos().getStartX() + 8, cz = ctx.chunkPos().getStartZ() + 8;
            int r = kind.radius;
            // 수도 둘레 (반지름 13청크) 에는 다른 구조물을 짓지 않음
            if (kind != Kind.CAPITAL) {
                ChunkPos cap = CapitalPlacement.locate(ctx.seed(), ctx.biomeSource(), ctx.noiseConfig().getMultiNoiseSampler());
                if (Math.max(Math.abs(cap.x - ctx.chunkPos().x), Math.abs(cap.z - ctx.chunkPos().z)) < 13) return Optional.empty();
            }
            ChunkGenerator gen = ctx.chunkGenerator();
            int[] hs = new int[9];
            int i = 0;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    hs[i++] = gen.getHeightOnGround(cx + dx * r * 2 / 3, cz + dz * r * 2 / 3, Heightmap.Type.WORLD_SURFACE_WG, ctx.world(), ctx.noiseConfig());
            Arrays.sort(hs);
            int sea = gen.getSeaLevel();
            int y = kind == Kind.CAPITAL ? Math.max(sea + 2, (hs[3] + hs[4] + hs[5]) / 3) : hs[4];
            // 물 위·너무 울퉁불퉁한 곳은 건너뜀 (수도는 어디든 지음)
            if (kind != Kind.CAPITAL) {
                if (hs[4] <= sea) return Optional.empty();
                if (kind != Kind.WYVERN_ROOST && hs[8] - hs[0] > 28) return Optional.empty();
            }
            if (kind == Kind.WYVERN_ROOST) y = hs[6];
            final int baseY = y;
            BlockBox box = new BlockBox(cx - r - 2, baseY + 1, cz - r - 2, cx + r + 2, baseY + kind.height, cz + r + 2);
            long seed = ctx.seed();
            return Optional.of(new StructurePosition(new BlockPos(cx, baseY, cz), collector -> {
                // 실제 범위 = 설계도 범위 (지붕·땅 고르기 포함)
                BlockBox bb = blueprint(kind, cx, baseY, cz, seed).bounds();
                BlockBox pieceBox = new BlockBox(bb.getMinX(), baseY + 1, bb.getMinZ(), bb.getMaxX(), Math.max(bb.getMaxY(), baseY + 2), bb.getMaxZ());
                collector.addPiece(new RpgPiece(kind, cx, baseY, cz, seed, pieceBox.getBlockCountX() > 0 ? pieceBox : box));
            }));
        }

        @Override
        public StructureType<?> getType() {
            return TYPE;
        }
    }

    public static final StructureType<RpgStructure> TYPE = Registry.register(Registries.STRUCTURE_TYPE, AbilityWar.id("rpg"), () -> RpgStructure.CODEC);

    // ================================================================ 수도 자리: 처음 땅 (원점에서 가장 가까운 평평한 육지)
    public static final class CapitalPlacement extends StructurePlacement {
        public static final CapitalPlacement INSTANCE = new CapitalPlacement();
        public static final Codec<CapitalPlacement> CODEC = Codec.unit(() -> INSTANCE);
        static final Map<Long, ChunkPos> FOUND = new ConcurrentHashMap<>();

        CapitalPlacement() {
            super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1f, 0, Optional.empty());
        }

        @Override
        protected boolean isStartChunk(StructurePlacementCalculator calc, int chunkX, int chunkZ) {
            ChunkPos c = locate(calc);
            return c.x == chunkX && c.z == chunkZ;
        }

        @Override
        public StructurePlacementType<?> getType() {
            return PLACEMENT;
        }

        static boolean good(RegistryEntry<Biome> b) {
            return !b.isIn(BiomeTags.IS_OCEAN) && !b.isIn(BiomeTags.IS_RIVER) && !b.isIn(BiomeTags.IS_BEACH) && !b.isIn(BiomeTags.IS_MOUNTAIN)
                    && !b.matchesKey(BiomeKeys.SWAMP) && !b.matchesKey(BiomeKeys.MANGROVE_SWAMP) && !b.matchesKey(BiomeKeys.STONY_SHORE)
                    && !b.matchesKey(BiomeKeys.WINDSWEPT_HILLS) && !b.matchesKey(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS) && !b.matchesKey(BiomeKeys.WINDSWEPT_FOREST)
                    && !b.matchesKey(BiomeKeys.DARK_FOREST) && !b.matchesKey(BiomeKeys.MUSHROOM_FIELDS);
        }

        public static ChunkPos locate(StructurePlacementCalculator calc) {
            return locate(calc.getStructureSeed(), ((com.abilitywar.mixin.StructurePlacementCalculatorAccessor) calc).abilitywar$getBiomeSource(), calc.getNoiseConfig().getMultiNoiseSampler());
        }

        public static ChunkPos locate(long seed, net.minecraft.world.biome.source.BiomeSource src, net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler sampler) {
            return FOUND.computeIfAbsent(seed, s -> {
                // 원점부터 나선으로 (8청크 간격) 찾음: 가운데 + 둘레 8곳이 모두 좋은 땅
                for (int ring = 0; ring <= 40; ring++) {
                    for (int dx = -ring; dx <= ring; dx++)
                        for (int dz = -ring; dz <= ring; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                            int cx = dx * 8, cz = dz * 8;
                            int bx = cx * 16 + 8, bz = cz * 16 + 8;
                            boolean ok = true;
                            for (int k = -1; k <= 8 && ok; k++) {
                                int px = bx, pz = bz;
                                if (k >= 0) {
                                    double a = k * Math.PI / 4;
                                    px += (int) (Math.cos(a) * 120);
                                    pz += (int) (Math.sin(a) * 120);
                                }
                                ok = good(src.getBiome(BiomeCoords.fromBlock(px), BiomeCoords.fromBlock(72), BiomeCoords.fromBlock(pz), sampler));
                            }
                            if (ok) return new ChunkPos(cx, cz);
                        }
                }
                return new ChunkPos(0, 0);
            });
        }
    }

    public static final StructurePlacementType<CapitalPlacement> PLACEMENT = Registry.register(Registries.STRUCTURE_PLACEMENT, AbilityWar.id("capital"), () -> CapitalPlacement.CODEC);

    public static void init() {
        RpgWorld.init();
        AbilityWar.LOG.info("[rpg] structures: {} {} {}", Registries.STRUCTURE_TYPE.getId(TYPE), Registries.STRUCTURE_PIECE.getId(PIECE), Registries.STRUCTURE_PLACEMENT.getId(PLACEMENT));
    }

    // ================================================================ 구조물 청크의 식생 막기 (ChunkGeneratorMixin)
    static final Map<net.minecraft.world.biome.source.BiomeSource, java.util.Set<net.minecraft.world.gen.feature.PlacedFeature>> VEGETATION =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static boolean suppressFeature(net.minecraft.world.chunk.Chunk chunk, net.minecraft.world.gen.feature.PlacedFeature f, ChunkGenerator gen) {
        boolean rpg = false;
        for (Structure st : chunk.getStructureReferences().keySet())
            if (st instanceof RpgStructure) {
                rpg = true;
                break;
            }
        if (!rpg) return false;
        return VEGETATION.computeIfAbsent(gen.getBiomeSource(), src -> {
            java.util.Set<net.minecraft.world.gen.feature.PlacedFeature> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            int step = net.minecraft.world.gen.GenerationStep.Feature.VEGETAL_DECORATION.ordinal();
            for (RegistryEntry<Biome> b : src.getBiomes()) {
                var feats = b.value().getGenerationSettings().getFeatures();
                if (feats.size() > step) for (var e : feats.get(step)) set.add(e.value());
            }
            return set;
        }).contains(f);
    }

    private RpgStructures() {
    }
}

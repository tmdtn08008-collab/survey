package com.abilitywar.world;

import com.abilitywar.rpg.DungeonGateBlock;
import com.abilitywar.rpg.InstanceDungeon;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Random;

import static com.abilitywar.world.Build.s;

/**
 * 야생의 던전 입구: 돌아다니다 보면 가끔 나오는 작은 유적 (테마마다 모양이 다름) + 소용돌이 문 (DungeonGateBlock).
 * 고블린 · 슬라임 · 거미 = 흙더미 굴, 해적 = 난파 아치, 광신도 = 지하 묘실, 골렘 = 무너진 석문, 용 = 송곳니 아치.
 */
public final class GatePlan {
    /** 이 자리 생물군계에 맞는 던전 (해변 → 해적, 늪 · 정글 → 슬라임, 어두운 숲 → 거미 · 광신도, 산 → 골렘, 메사 · 사막 → 용, 그 밖 → 고블린 · 슬라임 · 거미). */
    public static InstanceDungeon.Theme themeFor(RegistryEntry<Biome> b, int hash) {
        InstanceDungeon.Theme[] pick;
        if (b.isIn(BiomeTags.IS_BEACH) || b.isIn(BiomeTags.IS_OCEAN)) pick = new InstanceDungeon.Theme[]{InstanceDungeon.Theme.PIRATE};
        else if (b.matchesKey(BiomeKeys.SWAMP) || b.matchesKey(BiomeKeys.MANGROVE_SWAMP) || b.isIn(BiomeTags.IS_JUNGLE))
            pick = new InstanceDungeon.Theme[]{InstanceDungeon.Theme.SLIME, InstanceDungeon.Theme.SLIME, InstanceDungeon.Theme.SPIDER};
        else if (b.matchesKey(BiomeKeys.DARK_FOREST) || b.matchesKey(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA) || b.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA))
            pick = new InstanceDungeon.Theme[]{InstanceDungeon.Theme.SPIDER, InstanceDungeon.Theme.CULT};
        else if (b.isIn(BiomeTags.IS_BADLANDS) || b.matchesKey(BiomeKeys.DESERT) || b.matchesKey(BiomeKeys.SAVANNA_PLATEAU) || b.matchesKey(BiomeKeys.WINDSWEPT_SAVANNA))
            pick = new InstanceDungeon.Theme[]{InstanceDungeon.Theme.DRAGON, InstanceDungeon.Theme.GOLEM};
        else if (b.isIn(BiomeTags.IS_MOUNTAIN) || b.isIn(BiomeTags.IS_HILL) || b.matchesKey(BiomeKeys.SNOWY_PLAINS) || b.matchesKey(BiomeKeys.SNOWY_TAIGA))
            pick = new InstanceDungeon.Theme[]{InstanceDungeon.Theme.GOLEM, InstanceDungeon.Theme.CULT, InstanceDungeon.Theme.DRAGON};
        else if (b.isIn(BiomeTags.IS_TAIGA))
            pick = new InstanceDungeon.Theme[]{InstanceDungeon.Theme.CULT, InstanceDungeon.Theme.GOBLIN, InstanceDungeon.Theme.SPIDER};
        else pick = new InstanceDungeon.Theme[]{InstanceDungeon.Theme.GOBLIN, InstanceDungeon.Theme.GOBLIN, InstanceDungeon.Theme.SLIME, InstanceDungeon.Theme.SPIDER, InstanceDungeon.Theme.CULT};
        return pick[Math.floorMod(hash, pick.length)];
    }

    /** 야생 입구 하나: 땅 고르기 + 테마 모양. */
    public static void build(InstanceDungeon.Theme t, Blueprint b, int cx, int by, int cz, Random r) {
        Direction f = Direction.fromHorizontal(r.nextInt(4));
        BlockState ground = switch (t) {
            case PIRATE -> s(Blocks.SAND);
            case SLIME -> s(Blocks.MOSS_BLOCK);
            case SPIDER -> s(Blocks.PODZOL);
            case CULT -> s(Blocks.PODZOL);
            case GOLEM -> s(Blocks.GRASS_BLOCK);
            case DRAGON -> s(Blocks.COARSE_DIRT);
            default -> s(Blocks.COARSE_DIRT);
        };
        b.terraformCircle(cx, cz, 6, 5, by, 12, ground, s(Blocks.DIRT));
        site(t, b, cx, by, cz, f, r, true);
    }

    /** 입구 모양 (터는 이미 고른 곳). sign = 앞에 간판. 지역 가운데에서도 씀. */
    public static void site(InstanceDungeon.Theme t, Blueprint b, int cx, int by, int cz, Direction f, Random r, boolean sign) {
        L l = new L(cx, cz, f);
        switch (t) {
            case GOBLIN -> mound(b, l, by, t, r, new Block[]{Blocks.STONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.ANDESITE}, Blocks.COARSE_DIRT);
            case SLIME -> mound(b, l, by, t, r, new Block[]{Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.MOSSY_COBBLESTONE}, Blocks.MUD);
            case SPIDER -> mound(b, l, by, t, r, new Block[]{Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL}, Blocks.GRAVEL);
            case PIRATE -> wreck(b, l, by, r);
            case CULT -> crypt(b, l, by, r);
            case GOLEM -> ruinArch(b, l, by, r);
            case DRAGON -> fangs(b, l, by, r);
        }
        if (sign) {
            int[] sp = l.at(-3, -4);
            b.fill(sp[0], by + 1, sp[1], sp[0], by + 2, sp[1], s(Blocks.SPRUCE_LOG));
            int[] sg = l.at(-3, -5);
            b.signs.add(new Blueprint.Sign(new BlockPos(sg[0], by + 2, sg[1]), f, new String[]{"gate.abilitywar.sign.top", "dungeon.abilitywar.name." + t.key,
                    "gate.abilitywar.sign.lv." + t.key, "gate.abilitywar.sign.use"}));
        }
    }

    /** 앞 (f) 을 보는 자리의 지역 좌표: u = 옆 (오른쪽 +), v = 안쪽 (뒤 +). */
    record L(int cx, int cz, Direction f) {
        int[] at(int u, int v) {
            Direction side = f.rotateYClockwise();
            return new int[]{cx + side.getOffsetX() * u - f.getOffsetX() * v, cz + side.getOffsetZ() * u - f.getOffsetZ() * v};
        }

        void set(Blueprint b, int u, int y, int v, BlockState st) {
            int[] p = at(u, v);
            b.set(p[0], y, p[1], st);
        }

        void shape(Shape sh, int u, int y, int v, BlockState st) {
            int[] p = at(u, v);
            sh.set(p[0], y, p[1], st);
        }

        Direction.Axis axis() {
            return f.rotateYClockwise().getAxis();
        }
    }

    /** 소용돌이 문 2 × 3 (u = 0..1, v) + 둘레 틀. */
    static void portal(Blueprint b, L l, int by, int v, InstanceDungeon.Theme t, Block frame) {
        for (int u = -1; u <= 2; u++)
            for (int h = 0; h <= 4; h++) {
                boolean edge = u == -1 || u == 2 || h == 0 || h == 4;
                l.set(b, u, by + h, v, edge ? s(frame) : DungeonGateBlock.state(t, l.axis()));
            }
    }

    // ================================================================ 흙더미 굴 (고블린 · 슬라임 · 거미)
    static void mound(Blueprint b, L l, int by, InstanceDungeon.Theme t, Random r, Block[] mats, Block floor) {
        Shape sh = new Shape();
        double rx = 5.5, ry = 5, rz = 5;
        for (int u = -7; u <= 8; u++)
            for (int v = -2; v <= 9; v++)
                for (int h = -1; h <= 6; h++) {
                    double du = (u - 0.5) / rx, dv = (v - 3.5) / rz, dh = (h + 1) / ry;
                    double d = du * du + dv * dv + dh * dh;
                    if (d > 1 || (d > 0.75 && r.nextFloat() < 0.35)) continue;
                    Block m = mats[r.nextInt(mats.length)];
                    if (t == InstanceDungeon.Theme.SPIDER && d > 0.6 && r.nextFloat() < 0.18) m = Blocks.COBWEB;
                    if (t == InstanceDungeon.Theme.SLIME && d > 0.7 && r.nextFloat() < 0.12) m = Blocks.SLIME_BLOCK;
                    l.shape(sh, u, by + h, v, s(m));
                }
        // 굴: 앞에서 문까지 (2칸 × 3칸)
        for (int v = -3; v <= 2; v++)
            for (int u = 0; u <= 1; u++) {
                l.shape(sh, u, by, v, s(floor));
                for (int h = 1; h <= 3; h++) l.shape(sh, u, by + h, v, s(Blocks.AIR));
            }
        sh.addTo(b);
        portal(b, l, by, 2, t, t == InstanceDungeon.Theme.SPIDER ? Blocks.POLISHED_DEEPSLATE : t == InstanceDungeon.Theme.SLIME ? Blocks.MUD_BRICKS : Blocks.MOSSY_STONE_BRICKS);
        // 입구 받침 (나무 기둥 + 들보)
        Block post = t == InstanceDungeon.Theme.SLIME ? Blocks.MANGROVE_LOG : t == InstanceDungeon.Theme.SPIDER ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.SPRUCE_LOG;
        Block beam = t == InstanceDungeon.Theme.SLIME ? Blocks.MANGROVE_PLANKS : t == InstanceDungeon.Theme.SPIDER ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS;
        for (int h = 1; h <= 3; h++) {
            l.set(b, -1, by + h, -1, s(post));
            l.set(b, 2, by + h, -1, s(post));
        }
        for (int u = -1; u <= 2; u++) l.set(b, u, by + 4, -1, s(beam));
        // 앞마당 꾸미기
        switch (t) {
            case GOBLIN -> {
                for (int u : new int[]{-3, 4}) {
                    l.set(b, u, by + 1, -3, s(Blocks.SPRUCE_FENCE));
                    l.set(b, u, by + 2, -3, s(Blocks.SPRUCE_FENCE));
                    l.set(b, u, by + 3, -3, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
                }
                l.set(b, 4, by + 1, -6, s(Blocks.CAMPFIRE));
                l.set(b, -4, by + 1, -6, s(Blocks.BONE_BLOCK));
                l.set(b, 5, by + 1, -5, s(Blocks.BARREL));
            }
            case SLIME -> {
                for (int u : new int[]{-3, 4}) {
                    l.set(b, u, by + 1, -3, s(Blocks.MOSSY_COBBLESTONE_WALL));
                    l.set(b, u, by + 2, -3, s(Blocks.VERDANT_FROGLIGHT));
                }
                l.set(b, -4, by + 1, -6, s(Blocks.SLIME_BLOCK));
                l.set(b, 5, by + 1, -5, s(Blocks.SLIME_BLOCK));
                l.set(b, 5, by + 2, -5, s(Blocks.SLIME_BLOCK));
            }
            default -> {
                for (int u : new int[]{-3, 4}) {
                    l.set(b, u, by + 1, -3, s(Blocks.BONE_BLOCK));
                    l.set(b, u, by + 2, -3, s(Blocks.COBWEB));
                }
                l.set(b, -4, by + 1, -6, s(Blocks.WHITE_WOOL));
                l.set(b, -4, by + 2, -6, s(Blocks.COBWEB));
                l.set(b, 5, by + 1, -5, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
            }
        }
    }

    // ================================================================ 해적: 난파선 아치
    static void wreck(Blueprint b, L l, int by, Random r) {
        for (int u = -3; u <= 4; u++)
            for (int v = -2; v <= 4; v++) l.set(b, u, by, v, s(r.nextFloat() < 0.3f ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE));
        portal(b, l, by, 1, InstanceDungeon.Theme.PIRATE, Blocks.CUT_SANDSTONE);
        for (int h = 1; h <= 6; h++) {
            l.set(b, -2, by + h, 1, s(Blocks.DARK_OAK_LOG));
            l.set(b, 3, by + h, 1, s(Blocks.DARK_OAK_LOG));
        }
        for (int u = -3; u <= 4; u++) l.set(b, u, by + 6, 1, s(Blocks.DARK_OAK_PLANKS));
        l.set(b, -3, by + 7, 1, s(Blocks.DARK_OAK_FENCE));
        l.set(b, 4, by + 7, 1, s(Blocks.DARK_OAK_FENCE));
        l.set(b, 0, by + 5, 0, s(Blocks.CHAIN));
        l.set(b, 1, by + 5, 0, s(Blocks.LANTERN).with(LanternBlock.HANGING, true));
        // 갈비뼈 같은 배 늑재
        for (int k = 0; k < 3; k++) {
            int v = 3 + k * 2;
            for (int h = 1; h <= 4 - k; h++) {
                l.set(b, -3 + (h > 3 ? 1 : 0), by + h, v, s(Blocks.STRIPPED_DARK_OAK_LOG));
                l.set(b, 4 - (h > 3 ? 1 : 0), by + h, v, s(Blocks.STRIPPED_DARK_OAK_LOG));
            }
        }
        // 닻 (쇠사슬 + 쇠 블록)
        l.set(b, 6, by + 1, -2, s(Blocks.IRON_BLOCK));
        l.set(b, 5, by + 1, -2, s(Blocks.IRON_BARS));
        l.set(b, 7, by + 1, -2, s(Blocks.IRON_BARS));
        for (int h = 2; h <= 4; h++) l.set(b, 6, by + h, -2, s(Blocks.CHAIN));
        l.set(b, -5, by + 1, -2, s(Blocks.BARREL));
        l.set(b, -5, by + 1, -3, s(Blocks.BARREL));
        l.set(b, -5, by + 2, -2, s(Blocks.SPRUCE_PLANKS));
    }

    // ================================================================ 광신도: 지하 묘실
    static void crypt(Blueprint b, L l, int by, Random r) {
        for (int u = -3; u <= 4; u++)
            for (int v = 0; v <= 5; v++)
                for (int h = 0; h <= 5; h++) {
                    boolean wall = u == -3 || u == 4 || v == 0 || v == 5 || h == 0 || h == 5;
                    BlockState st = !wall ? s(Blocks.AIR) : h == 5 ? s(Blocks.DEEPSLATE_TILES) : r.nextFloat() < 0.2f ? s(Blocks.CRACKED_DEEPSLATE_BRICKS) : s(Blocks.DEEPSLATE_BRICKS);
                    l.set(b, u, by + h, v, st);
                }
        // 지붕 꼭대기 + 창
        for (int u = -2; u <= 3; u++) l.set(b, u, by + 6, 2, s(Blocks.DEEPSLATE_BRICK_SLAB));
        for (int v = 2; v <= 3; v++) {
            l.set(b, -3, by + 3, v, s(Blocks.IRON_BARS));
            l.set(b, 4, by + 3, v, s(Blocks.IRON_BARS));
        }
        portal(b, l, by, 0, InstanceDungeon.Theme.CULT, Blocks.CHISELED_DEEPSLATE);
        l.set(b, 0, by + 4, 3, s(Blocks.SOUL_LANTERN).with(LanternBlock.HANGING, true));
        for (int u : new int[]{-2, 3}) {
            l.set(b, u, by + 1, -1, s(Blocks.DEEPSLATE_BRICK_WALL));
            l.set(b, u, by + 2, -1, s(Blocks.SOUL_LANTERN));
        }
        // 앞 무덤 둘
        for (int u : new int[]{-3, 4}) {
            l.set(b, u, by + 1, -4, s(Blocks.COBBLED_DEEPSLATE_WALL));
            l.set(b, u, by, -5, s(Blocks.PODZOL));
            l.set(b, u, by + 1, -5, s(Blocks.CANDLE).with(CandleBlock.CANDLES, 1 + r.nextInt(3)).with(CandleBlock.LIT, true));
        }
    }

    // ================================================================ 골렘: 무너진 석문
    static void ruinArch(Blueprint b, L l, int by, Random r) {
        for (int u = -4; u <= 5; u++)
            for (int v = -2; v <= 3; v++) if (r.nextFloat() < 0.8f) l.set(b, u, by, v, s(r.nextFloat() < 0.3f ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS));
        portal(b, l, by, 1, InstanceDungeon.Theme.GOLEM, Blocks.POLISHED_ANDESITE);
        int hl = 7, hr = 5 + r.nextInt(2);
        for (int h = 1; h <= hl; h++)
            for (int v = 0; v <= 2; v++) l.set(b, -3, by + h, v, s(h == hl ? Blocks.CHISELED_STONE_BRICKS : Build.hash(h, v, 3) % 3 == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS));
        for (int h = 1; h <= hr; h++)
            for (int v = 0; v <= 2; v++) l.set(b, 4, by + h, v, s(Build.hash(h, v, 4) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS));
        for (int u = -3; u <= 1; u++) l.set(b, u, by + hl, 1, s(Blocks.STONE_BRICKS));
        l.set(b, 2, by + hl - 1, 1, s(Blocks.STONE_BRICK_STAIRS));
        // 쓰러진 골렘 석상 (머리 + 몸)
        l.set(b, -6, by + 1, -2, s(Blocks.IRON_BLOCK));
        l.set(b, -7, by + 1, -2, s(Blocks.IRON_BLOCK));
        l.set(b, -8, by + 1, -2, s(Blocks.CARVED_PUMPKIN));
        l.set(b, 6, by + 1, -3, s(Blocks.CRACKED_STONE_BRICKS));
        l.set(b, 6, by + 1, -4, s(Blocks.STONE_BRICK_SLAB));
        l.set(b, 0, by + 1, -3, s(Blocks.LANTERN));
    }

    // ================================================================ 용: 송곳니 아치
    static void fangs(Blueprint b, L l, int by, Random r) {
        for (int u = -4; u <= 5; u++)
            for (int v = -3; v <= 3; v++) l.set(b, u, by, v, s(r.nextFloat() < 0.18f ? Blocks.MAGMA_BLOCK : r.nextFloat() < 0.5f ? Blocks.BLACKSTONE : Blocks.BASALT));
        portal(b, l, by, 1, InstanceDungeon.Theme.DRAGON, Blocks.CRYING_OBSIDIAN);
        // 휘어 들어오는 송곳니 두 개
        for (int h = 1; h <= 8; h++) {
            int in = h >= 6 ? h - 5 : 0;
            l.set(b, -2 + in, by + h, 1, s(h >= 7 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.BLACKSTONE));
            l.set(b, 3 - in, by + h, 1, s(h >= 7 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.BLACKSTONE));
        }
        for (int u : new int[]{-4, 5})
            for (int h = 1; h <= 3 + r.nextInt(3); h++) l.set(b, u, by + h, -1, s(Blocks.BASALT));
        l.set(b, -4, by + 1, -3, s(Blocks.GILDED_BLACKSTONE));
        l.set(b, 5, by + 1, -3, s(Blocks.GOLD_BLOCK));
        l.set(b, 0, by + 1, -3, s(Blocks.BONE_BLOCK));
        l.set(b, 1, by + 1, -4, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
    }

    private GatePlan() {
    }
}

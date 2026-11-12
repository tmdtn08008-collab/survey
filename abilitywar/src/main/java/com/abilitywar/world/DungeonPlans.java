package com.abilitywar.world;

import com.abilitywar.AbilityWar;
import com.abilitywar.mob.AWMobs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

import static com.abilitywar.world.Build.s;

/**
 * 던전 5종 (보스 제단 + 몹 생성기 + 상자):
 * 몰락한 요새 (몰락한 왕) · 잿불 제단 (잿불 거신) · 서리 신전 (서리 마녀) · 고룡 둥지 (고대 와이번) · 고블린 야영지.
 */
public final class DungeonPlans {
    public static void build(RpgStructures.Kind kind, Blueprint b, int x, int y, int z, Random r) {
        switch (kind) {
            case FALLEN_FORTRESS -> fortress(b, x, y, z, r);
            case EMBER_ALTAR -> ember(b, x, y, z, r);
            case FROST_TEMPLE -> frost(b, x, y, z, r);
            case WYVERN_ROOST -> roost(b, x, y, z, r);
            case GOBLIN_CAMP -> camp(b, x, y, z, r);
            case DRAGON_NEST -> nest(b, x, y, z, r);
            default -> {
            }
        }
    }

    // ================================================================ 공통
    static int sq(int v) {
        return v * v;
    }

    static int h100(int x, int y, int z) {
        return Math.floorMod(Build.hash(x, y, z), 100);
    }

    /** 무게 (w) 대로 섞은 블록. */
    static BlockState mix(int x, int y, int z, BlockState[] opts, int[] w) {
        int total = 0;
        for (int v : w) total += v;
        int h = Math.floorMod(Build.hash(x, y * 31 + 7, z), total);
        for (int i = 0; i < opts.length; i++) {
            h -= w[i];
            if (h < 0) return opts[i];
        }
        return opts[0];
    }

    static void ground(Blueprint b, int cx, int cz, int by, int R, BlockState[] opts, int[] w) {
        b.fn(cx - R, by, cz - R, cx + R, by, cz + R, (x, y, z) -> sq(x - cx) + sq(z - cz) <= R * R ? mix(x, y, z, opts, w) : null);
    }

    static void circleTerraform(Blueprint b, int cx, int cz, int by, int R, int clear, BlockState top, BlockState sub) {
        b.terraformCircle(cx, cz, R, 9, by, clear, top, sub);
    }

    static void spawner(Blueprint b, int x, int y, int z, EntityType<?> t) {
        b.spawners.add(new Blueprint.Spawner(new BlockPos(x, y, z), t));
    }

    static void chest(Blueprint b, int x, int y, int z, Direction f, int tier) {
        b.chests.add(new Blueprint.Chest(new BlockPos(x, y, z), f, tier));
    }

    static void altar(Blueprint b, int x, int y, int z, String boss) {
        b.altars.add(new Blueprint.Altar(new BlockPos(x, y, z), AbilityWar.id(boss)));
    }

    static void mob(Blueprint b, int x, int y, int z, EntityType<?> t) {
        b.spawns.add(new Blueprint.Spawn(new BlockPos(x, y, z), t::create));
    }

    /** 부서진 원형 탑: 껍데기 + 층 + 꼭대기 들쭉날쭉 (cut > 0). */
    static void ruinTower(Blueprint b, int cx, int cz, int by, int r, int h, BlockState[] mat, int[] w, int cut) {
        b.fn(cx - r, by, cz - r, cx + r, by + h + 1, cz + r, (x, y, z) -> {
            double dx = x - cx, dz = z - cz, d = Math.sqrt(dx * dx + dz * dz);
            if (d >= r + 0.3) return null;
            int ly = y - by;
            int top = h - (cut > 0 ? Math.floorMod(Build.hash(x >> 1, 11, z >> 1), cut) : 0);
            boolean shell = d >= r - 1.2;
            if (ly > top) {
                if (cut == 0 && ly == h + 1 && shell && Math.floorMod(x + z, 2) == 0) return mix(x, y, z, mat, w);
                return ly <= h + 1 ? s(Blocks.AIR) : null;
            }
            if (!shell) return ly == 0 || (ly % 6 == 0 && ly < top) ? s(Blocks.SPRUCE_PLANKS) : s(Blocks.AIR);
            if ((ly % 6 == 3) && (Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6)) return s(Blocks.AIR);
            return mix(x, y, z, mat, w);
        });
        // 층마다 오르는 사다리 (북쪽 안벽)
        for (int y = by + 1; y <= by + h - 1; y++) b.set(cx, y, cz - r + 2, s(Blocks.LADDER).with(net.minecraft.block.LadderBlock.FACING, Direction.SOUTH));
    }

    /** 대각선 출입구 (모서리 탑 → 안뜰). */
    static void diagonalDoor(Blueprint b, int fromX, int fromZ, int sx, int sz, int len, int by) {
        for (int k = 0; k <= len; k++) {
            int x = fromX - sx * k, z = fromZ - sz * k;
            b.fill(x, by + 1, z, x, by + 3, z, s(Blocks.AIR));
            b.fill(x - sx, by + 1, z, x - sx, by + 3, z, s(Blocks.AIR));
        }
    }

    /** 긴 회당: 박공지붕 (용마루 z 방향), 남쪽 가운데 문, 벽 기둥 + 높은 창. */
    static void hall(Blueprint b, int x0, int z0, int x1, int z1, int by, int H, BlockState wall, BlockState pillar, BlockState f1, BlockState f2,
                     Block roofStairs, BlockState roofTop, BlockState window, BlockState gable) {
        int cx = (x0 + x1) / 2;
        int W = x1 - x0 + 1;
        int layers = (W + 2) / 2 + 1;
        b.fn(x0 - 1, by, z0 - 1, x1 + 1, by + H + layers, z1 + 1, (x, y, z) -> {
            int ly = y - by;
            boolean inX = x >= x0 && x <= x1, inZ = z >= z0 && z <= z1;
            if (ly > H) {
                int i = ly - H - 1;
                int wx = x0 - 1 + i, ex = x1 + 1 - i;
                if (z < z0 - 1 || z > z1 + 1 || wx > ex) return null;
                if (wx == ex) return roofTop;
                if (x == wx) return Build.stairs(roofStairs, Direction.EAST, false);
                if (x == ex) return Build.stairs(roofStairs, Direction.WEST, false);
                if ((z == z0 || z == z1) && x > wx && x < ex) return (Math.abs(x - cx) <= 1 && i >= 1 && i <= 2) ? window : gable;
                return x > wx && x < ex && inZ ? s(Blocks.AIR) : null;
            }
            if (!inX || !inZ) return null;
            boolean wallX = x == x0 || x == x1, wallZ = z == z0 || z == z1;
            if (ly == 0) return wallX || wallZ ? pillar : (((x + z) & 1) == 0 ? f1 : f2);
            if (wallX || wallZ) {
                if (z == z1 && Math.abs(x - cx) <= 1 && ly <= 4) return s(Blocks.AIR);
                if (wallX && wallZ) return pillar;
                if (wallX && (z - z0) % 4 == 0) return pillar;
                if (wallZ && (x - x0) % 4 == 0) return pillar;
                if (ly == H) return pillar;
                if (wallX && (z - z0) % 4 == 2 && ly >= 2 && ly <= H - 2) return window;
                return wall;
            }
            return s(Blocks.AIR);
        });
    }

    // ================================================================ 몰락한 요새
    static final BlockState[] DEEP = {s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS), s(Blocks.DEEPSLATE_TILES), s(Blocks.COBBLED_DEEPSLATE),
            s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.MOSSY_COBBLESTONE)};
    static final int[] DEEP_W = {40, 20, 15, 10, 8, 7};

    static void fortress(Blueprint b, int cx, int by, int cz, Random r) {
        int R = 28, W = 19, H = 11;
        circleTerraform(b, cx, cz, by, R + 2, 26, s(Blocks.COARSE_DIRT), s(Blocks.DIRT));
        ground(b, cx, cz, by, R + 1, new BlockState[]{s(Blocks.COARSE_DIRT), s(Blocks.PODZOL), s(Blocks.GRAVEL), s(Blocks.SOUL_SOIL), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.ROOTED_DIRT)},
                new int[]{30, 25, 15, 10, 8, 12});

        // 무너진 성벽 (사각, 두께 3)
        b.fn(cx - W - 1, by + 1, cz - W - 1, cx + W + 1, by + H + 1, cz + W + 1, (x, y, z) -> {
            int dx = x - cx, dz = z - cz, ax = Math.abs(dx), az = Math.abs(dz);
            boolean onX = ax >= W - 1 && az <= W + 1, onZ = az >= W - 1 && ax <= W + 1;
            if (!onX && !onZ) return null;
            if (dz >= W - 1 && ax <= 2) return y - by <= 6 ? s(Blocks.AIR) : mix(x, y, z, DEEP, DEEP_W);
            int seg = onZ ? x >> 3 : z >> 3;
            int drop;
            if (h100(seg, 3, onZ ? dz : dx) < 14) drop = H - 2 - Math.floorMod(Build.hash(x, 5, z), 3);
            else {
                int hh = h100(x >> 1, 9, z >> 1);
                drop = hh < 55 ? 0 : hh < 82 ? 1 + hh % 3 : 3 + hh % 5;
            }
            int top = by + H - drop;
            if (y > top) {
                boolean outer = ax == W + 1 || az == W + 1;
                return drop == 0 && y == by + H + 1 && outer && Math.floorMod(x + z, 2) == 0 ? mix(x, y, z, DEEP, DEEP_W) : null;
            }
            return mix(x, y, z, DEEP, DEEP_W);
        });
        // 모서리 탑 4 (둘은 무너짐)
        int[][] corners = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int i = 0; i < 4; i++) {
            int tx = cx + corners[i][0] * W, tz = cz + corners[i][1] * W;
            ruinTower(b, tx, tz, by, 5, 16, DEEP, DEEP_W, i % 2 == 0 ? 7 : 0);
            diagonalDoor(b, tx, tz, corners[i][0], corners[i][1], 7, by);
            if (corners[i][1] < 0) spawner(b, tx, by + 1, tz, AWMobs.SKELETON_KNIGHT);
            else chest(b, tx, by + 1, tz, Direction.SOUTH, 1);
            b.set(tx + 1, by + 5, tz + 1, s(Blocks.COBWEB));
            b.set(tx - 1, by + 4, tz, s(Blocks.COBWEB));
        }
        // 안뜰 단 (보스 제단)
        b.fn(cx - 8, by, cz - 8, cx + 8, by + 1, cz + 8, (x, y, z) -> {
            int d2 = sq(x - cx) + sq(z - cz);
            if (y == by) return d2 <= 64 ? (h100(x, y, z) < 20 ? s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : s(Blocks.POLISHED_BLACKSTONE_BRICKS)) : null;
            return d2 <= 30 ? s(Blocks.POLISHED_BLACKSTONE) : null;
        });
        b.fill(cx - 1, by + 1, cz + 5, cx + 1, by + 1, cz + 6, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.fill(cx - 1, by + 1, cz + 6, cx + 1, by + 1, cz + 6, Build.stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.NORTH, false));
        altar(b, cx, by + 2, cz, "fallen_king");
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            b.set(cx + (int) Math.round(Math.cos(a) * 3.5), by + 2, cz + (int) Math.round(Math.sin(a) * 3.5), s(Blocks.SOUL_CAMPFIRE));
        }
        chest(b, cx, by + 2, cz - 4, Direction.SOUTH, 2);
        // 부서진 기둥 8
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int px = cx + (int) Math.round(Math.cos(a) * 11), pz = cz + (int) Math.round(Math.sin(a) * 11);
            int hgt = 2 + r.nextInt(6);
            b.fill(px, by + 1, pz, px, by + hgt, pz, s(Blocks.POLISHED_DEEPSLATE));
            if (hgt >= 6) {
                b.set(px, by + hgt + 1, pz, s(Blocks.CHISELED_DEEPSLATE));
                b.set(px, by + hgt + 2, pz, s(Blocks.SOUL_LANTERN));
            } else b.set(px + (r.nextBoolean() ? 1 : -1), by + 1, pz, s(Blocks.COBBLED_DEEPSLATE_SLAB));
        }
        // 북쪽: 무너진 왕좌
        b.fill(cx - 3, by + 1, cz - W + 2, cx + 3, by + 2, cz - W + 4, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.set(cx, by + 3, cz - W + 3, Build.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.NORTH, false));
        b.fill(cx - 1, by + 3, cz - W + 2, cx + 1, by + 5, cz - W + 2, s(Blocks.GILDED_BLACKSTONE));
        b.set(cx, by + 6, cz - W + 2, s(Blocks.GILDED_BLACKSTONE));
        b.fill(cx - 3, by + 3, cz - W + 4, cx + 3, by + 3, cz - W + 4, Build.stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.NORTH, false));
        b.set(cx - 2, by + 3, cz - W + 3, s(Blocks.SOUL_LANTERN));
        b.set(cx + 2, by + 3, cz - W + 3, s(Blocks.SOUL_LANTERN));
        // 거미줄 · 뼈 · 해골
        for (int k = 0; k < 26; k++) {
            int x = cx + r.nextInt(W * 2 - 3) - W + 2, z = cz + r.nextInt(W * 2 - 3) - W + 2;
            if (sq(x - cx) + sq(z - cz) < 81) continue;
            int t = r.nextInt(4);
            b.set(x, by + 1, z, t == 0 ? s(Blocks.BONE_BLOCK) : t == 1 ? s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)) : t == 2 ? s(Blocks.COBWEB) : s(Blocks.DEAD_BUSH));
        }
        // 성 밖 무덤
        for (int k = 0; k < 18; k++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = W + 4 + r.nextDouble() * (R - W - 5);
            int x = cx + (int) Math.round(Math.cos(a) * d), z = cz + (int) Math.round(Math.sin(a) * d);
            if (z > cz + W && Math.abs(x - cx) <= 3) continue;
            b.set(x, by, z, s(Blocks.COARSE_DIRT));
            b.set(x, by + 1, z, r.nextBoolean() ? s(Blocks.MOSSY_STONE_BRICK_WALL) : s(Blocks.COBBLESTONE_WALL));
            if (r.nextFloat() < 0.3f) b.set(x, by + 2, z, s(Blocks.SOUL_LANTERN));
        }
        // 남쪽 길
        b.fn(cx - 2, by, cz + W, cx + 2, by, cz + R + 1, (x, y, z) -> h100(x, y, z) < 60 ? s(Blocks.GRAVEL) : s(Blocks.COBBLED_DEEPSLATE));
        for (int k = 0; k < 3; k++) mob(b, cx - 6 + k * 6, by + 1, cz + 12, AWMobs.SKELETON_KNIGHT);
        mob(b, cx, by + 2, cz - W + 6, AWMobs.WISP);
    }

    // ================================================================ 잿불 제단
    static void ember(Blueprint b, int cx, int by, int cz, Random r) {
        int R = 25;
        circleTerraform(b, cx, cz, by, R + 2, 26, s(Blocks.BLACKSTONE), s(Blocks.BLACKSTONE));
        ground(b, cx, cz, by, R + 1, new BlockState[]{s(Blocks.BLACKSTONE), s(Blocks.BASALT), s(Blocks.SMOOTH_BASALT), s(Blocks.MAGMA_BLOCK), s(Blocks.GRAVEL), s(Blocks.TUFF)},
                new int[]{35, 22, 15, 6, 10, 12});
        // 용암 해자 (다리 4)
        b.fn(cx - 15, by, cz - 15, cx + 15, by, cz + 15, (x, y, z) -> {
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            if (d < 12.5 || d >= 14.2) return null;
            if (Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1) return s(Blocks.POLISHED_BLACKSTONE_BRICKS);
            return s(Blocks.LAVA);
        });
        b.fn(cx - 15, by - 1, cz - 15, cx + 15, by - 1, cz + 15, (x, y, z) -> {
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            return d >= 12 && d < 14.7 ? s(Blocks.BLACKSTONE) : null;
        });
        // 3단 지구라트 + 계단
        for (int t = 0; t < 3; t++) {
            int hs = 9 - t * 2, y = by + 1 + t;
            b.fn(cx - hs, y, cz - hs, cx + hs, y, cz + hs, (x, yy, z) -> {
                int ax = Math.abs(x - cx), az = Math.abs(z - cz);
                if (ax == hs && az == hs) return s(Blocks.GILDED_BLACKSTONE);
                if (az == hs && ax <= 1) return Build.stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, z > cz ? Direction.NORTH : Direction.SOUTH, false);
                if (ax == hs && az <= 1) return Build.stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, x > cx ? Direction.WEST : Direction.EAST, false);
                return (ax == hs || az == hs) ? s(Blocks.CHISELED_POLISHED_BLACKSTONE) : s(Blocks.POLISHED_BLACKSTONE_BRICKS);
            });
        }
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            b.set(cx + c[0], by + 3, cz + c[1], s(Blocks.MAGMA_BLOCK));
            b.set(cx + c[0] * 2, by + 4, cz + c[1] * 2, s(Blocks.CAMPFIRE));
        }
        altar(b, cx, by + 4, cz, "ember_colossus");
        chest(b, cx, by + 4, cz - 3, Direction.SOUTH, 2);
        // 불기둥 오벨리스크 8
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int px = cx + (int) Math.round(Math.cos(a) * 19), pz = cz + (int) Math.round(Math.sin(a) * 19);
            int hgt = 6 + r.nextInt(5);
            b.fill(px - 1, by + 1, pz - 1, px + 1, by + 1, pz + 1, s(Blocks.POLISHED_BLACKSTONE));
            b.fill(px, by + 2, pz, px, by + hgt, pz, Build.log(Blocks.BASALT, Direction.Axis.Y));
            b.set(px, by + hgt + 1, pz, s(Blocks.NETHERRACK));
            b.set(px, by + hgt + 2, pz, s(Blocks.FIRE));
            if (k == 1 || k == 5) chest(b, px + (k == 1 ? 2 : -2), by + 1, pz, Direction.NORTH, 1);
        }
        // 오크 생성기 + 오크
        spawner(b, cx + 17, by + 1, cz, AWMobs.ORC);
        spawner(b, cx - 17, by + 1, cz, AWMobs.ORC);
        b.fill(cx + 16, by + 1, cz - 1, cx + 18, by + 1, cz - 1, s(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
        b.fill(cx - 18, by + 1, cz + 1, cx - 16, by + 1, cz + 1, s(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
        mob(b, cx + 6, by + 1, cz + 18, AWMobs.ORC);
        mob(b, cx - 6, by + 1, cz - 18, AWMobs.ORC);
        // 길 (남쪽)
        b.fn(cx - 1, by, cz + 14, cx + 1, by, cz + R + 1, (x, y, z) -> s(Blocks.POLISHED_BLACKSTONE_BRICKS));
    }

    // ================================================================ 서리 신전
    static void frost(Blueprint b, int cx, int by, int cz, Random r) {
        int R = 25;
        circleTerraform(b, cx, cz, by, R + 2, 30, s(Blocks.SNOW_BLOCK), s(Blocks.SNOW_BLOCK));
        ground(b, cx, cz, by, R + 1, new BlockState[]{s(Blocks.SNOW_BLOCK), s(Blocks.PACKED_ICE), s(Blocks.BLUE_ICE)},
                new int[]{70, 25, 5});
        int x0 = cx - 9, x1 = cx + 9, z0 = cz - 15, z1 = cz + 9, H = 11;
        BlockState wall = s(Blocks.POLISHED_DIORITE), pil = s(Blocks.PACKED_ICE);
        hall(b, x0, z0, x1, z1, by, H, wall, pil, s(Blocks.POLISHED_DIORITE), s(Blocks.BLUE_ICE), Blocks.QUARTZ_STAIRS, s(Blocks.QUARTZ_BLOCK),
                s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), s(Blocks.CALCITE));
        // 안: 얼음 기둥 2줄 + 매달린 영혼 등불 + 바닥 바다 랜턴
        for (int z = z0 + 4; z <= z1 - 3; z += 4)
            for (int sx = -1; sx <= 1; sx += 2) {
                b.fill(cx + sx * 5, by + 1, z, cx + sx * 5, by + H - 1, z, s(Blocks.PACKED_ICE));
                b.set(cx + sx * 5, by + H - 2, z + 2, s(Blocks.SOUL_LANTERN).with(LanternBlock.HANGING, true));
                b.fill(cx + sx * 5, by + H - 1, z + 2, cx + sx * 5, by + H - 1, z + 2, s(Blocks.CHAIN));
                b.set(cx + sx * 2, by, z, s(Blocks.SEA_LANTERN));
            }
        // 북쪽 단 + 제단
        b.fill(cx - 5, by + 1, z0 + 1, cx + 5, by + 1, z0 + 6, s(Blocks.PACKED_ICE));
        b.fill(cx - 3, by + 2, z0 + 1, cx + 3, by + 2, z0 + 4, s(Blocks.BLUE_ICE));
        b.fill(cx - 1, by + 1, z0 + 7, cx + 1, by + 1, z0 + 7, Build.stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH, false));
        b.fill(cx - 1, by + 2, z0 + 5, cx + 1, by + 2, z0 + 5, Build.stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH, false));
        altar(b, cx, by + 3, z0 + 3, "frost_witch");
        chest(b, cx - 3, by + 3, z0 + 1, Direction.SOUTH, 2);
        chest(b, cx + 3, by + 3, z0 + 1, Direction.SOUTH, 1);
        // 모서리 첨탑 4
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}})
            Build.tower(b, c[0], c[1], by, 3, 16, Blocks.PACKED_ICE, Blocks.BLUE_ICE, true, null, 0);
        // 입구 앞 다리 + 얼음 가시
        b.fn(cx - 2, by, z1 + 1, cx + 2, by, cz + R, (x, y, z) -> Math.abs(x - cx) == 2 ? s(Blocks.PACKED_ICE) : s(Blocks.POLISHED_DIORITE));
        for (int k = 0; k < 9; k++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 14 + r.nextDouble() * 9;
            int sx = cx + (int) Math.round(Math.cos(a) * d), sz = cz + (int) Math.round(Math.sin(a) * d);
            if (sx >= x0 - 3 && sx <= x1 + 3 && sz >= z0 - 3 && sz <= z1 + 3) continue;
            if (Math.abs(sx - cx) <= 3 && sz > z1) continue;
            int hgt = 5 + r.nextInt(8);
            b.fn(sx - 2, by + 1, sz - 2, sx + 2, by + hgt, sz + 2, (x, y, z) -> {
                double rr = 2.2 * (1 - (double) (y - by - 1) / hgt);
                return sq(x - sx) + sq(z - sz) <= rr * rr + 0.3 ? s(Blocks.PACKED_ICE) : null;
            });
        }
        spawner(b, cx - 6, by + 1, z1 - 2, EntityType.STRAY);
        spawner(b, cx + 6, by + 1, z1 - 2, EntityType.STRAY);
        mob(b, cx, by + 1, cz + 16, AWMobs.WISP);
    }

    // ================================================================ 고룡 둥지 (산 위 바위 기둥)
    /**
     * 용의 둥지 (용왕): 산꼭대기 바위 받침 위 움푹한 둥지. 금 더미 · 거대한 용의 알 셋 · 둥지를 감싼 거대한 갈비뼈,
     * 가운데 용왕 제단, 위를 맴도는 비룡들, 상자 (보스방 보상) 셋.
     */
    static void nest(Blueprint b, int cx, int by, int cz, Random r) {
        int depth = 30;
        BlockState[] rock = {s(Blocks.STONE), s(Blocks.ANDESITE), s(Blocks.DEEPSLATE), s(Blocks.BLACKSTONE), s(Blocks.BASALT), s(Blocks.TUFF)};
        int[] rw = {30, 15, 20, 15, 12, 8};
        // 바위 받침: 아래로 넓어짐
        b.fn(cx - 24, by - depth, cz - 24, cx + 24, by, cz + 24, (x, y, z) -> {
            double rr = 15.5 + (by - y) * 0.26 + (Math.floorMod(Build.hash(x >> 1, y >> 2, z >> 1), 5) - 2) * 0.5;
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            return d > rr ? null : mix(x, y, z, rock, rw);
        });
        // 위를 비움
        b.fn(cx - 18, by + 1, cz - 18, cx + 18, by + 22, cz + 18, (x, y, z) -> sq(x - cx) + sq(z - cz) <= 18 * 18 ? s(Blocks.AIR) : null);
        // 움푹한 둥지 바닥 (금 더미 섞임)
        BlockState[] hoard = {s(Blocks.COARSE_DIRT), s(Blocks.GRAVEL), s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.GILDED_BLACKSTONE), s(Blocks.BONE_BLOCK), s(Blocks.HAY_BLOCK)};
        int[] hw = {25, 15, 8, 10, 15, 12, 15};
        b.fn(cx - 11, by - 3, cz - 11, cx + 11, by, cz + 11, (x, y, z) -> {
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            if (d > 11) return null;
            int floor = by - (d < 8 ? 2 : d < 10 ? 1 : 0);
            if (y > floor) return s(Blocks.AIR);
            return y == floor ? mix(x, y, z, hoard, hw) : mix(x, y, z, rock, rw);
        });
        // 둥지 테 (통나무 · 뼈 · 건초 · 마른 덤불)
        b.fn(cx - 15, by, cz - 15, cx + 15, by + 4, cz + 15, (x, y, z) -> {
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            int ly = y - by;
            double outer = 14.6 - ly * 0.7, inner = 11.2 - ly * 0.15;
            if (d >= outer || d < inner) return null;
            int h = h100(x, y, z);
            if (h < 32) return Build.log(Blocks.DARK_OAK_LOG, ((x + y) & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z);
            if (h < 52) return s(Blocks.BONE_BLOCK);
            if (h < 70) return s(Blocks.HAY_BLOCK);
            if (h < 82) return Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
            if (h < 90) return s(Blocks.GILDED_BLACKSTONE);
            return ly >= 1 ? s(Blocks.DEAD_BUSH) : s(Blocks.COARSE_DIRT);
        });
        // 둥지를 감싼 거대한 갈비뼈 6 개 (안쪽으로 휨)
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + 0.25;
            for (double t = 0; t <= 0.86; t += 0.02) {
                double rad = 15.5 - 7.5 * t * t;
                int y = by + (int) Math.round(15 * Math.sin(t * Math.PI / 2));
                int x = cx + (int) Math.round(Math.cos(a) * rad), z = cz + (int) Math.round(Math.sin(a) * rad);
                b.set(x, y, z, s(Blocks.BONE_BLOCK));
                if (t < 0.45) b.set(x, y + 1, z, s(Blocks.BONE_BLOCK));
            }
        }
        // 거대한 용의 알 셋 (푸른 껍데기 · 금빛 점)
        BlockState[] shell = {s(Blocks.BLUE_CONCRETE), s(Blocks.LIGHT_BLUE_CONCRETE), s(Blocks.CYAN_TERRACOTTA), s(Blocks.GOLD_BLOCK)};
        int[] sw = {50, 22, 16, 12};
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3 + 0.9;
            int ex = cx + (int) Math.round(Math.cos(a) * 5.5), ez = cz + (int) Math.round(Math.sin(a) * 5.5), ey = by - 1;
            b.fn(ex - 2, ey, ez - 2, ex + 2, ey + 4, ez + 2, (x, y, z) -> {
                double dx = (x - ex) / 1.7, dy = (y - ey - 1.8) / 2.4, dz = (z - ez) / 1.7;
                return dx * dx + dy * dy + dz * dz <= 1.0 ? mix(x, y, z, shell, sw) : null;
            });
        }
        // 가운데: 금빛 단 위 용왕 제단
        b.fill(cx - 2, by - 2, cz - 2, cx + 2, by - 2, cz + 2, s(Blocks.GILDED_BLACKSTONE));
        b.fill(cx - 1, by - 1, cz - 1, cx + 1, by - 1, cz + 1, s(Blocks.GOLD_BLOCK));
        altar(b, cx, by, cz, "dragon_king");
        for (int k = 0; k < 4; k++) {
            int px = cx + (k < 2 ? -3 : 3), pz = cz + (k % 2 == 0 ? -3 : 3);
            b.fill(px, by - 1, pz, px, by + 2, pz, s(Blocks.POLISHED_BLACKSTONE_WALL));
            b.set(px, by + 3, pz, s(Blocks.SOUL_LANTERN));
        }
        chest(b, cx + 8, by - 1, cz, Direction.WEST, 2);
        chest(b, cx - 8, by - 1, cz, Direction.EAST, 2);
        chest(b, cx, by - 1, cz + 8, Direction.NORTH, 1);
        // 둥지 위를 맴도는 비룡
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3;
            mob(b, cx + (int) Math.round(Math.cos(a) * 9), by + 8 + k, cz + (int) Math.round(Math.sin(a) * 9), AWMobs.DRAKE);
        }
        // 나선 비탈길 (받침 바깥, 아래에서 위로)
        for (int i = 0; i <= depth; i++) {
            int y = by - depth + i;
            double rr = 15.5 + (by - y) * 0.26;
            double a0 = i * 0.36;
            for (double a = a0; a < a0 + 0.4; a += 0.04)
                for (double dr = 0.4; dr <= 2.6; dr += 0.6) {
                    int x = cx + (int) Math.round(Math.cos(a) * (rr + dr)), z = cz + (int) Math.round(Math.sin(a) * (rr + dr));
                    b.set(x, y, z, h100(x, y, z) < 70 ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS));
                    b.fill(x, y + 1, z, x, y + 3, z, s(Blocks.AIR));
                }
        }
    }

    static void roost(Blueprint b, int cx, int by, int cz, Random r) {
        int depth = 26;
        BlockState[] rock = {s(Blocks.STONE), s(Blocks.ANDESITE), s(Blocks.TUFF), s(Blocks.COBBLESTONE), s(Blocks.DEEPSLATE), s(Blocks.CALCITE)};
        int[] rw = {40, 20, 15, 12, 8, 5};
        // 바위 기둥: 위로 갈수록 좁아짐
        b.fn(cx - 16, by - depth, cz - 16, cx + 16, by, cz + 16, (x, y, z) -> {
            double rr = 9.5 + (by - y) * 0.22 + (Math.floorMod(Build.hash(x >> 1, y >> 2, z >> 1), 5) - 2) * 0.4;
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            if (d > rr) return null;
            if (y == by) return d < 9 ? mix(x, y, z, new BlockState[]{s(Blocks.COARSE_DIRT), s(Blocks.ROOTED_DIRT), s(Blocks.GRAVEL), s(Blocks.HAY_BLOCK)}, new int[]{35, 25, 20, 20}) : mix(x, y, z, rock, rw);
            return mix(x, y, z, rock, rw);
        });
        // 위를 비움
        b.fn(cx - 12, by + 1, cz - 12, cx + 12, by + 14, cz + 12, (x, y, z) -> sq(x - cx) + sq(z - cz) <= 121 ? s(Blocks.AIR) : null);
        // 둥지 테 (통나무 · 건초 · 뼈 · 울타리)
        b.fn(cx - 10, by + 1, cz - 10, cx + 10, by + 3, cz + 10, (x, y, z) -> {
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            int ly = y - by;
            double outer = 9.3 - ly * 0.6, inner = 6.5 - ly * 0.2;
            if (d >= outer || d < inner) return null;
            int h = h100(x, y, z);
            if (h < 35) return Build.log(Blocks.SPRUCE_LOG, ((x + y) & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z);
            if (h < 55) return s(Blocks.HAY_BLOCK);
            if (h < 65) return s(Blocks.BONE_BLOCK);
            if (h < 80) return Build.log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
            if (h < 90) return s(Blocks.DARK_OAK_FENCE);
            return ly == 1 ? s(Blocks.COARSE_DIRT) : null;
        });
        // 알 · 뼈 · 해골
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.6;
            b.set(cx + (int) Math.round(Math.cos(a) * 4), by + 1, cz + (int) Math.round(Math.sin(a) * 4), s(Blocks.TURTLE_EGG).with(TurtleEggBlock.EGGS, 2 + r.nextInt(3)));
        }
        for (int k = 0; k < 6; k++) {
            int x = cx + r.nextInt(11) - 5, z = cz + r.nextInt(11) - 5;
            if (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1) continue;
            b.set(x, by + 1, z, r.nextBoolean() ? s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)) : s(Blocks.BONE_BLOCK));
        }
        altar(b, cx, by + 1, cz, "ancient_wyvern");
        chest(b, cx + 5, by + 1, cz - 2, Direction.WEST, 2);
        chest(b, cx - 5, by + 1, cz + 2, Direction.EAST, 1);
        // 선 돌 (부서진 아치)
        for (int k = 0; k < 5; k++) {
            double a = k * Math.PI * 2 / 5 + 0.3;
            int px = cx + (int) Math.round(Math.cos(a) * 11), pz = cz + (int) Math.round(Math.sin(a) * 11);
            b.fill(px, by - 2, pz, px, by + 3 + r.nextInt(5), pz, s(Blocks.POLISHED_ANDESITE));
        }
        // 나선 비탈길 (기둥 바깥, 아래에서 위로)
        for (int i = 0; i <= depth; i++) {
            int y = by - depth + i;
            double rr = 9.5 + (by - y) * 0.22;
            double a0 = i * 0.42;
            for (double a = a0; a < a0 + 0.46; a += 0.05)
                for (double dr = 0.4; dr <= 2.6; dr += 0.6) {
                    int x = cx + (int) Math.round(Math.cos(a) * (rr + dr)), z = cz + (int) Math.round(Math.sin(a) * (rr + dr));
                    b.set(x, y, z, h100(x, y, z) < 70 ? s(Blocks.COBBLESTONE) : s(Blocks.MOSSY_COBBLESTONE));
                    b.fill(x, y + 1, z, x, y + 3, z, s(Blocks.AIR));
                }
        }
    }

    // ================================================================ 고블린 야영지
    static void camp(Blueprint b, int cx, int by, int cz, Random r) {
        int R = 13;
        circleTerraform(b, cx, cz, by, R + 1, 12, s(Blocks.GRASS_BLOCK), s(Blocks.DIRT));
        ground(b, cx, cz, by, R, new BlockState[]{s(Blocks.GRASS_BLOCK), s(Blocks.COARSE_DIRT), s(Blocks.ROOTED_DIRT), s(Blocks.DIRT_PATH)}, new int[]{35, 40, 10, 15});
        // 뾰족 목책 (남쪽 입구)
        b.fn(cx - R, by + 1, cz - R, cx + R, by + 5, cz + R, (x, y, z) -> {
            double d = Math.sqrt(sq(x - cx) + sq(z - cz));
            if (d < R - 0.5 || d >= R + 0.5) return null;
            if (z > cz && Math.abs(x - cx) <= 1) return null;
            int hgt = 2 + Math.floorMod(Build.hash(x, 1, z), 3);
            int ly = y - by;
            if (ly <= hgt) return Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
            return ly == hgt + 1 ? s(Blocks.SPRUCE_FENCE) : null;
        });
        // 가운데 모닥불 + 꼬치 + 통나무 의자
        b.set(cx, by + 1, cz, s(Blocks.CAMPFIRE).with(CampfireBlock.LIT, true));
        b.fill(cx - 1, by + 1, cz, cx - 1, by + 2, cz, s(Blocks.SPRUCE_FENCE));
        b.fill(cx + 1, by + 1, cz, cx + 1, by + 2, cz, s(Blocks.SPRUCE_FENCE));
        b.fill(cx - 1, by + 3, cz, cx + 1, by + 3, cz, Build.log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        b.fill(cx - 2, by + 1, cz + 3, cx + 2, by + 1, cz + 3, Build.log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        b.fill(cx - 2, by + 1, cz - 3, cx + 2, by + 1, cz - 3, Build.log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        // 천막 4 (A자)
        Block[] wool = {Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.BROWN_WOOL, Blocks.LIME_WOOL};
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            int tx = cx + (int) Math.round(Math.cos(a) * 7.5), tz = cz + (int) Math.round(Math.sin(a) * 7.5);
            Block wl = wool[k];
            boolean alongX = k % 2 == 0;
            b.fn(tx - 3, by + 1, tz - 3, tx + 3, by + 3, tz + 3, (x, y, z) -> {
                int u = alongX ? z - tz : x - tx, v = alongX ? x - tx : z - tz;
                int ly = y - by;
                if (Math.abs(v) > 2) return null;
                int edge = 3 - ly;
                if (Math.abs(u) == edge) return s(wl);
                if (ly == 3 && u == 0) return s(wl);
                if (Math.abs(u) < edge) return ly == 1 && Math.abs(v) == 2 && u == 0 ? null : s(Blocks.AIR);
                return null;
            });
            b.set(tx, by + 1, tz, s(Blocks.WHITE_CARPET));
            if (k == 0) chest(b, tx + (alongX ? 1 : 0), by + 1, tz + (alongX ? 0 : 1), Direction.NORTH, 1);
            else b.set(tx + (alongX ? 1 : 0), by + 1, tz + (alongX ? 0 : 1), r.nextBoolean() ? s(Blocks.BARREL) : s(Blocks.HAY_BLOCK));
        }
        // 해골 토템
        b.fill(cx + 4, by + 1, cz - 9, cx + 4, by + 3, cz - 9, s(Blocks.SPRUCE_FENCE));
        b.set(cx + 4, by + 4, cz - 9, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, 8));
        b.fill(cx - 4, by + 1, cz + 9, cx - 4, by + 3, cz + 9, s(Blocks.SPRUCE_FENCE));
        b.set(cx - 4, by + 4, cz + 9, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, 0));
        // 고블린 생성기 (모닥불 북쪽)
        spawner(b, cx, by + 1, cz - 5, AWMobs.GOBLIN);
        for (int k = 0; k < 4; k++) mob(b, cx - 3 + k * 2, by + 1, cz + (k % 2 == 0 ? 5 : -6), AWMobs.GOBLIN);
        mob(b, cx, by + 1, cz + 7, AWMobs.ORC);
        if (r.nextInt(3) == 0) mob(b, cx + 2, by + 1, cz - 2, AWMobs.GOBLIN_LORD);
    }

    private DungeonPlans() {
    }
}

package com.abilitywar.world;

import com.abilitywar.registry.AWEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.abilitywar.world.Build.s;

/**
 * 수도 「왕도 아르덴」: 모서리를 고르지 않게 깎은 8각 성벽 안을 건물로 꽉 채운 도시.
 * <pre>
 *  북쪽: 왕성 (성벽 + 탑 + 왕좌의 방, 왕) · 가운데: 네모난 왕도 광장 (분수, 노점, 스폰)
 *  십자 큰길 → 남·서·동 성문 (네모 탑 성문) · 성벽 모서리마다 네모 망루
 *  동쪽: 전직 교관 길드 · 북서: 신전 · 남쪽: 여관 · 대장간
 *  나머지 블록: 길을 따라 붙어 선 집들 (블록 네 변, 1~3층) + 가운데 작은 안마당, 돌 포장 골목
 * </pre>
 */
public final class CapitalPlan {
    /** 성벽 꼭짓점 (중심 기준). 남 (z=112) · 서 (x=-118) · 동 (x=112) 성문 자리는 곧은 변. */
    static final int[][] WALL = {{-80, -116}, {70, -116}, {112, -74}, {112, 60}, {64, 112}, {-90, 112}, {-118, 84}, {-118, -60}};
    static final int WALL_T = 5, WALL_H = 12;
    /** 성문 (중심 기준): 남, 서, 동. */
    static final int[][] GATES = {{0, 112}, {-118, 0}, {112, 0}};

    record Rect(int x0, int z0, int x1, int z1) {
        boolean overlaps(int ax0, int az0, int ax1, int az1) {
            return ax0 <= x1 && ax1 >= x0 && az0 <= z1 && az1 >= z0;
        }
    }

    static int[][] wall(int cx, int cz) {
        int[][] w = new int[WALL.length][];
        for (int i = 0; i < WALL.length; i++) w[i] = new int[]{cx + WALL[i][0], cz + WALL[i][1]};
        return w;
    }

    public static void build(Blueprint b, int cx, int by, int cz, Random r) {
        int[][] W = wall(cx, cz);
        List<Rect> reserved = new ArrayList<>();
        // ---- 땅 고르기: 성벽 바깥 6칸까지 평평 (그 바깥 비탈은 왕령 (LandsPlan) 이 40칸에 걸쳐 만듦)
        b.terraformPoly(W, 6, 1, by, 45, s(Blocks.GRASS_BLOCK), s(Blocks.DIRT));

        // ---- 골목 (돌 포장, 24칸마다) + 성벽 안쪽 둘레길
        for (int k = -5; k <= 5; k++) {
            int lx = cx + k * 24 - 12, lz = cz + k * 24 - 12;
            b.fn(lx - 1, by, cz - 120, lx + 1, by, cz + 116, (x, y, z) -> Build.polySigned(W, x, z) >= 4 ? streetBlock(x, y, z) : null);
            b.fn(cx - 122, by, lz - 1, cx + 116, by, lz + 1, (x, y, z) -> Build.polySigned(W, x, z) >= 4 ? streetBlock(x, y, z) : null);
        }
        b.fn(cx - 118, by, cz - 116, cx + 112, by, cz + 112, (x, y, z) -> {
            double d = Build.polySigned(W, x, z);
            return d >= 3.5 && d <= 6.5 ? streetBlock(x, y, z) : null;
        });
        // ---- 큰길: 남북 (남문 → 왕성 문), 동서 (서문 → 동문)
        b.fn(cx - 5, by, cz - 44, cx + 5, by, cz + 108, (x, y, z) -> Math.abs(x - cx) > 3 ? streetBlock(x, y, z) : null);
        b.fn(cx - 114, by, cz - 5, cx + 108, by, cz + 5, (x, y, z) -> Math.abs(z - cz) > 3 ? streetBlock(x, y, z) : null);
        b.fn(cx - 3, by, cz - 44, cx + 3, by, cz + 118, (x, y, z) -> roadBlock(x - cx, x, y, z));
        b.fn(cx - 124, by, cz - 3, cx + 118, by, cz + 3, (x, y, z) -> roadBlock(z - cz, x, y, z));
        reserved.add(new Rect(cx - 5, cz - 120, cx + 5, cz + 120));
        reserved.add(new Rect(cx - 124, cz - 5, cx + 118, cz + 5));

        // ---- 왕도 광장 (네모): 바둑판 포장 + 분수 + 가로등 + 노점 + 벤치
        int px0 = cx - 21, px1 = cx + 21, pz0 = cz - 17, pz1 = cz + 19;
        b.fn(px0 - 1, by, pz0 - 1, px1 + 1, by, pz1 + 1, (x, y, z) -> {
            if (x == px0 - 1 || x == px1 + 1 || z == pz0 - 1 || z == pz1 + 1) return s(Blocks.POLISHED_DIORITE);
            if (Math.floorMod(x - cx, 6) == 0 || Math.floorMod(z - cz, 6) == 0) return s(Blocks.POLISHED_ANDESITE);
            return ((x + z) & 1) == 0 ? s(Blocks.SMOOTH_STONE) : s(Blocks.STONE_BRICKS);
        });
        Build.fountain(b, cx, cz, by, 5);
        for (int x = px0; x <= px1; x += 7) {
            Build.lamp(b, x, pz0, by);
            Build.lamp(b, x, pz1, by);
        }
        for (int z = pz0 + 9; z <= pz1 - 9; z += 9) {
            if (Math.abs(z - cz) <= 4) continue;
            Build.lamp(b, px0, z, by);
            Build.lamp(b, px1, z, by);
        }
        for (int[] q : new int[][]{{7, 7}, {-7, 7}, {7, -7}, {-7, -7}})
            b.set(cx + q[0], by + 1, cz + q[1], Build.stairs(Blocks.SPRUCE_STAIRS, q[1] > 0 ? Direction.SOUTH : Direction.NORTH, false));
        Block[][] wools = {{Blocks.RED_WOOL, Blocks.WHITE_WOOL}, {Blocks.BLUE_WOOL, Blocks.WHITE_WOOL}, {Blocks.YELLOW_WOOL, Blocks.ORANGE_WOOL}, {Blocks.GREEN_WOOL, Blocks.LIME_WOOL}, {Blocks.PURPLE_WOOL, Blocks.MAGENTA_WOOL}};
        VillagerProfession[] trades = {VillagerProfession.FARMER, VillagerProfession.FLETCHER, VillagerProfession.LIBRARIAN, VillagerProfession.ARMORER,
                VillagerProfession.WEAPONSMITH, VillagerProfession.BUTCHER, VillagerProfession.CARTOGRAPHER, VillagerProfession.TOOLSMITH, VillagerProfession.SHEPHERD, VillagerProfession.FISHERMAN};
        int stall = 0;
        for (int sz = pz0 + 2; sz <= pz1 - 4; sz += 5) {
            if (Math.abs(sz + 1 - cz) <= 5) continue;
            for (int sx : new int[]{px0 + 1, px1 - 3}) {
                Block[] wl = wools[stall % wools.length];
                Build.stall(b, sx, sz, by, wl[0], wl[1], r);
                VillagerProfession pr = trades[stall % trades.length];
                b.spawns.add(new Blueprint.Spawn(new BlockPos(sx + 1, by + 1, sz + 2), w -> villager(w, pr)));
                stall++;
            }
        }
        reserved.add(new Rect(px0 - 2, pz0 - 2, px1 + 2, pz1 + 2));

        // ---- 성벽 + 네모 망루 + 성문 3
        Build.polyWall(b, W, by, WALL_H, WALL_T, Blocks.STONE_BRICKS, 12);
        for (int[] v : W) Build.squareTower(b, v[0], v[1], by, 5, 19, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, true, 10);
        for (int i = 0, j = W.length - 1; i < W.length; j = i++) {
            double ax = W[j][0], az = W[j][1], bx = W[i][0], bz = W[i][1];
            double len = Math.hypot(bx - ax, bz - az);
            int n = (int) (len / 36);
            for (int k = 1; k <= n; k++) {
                int tx = (int) Math.round(ax + (bx - ax) * k / (n + 1)), tz = (int) Math.round(az + (bz - az) * k / (n + 1));
                boolean nearGate = false;
                for (int[] g : GATES) if (Math.abs(tx - cx - g[0]) + Math.abs(tz - cz - g[1]) < 16) nearGate = true;
                if (!nearGate) Build.squareTower(b, tx, tz, by, 3, 15, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, false, 10);
            }
            // 성벽 위 등불
            for (double t = 8; t < len - 8; t += 16) {
                int lx = (int) Math.round(ax + (bx - ax) * t / len), lz = (int) Math.round(az + (bz - az) * t / len);
                b.set(lx, by + WALL_H + 1, lz, s(Blocks.LANTERN));
            }
        }
        gate(b, cx, cz + 112, by, true, 1);
        gate(b, cx - 118, cz, by, false, -1);
        gate(b, cx + 112, cz, by, false, 1);
        // 성문 밖 길은 왕령 (LandsPlan) 이 성문부터 그림

        // ---- 왕성
        castle(b, cx, cz, by, r);
        reserved.add(new Rect(cx - 46, cz - 120, cx + 46, cz - 38));

        // ---- 전직 교관 길드 (동쪽, 큰길 남쪽) + 수련장
        Build.Palette guild = new Build.Palette(Blocks.STONE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.PURPUR_STAIRS, Blocks.PURPUR_BLOCK, Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.POLISHED_ANDESITE);
        Build.house(b, cx + 32, cz + 8, 22, 15, by, 2, Direction.NORTH, guild, r);
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx + 43, by + 1, cz + 11), w -> named(AWEntities.JOB_MASTER.create(w), 0)));
        for (int k = 0; k < 4; k++) {
            int dx = cx + 58 + k * 4, dz = cz + 14;
            b.spawns.add(new Blueprint.Spawn(new BlockPos(dx, by + 1, dz), w -> {
                var as = EntityType.ARMOR_STAND.create(w);
                if (as != null) as.setYaw(180);
                return as;
            }));
            b.set(dx, by + 1, dz + 4, s(Blocks.TARGET));
            b.set(dx, by + 2, dz + 4, s(Blocks.HAY_BLOCK));
        }
        b.fn(cx + 56, by, cz + 10, cx + 74, by, cz + 20, (x, y, z) -> Math.floorMod(Build.hash(x, y, z), 3) == 0 ? s(Blocks.COARSE_DIRT) : s(Blocks.GRAVEL));
        reserved.add(new Rect(cx + 28, cz + 6, cx + 78, cz + 26));

        // 신전 (서쪽, 큰길 북쪽): 석영 회당 + 앞쪽 첨탑 둘 + 안쪽 제단 · 긴 의자
        {
            int tx0 = cx - 58, tx1 = cx - 36, tz0 = cz - 34, tz1 = cz - 9, tcx = (tx0 + tx1) / 2;
            DungeonPlans.hall(b, tx0, tz0, tx1, tz1, by, 12, s(Blocks.QUARTZ_BRICKS), s(Blocks.QUARTZ_PILLAR), s(Blocks.POLISHED_DIORITE), s(Blocks.SMOOTH_QUARTZ),
                    Blocks.PRISMARINE_BRICK_STAIRS, s(Blocks.DARK_PRISMARINE), s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), s(Blocks.QUARTZ_BRICKS));
            Build.tower(b, tx0, tz1, by, 3, 20, Blocks.QUARTZ_BRICKS, Blocks.PRISMARINE_BRICKS, true, null, 0);
            Build.tower(b, tx1, tz1, by, 3, 20, Blocks.QUARTZ_BRICKS, Blocks.PRISMARINE_BRICKS, true, null, 0);
            // 제단 (북쪽) + 촛대 + 긴 의자 + 매달린 등불
            b.fill(tcx - 3, by + 1, tz0 + 1, tcx + 3, by + 1, tz0 + 4, s(Blocks.SMOOTH_QUARTZ));
            b.fill(tcx - 1, by + 2, tz0 + 2, tcx + 1, by + 2, tz0 + 2, s(Blocks.GOLD_BLOCK));
            b.set(tcx, by + 3, tz0 + 2, s(Blocks.ENCHANTING_TABLE));
            b.set(tcx - 2, by + 2, tz0 + 3, s(Blocks.LANTERN));
            b.set(tcx + 2, by + 2, tz0 + 3, s(Blocks.LANTERN));
            b.set(tcx, by + 2, tz0 + 4, s(Blocks.LECTERN));
            for (int z = tz0 + 8; z <= tz1 - 4; z += 2)
                for (int sx = -1; sx <= 1; sx += 2)
                    b.fill(tcx + sx * 2, by + 1, z, tcx + sx * 7, by + 1, z, Build.stairs(Blocks.BIRCH_STAIRS, Direction.SOUTH, false));
            for (int z = tz0 + 6; z <= tz1 - 2; z += 6) b.set(tcx, by + 10, z, s(Blocks.LANTERN).with(LanternBlock.HANGING, true));
            b.fn(tcx - 1, by, tz1 + 1, tcx + 1, by, cz - 4, (x, y, z) -> s(Blocks.SMOOTH_QUARTZ));
            b.spawns.add(new Blueprint.Spawn(new BlockPos(tcx, by + 1, tz0 + 6), w -> villager(w, VillagerProfession.CLERIC)));
            reserved.add(new Rect(tx0 - 4, tz0 - 4, tx1 + 4, cz - 4));
        }

        Build.house(b, cx + 10, cz + 54, 16, 12, by, 2, Direction.NORTH, Build.SPRUCE, r);
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx + 17, by + 1, cz + 58), w -> villager(w, VillagerProfession.NONE)));
        Build.house(b, cx - 26, cz + 54, 12, 10, by, 1, Direction.NORTH, Build.STONE, r);
        b.set(cx - 22, by + 1, cz + 52, s(Blocks.ANVIL));
        b.set(cx - 19, by + 1, cz + 52, s(Blocks.BLAST_FURNACE));
        b.set(cx - 18, by + 1, cz + 52, s(Blocks.LAVA_CAULDRON));
        b.set(cx - 17, by + 1, cz + 52, s(Blocks.GRINDSTONE));
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx - 20, by + 1, cz + 51), w -> villager(w, VillagerProfession.WEAPONSMITH)));
        reserved.add(new Rect(cx - 30, cz + 50, cx + 30, cz + 68));

        // ---- 도시 블록: 길을 따라 집이 붙어 선 블록 (큰길이 지나가는 블록은 큰길 쪽 한 줄)
        int parks = 0;
        for (int gx = -5; gx <= 4; gx++)
            for (int gz = -5; gz <= 4; gz++) {
                int x0 = cx + gx * 24 - 10, x1 = x0 + 20, z0 = cz + gz * 24 - 10, z1 = z0 + 20;
                List<int[]> parts = new ArrayList<>();
                parts.add(new int[]{x0, z0, x1, z1});
                parts = split(parts, cx, true);
                parts = split(parts, cz, false);
                for (int[] q : parts) {
                    int w = q[2] - q[0] + 1, d = q[3] - q[1] + 1;
                    if (w >= 15 && d >= 15) {
                        // 공원: 성벽 가까이 두세 곳
                        boolean free = true;
                        for (Rect rc : reserved) if (rc.overlaps(q[0], q[1], q[2], q[3])) free = false;
                        for (int[] c : new int[][]{{q[0], q[1]}, {q[2], q[1]}, {q[0], q[3]}, {q[2], q[3]}}) if (Build.polySigned(W, c[0], c[1]) < 7) free = false;
                        if (free && parks < 3 && r.nextFloat() < 0.06f) {
                            parks++;
                            garden(b, q[0] + 1, q[1] + 1, q[2] - 1, q[3] - 1, by, r);
                            continue;
                        }
                        cityBlock(b, q[0], q[1], q[2], q[3], by, cx, cz, W, reserved, r);
                    } else {
                        // 큰길 옆 한 줄 (큰길을 봄)
                        Direction face = w < d ? (q[0] > cx ? Direction.WEST : Direction.EAST) : (q[1] > cz ? Direction.NORTH : Direction.SOUTH);
                        row(b, q[0], q[1], q[2], q[3], face, by, cx, cz, W, reserved, r, true);
                    }
                }
            }

        // ---- 골목 교차점 · 큰길 가로등
        for (int kx = -5; kx <= 5; kx++)
            for (int kz = -5; kz <= 5; kz++) {
                int lx = cx + kx * 24 - 12, lz = cz + kz * 24 - 12;
                if (Build.polySigned(W, lx, lz) < 8) continue;
                boolean inRes = false;
                for (Rect rc : reserved) if (rc.overlaps(lx - 1, lz - 1, lx - 1, lz - 1)) inRes = true;
                if (!inRes) Build.lamp(b, lx - 1, lz - 1, by);
            }
        for (int k = -116; k <= 112; k += 12) {
            if (Math.abs(k) < 24) continue;
            if (k > -44) {
                Build.lamp(b, cx + 5, cz + k, by);
                Build.lamp(b, cx - 5, cz + k, by);
            }
            Build.lamp(b, cx + k, cz + 5, by);
            Build.lamp(b, cx + k, cz - 5, by);
        }

        // ---- 시민 · 경비
        for (int k = 0; k < 16; k++) {
            int px, pz;
            if (r.nextBoolean()) {
                px = cx + r.nextInt(5) - 2;
                pz = cz + (24 + r.nextInt(80)) * (r.nextBoolean() ? 1 : -1) / (r.nextBoolean() ? 1 : 2);
            } else {
                px = cx + (24 + r.nextInt(80)) * (r.nextBoolean() ? 1 : -1);
                pz = cz + r.nextInt(5) - 2;
            }
            if (pz < cz - 40) pz = cz + 30;
            b.spawns.add(new Blueprint.Spawn(new BlockPos(px, by + 1, pz), w -> villager(w, VillagerProfession.NONE)));
        }
        for (int k = 0; k < 8; k++) {
            int px = px0 + 4 + r.nextInt(px1 - px0 - 8), pz = pz0 + 3 + r.nextInt(pz1 - pz0 - 6);
            if (Math.abs(px - cx) <= 6 && Math.abs(pz - cz) <= 6) continue;
            b.spawns.add(new Blueprint.Spawn(new BlockPos(px, by + 1, pz), w -> villager(w, VillagerProfession.NONE)));
        }
        for (int[] g : new int[][]{{cx - 5, cz + 106}, {cx + 5, cz + 106}, {cx - 112, cz + 5}, {cx + 106, cz - 5}, {cx - 4, cz - 48}, {cx + 4, cz - 48}})
            b.spawns.add(new Blueprint.Spawn(new BlockPos(g[0], by + 1, g[1]), w -> {
                IronGolemEntity e = EntityType.IRON_GOLEM.create(w);
                if (e != null) e.setPlayerCreated(true);
                return e;
            }));
    }

    /** 큰길 (x 또는 z = c, 폭 11) 이 지나가는 조각을 둘로 나눔. */
    static List<int[]> split(List<int[]> in, int c, boolean alongX) {
        List<int[]> out = new ArrayList<>();
        for (int[] q : in) {
            int a0 = alongX ? q[0] : q[1], a1 = alongX ? q[2] : q[3];
            if (a0 < c - 5 && a1 > c + 5) {
                out.add(alongX ? new int[]{q[0], q[1], c - 6, q[3]} : new int[]{q[0], q[1], q[2], c - 6});
                out.add(alongX ? new int[]{c + 6, q[1], q[2], q[3]} : new int[]{q[0], c + 6, q[2], q[3]});
            } else if (a1 < c - 5 || a0 > c + 5) out.add(q);
            else if (a0 < c - 5) out.add(alongX ? new int[]{q[0], q[1], c - 6, q[3]} : new int[]{q[0], q[1], q[2], c - 6});
            else if (a1 > c + 5) out.add(alongX ? new int[]{c + 6, q[1], q[2], q[3]} : new int[]{q[0], c + 6, q[2], q[3]});
        }
        return out;
    }

    /** 도시 블록: 네 변을 따라 집 (깊이 7, 문은 바깥 길 쪽) + 가운데 안마당. */
    static void cityBlock(Blueprint b, int x0, int z0, int x1, int z1, int by, int cx, int cz, int[][] W, List<Rect> reserved, Random r) {
        int D = 7;
        row(b, x0, z0, x1, z0 + D - 1, Direction.NORTH, by, cx, cz, W, reserved, r, false);
        row(b, x0, z1 - D + 1, x1, z1, Direction.SOUTH, by, cx, cz, W, reserved, r, false);
        if (z1 - z0 + 1 - 2 * D - 2 >= 4) {
            row(b, x0, z0 + D + 1, x0 + D - 1, z1 - D - 1, Direction.WEST, by, cx, cz, W, reserved, r, false);
            row(b, x1 - D + 1, z0 + D + 1, x1, z1 - D - 1, Direction.EAST, by, cx, cz, W, reserved, r, false);
        }
        // 안마당
        int ax0 = x0 + D + 1, ax1 = x1 - D - 1, az0 = z0 + D + 1, az1 = z1 - D - 1;
        if (ax1 - ax0 < 2 || az1 - az0 < 2 || Build.polySigned(W, (ax0 + ax1) / 2.0, (az0 + az1) / 2.0) < 8) return;
        for (Rect rc : reserved) if (rc.overlaps(ax0, az0, ax1, az1)) return;
        int mx = (ax0 + ax1) / 2, mz = (az0 + az1) / 2;
        float f = r.nextFloat();
        if (f < 0.4f) Build.tree(b, mx, mz, by, r, r.nextBoolean() ? Blocks.OAK_LOG : Blocks.BIRCH_LOG, r.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.AZALEA_LEAVES);
        else if (f < 0.6f) {
            b.fill(mx - 1, by, mz - 1, mx + 1, by + 1, mz + 1, s(Blocks.COBBLESTONE));
            b.set(mx, by, mz, s(Blocks.WATER));
            b.set(mx, by + 1, mz, s(Blocks.WATER));
            b.fill(mx - 1, by + 2, mz - 1, mx - 1, by + 3, mz - 1, s(Blocks.OAK_FENCE));
            b.fill(mx + 1, by + 2, mz + 1, mx + 1, by + 3, mz + 1, s(Blocks.OAK_FENCE));
        } else if (f < 0.85f) {
            flowers(b, ax0, az0, ax1 + 1, az1 + 1, by, r, 0, 0, -1, -1, 10);
            b.set(ax0, by + 1, az0, s(Blocks.COMPOSTER));
        } else {
            b.set(ax0, by + 1, az0, s(Blocks.BARREL));
            b.set(ax1, by + 1, az1, s(Blocks.HAY_BLOCK));
            b.set(ax0, by + 1, az1, s(Blocks.CAULDRON));
        }
    }

    /**
     * 집 한 줄: 사각형 (x0..x1, z0..z1) 을 긴 쪽을 따라 폭 5~8 의 집으로 채움 (집 사이 1칸 골목).
     * face = 문이 보는 쪽 (길). 성벽에서 7칸 안쪽 · 예약 구역 밖에만.
     */
    static void row(Blueprint b, int x0, int z0, int x1, int z1, Direction face, int by, int cx, int cz, int[][] W, List<Rect> reserved, Random r, boolean mainStreet) {
        boolean alongX = face.getAxis() == Direction.Axis.Z;
        int start = alongX ? x0 : z0, end = alongX ? x1 : z1;
        int pos = start;
        while (pos <= end - 4) {
            int w = 5 + r.nextInt(4);
            if (end - (pos + w - 1) - 1 < 5) w = end - pos + 1;
            int bx0 = alongX ? pos : x0, bz0 = alongX ? z0 : pos, bx1 = alongX ? pos + w - 1 : x1, bz1 = alongX ? z1 : pos + w - 1;
            pos += w + 1;
            boolean ok = true;
            for (Rect rc : reserved) if (rc.overlaps(bx0, bz0, bx1, bz1)) ok = false;
            for (int[] c : new int[][]{{bx0, bz0}, {bx1, bz0}, {bx0, bz1}, {bx1, bz1}}) if (Build.polySigned(W, c[0], c[1]) < 7) ok = false;
            if (!ok) continue;
            // 동네마다 높이와 양식
            int mx = (bx0 + bx1) / 2 - cx, mz = (bz0 + bz1) / 2 - cz;
            boolean plaza = Math.abs(mx) <= 36 && Math.abs(mz) <= 36, noble = mz < -14;
            Build.Palette p;
            int floors;
            if (plaza || mainStreet) {
                p = r.nextFloat() < 0.5f ? Build.GRAND[r.nextInt(Build.GRAND.length)] : Build.CITY[r.nextInt(Build.CITY.length)];
                floors = r.nextFloat() < 0.6f ? 3 : 2;
            } else if (noble) {
                p = Build.GRAND[r.nextInt(Build.GRAND.length)];
                floors = r.nextFloat() < 0.5f ? 3 : 2;
            } else {
                p = Build.CITY[r.nextInt(Build.CITY.length)];
                float f = r.nextFloat();
                floors = f < 0.3f ? 1 : f < 0.85f ? 2 : 3;
            }
            Build.house(b, bx0, bz0, bx1 - bx0 + 1, bz1 - bz0 + 1, by, floors, face, p, r);
            // 문 옆 화분 · 통 · 간판 등불
            int[] o = Build.doorOutside(bx0, bz0, bx1 - bx0 + 1, bz1 - bz0 + 1, face);
            if (r.nextFloat() < 0.35f) {
                int ox = o[0] + (alongX ? 1 : 0), oz = o[1] + (alongX ? 0 : 1);
                b.set(ox, by + 1, oz, r.nextBoolean() ? s(Blocks.POTTED_RED_TULIP) : s(Blocks.BARREL));
            }
            if (r.nextFloat() < 0.07f)
                b.spawns.add(new Blueprint.Spawn(new BlockPos((bx0 + bx1) / 2, by + 1, (bz0 + bz1) / 2), wd -> villager(wd, VillagerProfession.NONE)));
        }
    }

    static BlockState pathBlock(int x, int y, int z) {
        int hsh = Math.floorMod(Build.hash(x, y, z), 10);
        return hsh < 7 ? s(Blocks.DIRT_PATH) : hsh < 9 ? s(Blocks.GRAVEL) : s(Blocks.COARSE_DIRT);
    }

    static BlockState roadBlock(int across, int x, int y, int z) {
        if (Math.abs(across) == 3) return s(Blocks.POLISHED_ANDESITE);
        int hsh = Math.floorMod(Build.hash(x, y, z), 20);
        return hsh < 2 ? s(Blocks.COBBLESTONE) : hsh < 4 ? s(Blocks.ANDESITE) : hsh < 5 ? s(Blocks.CRACKED_STONE_BRICKS) : s(Blocks.STONE_BRICKS);
    }

    static net.minecraft.entity.Entity named(net.minecraft.entity.Entity e, float yaw) {
        if (e != null) {
            e.setYaw(yaw);
            e.setHeadYaw(yaw);
        }
        return e;
    }

    /**
     * 주민: 생성 중에는 직업 없이 만들고 (거래 목록을 만들면 지도 거래가 구조물을 찾다가 멈춤), 월드에 들어올 때 직업을 줌 (RpgWorld).
     */
    static net.minecraft.entity.Entity villager(net.minecraft.server.world.ServerWorld w, VillagerProfession p) {
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v == null) return null;
        v.setVillagerData(v.getVillagerData().withType(VillagerType.PLAINS).withProfession(VillagerProfession.NONE).withLevel(1));
        if (p != VillagerProfession.NONE) v.addCommandTag(RpgWorld.PROF_TAG + Registries.VILLAGER_PROFESSION.getId(p));
        return v;
    }

    /** 성문: 벽에 구멍 + 양쪽 네모 탑 + 아치 + 쇠창살 + 깃발. ns = 남·북 벽 (x 방향 벽). */
    static void gate(Blueprint b, int gx, int gz, int by, boolean ns, int out) {
        int a = 3, t = 4;
        int ax0 = ns ? gx - a : gx - t, ax1 = ns ? gx + a : gx + t, az0 = ns ? gz - t : gz - a, az1 = ns ? gz + t : gz + a;
        b.fill(ax0, by + 1, az0, ax1, by + 8, az1, s(Blocks.AIR));
        b.fill(ax0, by + 9, az0, ax1, by + 9, az1, s(Blocks.CHISELED_STONE_BRICKS));
        b.fill(ax0, by + 10, az0, ax1, by + 12, az1, s(Blocks.STONE_BRICKS));
        if (ns) b.fill(gx - a, by + 8, gz, gx + a, by + 8, gz, s(Blocks.IRON_BARS));
        else b.fill(gx, by + 8, gz - a, gx, by + 8, gz + a, s(Blocks.IRON_BARS));
        b.fn(ax0, by, az0, ax1, by, az1, (x, y, z) -> roadBlock(ns ? x - gx : z - gz, x, y, z));
        int ox = ns ? 7 : 0, oz = ns ? 0 : 7;
        Build.squareTower(b, gx - ox, gz - oz, by, 3, 17, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, true, 10);
        Build.squareTower(b, gx + ox, gz + oz, by, 3, 17, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, true, 10);
        // 탑 바깥 면의 붉은 깃발 (양털 기둥)
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int fx = gx + sgn * ox, fz = gz + sgn * oz;
            int px = ns ? fx : fx + 4 * out, pz = ns ? fz + 4 * out : fz;
            b.fill(px, by + 9, pz, px, by + 14, pz, s(Blocks.RED_WOOL));
            b.set(px, by + 11, pz, s(Blocks.YELLOW_WOOL));
        }
    }

    static BlockState streetBlock(int x, int y, int z) {
        int h = Math.floorMod(Build.hash(x, y, z), 20);
        return h < 7 ? s(Blocks.COBBLESTONE) : h < 11 ? s(Blocks.STONE_BRICKS) : h < 14 ? s(Blocks.ANDESITE) : h < 17 ? s(Blocks.GRAVEL) : h < 19 ? s(Blocks.MOSSY_COBBLESTONE) : s(Blocks.CRACKED_STONE_BRICKS);
    }

    static void garden(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r) {
        for (int k = 0; k < 3; k++) Build.tree(b, x0 + 2 + r.nextInt(Math.max(1, x1 - x0 - 4)), z0 + 2 + r.nextInt(Math.max(1, z1 - z0 - 4)), by, r,
                r.nextBoolean() ? Blocks.OAK_LOG : Blocks.BIRCH_LOG, r.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.BIRCH_LEAVES);
        flowers(b, x0, z0, x1, z1, by, r, 0, 0, -1, -1, 18);
        // 작은 연못
        int px = (x0 + x1) / 2, pz = (z0 + z1) / 2;
        b.fn(px - 2, by, pz - 2, px + 2, by, pz + 2, (x, y, z) -> (Math.abs(x - px) + Math.abs(z - pz) <= 3) ? s(Blocks.WATER) : null);
        b.set(px + 3, by + 1, pz, s(Blocks.LANTERN));
    }

    static void flowers(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, int ex0, int ez0, int ex1, int ez1, int n) {
        Block[] fl = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.GRASS, Blocks.GRASS};
        for (int k = 0; k < n; k++) {
            int x = x0 + r.nextInt(Math.max(1, x1 - x0)), z = z0 + r.nextInt(Math.max(1, z1 - z0));
            if (x >= ex0 && x <= ex1 && z >= ez0 && z <= ez1) continue;
            b.set(x, by + 1, z, s(fl[r.nextInt(fl.length)]));
        }
    }

    // ================================================================ 왕성
    static void castle(Blueprint b, int cx, int cz, int by, Random r) {
        int x0 = cx - 40, x1 = cx + 40, z0 = cz - 104, z1 = cz - 44;
        // 안뜰 바닥: 자갈길 + 잔디
        b.fn(x0, by, z0, x1, by, z1, (x, y, z) -> Math.abs(x - cx) <= 2 ? CapitalPlan.roadBlock(x - cx, x, y, z) : Math.floorMod(Build.hash(x, y, z), 9) == 0 ? s(Blocks.GRAVEL) : null);
        Build.wall(b, x0, z0, x1, z0, by, 14, 3, Blocks.STONE_BRICKS, 8);
        Build.wall(b, x0, z1, x1, z1, by, 14, 3, Blocks.STONE_BRICKS, 8);
        Build.wall(b, x0, z0, x0, z1, by, 14, 3, Blocks.STONE_BRICKS, 8);
        Build.wall(b, x1, z0, x1, z1, by, 14, 3, Blocks.STONE_BRICKS, 8);
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) Build.tower(b, c[0], c[1], by, 6, 24, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, true, null, 6);
        // 성문 (남쪽)
        b.fill(cx - 3, by + 1, z1 - 2, cx + 3, by + 9, z1 + 2, s(Blocks.AIR));
        b.fill(cx - 3, by + 10, z1 - 2, cx + 3, by + 10, z1 + 2, s(Blocks.CHISELED_STONE_BRICKS));
        b.fill(cx - 3, by + 9, z1, cx + 3, by + 9, z1, s(Blocks.IRON_BARS));
        Build.tower(b, cx - 8, z1, by, 4, 18, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, false, null, 6);
        Build.tower(b, cx + 8, z1, by, 4, 18, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, false, null, 6);
        // 큰 탑 둘 (뾰족 지붕)
        Build.tower(b, cx - 24, cz - 90, by, 6, 36, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, true, Direction.EAST, 4);
        Build.tower(b, cx + 24, cz - 90, by, 6, 36, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, true, Direction.WEST, 4);
        greatHall(b, cx, cz - 96, cz - 60, by, r);
        // 안뜰 가로등 · 화단
        for (int k = 0; k < 4; k++) {
            Build.lamp(b, cx - 6, cz - 52 - k * 0, by);
            Build.lamp(b, cx + 6, cz - 52, by);
        }
        for (int[] t : new int[][]{{cx - 30, cz - 52}, {cx + 30, cz - 52}, {cx - 32, cz - 70}, {cx + 32, cz - 70}})
            Build.tree(b, t[0], t[1], by, r, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES);
    }

    /** 왕좌의 방: 가로 29 (x), 세로 37 (z), 높이 13 + 지붕. 북쪽 끝에 단 + 왕좌, 왕. */
    static void greatHall(Blueprint b, int cx, int z0, int z1, int by, Random r) {
        int x0 = cx - 14, x1 = cx + 14, H = 13;
        int W = x1 - x0 + 1;
        int layers = (W + 2) / 2 + 1;
        b.fn(x0 - 1, by, z0 - 1, x1 + 1, by + H + layers, z1 + 1, (x, y, z) -> {
            int ly = y - by;
            boolean inX = x >= x0 && x <= x1, inZ = z >= z0 && z <= z1;
            // 지붕 (용마루 z 방향, 경사는 동·서)
            if (ly > H) {
                int i = ly - H - 1;
                int wx = x0 - 1 + i, ex = x1 + 1 - i;
                if (z < z0 - 1 || z > z1 + 1 || wx > ex) return null;
                if (wx == ex) return s(Blocks.DEEPSLATE_TILES);
                if (x == wx) return Build.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST, false);
                if (x == ex) return Build.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST, false);
                if ((z == z0 || z == z1) && x > wx && x < ex) return Build.weather(s(Blocks.STONE_BRICKS), x, y, z, 5);
                return x > wx && x < ex && inZ ? s(Blocks.AIR) : null;
            }
            if (!inX || !inZ) return null;
            boolean wallX = x == x0 || x == x1, wallZ = z == z0 || z == z1;
            if (ly == 0) {
                if (Math.abs(x - cx) <= 1 && z > z0 + 6) return s(Blocks.RED_WOOL);
                return ((x + z) & 1) == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.POLISHED_DIORITE);
            }
            if (ly == H) return wallX || wallZ ? Build.weather(s(Blocks.STONE_BRICKS), x, y, z, 5) : s(Blocks.DARK_OAK_PLANKS);
            if (wallX || wallZ) {
                // 정문 (남쪽 가운데)
                if (z == z1 && Math.abs(x - cx) <= 2 && ly <= 6) return ly == 6 ? s(Blocks.CHISELED_STONE_BRICKS) : s(Blocks.AIR);
                // 긴 벽의 높은 창
                if (wallX && !wallZ && (z - z0) % 6 == 3 && ly >= 3 && ly <= 9) return s(Blocks.GLASS_PANE);
                if (wallZ && !wallX && z == z0 && Math.abs(x - cx) % 6 == 4 && ly >= 4 && ly <= 10) return s(Blocks.YELLOW_STAINED_GLASS_PANE);
                if ((wallX && (z - z0) % 6 == 0) || (wallZ && (x - x0) % 6 == 0)) return s(Blocks.POLISHED_ANDESITE);
                return Build.weather(s(Blocks.STONE_BRICKS), x, y, z, 5);
            }
            int dz = z - z0;
            // 단 (북쪽 끝)
            if (Math.abs(x - cx) <= 6 && dz >= 1 && dz <= 6) {
                int hgt = dz <= 2 ? 3 : dz <= 4 ? 2 : 1;
                if (ly <= hgt) return Math.abs(x - cx) <= 1 && ly == hgt ? s(Blocks.RED_WOOL) : s(Blocks.QUARTZ_BLOCK);
                // 왕좌
                if (dz == 1 && ly == 4 && x == cx) return Build.stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH, false);
                if (dz == 1 && (ly == 5 || ly == 6) && x == cx) return s(Blocks.GOLD_BLOCK);
                if (dz <= 2 && ly == 4 && Math.abs(x - cx) == 1) return s(Blocks.GOLD_BLOCK);
                if (dz == 1 && ly == 7 && x == cx) return s(Blocks.LANTERN);
            }
            // 붉은 휘장 (왕좌 뒤 벽 앞)
            if (dz == 1 && Math.abs(x - cx) >= 3 && Math.abs(x - cx) <= 5 && ly >= 4 && ly <= 11) return s(Blocks.RED_WOOL);
            // 기둥
            if (Math.abs(x - cx) == 8 && dz >= 8 && (dz - 8) % 6 == 0) return s(Blocks.QUARTZ_PILLAR);
            // 샹들리에
            if (ly == H - 1 && x == cx && dz >= 8 && (dz - 8) % 6 == 3) return s(Blocks.LANTERN).with(LanternBlock.HANGING, true);
            if (ly == H - 1 && Math.abs(x - cx) == 8 && dz >= 8 && (dz - 8) % 6 == 3) return s(Blocks.LANTERN).with(LanternBlock.HANGING, true);
            // 긴 탁자 (양쪽)
            if (ly == 1 && Math.abs(x - cx) == 4 && dz >= 10 && dz <= z1 - z0 - 6) return s(Blocks.DARK_OAK_PLANKS);
            return s(Blocks.AIR);
        });
        // 왕: 단 위, 왕좌 앞 (남쪽을 봄)
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx, by + 3, z0 + 3), w -> named(AWEntities.KING.create(w), 0)));
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx - 4, by + 1, z0 + 9), w -> {
            IronGolemEntity e = EntityType.IRON_GOLEM.create(w);
            if (e != null) e.setPlayerCreated(true);
            return e;
        }));
    }

    private CapitalPlan() {
    }
}

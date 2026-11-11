package com.abilitywar.world;

import com.abilitywar.registry.AWEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
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
 * 수도 「왕도 아르덴」: 둥근 겹 도시 (사용자가 준 그림).
 * <pre>
 *  가운데: 높이 올린 성채 (안쪽 둥근 성벽 + 탑) 위에 푸른 돔의 왕궁 (왕좌의 방, 왕) 과 정원 · 분수
 *  성채 둘레: 안쪽 동네 (A) → 둥근 운하 (다리) → 바깥 동네 (B) → 둥근 바깥 성벽 (푸른 원뿔 탑 16, 성문 4) → 해자 (다리)
 *  길: 큰길 4 (성문으로) + 대각선 길 4 + 골목 8, 둥근 길 3
 *  남쪽: 스폰 광장 (분수) · 시장 광장 (노점) · 동쪽: 모험가 길드 · 여관 · 서쪽: 전직 교관 회관 · 신전 · 북쪽: 병영 · 공원
 *  집: 빈 땅마다 빽빽하게 (푸른·주황 구리 지붕, 1~3층)
 * </pre>
 */
public final class CapitalPlan {
    static final int R_CIT = 38, CIT_H = 6;           // 성채 (안쪽 성벽 가운데 반지름, 올린 높이)
    static final int R_A0 = 44, R_A1 = 57;             // 안쪽 동네
    static final int R_CANAL0 = 59, R_CANAL1 = 64;     // 운하 (물)
    static final int R_B0 = 69, R_B1 = 97;             // 바깥 동네
    static final int R_WALL = 104, WALL_T = 5, WALL_H = 13;
    static final int R_MOAT0 = 110, R_MOAT1 = 118;     // 해자
    public static final int R_EDGE = 121, MARGIN = 2;  // 수도가 평평하게 고르는 끝 (+ MARGIN)
    /** 스폰: 가운데에서 남쪽으로 (스폰 광장). */
    public static final int SPAWN_DZ = 55;

    record Rect(int x0, int z0, int x1, int z1) {
        boolean overlaps(int ax0, int az0, int ax1, int az1) {
            return ax0 <= x1 && ax1 >= x0 && az0 <= z1 && az1 >= z0;
        }
    }

    /** 수도 둘레 (왕령의 비탈이 여기서 시작): 반지름 R_EDGE 의 64각형. */
    static int[][] wall(int cx, int cz) {
        int n = 64;
        int[][] w = new int[n][];
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            w[i] = new int[]{cx + (int) Math.round(Math.cos(a) * R_EDGE), cz + (int) Math.round(Math.sin(a) * R_EDGE)};
        }
        return w;
    }

    // ================================================================ 땅 구역
    static final int Z_NONE = 0, Z_AVENUE = 1, Z_DIAG = 2, Z_LANE = 3, Z_RING = 4, Z_HOUSES = 5, Z_CANAL = 6, Z_BANK = 7, Z_MOAT = 8, Z_CITADEL = 9, Z_WALL = 10, Z_OUT = 11;
    static final double[] LANES = new double[8], DIAGS = new double[4];

    static {
        for (int k = 0; k < 8; k++) LANES[k] = Math.toRadians(22.5 + k * 45);
        for (int k = 0; k < 4; k++) DIAGS[k] = Math.toRadians(45 + k * 90);
    }

    static boolean onRay(double x, double z, double a, double half) {
        double ux = Math.cos(a), uz = Math.sin(a);
        return x * ux + z * uz > 0 && Math.abs(-x * uz + z * ux) <= half;
    }

    /** (가운데 기준) 이 칸이 무슨 땅인지. */
    static int zone(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r > R_EDGE) return Z_NONE;
        if (r < R_CIT + 2) return Z_CITADEL;
        boolean avenue = Math.abs(x) <= 3 || Math.abs(z) <= 3;
        if (r >= R_MOAT0 && r < R_MOAT1) return avenue ? Z_AVENUE : Z_MOAT;
        if (r >= R_WALL - 3 && r < R_WALL + 3) return avenue ? Z_AVENUE : Z_WALL;
        if (r >= R_WALL + 3) return avenue ? Z_AVENUE : Z_OUT;
        if (avenue) return Z_AVENUE;
        if (r >= R_CANAL0 && r < R_CANAL1) {
            for (double a : DIAGS) if (onRay(x, z, a, 2.5)) return Z_DIAG;
            for (double a : LANES) if (onRay(x, z, a, 1.5)) return Z_LANE;
            return Z_CANAL;
        }
        if (r < R_A0 || (r >= R_A1 && r < R_A1 + 2) || (r >= R_CANAL1 && r < R_B0) || r >= R_B1) {
            if ((r >= R_A1 && r < R_CANAL0) || (r >= R_CANAL1 && r < R_CANAL1 + 1.5)) return Z_BANK;
            return Z_RING;
        }
        for (double a : DIAGS) if (onRay(x, z, a, 2.5)) return Z_DIAG;
        for (double a : LANES) if (onRay(x, z, a, 1.5)) return Z_LANE;
        return Z_HOUSES;
    }

    public static void build(Blueprint b, int cx, int by, int cz, Random r) {
        int P = by + CIT_H;
        int[][] W = wall(cx, cz);
        b.terraformPoly(W, MARGIN, 1, by, 50, s(Blocks.GRASS_BLOCK), s(Blocks.DIRT));

        // ---- 바닥: 길 · 운하 둑 · 해자 둑
        b.fn(cx - R_EDGE, by, cz - R_EDGE, cx + R_EDGE, by, cz + R_EDGE, (x, y, z) -> {
            int dx = x - cx, dz = z - cz;
            return switch (zone(dx, dz)) {
                case Z_AVENUE -> roadBlock(Math.abs(dx) <= 3 ? dx : dz, x, y, z);
                case Z_DIAG, Z_RING, Z_LANE -> streetBlock(x, y, z);
                case Z_BANK -> s(Blocks.STONE_BRICKS);
                default -> null;
            };
        });
        // ---- 운하 (깊이 3, 바닥 돌) + 해자 (깊이 4)
        b.fn(cx - R_CANAL1, by - 3, cz - R_CANAL1, cx + R_CANAL1, by, cz + R_CANAL1, (x, y, z) -> {
            if (zone(x - cx, z - cz) != Z_CANAL) return null;
            return y == by - 3 ? s(Blocks.STONE_BRICKS) : s(Blocks.WATER);
        });
        b.fn(cx - R_MOAT1, by - 4, cz - R_MOAT1, cx + R_MOAT1, by, cz + R_MOAT1, (x, y, z) -> {
            if (zone(x - cx, z - cz) != Z_MOAT) return null;
            return y == by - 4 ? s(Blocks.GRAVEL) : s(Blocks.WATER);
        });
        // 운하 난간 (둑 가장자리) + 다리 난간
        b.fn(cx - R_CANAL1 - 2, by + 1, cz - R_CANAL1 - 2, cx + R_CANAL1 + 2, by + 1, cz + R_CANAL1 + 2, (x, y, z) -> {
            int dx = x - cx, dz = z - cz;
            double rr = Math.sqrt(dx * dx + dz * dz);
            int zn = zone(dx, dz);
            if (zn == Z_BANK && (Math.abs(rr - (R_CANAL0 - 0.5)) < 0.6 || Math.abs(rr - (R_CANAL1 + 0.3)) < 0.6)) return Math.floorMod(x + z, 6) == 0 ? s(Blocks.LANTERN) : s(Blocks.STONE_BRICK_WALL);
            if (rr >= R_CANAL0 && rr < R_CANAL1 && zn != Z_CANAL) {
                // 다리 가장자리 (옆이 물이면 난간)
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    if (zone(dx + d[0], dz + d[1]) == Z_CANAL) return s(Blocks.STONE_BRICK_WALL);
            }
            return null;
        });
        // 해자 다리 난간 + 기둥
        b.fn(cx - R_MOAT1 - 1, by - 4, cz - R_MOAT1 - 1, cx + R_MOAT1 + 1, by + 1, cz + R_MOAT1 + 1, (x, y, z) -> {
            int dx = x - cx, dz = z - cz;
            double rr = Math.sqrt(dx * dx + dz * dz);
            if (rr < R_MOAT0 || rr >= R_MOAT1 || zone(dx, dz) != Z_AVENUE) return null;
            int across = Math.abs(dx) <= 3 ? dx : dz;
            if (y == by + 1) return Math.abs(across) == 3 ? s(Blocks.STONE_BRICK_WALL) : null;
            if (y < by && Math.abs(across) == 3 && Math.floorMod((Math.abs(dx) <= 3 ? z : x), 4) == 0) return s(Blocks.STONE_BRICKS);
            return y < by ? null : s(Blocks.STONE_BRICKS);
        });

        // ---- 바깥 둥근 성벽 + 탑 16 + 성문 4
        ringWall(b, cx, cz, R_WALL, WALL_T, by, WALL_H, Blocks.STONE_BRICKS, 10);
        for (int k = 0; k < 16; k++) {
            if (k % 4 == 0) continue; // 성문 자리
            double a = k * Math.PI / 8;
            Build.tower(b, cx + (int) Math.round(Math.cos(a) * R_WALL), cz + (int) Math.round(Math.sin(a) * R_WALL), by, 5, 21, Blocks.STONE_BRICKS,
                    Blocks.WAXED_OXIDIZED_CUT_COPPER, true, null, 8);
        }
        gate(b, cx, cz + R_WALL, by, true, 1);
        gate(b, cx, cz - R_WALL, by, true, -1);
        gate(b, cx - R_WALL, cz, by, false, -1);
        gate(b, cx + R_WALL, cz, by, false, 1);
        for (int k = 0; k < 64; k++) {
            double a = k * Math.PI / 32 + Math.PI / 64;
            b.set(cx + (int) Math.round(Math.cos(a) * R_WALL), by + WALL_H + 1, cz + (int) Math.round(Math.sin(a) * R_WALL), s(Blocks.LANTERN));
        }

        // ---- 성채 (올린 땅) + 안쪽 성벽 + 탑 + 남쪽 계단 · 문
        b.fn(cx - R_CIT, by + 1, cz - R_CIT, cx + R_CIT, P, cz + R_CIT, (x, y, z) -> {
            double rr = Math.hypot(x - cx, z - cz);
            if (rr >= R_CIT - 1) return null;
            return y == P ? s(Blocks.GRASS_BLOCK) : s(Blocks.DIRT);
        });
        ringWall(b, cx, cz, R_CIT, 3, by, CIT_H + 9, Blocks.STONE_BRICKS, 6);
        for (int k = 0; k < 8; k++) {
            if (k == 2) continue; // 남쪽 문
            double a = k * Math.PI / 4;
            Build.tower(b, cx + (int) Math.round(Math.cos(a) * R_CIT), cz + (int) Math.round(Math.sin(a) * R_CIT), by, 4, CIT_H + 15, Blocks.STONE_BRICKS,
                    Blocks.WAXED_OXIDIZED_CUT_COPPER, true, null, 4);
        }
        // 남쪽 문 (성채 위 높이) + 계단 (길 → 성채)
        b.fill(cx - 3, P + 1, cz + R_CIT - 2, cx + 3, P + 6, cz + R_CIT + 2, s(Blocks.AIR));
        b.fill(cx - 3, P, cz + R_CIT - 2, cx + 3, P, cz + R_CIT + 2, s(Blocks.POLISHED_ANDESITE));
        b.fill(cx - 3, P + 7, cz + R_CIT - 2, cx + 3, P + 7, cz + R_CIT + 2, s(Blocks.CHISELED_STONE_BRICKS));
        b.fill(cx - 3, P + 6, cz + R_CIT, cx + 3, P + 6, cz + R_CIT, s(Blocks.IRON_BARS));
        for (int k = 0; k <= 5; k++) {
            int z = cz + R_CIT + 8 - k, top = by + 1 + k;
            b.fill(cx - 3, by + 1, z, cx + 3, top - 1, z, s(Blocks.STONE_BRICKS));
            b.fill(cx - 3, top, z, cx + 3, top, z, Build.stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
            b.fill(cx - 4, by + 1, z, cx - 4, top + 1, z, s(Blocks.STONE_BRICK_WALL));
            b.fill(cx + 4, by + 1, z, cx + 4, top + 1, z, s(Blocks.STONE_BRICK_WALL));
        }
        b.fill(cx - 3, by + 1, cz + R_CIT + 3, cx + 3, P, cz + R_CIT + 2, s(Blocks.STONE_BRICKS));
        b.fill(cx - 4, P + 1, cz + R_CIT + 2, cx - 4, P + 1, cz + R_CIT + 3, s(Blocks.LANTERN));
        b.fill(cx + 4, P + 1, cz + R_CIT + 2, cx + 4, P + 1, cz + R_CIT + 3, s(Blocks.LANTERN));
        palace(b, cx, cz, P, r);
        citadelGardens(b, cx, cz, P, r);

        // ---- 특별한 곳 (집보다 먼저)
        List<Rect> reserved = new ArrayList<>();
        spawnPlaza(b, cx, cz, by, r, reserved);
        market(b, cx, cz, by, r, reserved);
        guildHall(b, cx + 71, cz + 7, cx + 93, cz + 25, by, r, reserved);
        classMasterHall(b, cx - 93, cz + 7, cx - 71, cz + 25, by, r, reserved);
        temple(b, cx - 93, cz - 25, cx - 71, cz - 7, by, r, reserved);
        inn(b, cx + 71, cz - 25, cx + 93, cz - 7, by, r, reserved);
        barracks(b, cx + 7, cz - 93, cx + 25, cz - 74, by, r, reserved);
        park(b, cx - 25, cz - 93, cx - 7, cz - 74, by, r, reserved);
        smithy(b, cx + 30, cz + 72, cx + 44, cz + 84, by, r, reserved);

        // ---- 집으로 빽빽하게
        houses(b, cx, cz, by, r, reserved);

        // ---- 길 가로등
        for (int rr = R_A0; rr <= R_B1; rr += 12)
            for (int k = 0; k < 4; k++) {
                int[] q = {0, 1, 0, -1, 1, 0, -1, 0};
                int dx = q[k * 2], dz = q[k * 2 + 1];
                int lx = cx + dx * rr + (dz != 0 ? 5 : 0), lz = cz + dz * rr + (dx != 0 ? 5 : 0);
                if (zone(lx - cx, lz - cz) == Z_HOUSES || zone(lx - cx, lz - cz) == Z_CANAL) continue;
                Build.lamp(b, lx, lz, by);
            }

        // ---- 시민 · 경비
        for (int k = 0; k < 18; k++) {
            double a = r.nextDouble() * Math.PI * 2;
            int rr = r.nextBoolean() ? 41 + r.nextInt(2) : 66 + r.nextInt(2);
            int px = cx + (int) Math.round(Math.cos(a) * rr), pz = cz + (int) Math.round(Math.sin(a) * rr);
            b.spawns.add(new Blueprint.Spawn(new BlockPos(px, by + 1, pz), w -> villager(w, VillagerProfession.NONE)));
        }
        for (int[] g : new int[][]{{-5, R_WALL - 6}, {5, R_WALL - 6}, {-(R_WALL - 6), 5}, {R_WALL - 6, -5}, {5, -(R_WALL - 6)}, {-3, R_CIT + 10}, {3, R_CIT + 10}})
            b.spawns.add(new Blueprint.Spawn(new BlockPos(cx + g[0], by + 1, cz + g[1]), w -> {
                IronGolemEntity e = EntityType.IRON_GOLEM.create(w);
                if (e != null) e.setPlayerCreated(true);
                return e;
            }));
    }

    // ================================================================ 둥근 성벽
    static void ringWall(Blueprint b, int cx, int cz, int R, int t, int by, int h, Block mat, int weather) {
        double half = t / 2.0;
        int e = R + t;
        b.fn(cx - e, by, cz - e, cx + e, by + h + 1, cz + e, (x, y, z) -> {
            double d = Math.hypot(x - cx, z - cz);
            if (Math.abs(d - R) > half) return null;
            int ly = y - by;
            if (ly <= h) return ly == h && Math.abs(d - R) < half - 1 ? s(Blocks.POLISHED_ANDESITE) : Build.weather(s(mat), x, y, z, weather);
            return d >= R + half - 1 && Math.floorMod(x + z, 2) == 0 ? Build.weather(s(mat), x, y, z, weather) : null;
        });
    }

    /** 성문: 벽에 구멍 + 양쪽 둥근 탑 (원뿔 지붕) + 아치 + 쇠창살 + 깃발. ns = 남·북 (z 방향 큰길). out = 바깥쪽 부호. */
    static void gate(Blueprint b, int gx, int gz, int by, boolean ns, int out) {
        int a = 3, t = 5;
        int ax0 = ns ? gx - a : gx - t, ax1 = ns ? gx + a : gx + t, az0 = ns ? gz - t : gz - a, az1 = ns ? gz + t : gz + a;
        b.fill(ax0, by + 1, az0, ax1, by + 8, az1, s(Blocks.AIR));
        b.fill(ax0, by + 9, az0, ax1, by + 9, az1, s(Blocks.CHISELED_STONE_BRICKS));
        b.fill(ax0, by + 10, az0, ax1, by + WALL_H, az1, s(Blocks.STONE_BRICKS));
        if (ns) b.fill(gx - a, by + 8, gz, gx + a, by + 8, gz, s(Blocks.IRON_BARS));
        else b.fill(gx, by + 8, gz - a, gx, by + 8, gz + a, s(Blocks.IRON_BARS));
        int ox = ns ? 8 : 0, oz = ns ? 0 : 8;
        Build.tower(b, gx - ox, gz - oz, by, 4, 19, Blocks.STONE_BRICKS, Blocks.WAXED_OXIDIZED_CUT_COPPER, true, null, 8);
        Build.tower(b, gx + ox, gz + oz, by, 4, 19, Blocks.STONE_BRICKS, Blocks.WAXED_OXIDIZED_CUT_COPPER, true, null, 8);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int fx = gx + sgn * ox, fz = gz + sgn * oz;
            int px = ns ? fx : fx + 5 * out, pz = ns ? fz + 5 * out : fz;
            b.fill(px, by + 9, pz, px, by + 15, pz, s(Blocks.BLUE_WOOL));
            b.set(px, by + 12, pz, s(Blocks.YELLOW_WOOL));
        }
    }

    // ================================================================ 왕궁 (푸른 돔) + 왕좌의 방
    static void palace(Blueprint b, int cx, int cz, int P, Random r) {
        int H = 14, half = 14;
        b.fn(cx - half, P, cz - half, cx + half, P + 34, cz + half, (x, y, z) -> {
            int dx = x - cx, dz = z - cz, ly = y - P;
            int ax = Math.abs(dx), az = Math.abs(dz);
            double rr = Math.hypot(dx, dz);
            boolean wallX = ax == half, wallZ = az == half;
            if (ly == 0) {
                if (ax <= 1 && dz > -9) return s(Blocks.RED_WOOL);
                return ((x + z) & 1) == 0 ? s(Blocks.POLISHED_DIORITE) : s(Blocks.POLISHED_ANDESITE);
            }
            if (ly <= H) {
                if (wallX || wallZ) {
                    if (dz == half && ax <= 2 && ly <= 6) return ly == 6 ? s(Blocks.CHISELED_QUARTZ_BLOCK) : s(Blocks.AIR);
                    if ((wallX ? az : ax) % 4 == 0) return s(Blocks.QUARTZ_PILLAR);
                    if (ly % 5 == 0) return s(Blocks.SMOOTH_QUARTZ);
                    if ((wallX ? az : ax) % 4 == 2 && ly >= 2 && ly <= H - 3) return s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
                    return s(Blocks.QUARTZ_BRICKS);
                }
                // 안: 기둥 2줄 · 단 · 왕좌 · 휘장 · 샹들리에
                if (ax == 7 && dz >= -6 && (dz + 6) % 5 == 0) return s(Blocks.QUARTZ_PILLAR);
                if (dz <= -9) {
                    int hgt = dz <= -11 ? 2 : 1;
                    if (ax <= 7 && ly <= hgt) return ax <= 1 && ly == hgt ? s(Blocks.RED_WOOL) : s(Blocks.QUARTZ_BLOCK);
                    if (dz == -13 && x == cx && ly == 3) return Build.stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH, false);
                    if (dz == -13 && ax == 0 && (ly == 4 || ly == 5)) return s(Blocks.GOLD_BLOCK);
                    if (dz == -13 && ax == 1 && ly == 3) return s(Blocks.GOLD_BLOCK);
                    if (dz == -13 && ax >= 3 && ax <= 6 && ly >= 3 && ly <= 12) return ax == 3 || ax == 6 ? s(Blocks.YELLOW_WOOL) : s(Blocks.BLUE_WOOL);
                }
                if (ly == H - 1 && (ax == 0 || ax == 7) && dz >= -6 && (dz + 6) % 5 == 2) return s(Blocks.LANTERN).with(LanternBlock.HANGING, true);
                return s(Blocks.AIR);
            }
            // 지붕: 평평한 단 (가운데는 돔 안으로 뚫림) + 난간
            if (ly == H + 1) {
                if (rr < 9.5) return s(Blocks.AIR);
                return s(Blocks.SMOOTH_QUARTZ);
            }
            if (ly == H + 2 && (wallX || wallZ)) return Math.floorMod(x + z, 2) == 0 ? s(Blocks.QUARTZ_BLOCK) : s(Blocks.QUARTZ_SLAB);
            // 드럼 (원통, 창) + 돔 (반구) + 꼭대기 등
            int drumTop = H + 5;
            if (ly > H + 1 && ly <= drumTop) {
                if (rr >= 9.5 && rr < 11) return (ly == H + 3 || ly == H + 4) && Math.floorMod((int) Math.round(Math.atan2(dz, dx) * 12 / Math.PI), 2) == 0
                        ? s(Blocks.LIGHT_BLUE_STAINED_GLASS) : s(Blocks.QUARTZ_BRICKS);
                return null;
            }
            if (ly > drumTop) {
                double dy = ly - drumTop, d3 = Math.sqrt(rr * rr + dy * dy);
                if (d3 >= 10 && d3 < 11.2) return s(Blocks.WAXED_OXIDIZED_CUT_COPPER);
                if (rr < 1.6 && ly >= drumTop + 11 && ly <= drumTop + 13) return ly == drumTop + 13 ? s(Blocks.GOLD_BLOCK) : s(Blocks.QUARTZ_PILLAR);
                if (rr < 0.5 && ly == drumTop + 14) return s(Blocks.LIGHTNING_ROD);
            }
            return null;
        });
        // 돔 안 큰 샹들리에
        b.fill(cx, P + H + 6, cz, cx, P + H + 13, cz, s(Blocks.CHAIN));
        b.set(cx, P + H + 5, cz, s(Blocks.LANTERN).with(LanternBlock.HANGING, true));
        // 모서리 탑 4 (원뿔)
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}})
            Build.tower(b, cx + c[0] * half, cz + c[1] * half, P, 4, 24, Blocks.QUARTZ_BRICKS, Blocks.WAXED_OXIDIZED_CUT_COPPER, true, null, 0);
        // 현관 기둥 (남쪽)
        for (int px = -6; px <= 6; px += 4) b.fill(cx + px, P + 1, cz + half + 3, cx + px, P + 8, cz + half + 3, s(Blocks.QUARTZ_PILLAR));
        b.fill(cx - 7, P + 9, cz + half + 1, cx + 7, P + 9, cz + half + 4, s(Blocks.SMOOTH_QUARTZ));
        b.fill(cx - 7, P, cz + half + 1, cx + 7, P, cz + half + 4, s(Blocks.POLISHED_DIORITE));
        // 왕 + 경비
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx, P + 3, cz - 11), w -> named(AWEntities.KING.create(w), 0)));
        for (int sx = -1; sx <= 1; sx += 2) {
            int gx = cx + sx * 4;
            b.spawns.add(new Blueprint.Spawn(new BlockPos(gx, P + 1, cz + half + 6), w -> {
                IronGolemEntity e = EntityType.IRON_GOLEM.create(w);
                if (e != null) e.setPlayerCreated(true);
                return e;
            }));
        }
    }

    /** 성채 정원: 가운데 길 · 분수 2 · 생울타리 · 꽃밭 · 나무 · 가로등. */
    static void citadelGardens(Blueprint b, int cx, int cz, int P, Random r) {
        b.fn(cx - 2, P, cz + 19, cx + 2, P, cz + R_CIT - 2, (x, y, z) -> Math.abs(x - cx) == 2 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.SMOOTH_QUARTZ));
        b.fn(cx - 24, P, cz + 26, cx + 24, P, cz + 27, (x, y, z) -> Math.abs(x - cx) > 2 ? s(Blocks.POLISHED_ANDESITE) : null);
        for (int sx = -1; sx <= 1; sx += 2) {
            Build.fountain(b, cx + sx * 12, cz + 31, P, 3);
            // 생울타리 + 꽃밭
            int hx = cx + sx * 6;
            b.fill(hx, P + 1, cz + 20, hx, P + 1, cz + 25, s(Blocks.OAK_LEAVES).with(LeavesBlock.PERSISTENT, true));
            b.fill(cx + sx * 20, P + 1, cz + 20, cx + sx * 20, P + 1, cz + 25, s(Blocks.OAK_LEAVES).with(LeavesBlock.PERSISTENT, true));
            for (int fz = cz + 20; fz <= cz + 25; fz++)
                for (int fx = Math.min(hx, cx + sx * 20) + 1; fx < Math.max(hx, cx + sx * 20); fx++)
                    if (Math.floorMod(fx + fz, 3) != 0) b.set(fx, P + 1, fz, s(new Block[]{Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.LILAC, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY}[Math.floorMod(fx * 7 + fz, 5)]));
            for (int[] t : new int[][]{{24, 14}, {26, 0}, {24, -14}, {16, -26}})
                Build.tree(b, cx + sx * t[0], cz + t[1], P, r, Blocks.BIRCH_LOG, Blocks.AZALEA_LEAVES);
            Build.lamp(b, cx + sx * 4, cz + 34, P);
            Build.lamp(b, cx + sx * 4, cz + 22, P);
        }
        Build.tree(b, cx, cz - 28, P, r, Blocks.OAK_LOG, Blocks.FLOWERING_AZALEA_LEAVES);
    }

    // ================================================================ 특별한 곳
    static void reserve(List<Rect> res, int x0, int z0, int x1, int z1) {
        res.add(new Rect(x0, z0, x1, z1));
    }

    static void sign(Blueprint b, int x, int y, int z, Direction face, String key) {
        b.signs.add(new Blueprint.Sign(new BlockPos(x, y, z), face, new String[]{"", key, ""}));
    }

    /** 스폰 광장: 계단 아래 (남쪽), 분수 · 벤치 · 가로등. */
    static void spawnPlaza(Blueprint b, int cx, int cz, int by, Random r, List<Rect> res) {
        int x0 = cx - 14, x1 = cx + 14, z0 = cz + R_CIT + 9, z1 = cz + R_A1 - 1;
        b.fn(x0, by, z0, x1, by, z1, (x, y, z) -> {
            if (Math.hypot(x - cx, z - cz) >= R_A1) return null;
            if (x == x0 || x == x1 || z == z1) return s(Blocks.POLISHED_DIORITE);
            return ((x + z) & 1) == 0 ? s(Blocks.SMOOTH_STONE) : s(Blocks.STONE_BRICKS);
        });
        Build.fountain(b, cx, cz + 50, by, 3);
        for (int sx = -1; sx <= 1; sx += 2) {
            Build.lamp(b, cx + sx * 10, cz + 49, by);
            Build.lamp(b, cx + sx * 6, cz + 55, by);
            b.set(cx + sx * 6, by + 1, cz + 50, Build.stairs(Blocks.SPRUCE_STAIRS, sx < 0 ? Direction.WEST : Direction.EAST, false));
        }
        reserve(res, x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    /** 시장 광장: 남쪽 큰길 양쪽, 노점 + 상인 + 상자 · 통. */
    static void market(Blueprint b, int cx, int cz, int by, Random r, List<Rect> res) {
        int z0 = cz + R_B0 + 1, z1 = cz + R_B1 - 4;
        b.fn(cx - 26, by, z0, cx + 26, by, z1, (x, y, z) -> {
            if (Math.abs(x - cx) <= 3) return null;
            return Math.floorMod(x + z, 4) == 0 ? s(Blocks.POLISHED_ANDESITE) : ((x + z) & 1) == 0 ? s(Blocks.SMOOTH_STONE) : s(Blocks.STONE_BRICKS);
        });
        Block[][] wools = {{Blocks.RED_WOOL, Blocks.WHITE_WOOL}, {Blocks.BLUE_WOOL, Blocks.WHITE_WOOL}, {Blocks.YELLOW_WOOL, Blocks.ORANGE_WOOL},
                {Blocks.GREEN_WOOL, Blocks.LIME_WOOL}, {Blocks.PURPLE_WOOL, Blocks.MAGENTA_WOOL}, {Blocks.PINK_WOOL, Blocks.WHITE_WOOL}};
        VillagerProfession[] trades = {VillagerProfession.FARMER, VillagerProfession.FLETCHER, VillagerProfession.LIBRARIAN, VillagerProfession.ARMORER,
                VillagerProfession.WEAPONSMITH, VillagerProfession.BUTCHER, VillagerProfession.CARTOGRAPHER, VillagerProfession.TOOLSMITH,
                VillagerProfession.SHEPHERD, VillagerProfession.FISHERMAN, VillagerProfession.CLERIC, VillagerProfession.LEATHERWORKER, VillagerProfession.MASON};
        int n = 0;
        for (int sz = z0 + 2; sz <= z1 - 4; sz += 6)
            for (int k = 0; k < 3; k++)
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    int sx = sgn < 0 ? cx - 8 - k * 6 : cx + 6 + k * 6;
                    if (Math.hypot(sx - cx + 1, sz - cz + 1) > R_B1 - 4) continue;
                    Block[] wl = wools[n % wools.length];
                    Build.stall(b, sx, sz, by, wl[0], wl[1], r);
                    VillagerProfession pr = trades[n % trades.length];
                    b.spawns.add(new Blueprint.Spawn(new BlockPos(sx + 1, by + 1, sz + 2), w -> villager(w, pr)));
                    n++;
                }
        for (int k = 0; k < 10; k++) {
            int x = cx + (r.nextBoolean() ? 1 : -1) * (5 + r.nextInt(20)), z = z0 + r.nextInt(z1 - z0);
            b.set(x, by + 1, z, new BlockState[]{s(Blocks.BARREL), s(Blocks.HAY_BLOCK), s(Blocks.PUMPKIN), s(Blocks.MELON), s(Blocks.COMPOSTER)}[r.nextInt(5)]);
        }
        Build.lamp(b, cx - 5, z0 + 1, by);
        Build.lamp(b, cx + 5, z0 + 1, by);
        sign(b, cx - 5, by + 3, z0, Direction.NORTH, "sign.abilitywar.market");
        reserve(res, cx - 27, z0 - 1, cx + 27, z1 + 1);
    }

    /** 모험가 길드: 큰 3층 목조 회관 + 앞마당 게시판 + 접수원. 문은 북쪽 (동쪽 큰길). */
    static void guildHall(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, List<Rect> res) {
        Build.Palette pal = new Build.Palette(Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER,
                Blocks.GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.STONE_BRICKS);
        int hz0 = z0 + 4;
        Build.house(b, x0, hz0, x1 - x0 + 1, z1 - hz0 + 1, by, 3, Direction.NORTH, pal, r);
        int mx = (x0 + x1) / 2;
        // 접수대 (문 안쪽) + 접수원 + 게시판 (안쪽 벽)
        b.fill(mx - 4, by + 1, hz0 + 4, mx + 4, by + 1, hz0 + 4, s(Blocks.DARK_OAK_PLANKS));
        b.set(mx - 4, by + 2, hz0 + 4, s(Blocks.LANTERN));
        b.set(mx + 4, by + 2, hz0 + 4, s(Blocks.LECTERN));
        b.spawns.add(new Blueprint.Spawn(new BlockPos(mx, by + 1, hz0 + 6), w -> named(AWEntities.GUILD_CLERK.create(w), 180)));
        b.fill(x0 + 2, by + 2, hz0 + 7, x0 + 2, by + 3, hz0 + 11, s(Blocks.BOOKSHELF));
        b.fill(x1 - 2, by + 1, hz0 + 7, x1 - 2, by + 1, hz0 + 11, s(Blocks.BARREL));
        // 앞마당: 게시판 (판자 + 등) + 깃발 + 간판
        b.fn(x0, by, z0, x1, by, hz0 - 1, (x, y, z) -> CapitalPlan.streetBlock(x, y, z));
        b.fill(mx - 6, by + 1, z0 + 1, mx - 6, by + 3, z0 + 1, Build.log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(mx - 9, by + 1, z0 + 1, mx - 9, by + 3, z0 + 1, Build.log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(mx - 8, by + 2, z0 + 1, mx - 7, by + 3, z0 + 1, s(Blocks.BIRCH_PLANKS));
        for (int sx : new int[]{x0 + 1, x1 - 1}) {
            b.fill(sx, by + 1, z0 + 1, sx, by + 7, z0 + 1, s(Blocks.DARK_OAK_FENCE));
            b.fill(sx, by + 4, z0 + 2, sx, by + 7, z0 + 2, s(Blocks.RED_WOOL));
            b.set(sx, by + 5, z0 + 2, s(Blocks.YELLOW_WOOL));
        }
        sign(b, mx, by + 4, hz0 - 1, Direction.NORTH, "sign.abilitywar.guild");
        sign(b, mx - 8, by + 4, z0, Direction.NORTH, "sign.abilitywar.guild_board");
        reserve(res, x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    /** 전직 교관 회관 (서쪽): 보라 지붕 2층 + 수련장 허수아비. */
    static void classMasterHall(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, List<Rect> res) {
        Build.Palette guild = new Build.Palette(Blocks.STONE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.PURPUR_STAIRS, Blocks.PURPUR_BLOCK,
                Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.POLISHED_ANDESITE);
        int hz0 = z0 + 3, hx1 = x1 - 6;
        Build.house(b, x0, hz0, hx1 - x0 + 1, z1 - hz0 + 1, by, 2, Direction.NORTH, guild, r);
        int mx = (x0 + hx1) / 2;
        b.spawns.add(new Blueprint.Spawn(new BlockPos(mx, by + 1, hz0 + 4), w -> named(AWEntities.JOB_MASTER.create(w), 180)));
        sign(b, mx, by + 4, hz0 - 1, Direction.NORTH, "sign.abilitywar.class_master");
        // 수련장 (동쪽 옆)
        b.fn(hx1 + 2, by, z0 + 2, x1, by, z1, (x, y, z) -> Math.floorMod(Build.hash(x, y, z), 3) == 0 ? s(Blocks.COARSE_DIRT) : s(Blocks.GRAVEL));
        for (int k = 0; k < 3; k++) {
            int dz = z0 + 5 + k * 5;
            b.spawns.add(new Blueprint.Spawn(new BlockPos(x1 - 2, by + 1, dz), w -> {
                var as = EntityType.ARMOR_STAND.create(w);
                if (as != null) as.setYaw(90);
                return as;
            }));
            b.set(hx1 + 2, by + 1, dz, s(Blocks.TARGET));
        }
        reserve(res, x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    /** 신전 (서쪽 큰길 북쪽): 석영 회당 + 첨탑 2 + 제단 · 긴 의자. 문은 남쪽 (큰길). */
    static void temple(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, List<Rect> res) {
        int tcx = (x0 + x1) / 2;
        DungeonPlans.hall(b, x0, z0, x1, z1 - 2, by, 12, s(Blocks.QUARTZ_BRICKS), s(Blocks.QUARTZ_PILLAR), s(Blocks.POLISHED_DIORITE), s(Blocks.SMOOTH_QUARTZ),
                Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, s(Blocks.WAXED_OXIDIZED_CUT_COPPER), s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), s(Blocks.QUARTZ_BRICKS));
        Build.tower(b, x0, z1 - 2, by, 3, 20, Blocks.QUARTZ_BRICKS, Blocks.WAXED_OXIDIZED_CUT_COPPER, true, null, 0);
        Build.tower(b, x1, z1 - 2, by, 3, 20, Blocks.QUARTZ_BRICKS, Blocks.WAXED_OXIDIZED_CUT_COPPER, true, null, 0);
        b.fill(tcx - 3, by + 1, z0 + 1, tcx + 3, by + 1, z0 + 3, s(Blocks.SMOOTH_QUARTZ));
        b.fill(tcx - 1, by + 2, z0 + 2, tcx + 1, by + 2, z0 + 2, s(Blocks.GOLD_BLOCK));
        b.set(tcx, by + 3, z0 + 2, s(Blocks.ENCHANTING_TABLE));
        for (int z = z0 + 6; z <= z1 - 6; z += 2)
            for (int sx = -1; sx <= 1; sx += 2)
                b.fill(tcx + sx * 2, by + 1, z, tcx + sx * 6, by + 1, z, Build.stairs(Blocks.BIRCH_STAIRS, Direction.SOUTH, false));
        b.spawns.add(new Blueprint.Spawn(new BlockPos(tcx, by + 1, z0 + 5), w -> villager(w, VillagerProfession.CLERIC)));
        sign(b, tcx + 2, by + 6, z1 - 1, Direction.SOUTH, "sign.abilitywar.temple");
        reserve(res, x0 - 4, z0 - 1, x1 + 4, z1 + 1);
    }

    /** 여관 (동쪽 큰길 북쪽): 3층, 문은 남쪽. */
    static void inn(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, List<Rect> res) {
        Build.Palette p = new Build.Palette(Blocks.WHITE_TERRACOTTA, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS,
                Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.STONE_BRICKS);
        Build.house(b, x0, z0, x1 - x0 + 1, z1 - z0 - 2, by, 3, Direction.SOUTH, p, r);
        int mx = (x0 + x1) / 2;
        b.spawns.add(new Blueprint.Spawn(new BlockPos(mx, by + 1, z0 + 4), w -> villager(w, VillagerProfession.NONE)));
        sign(b, mx + 2, by + 3, z1 - 1, Direction.SOUTH, "sign.abilitywar.inn");
        b.fill(x0 + 2, by + 1, z1 - 1, x0 + 2, by + 1, z1 - 1, s(Blocks.BARREL));
        reserve(res, x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    /** 병영 (북쪽 큰길 동쪽): 돌 2층 + 훈련장. */
    static void barracks(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, List<Rect> res) {
        Build.house(b, x0, z0, x1 - x0 + 1, 10, by, 2, Direction.SOUTH, Build.STONE, r);
        b.fn(x0, by, z0 + 11, x1, by, z1, (x, y, z) -> Math.floorMod(Build.hash(x, y, z), 3) == 0 ? s(Blocks.COARSE_DIRT) : s(Blocks.GRAVEL));
        for (int k = 0; k < 3; k++) {
            int ax = x0 + 3 + k * 5;
            b.spawns.add(new Blueprint.Spawn(new BlockPos(ax, by + 1, z1 - 1), w -> {
                var as = EntityType.ARMOR_STAND.create(w);
                if (as != null) as.setYaw(180);
                return as;
            }));
        }
        b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + 4, by + 1, z0 + 13), w -> {
            IronGolemEntity e = EntityType.IRON_GOLEM.create(w);
            if (e != null) e.setPlayerCreated(true);
            return e;
        }));
        reserve(res, x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    static void park(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, List<Rect> res) {
        garden(b, x0, z0, x1, z1, by, r);
        b.fn(x0, by, (z0 + z1) / 2, x1, by, (z0 + z1) / 2, (x, y, z) -> s(Blocks.DIRT_PATH));
        reserve(res, x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    static void smithy(Blueprint b, int x0, int z0, int x1, int z1, int by, Random r, List<Rect> res) {
        Build.house(b, x0, z0, x1 - x0 + 1 - 3, z1 - z0 + 1, by, 1, Direction.WEST, Build.STONE, r);
        int ox = x1 - 2;
        b.set(ox, by + 1, z0 + 1, s(Blocks.ANVIL));
        b.set(ox, by + 1, z0 + 3, s(Blocks.BLAST_FURNACE));
        b.set(ox, by + 1, z0 + 5, s(Blocks.LAVA_CAULDRON));
        b.set(ox, by + 1, z0 + 7, s(Blocks.GRINDSTONE));
        b.set(ox, by + 1, z0 + 9, s(Blocks.SMITHING_TABLE));
        b.spawns.add(new Blueprint.Spawn(new BlockPos(ox - 1, by + 1, z0 + 4), w -> villager(w, VillagerProfession.WEAPONSMITH)));
        sign(b, x0 - 1, by + 3, (z0 + z1) / 2 - 1, Direction.WEST, "sign.abilitywar.smithy");
        reserve(res, x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    // ================================================================ 집 (빈 땅을 빽빽하게)
    static Build.Palette[] ROOF_BLUE, ROOF_ORANGE;

    static Build.Palette pal(Block wall, Block frame, boolean blue) {
        return new Build.Palette(wall, frame, Blocks.SPRUCE_PLANKS, blue ? Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS : Blocks.WAXED_CUT_COPPER_STAIRS,
                blue ? Blocks.WAXED_OXIDIZED_CUT_COPPER : Blocks.WAXED_CUT_COPPER, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.STONE_BRICKS);
    }

    static Build.Palette randomPalette(Random r, boolean noble) {
        Block[] walls = noble ? new Block[]{Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.POLISHED_DIORITE, Blocks.WHITE_TERRACOTTA, Blocks.CALCITE}
                : new Block[]{Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_SANDSTONE, Blocks.BIRCH_PLANKS, Blocks.OAK_PLANKS, Blocks.STONE_BRICKS, Blocks.CALCITE, Blocks.BRICKS, Blocks.MUD_BRICKS};
        Block[] frames = {Blocks.DARK_OAK_LOG, Blocks.SPRUCE_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.OAK_LOG, Blocks.POLISHED_ANDESITE};
        return pal(walls[r.nextInt(walls.length)], frames[r.nextInt(frames.length)], r.nextFloat() < 0.6f);
    }

    static void houses(Blueprint b, int cx, int cz, int by, Random r, List<Rect> res) {
        int N = R_EDGE * 2 + 1;
        boolean[][] occ = new boolean[N][N];
        for (int x = -R_EDGE; x <= R_EDGE; x++)
            for (int z = -R_EDGE; z <= R_EDGE; z++) {
                boolean blocked = zone(x, z) != Z_HOUSES;
                if (!blocked) for (Rect rc : res) if (rc.overlaps(cx + x, cz + z, cx + x, cz + z)) {
                    blocked = true;
                    break;
                }
                occ[x + R_EDGE][z + R_EDGE] = blocked;
            }
        int placed = 0;
        int[][] bands = {{R_B0, R_B1}, {R_A0, R_A1}};
        for (int[] band : bands) {
            boolean noble = band[0] == R_A0;
            for (double rr = band[1] - 4; rr >= band[0] + 3; rr -= 7) {
                double step = 7.5 / rr;
                double off = r.nextDouble() * step;
                for (double a = off; a < Math.PI * 2; a += step) {
                    int hx = (int) Math.round(Math.cos(a) * rr), hz = (int) Math.round(Math.sin(a) * rr);
                    // 크기를 큰 것부터 줄여 가며 맞춰 봄
                    int[][] sizes = r.nextBoolean() ? new int[][]{{8, 7}, {7, 8}, {7, 6}, {6, 7}, {6, 6}, {5, 6}, {6, 5}, {5, 5}}
                            : new int[][]{{7, 8}, {8, 7}, {6, 7}, {7, 6}, {6, 6}, {6, 5}, {5, 6}, {5, 5}};
                    for (int[] sz : sizes) {
                        int w = sz[0], d = sz[1];
                        int x0 = hx - w / 2, z0 = hz - d / 2;
                        if (!free(occ, x0, z0, w, d)) continue;
                        // 문: 가까운 길 쪽
                        Direction door = doorTowardStreet(x0, z0, w, d);
                        int floors = noble ? (r.nextFloat() < 0.6f ? 3 : 2) : (r.nextFloat() < 0.25f ? 1 : r.nextFloat() < 0.75f ? 2 : 3);
                        Build.house(b, cx + x0, cz + z0, w, d, by, floors, door, randomPalette(r, noble), r);
                        mark(occ, x0 - 1, z0 - 1, w + 2, d + 2);
                        int[] o = Build.doorOutside(cx + x0, cz + z0, w, d, door);
                        for (int k = 0; k < 3; k++) {
                            int px = o[0] + door.getOffsetX() * k - cx, pz = o[1] + door.getOffsetZ() * k - cz;
                            int zn = zone(px, pz);
                            if (zn != Z_HOUSES) break;
                            b.set(cx + px, by, cz + pz, pathBlock(px, by, pz));
                        }
                        if (r.nextFloat() < 0.3f) {
                            int fx = o[0] + (door.getAxis() == Direction.Axis.Z ? 1 : 0), fz = o[1] + (door.getAxis() == Direction.Axis.X ? 1 : 0);
                            b.set(fx, by + 1, fz, r.nextBoolean() ? s(Blocks.POTTED_RED_TULIP) : s(Blocks.BARREL));
                        }
                        if (r.nextFloat() < 0.05f)
                            b.spawns.add(new Blueprint.Spawn(new BlockPos(cx + x0 + w / 2, by + 1, cz + z0 + d / 2), wd -> villager(wd, VillagerProfession.NONE)));
                        placed++;
                        break;
                    }
                }
            }
        }
        // 남은 빈 땅: 나무 · 꽃
        for (int k = 0; k < 140; k++) {
            int x = r.nextInt(N) - R_EDGE, z = r.nextInt(N) - R_EDGE;
            if (!free(occ, x - 1, z - 1, 3, 3)) continue;
            if (r.nextFloat() < 0.35f) {
                Build.tree(b, cx + x, cz + z, by, r, r.nextBoolean() ? Blocks.OAK_LOG : Blocks.BIRCH_LOG, r.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.AZALEA_LEAVES);
                mark(occ, x - 2, z - 2, 5, 5);
            } else b.set(cx + x, by + 1, cz + z, s(new Block[]{Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.GRASS, Blocks.GRASS}[r.nextInt(5)]));
        }
        com.abilitywar.AbilityWar.LOG.debug("[rpg] capital houses: {}", placed);
    }

    static boolean free(boolean[][] occ, int x0, int z0, int w, int d) {
        int N = occ.length;
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++) {
                int ix = x + R_EDGE, iz = z + R_EDGE;
                if (ix < 0 || iz < 0 || ix >= N || iz >= N || occ[ix][iz]) return false;
            }
        return true;
    }

    static void mark(boolean[][] occ, int x0, int z0, int w, int d) {
        int N = occ.length;
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++) {
                int ix = x + R_EDGE, iz = z + R_EDGE;
                if (ix >= 0 && iz >= 0 && ix < N && iz < N) occ[ix][iz] = true;
            }
    }

    /** 집 네 변에서 바깥으로 가장 가까운 길 (집 땅이 아닌 곳) 이 있는 쪽. */
    static Direction doorTowardStreet(int x0, int z0, int w, int d) {
        Direction best = Direction.SOUTH;
        int bestDist = 99;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            int sx = dir == Direction.WEST ? x0 - 1 : dir == Direction.EAST ? x0 + w : x0 + w / 2;
            int sz = dir == Direction.NORTH ? z0 - 1 : dir == Direction.SOUTH ? z0 + d : z0 + d / 2;
            for (int k = 0; k < 8; k++) {
                int zn = zone(sx + dir.getOffsetX() * k, sz + dir.getOffsetZ() * k);
                if (zn == Z_AVENUE || zn == Z_DIAG || zn == Z_LANE || zn == Z_RING || zn == Z_BANK) {
                    if (k < bestDist) {
                        bestDist = k;
                        best = dir;
                    }
                    break;
                }
                if (zn != Z_HOUSES) break;
            }
        }
        return best;
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
    static net.minecraft.entity.Entity villager(net.minecraft.server.world.ServerWorld w, VillagerProfession p) {
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v == null) return null;
        v.setVillagerData(v.getVillagerData().withType(VillagerType.PLAINS).withProfession(VillagerProfession.NONE).withLevel(1));
        if (p != VillagerProfession.NONE) v.addCommandTag(RpgWorld.PROF_TAG + Registries.VILLAGER_PROFESSION.getId(p));
        return v;
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

    private CapitalPlan() {
    }
}

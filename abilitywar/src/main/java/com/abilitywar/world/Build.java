package com.abilitywar.world;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.Direction;

import java.util.Random;

/** 건물 부품: 집 (박공지붕 · 창 · 문 · 실내), 원형 탑, 성벽, 가로등, 나무, 분수, 노점, 길. 좌표는 모두 월드 좌표. */
public final class Build {
    public static BlockState s(Block b) {
        return b.getDefaultState();
    }

    static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    /** 돌벽에 이끼·금 간 벽돌을 섞음. */
    public static BlockState weather(BlockState base, int x, int y, int z, int pct) {
        if (base.getBlock() != Blocks.STONE_BRICKS || pct <= 0) return base;
        int h = Math.floorMod(hash(x, y, z), 100);
        return h < pct / 2 ? s(Blocks.MOSSY_STONE_BRICKS) : h < pct ? s(Blocks.CRACKED_STONE_BRICKS) : base;
    }

    public static BlockState stairs(Block b, Direction facing, boolean top) {
        return b.getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
    }

    public static BlockState log(Block b, Direction.Axis axis) {
        BlockState st = b.getDefaultState();
        return st.contains(PillarBlock.AXIS) ? st.with(PillarBlock.AXIS, axis) : st;
    }

    public static BlockState leaves(Block b) {
        return b.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    }

    // ================================================================ 집
    public record Palette(Block wall, Block frame, Block floor, Block roof, Block roofTop, Block window, Block door, Block base) {
    }

    public static final Palette OAK = new Palette(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.GLASS_PANE, Blocks.OAK_DOOR, Blocks.COBBLESTONE);
    public static final Palette WHITE = new Palette(Blocks.WHITE_TERRACOTTA, Blocks.DARK_OAK_LOG, Blocks.OAK_PLANKS, Blocks.BRICK_STAIRS, Blocks.BRICKS, Blocks.GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.STONE_BRICKS);
    public static final Palette SPRUCE = new Palette(Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.OAK_PLANKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.COBBLESTONE);
    public static final Palette STONE = new Palette(Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.STONE_BRICKS);
    public static final Palette BIRCH = new Palette(Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.OAK_PLANKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICKS, Blocks.GLASS_PANE, Blocks.BIRCH_DOOR, Blocks.COBBLESTONE);
    public static final Palette BRICK = new Palette(Blocks.BRICKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.STONE_BRICKS);
    public static final Palette DARK = new Palette(Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS, Blocks.GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.COBBLESTONE);
    public static final Palette PLASTER = new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.DARK_OAK_LOG, Blocks.OAK_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.GLASS_PANE, Blocks.OAK_DOOR, Blocks.COBBLESTONE);
    public static final Palette NOBLE = new Palette(Blocks.POLISHED_DIORITE, Blocks.STRIPPED_BIRCH_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.GLASS_PANE, Blocks.BIRCH_DOOR, Blocks.POLISHED_ANDESITE);
    public static final Palette[] HOUSES = {OAK, WHITE, SPRUCE, STONE, BIRCH};
    public static final Palette[] CITY = {OAK, WHITE, SPRUCE, BIRCH, BRICK, DARK, PLASTER, WHITE, BRICK};
    public static final Palette[] GRAND = {STONE, WHITE, NOBLE, BRICK, NOBLE};

    /**
     * 집: (x0, z0) 에서 가로 w (x), 세로 d (z), 층 floors (층마다 4칸), 문은 door 쪽 벽 가운데.
     * 지붕 용마루는 긴 쪽을 따라감. 1층 바닥 = base.
     */
    public static void house(Blueprint b, int x0, int z0, int w, int d, int base, int floors, Direction door, Palette p, Random r) {
        boolean alongZ = d > w; // 용마루 방향
        // 정규화: 용마루가 x 를 따라가는 좌표 (u, v) 로 계산하고 필요하면 바꿈
        int W = alongZ ? d : w, D = alongZ ? w : d;
        int yR = base + floors * 4;
        int layers = (D + 2) / 2 + 1;
        boolean bed = r.nextFloat() < 0.6f, ladderLeft = r.nextBoolean();
        int seed = r.nextInt();
        Direction doorDir = door;
        b.fn(x0 - 1, base, z0 - 1, x0 + w, yR + layers, z0 + d, (x, y, z) -> {
            int u = alongZ ? z - z0 : x - x0, v = alongZ ? x - x0 : z - z0; // u: 0..W-1 (용마루 방향), v: 0..D-1
            int ly = y - base;
            boolean inU = u >= 0 && u < W, inV = v >= 0 && v < D;
            // ---- 지붕 (yR 이상)
            if (y >= yR) {
                int i = y - yR;
                int vn = -1 + i, vs = D - i; // 북쪽/남쪽 경사가 이 층에서 있는 v
                if (u < -1 || u > W) return null;
                if (vn > vs) return null;
                if (vn == vs) return s(p.roofTop());
                if (v == vn) return orient(stairs(p.roof(), Direction.SOUTH, false), alongZ);
                if (v == vs) return orient(stairs(p.roof(), Direction.NORTH, false), alongZ);
                // 박공 (양 끝 벽)
                if ((u == 0 || u == W - 1) && v > vn && v < vs) return u % 2 == 0 && i % 2 == 1 && v == (D - 1) / 2 ? s(p.window()) : s(p.wall());
                // 다락 바닥
                if (i == 0 && inU && inV) return s(p.floor());
                return y == yR && inU && inV ? s(p.floor()) : (inU && inV && v > vn && v < vs ? s(Blocks.AIR) : null);
            }
            if (!inU || !inV) return null;
            boolean edgeU = u == 0 || u == W - 1, edgeV = v == 0 || v == D - 1;
            int storey = ly / 4, sy = ly % 4;
            // ---- 바닥
            if (sy == 0) {
                if (ly == 0 && (edgeU || edgeV)) return s(p.base());
                // 사다리 구멍
                if (ly > 0 && u == (ladderLeft ? 1 : W - 2) && v == D - 2) return s(Blocks.AIR);
                return s(p.floor());
            }
            // ---- 벽
            if (edgeU || edgeV) {
                if (edgeU && edgeV) return log(p.frame(), Direction.Axis.Y);
                // 문 (1층, door 쪽 벽 가운데)
                Direction side = wallSide(u, v, W, D, alongZ);
                if (storey == 0 && side == doorDir && (sy == 1 || sy == 2) && isMiddle(u, v, W, D, side, alongZ))
                    return doorState(p.door(), doorDir, sy == 1);
                // 창문: 3칸마다, 가운데 높이
                int along = (side == Direction.NORTH || side == Direction.SOUTH) == !alongZ ? u : v;
                int len = (side == Direction.NORTH || side == Direction.SOUTH) == !alongZ ? W : D;
                boolean ladderWall = v == D - 1 && u == (ladderLeft ? 1 : W - 2);
                if (sy == 2 && along % 3 == 1 && along > 0 && along < len - 1 && !ladderWall) return s(p.window());
                // 기둥: 층 경계에 가로 들보
                if (sy == 3 && p.frame() != p.wall()) return log(p.frame(), (side == Direction.NORTH || side == Direction.SOUTH) ? Direction.Axis.X : Direction.Axis.Z);
                return s(p.wall());
            }
            // ---- 실내
            if (sy == 3 && u == W / 2 && v == D / 2) return s(Blocks.LANTERN).with(LanternBlock.HANGING, true);
            int lu = ladderLeft ? 1 : W - 2;
            if (u == lu && v == D - 2 && storey < floors - 1)
                return orient(s(Blocks.LADDER).with(LadderBlock.FACING, Direction.NORTH), alongZ);
            if (storey == 0 && sy == 1) {
                if (u == W - 2 && v == 1 && !ladderLeft) return s(Blocks.CRAFTING_TABLE);
                if (u == 1 && v == 1 && ladderLeft) return s(Blocks.CRAFTING_TABLE);
                if (u == (ladderLeft ? W - 2 : 1) && v == D - 2) return s(Blocks.BARREL);
            }
            if (storey == floors - 1 && sy == 1 && bed && W >= 5 && D >= 4) {
                int bu = ladderLeft ? W - 2 : 1;
                if (u == bu && v == 1) return orient(s(Blocks.RED_BED).with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD), alongZ);
                if (u == bu && v == 2) return orient(s(Blocks.RED_BED).with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT), alongZ);
            }
            return s(Blocks.AIR);
        });
        // 문 앞 계단돌
        int[] dp = doorOutside(x0, z0, w, d, door);
        b.set(dp[0], base, dp[1], s(Blocks.COBBLESTONE));
    }

    /** (u,v) 정규 좌표의 벽이 실제로 어느 쪽을 보는지. */
    static Direction wallSide(int u, int v, int W, int D, boolean alongZ) {
        Direction d;
        if (v == 0) d = Direction.NORTH;
        else if (v == D - 1) d = Direction.SOUTH;
        else if (u == 0) d = Direction.WEST;
        else d = Direction.EAST;
        return alongZ ? transpose(d) : d;
    }

    static boolean isMiddle(int u, int v, int W, int D, Direction side, boolean alongZ) {
        Direction c = alongZ ? transpose(side) : side;
        return (c == Direction.NORTH || c == Direction.SOUTH) ? u == W / 2 : v == D / 2;
    }

    static Direction transpose(Direction d) {
        return switch (d) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case WEST -> Direction.NORTH;
            case EAST -> Direction.SOUTH;
            default -> d;
        };
    }

    /** 정규 좌표에서 만든 방향 블록을 실제 좌표로 (x, z 를 바꾼 경우). */
    static BlockState orient(BlockState s, boolean alongZ) {
        if (!alongZ) return s;
        if (s.contains(StairsBlock.FACING)) return s.with(StairsBlock.FACING, transpose(s.get(StairsBlock.FACING)));
        if (s.contains(LadderBlock.FACING)) return s.with(LadderBlock.FACING, transpose(s.get(LadderBlock.FACING)));
        if (s.contains(BedBlock.FACING)) return s.with(BedBlock.FACING, transpose(s.get(BedBlock.FACING)));
        return s;
    }

    static BlockState doorState(Block door, Direction facing, boolean lower) {
        return door.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                .with(DoorBlock.HINGE, DoorHinge.LEFT);
    }

    static int[] doorOutside(int x0, int z0, int w, int d, Direction door) {
        return switch (door) {
            case NORTH -> new int[]{x0 + w / 2, z0 - 1};
            case SOUTH -> new int[]{x0 + w / 2, z0 + d};
            case WEST -> new int[]{x0 - 1, z0 + d / 2};
            default -> new int[]{x0 + w, z0 + d / 2};
        };
    }

    // ================================================================ 원형 탑
    public static void tower(Blueprint b, int cx, int cz, int base, int r, int h, Block wall, Block roof, boolean cone, Direction doorDir, int weatherPct) {
        int roofH = cone ? (int) (r * 1.8) + 2 : 2;
        b.fn(cx - r, base, cz - r, cx + r, base + h + roofH, cz + r, (x, y, z) -> {
            double dx = x - cx, dz = z - cz, dist = Math.sqrt(dx * dx + dz * dz);
            int ly = y - base;
            if (ly <= h) {
                if (dist >= r + 0.3) return null;
                boolean shell = dist >= r - 1.2;
                if (!shell) return ly == 0 || ly % 7 == 0 ? s(Blocks.SPRUCE_PLANKS) : s(Blocks.AIR);
                // 문
                if (ly >= 1 && ly <= 2 && doorDir != null) {
                    int ox = doorDir.getOffsetX(), oz = doorDir.getOffsetZ();
                    if ((ox != 0 && Math.signum(dx) == ox && Math.abs(dz) < 1) || (oz != 0 && Math.signum(dz) == oz && Math.abs(dx) < 1)) return s(Blocks.AIR);
                }
                // 화살 구멍
                if (ly % 7 == 4 || ly % 7 == 5) {
                    if ((Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6)) return ly % 7 == 4 ? s(Blocks.AIR) : s(Blocks.AIR);
                }
                return weather(s(wall), x, y, z, weatherPct);
            }
            int j = ly - h - 1;
            if (!cone) {
                // 톱니 성가퀴
                if (j == 0 && dist < r + 0.3 && dist >= r - 1.2) {
                    double a = Math.atan2(dz, dx);
                    return ((int) Math.floor((a + Math.PI) / (Math.PI * 2) * (r * 4))) % 2 == 0 ? weather(s(wall), x, y, z, weatherPct) : null;
                }
                return null;
            }
            double rr = (r + 0.6) * (1 - (double) j / roofH);
            if (dist < rr && dist >= rr - 1.4) return s(roof);
            if (j == roofH - 1 && dist < 0.8) return s(roof);
            return null;
        });
        // 꼭대기 바닥 (지붕 없는 탑은 위에 올라갈 수 있게)
        if (!cone) b.fn(cx - r, base + h, cz - r, cx + r, base + h, cz + r, (x, y, z) -> {
            double dx = x - cx, dz = z - cz;
            return dx * dx + dz * dz < (r - 1.2) * (r - 1.2) ? s(Blocks.SPRUCE_PLANKS) : null;
        });
        // 안쪽 등불
        for (int k = 0; k < h; k += 7) b.set(cx, base + k + 3, cz, s(Blocks.LANTERN).with(LanternBlock.HANGING, false));
    }

    // ================================================================ 성벽 (x 또는 z 방향 직선)
    public static void wall(Blueprint b, int x0, int z0, int x1, int z1, int base, int h, int t, Block mat, int weatherPct) {
        boolean alongX = Math.abs(x1 - x0) >= Math.abs(z1 - z0);
        int ax0 = Math.min(x0, x1), ax1 = Math.max(x0, x1), az0 = Math.min(z0, z1), az1 = Math.max(z0, z1);
        int half = t / 2;
        int bx0 = alongX ? ax0 : ax0 - half, bx1 = alongX ? ax1 : ax0 + half, bz0 = alongX ? az0 - half : az0, bz1 = alongX ? az0 + half : az1;
        b.fn(bx0, base, bz0, bx1, base + h + 1, bz1, (x, y, z) -> {
            int ly = y - base;
            int across = alongX ? z - bz0 : x - bx0, along = alongX ? x : z;
            if (ly <= h) return ly == h && across > 0 && across < t - 1 ? s(Blocks.POLISHED_ANDESITE) : weather(s(mat), x, y, z, weatherPct);
            // 성가퀴: 바깥·안쪽 가장자리
            if ((across == 0 || across == t - 1) && Math.floorMod(along, 2) == 0) return weather(s(mat), x, y, z, weatherPct);
            return null;
        });
    }

    // ================================================================ 다각형 (성벽 모양)
    /** 점에서 선분까지 거리. */
    public static double segDist(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / l2));
        double qx = ax + t * dx - px, qz = az + t * dz - pz;
        return Math.sqrt(qx * qx + qz * qz);
    }

    public static boolean polyInside(int[][] pts, double x, double z) {
        boolean in = false;
        for (int i = 0, j = pts.length - 1; i < pts.length; j = i++) {
            double xi = pts[i][0], zi = pts[i][1], xj = pts[j][0], zj = pts[j][1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    public static double polyDist(int[][] pts, double x, double z) {
        double d = Double.MAX_VALUE;
        for (int i = 0, j = pts.length - 1; i < pts.length; j = i++) d = Math.min(d, segDist(x, z, pts[j][0], pts[j][1], pts[i][0], pts[i][1]));
        return d;
    }

    /** 안쪽이면 +거리, 바깥이면 -거리. */
    public static double polySigned(int[][] pts, double x, double z) {
        double d = polyDist(pts, x, z);
        return polyInside(pts, x, z) ? d : -d;
    }

    /** 다각형 성벽 (두께 t, 높이 h): 위는 걷는 길 + 양쪽 성가퀴. */
    public static void polyWall(Blueprint b, int[][] pts, int base, int h, int t, Block mat, int weatherPct) {
        double half = t / 2.0;
        for (int i = 0, j = pts.length - 1; i < pts.length; j = i++) {
            int ax = pts[j][0], az = pts[j][1], bx = pts[i][0], bz = pts[i][1];
            int x0 = Math.min(ax, bx) - t, x1 = Math.max(ax, bx) + t, z0 = Math.min(az, bz) - t, z1 = Math.max(az, bz) + t;
            b.fn(x0, base, z0, x1, base + h + 1, z1, (x, y, z) -> {
                double d = segDist(x, z, ax, az, bx, bz);
                if (d > half) return null;
                int ly = y - base;
                if (ly <= h) return ly == h && d < half - 1 ? s(Blocks.POLISHED_ANDESITE) : weather(s(mat), x, y, z, weatherPct);
                return d >= half - 1 && Math.floorMod(x + z, 2) == 0 ? weather(s(mat), x, y, z, weatherPct) : null;
            });
        }
    }

    /** 네모 탑: 속 빈 탑 + 층 + 사다리, 위는 성가퀴 (roof = false) 또는 피라미드 지붕. */
    public static void squareTower(Blueprint b, int cx, int cz, int base, int half, int h, Block wall, Block roofStairs, Block roofBlock, boolean roof, int weatherPct) {
        int roofH = roof ? half + 2 : 1;
        b.fn(cx - half - 1, base, cz - half - 1, cx + half + 1, base + h + roofH, cz + half + 1, (x, y, z) -> {
            int dx = Math.abs(x - cx), dz = Math.abs(z - cz), ly = y - base, d = Math.max(dx, dz);
            if (ly <= h) {
                if (d > half) return null;
                boolean shell = d == half;
                if (!shell) return ly == 0 || (ly % 6 == 0) ? s(Blocks.SPRUCE_PLANKS) : s(Blocks.AIR);
                if (dx == half && dz == half) return s(Blocks.POLISHED_ANDESITE);
                if (ly % 6 == 3 && (dx == 0 || dz == 0)) return s(Blocks.AIR);
                return weather(s(wall), x, y, z, weatherPct);
            }
            int i = ly - h - 1;
            if (!roof) return d == half && Math.floorMod(x + z, 2) == 0 ? weather(s(wall), x, y, z, weatherPct) : null;
            int ring = half + 1 - i;
            if (ring < 0) return null;
            if (ring == 0) return d == 0 ? s(roofBlock) : null;
            if (d != ring) return d < ring ? (i == 0 ? s(Blocks.SPRUCE_PLANKS) : null) : null;
            if (dx == ring && dz == ring) return s(roofBlock);
            Direction f = dx == ring ? (x > cx ? Direction.WEST : Direction.EAST) : (z > cz ? Direction.NORTH : Direction.SOUTH);
            return stairs(roofStairs, f, false);
        });
        for (int y = base + 1; y < base + h; y++) b.set(cx, y, cz - half + 1, s(Blocks.LADDER).with(LadderBlock.FACING, Direction.SOUTH));
        b.set(cx + 1, base + h - 2, cz + 1, s(Blocks.LANTERN));
    }

    // ================================================================ 소품
    public static void lamp(Blueprint b, int x, int z, int base) {
        b.set(x, base + 1, z, s(Blocks.COBBLESTONE_WALL));
        b.set(x, base + 2, z, s(Blocks.DARK_OAK_FENCE));
        b.set(x, base + 3, z, s(Blocks.DARK_OAK_FENCE));
        b.set(x, base + 4, z, s(Blocks.LANTERN));
    }

    public static void tree(Blueprint b, int x, int z, int base, Random r, Block logB, Block leafB) {
        int h = 4 + r.nextInt(3);
        int lr = 2 + (r.nextFloat() < 0.4f ? 1 : 0);
        int top = base + h;
        b.fn(x - lr, base + h - lr, z - lr, x + lr, top + lr, z + lr, (xx, yy, zz) -> {
            double dx = xx - x, dy = (yy - top) * 1.2, dz = zz - z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > lr + 0.4) return null;
            if (d > lr - 0.4 && Math.floorMod(hash(xx, yy, zz), 4) == 0) return null;
            return leaves(leafB);
        });
        b.fill(x, base + 1, z, x, top, z, log(logB, Direction.Axis.Y));
    }

    /** 둥근 분수: 돌 테두리 + 물 + 가운데 기둥과 위의 물. */
    public static void fountain(Blueprint b, int cx, int cz, int base, int r) {
        b.fn(cx - r, base, cz - r, cx + r, base + 1, cz + r, (x, y, z) -> {
            double dx = x - cx, dz = z - cz, d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 0.3) return null;
            if (y == base) return s(Blocks.STONE_BRICKS);
            if (d >= r - 0.9) return s(Blocks.STONE_BRICKS);
            return s(Blocks.WATER);
        });
        b.fill(cx, base + 1, cz, cx, base + 3, cz, s(Blocks.CHISELED_STONE_BRICKS));
        b.fill(cx - 1, base + 4, cz - 1, cx + 1, base + 4, cz + 1, s(Blocks.SMOOTH_STONE_SLAB));
        b.set(cx, base + 4, cz, s(Blocks.WATER));
    }

    /** 노점: 기둥 4개 + 줄무늬 천막 + 계산대. */
    public static void stall(Blueprint b, int x, int z, int base, Block wool1, Block wool2, Random r) {
        for (int[] c : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) b.fill(x + c[0], base + 1, z + c[1], x + c[0], base + 3, z + c[1], s(Blocks.SPRUCE_FENCE));
        b.fn(x - 1, base + 4, z - 1, x + 3, base + 4, z + 3, (xx, yy, zz) -> (xx - x + 1) % 2 == 0 ? s(wool1) : s(wool2));
        b.set(x + 1, base + 1, z, s(Blocks.BARREL));
        b.set(x, base + 1, z + 1, r.nextBoolean() ? s(Blocks.HAY_BLOCK) : s(Blocks.COMPOSTER));
        b.set(x + 2, base + 1, z + 1, s(Blocks.SPRUCE_SLAB).with(SlabBlock.TYPE, SlabType.TOP));
    }

    private Build() {
    }
}

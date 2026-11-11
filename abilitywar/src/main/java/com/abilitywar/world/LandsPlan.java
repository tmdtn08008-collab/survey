package com.abilitywar.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CarvedPumpkinBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.abilitywar.world.Build.s;

/**
 * 아르덴 왕령: 수도 성벽 밖의 들판 (중심에서 반지름 약 330).
 * <pre>
 *  성문 셋에서 뻗어 나가는 구불구불한 길 (땅을 따라감, 물 위는 나무 다리, 가로등)
 *  길가: 밭 + 농가, 풍차, 목장 (양·소·돼지·닭), 과수원, 외딴 농가, 길가 사당
 *  남쪽 길가의 작은 마을, 길 끝의 초소
 * </pre>
 * 땅 높이는 계획할 때 노이즈로 미리 재서 (Heights), 평평하고 물이 아닌 곳에만 짓는다.
 */
public final class LandsPlan {
    record Plot(int x0, int z0, int x1, int z1) {
        boolean overlaps(Plot o, int pad) {
            return x0 - pad <= o.x1 && x1 + pad >= o.x0 && z0 - pad <= o.z1 && z1 + pad >= o.z0;
        }
    }

    public static void build(Blueprint b, int cx, int by, int cz, Random r, Blueprint.Heights H) {
        List<Plot> used = new ArrayList<>();
        int edge = CapitalPlan.R_EDGE + CapitalPlan.MARGIN;
        used.add(new Plot(cx - edge - 46, cz - edge - 46, cx + edge + 46, cz + edge + 46)); // 수도 (해자 + 넓은 비탈)
        // 해자 둘레 넓은 비탈 (수도 바닥에서 원래 땅까지 40칸에 걸쳐)
        b.terraformPolyRing(CapitalPlan.wall(cx, cz), CapitalPlan.MARGIN, 40, by, s(Blocks.GRASS_BLOCK), s(Blocks.DIRT));
        int[][] dirs = {{0, 1}, {-1, 0}, {1, 0}, {0, -1}};
        int[][] starts = {{cx, cz + edge}, {cx - edge, cz}, {cx + edge, cz}, {cx, cz - edge}};
        List<double[][]> roads = new ArrayList<>();
        for (int i = 0; i < 4; i++) roads.add(roadPoints(starts[i], dirs[i], 180, r));
        for (double[][] pts : roads) {
            road(b, pts, by);
            lamps(b, pts, by, r);
        }

        Ctx c = new Ctx(b, H, used, roads, by, r);
        for (int i = 0; i < 4; i++) {
            double[][] pts = roads.get(i);
            double total = length(pts);
            boolean mill = false, shrine = false, hamlet = i != 0;
            for (double t = 30; t < total - 16; t += 15 + r.nextInt(10)) {
                double[] at = pointAt(pts, t), dir = dirAt(pts, t);
                for (int side = -1; side <= 1; side += 2) {
                    if (r.nextFloat() < 0.12f) continue;
                    double off = 13 + r.nextInt(12);
                    int px = (int) Math.round(at[0] - dir[1] * side * off), pz = (int) Math.round(at[1] + dir[0] * side * off);
                    String type;
                    if (!hamlet && t > 50) type = "hamlet";
                    else if (!mill && t > 40) type = "mill";
                    else if (!shrine && t > 60) type = "shrine";
                    else {
                        float f = r.nextFloat();
                        type = f < 0.5f ? "farm" : f < 0.68f ? "pasture" : f < 0.84f ? "orchard" : "house";
                    }
                    if (type.equals("hamlet")) {
                        double off2 = 26 + r.nextInt(6);
                        px = (int) Math.round(at[0] - dir[1] * side * off2);
                        pz = (int) Math.round(at[1] + dir[0] * side * off2);
                    }
                    if (c.plot(type, px, pz)) {
                        switch (type) {
                            case "mill" -> mill = true;
                            case "shrine" -> shrine = true;
                            case "hamlet" -> hamlet = true;
                            default -> {
                            }
                        }
                    }
                }
            }
            // 길 끝 초소
            double[] end = pts[pts.length - 1];
            c.plot("post", (int) end[0] + dirs[i][0] * 4, (int) end[1] + dirs[i][1] * 4);
        }
    }

    // ================================================================ 길
    static double[][] roadPoints(int[] start, int[] d, int len, Random r) {
        int n = len / 24 + 1;
        double[][] pts = new double[n + 1][];
        double lat = 0;
        for (int k = 0; k <= n; k++) {
            if (k >= 2) lat = Math.max(-30, Math.min(30, lat + r.nextInt(15) - 7));
            double along = k * 24;
            // d = 진행 방향, 옆 = (-d.z, d.x)
            pts[k] = new double[]{start[0] + d[0] * along - d[1] * lat, start[1] + d[1] * along + d[0] * lat};
        }
        return pts;
    }

    static double polyDist(double[][] pts, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int k = 1; k < pts.length; k++) best = Math.min(best, Build.segDist(x, z, pts[k - 1][0], pts[k - 1][1], pts[k][0], pts[k][1]));
        return best;
    }

    static double length(double[][] pts) {
        double l = 0;
        for (int k = 1; k < pts.length; k++) l += Math.hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1]);
        return l;
    }

    static double[] pointAt(double[][] pts, double t) {
        for (int k = 1; k < pts.length; k++) {
            double l = Math.hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1]);
            if (t <= l) return new double[]{pts[k - 1][0] + (pts[k][0] - pts[k - 1][0]) * t / l, pts[k - 1][1] + (pts[k][1] - pts[k - 1][1]) * t / l};
            t -= l;
        }
        return pts[pts.length - 1];
    }

    static double[] dirAt(double[][] pts, double t) {
        for (int k = 1; k < pts.length; k++) {
            double dx = pts[k][0] - pts[k - 1][0], dz = pts[k][1] - pts[k - 1][1], l = Math.hypot(dx, dz);
            if (t <= l || k == pts.length - 1) return new double[]{dx / l, dz / l};
            t -= l;
        }
        return new double[]{0, 1};
    }

    static int[] bbox(double[][] pts, int pad) {
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (double[] q : pts) {
            x0 = Math.min(x0, q[0]);
            z0 = Math.min(z0, q[1]);
            x1 = Math.max(x1, q[0]);
            z1 = Math.max(z1, q[1]);
        }
        return new int[]{(int) Math.floor(x0) - pad, (int) Math.floor(z0) - pad, (int) Math.ceil(x1) + pad, (int) Math.ceil(z1) + pad};
    }

    /** 길: 땅을 따라 (나무 사이는 뚫음), 물 위는 나무 다리 + 난간. */
    static void road(Blueprint b, double[][] pts, int by) {
        int[] bb = bbox(pts, 4);
        b.surface(bb[0], bb[1], bb[2], bb[3], by - 64, by + 64, (p, x, z) -> {
            double d = polyDist(pts, x, z);
            if (d > 2.6) return;
            int y = p.surface(x, z, p.top(x, z) + 24);
            if (p.water(x, y, z)) {
                p.set(x, y + 1, z, s(Blocks.SPRUCE_PLANKS));
                if (d > 1.9) p.set(x, y + 2, z, s(Blocks.SPRUCE_FENCE));
                if (Math.floorMod(x + z, 8) == 0 && d > 1.9) for (int k = 0; k < 6; k++) if (p.water(x, y - k, z)) p.set(x, y - k, z, s(Blocks.STRIPPED_SPRUCE_LOG));
                return;
            }
            int h = Math.floorMod(Build.hash(x, y, z), 10);
            BlockState st = d <= 1.6 ? (h < 5 ? s(Blocks.DIRT_PATH) : h < 8 ? s(Blocks.GRAVEL) : s(Blocks.COBBLESTONE)) : (h < 6 ? s(Blocks.DIRT_PATH) : s(Blocks.COARSE_DIRT));
            p.set(x, y, z, st);
            for (int k = 1; k <= 6; k++) if (!p.get(x, y + k, z).isAir()) p.set(x, y + k, z, s(Blocks.AIR));
        });
    }

    /** 길가 등불: 24칸마다 번갈아 양쪽. */
    static void lamps(Blueprint b, double[][] pts, int by, Random r) {
        double total = length(pts);
        int side = 1;
        for (double t = 12; t < total; t += 24) {
            double[] at = pointAt(pts, t), dir = dirAt(pts, t);
            int lx = (int) Math.round(at[0] - dir[1] * side * 4), lz = (int) Math.round(at[1] + dir[0] * side * 4);
            side = -side;
            b.surface(lx, lz, lx, lz, by - 64, by + 64, (p, x, z) -> {
                int y = p.surface(x, z, p.top(x, z) + 24);
                if (p.water(x, y, z)) return;
                p.set(x, y + 1, z, s(Blocks.COBBLESTONE_WALL));
                p.set(x, y + 2, z, s(Blocks.SPRUCE_FENCE));
                p.set(x, y + 3, z, s(Blocks.SPRUCE_FENCE));
                p.set(x, y + 4, z, s(Blocks.LANTERN));
            });
        }
    }

    /** 땅에서 길까지 좁은 흙길. */
    static void path(Blueprint b, int ax, int az, int bx, int bz, int by) {
        double[][] pts = {{ax, az}, {bx, bz}};
        int[] bb = bbox(pts, 2);
        b.surface(bb[0], bb[1], bb[2], bb[3], by - 64, by + 64, (p, x, z) -> {
            if (polyDist(pts, x, z) > 0.8) return;
            int y = p.surface(x, z, p.top(x, z) + 24);
            if (p.water(x, y, z)) return;
            BlockState cur = p.get(x, y, z);
            if (cur.isOf(Blocks.GRASS_BLOCK) || cur.isOf(Blocks.DIRT) || cur.isOf(Blocks.COARSE_DIRT) || cur.isOf(Blocks.PODZOL) || cur.isOf(Blocks.SAND) || cur.isOf(Blocks.SNOW_BLOCK))
                p.set(x, y, z, s(Blocks.DIRT_PATH));
            for (int k = 1; k <= 3; k++) if (!p.get(x, y + k, z).isAir()) p.set(x, y + k, z, s(Blocks.AIR));
        });
    }

    // ================================================================ 땅 고르기 + 짓기
    static final class Ctx {
        final Blueprint b;
        final Blueprint.Heights H;
        final List<Plot> used;
        final List<double[][]> roads;
        final int by;
        final Random r;

        Ctx(Blueprint b, Blueprint.Heights H, List<Plot> used, List<double[][]> roads, int by, Random r) {
            this.b = b;
            this.H = H;
            this.used = used;
            this.roads = roads;
            this.by = by;
            this.r = r;
        }

        /** (px, pz) 가운데에 type 을 지을 수 있으면 지음. */
        boolean plot(String type, int px, int pz) {
            int[] size = switch (type) {
                case "farm" -> new int[]{17, 21};
                case "pasture" -> new int[]{15, 15};
                case "orchard" -> new int[]{17, 13};
                case "mill" -> new int[]{13, 13};
                case "house" -> new int[]{11, 11};
                case "shrine" -> new int[]{7, 7};
                case "post" -> new int[]{9, 9};
                default -> new int[]{33, 33}; // hamlet
            };
            int x0 = px - size[0] / 2, z0 = pz - size[1] / 2, x1 = x0 + size[0] - 1, z1 = z0 + size[1] - 1;
            Plot pl = new Plot(x0, z0, x1, z1);
            for (Plot u : used) if (u.overlaps(pl, 4)) return false;
            double rad = Math.hypot(size[0], size[1]) / 2 + 3;
            double best = Double.MAX_VALUE;
            double[] near = null;
            for (double[][] rd : roads) {
                for (int k = 1; k < rd.length; k++)
                    for (double t = 0; t <= 1; t += 0.125) {
                        double qx = rd[k - 1][0] + (rd[k][0] - rd[k - 1][0]) * t, qz = rd[k - 1][1] + (rd[k][1] - rd[k - 1][1]) * t;
                        double dd = Math.hypot(qx - px, qz - pz);
                        if (dd < best) {
                            best = dd;
                            near = new double[]{qx, qz};
                        }
                    }
            }
            if (!type.equals("post") && best < rad) return false;
            // 땅: 9곳을 재서 물 없고 높낮이 7 이하
            int[] hs = new int[9];
            int i = 0;
            for (int ix = 0; ix <= 2; ix++)
                for (int iz = 0; iz <= 2; iz++) {
                    int sx = x0 + (x1 - x0) * ix / 2, sz = z0 + (z1 - z0) * iz / 2;
                    if (H.water(sx, sz)) return false;
                    hs[i++] = H.ground(sx, sz);
                }
            java.util.Arrays.sort(hs);
            if (hs[8] - hs[0] > (type.equals("hamlet") ? 11 : 9)) return false;
            int base = hs[4];
            used.add(pl);
            b.terraformRect(x0, z0, x1, z1, 5, base, 16, s(Blocks.GRASS_BLOCK), s(Blocks.DIRT));
            // 길 쪽 방향
            double dx = near == null ? 0 : near[0] - px, dz = near == null ? 1 : near[1] - pz;
            Direction face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            switch (type) {
                case "farm" -> farm(x0, z0, x1, z1, base, face);
                case "pasture" -> pasture(x0, z0, x1, z1, base, face);
                case "orchard" -> orchard(x0, z0, x1, z1, base);
                case "mill" -> mill(px, pz, base, face);
                case "house" -> farmstead(x0, z0, x1, z1, base, face);
                case "shrine" -> shrine(px, pz, base);
                case "post" -> post(px, pz, base);
                default -> hamlet(px, pz, base);
            }
            if (near != null && !type.equals("post")) {
                int ex = face.getAxis() == Direction.Axis.X ? (face == Direction.EAST ? x1 + 1 : x0 - 1) : px;
                int ez = face.getAxis() == Direction.Axis.Z ? (face == Direction.SOUTH ? z1 + 1 : z0 - 1) : pz;
                path(b, ex, ez, (int) Math.round(near[0]), (int) Math.round(near[1]), by);
            }
            return true;
        }

        void spawn(int x, int y, int z, java.util.function.Function<net.minecraft.server.world.ServerWorld, Entity> f) {
            b.spawns.add(new Blueprint.Spawn(new BlockPos(x, y, z), f));
        }

        // ---------------------------------------------------------------- 밭 + 농가
        void farm(int x0, int z0, int x1, int z1, int base, Direction face) {
            // 밭은 길 쪽, 농가는 반대쪽 (z 방향으로 나눔)
            boolean fieldNorth = face != Direction.SOUTH;
            int fz0 = fieldNorth ? z0 : z1 - 11, fz1 = fz0 + 11;
            Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.WHEAT};
            Block crop = crops[r.nextInt(crops.length)];
            boolean pumpkins = r.nextFloat() < 0.15f;
            int age = 3 + r.nextInt(5);
            b.fn(x0, base, fz0, x1, base + 1, fz1, (x, y, z) -> {
                boolean edge = x == x0 || x == x1 || z == fz0 || z == fz1;
                int j = z - fz0 - 1;
                if (y == base) {
                    if (edge) return s(Blocks.COARSE_DIRT);
                    if (j % 5 == 2) return s(Blocks.WATER);
                    return s(Blocks.FARMLAND).with(FarmlandBlock.MOISTURE, 7);
                }
                if (edge) {
                    int gx = (x0 + x1) / 2;
                    boolean gateSide = fieldNorth ? z == fz1 : z == fz0;
                    if (gateSide && x == gx) return s(Blocks.OAK_FENCE_GATE).with(FenceGateBlock.FACING, Direction.NORTH);
                    return s(Blocks.OAK_FENCE);
                }
                if (j % 5 == 2) return null;
                if (pumpkins) return Math.floorMod(Build.hash(x, y, z), 5) == 0 ? s(Blocks.PUMPKIN) : null;
                int max = ((CropBlock) crop).getMaxAge();
                return ((CropBlock) crop).withAge(Math.min(max, age * max / 7));
            });
            // 허수아비
            int sx = x0 + 3, sz = fz0 + 3;
            b.set(sx, base + 1, sz, s(Blocks.OAK_FENCE));
            b.set(sx, base + 2, sz, s(Blocks.HAY_BLOCK));
            b.set(sx, base + 3, sz, s(Blocks.CARVED_PUMPKIN).with(CarvedPumpkinBlock.FACING, face));
            // 농가 + 건초 더미
            int hz0 = fieldNorth ? fz1 + 2 : z0, hw = 7, hd = 6;
            int hx0 = (x0 + x1) / 2 - 3;
            Build.Palette p = new Build.Palette[]{Build.OAK, Build.SPRUCE, Build.BIRCH, Build.PLASTER}[r.nextInt(4)];
            Build.house(b, hx0, hz0, hw, hd, base, r.nextFloat() < 0.3f ? 2 : 1, fieldNorth ? Direction.NORTH : Direction.SOUTH, p, r);
            for (int k = 0; k < 3; k++) b.set(x0 + 1 + k, base + 1, fieldNorth ? z1 - 1 : z0 + 1, s(Blocks.HAY_BLOCK));
            b.set(x1 - 1, base + 1, fieldNorth ? z1 - 1 : z0 + 1, s(Blocks.COMPOSTER));
            spawn((x0 + x1) / 2, base + 1, hz0 + 3, w -> CapitalPlan.villager(w, VillagerProfession.FARMER));
        }

        // ---------------------------------------------------------------- 목장
        void pasture(int x0, int z0, int x1, int z1, int base, Direction face) {
            int gx = (x0 + x1) / 2, gz = (z0 + z1) / 2;
            b.fn(x0, base + 1, z0, x1, base + 1, z1, (x, y, z) -> {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) return null;
                boolean gate = (face == Direction.NORTH && z == z0 && x == gx) || (face == Direction.SOUTH && z == z1 && x == gx)
                        || (face == Direction.WEST && x == x0 && z == gz) || (face == Direction.EAST && x == x1 && z == gz);
                if (gate) return s(Blocks.SPRUCE_FENCE_GATE).with(FenceGateBlock.FACING, face.getAxis() == Direction.Axis.Z ? Direction.NORTH : Direction.EAST);
                return s(Blocks.SPRUCE_FENCE);
            });
            // 지붕 있는 쉼터 + 물통 + 건초
            for (int[] q : new int[][]{{x0 + 1, z0 + 1}, {x0 + 4, z0 + 1}, {x0 + 1, z0 + 4}, {x0 + 4, z0 + 4}})
                b.fill(q[0], base + 1, q[1], q[0], base + 2, q[1], s(Blocks.SPRUCE_FENCE));
            b.fill(x0 + 1, base + 3, z0 + 1, x0 + 4, base + 3, z0 + 4, s(Blocks.SPRUCE_SLAB).with(SlabBlock.TYPE, SlabType.BOTTOM));
            b.set(x1 - 2, base + 1, z1 - 2, s(Blocks.WATER_CAULDRON).with(LeveledCauldronBlock.LEVEL, 3));
            b.set(x1 - 3, base + 1, z1 - 2, s(Blocks.HAY_BLOCK));
            int kind = r.nextInt(4);
            int n = 3 + r.nextInt(3);
            for (int k = 0; k < n; k++) {
                int ax = x0 + 3 + r.nextInt(Math.max(1, x1 - x0 - 5)), az = z0 + 3 + r.nextInt(Math.max(1, z1 - z0 - 5));
                spawn(ax, base + 1, az, w -> {
                    Entity e = switch (kind) {
                        case 0 -> EntityType.SHEEP.create(w);
                        case 1 -> EntityType.COW.create(w);
                        case 2 -> EntityType.PIG.create(w);
                        default -> EntityType.CHICKEN.create(w);
                    };
                    if (e instanceof SheepEntity sh) sh.setColor(DyeColor.values()[w.getRandom().nextInt(4) == 0 ? w.getRandom().nextInt(16) : 0]);
                    return e;
                });
            }
        }

        // ---------------------------------------------------------------- 과수원
        void orchard(int x0, int z0, int x1, int z1, int base) {
            for (int x = x0 + 2; x <= x1 - 2; x += 4)
                for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                    int tx = x, tz = z;
                    Build.tree(b, tx, tz, base, r, Blocks.OAK_LOG, r.nextFloat() < 0.5f ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.OAK_LEAVES);
                }
            b.fn(x0, base, (z0 + z1) / 2 - 2, x1, base, (z0 + z1) / 2 - 2, (x, y, z) -> s(Blocks.DIRT_PATH));
            b.set(x0, base + 1, z0, s(Blocks.BARREL));
            b.set(x0 + 1, base + 1, z0, s(Blocks.BEEHIVE));
        }

        // ---------------------------------------------------------------- 풍차
        void mill(int mx, int mz, int base, Direction face) {
            // 돌 받침 (반지름 3, 높이 5) + 나무 몸통 (12칸, 위로 좁아짐) + 뾰족 지붕
            b.fn(mx - 4, base, mz - 4, mx + 4, base + 19, mz + 4, (x, y, z) -> {
                double d = Math.hypot(x - mx, z - mz);
                int ly = y - base;
                if (ly <= 5) {
                    if (d >= 3.5) return null;
                    if (ly == 0) return s(Blocks.COBBLESTONE);
                    return d >= 2.5 ? Build.weather(s(Blocks.STONE_BRICKS), x, y, z, 20) : s(Blocks.AIR);
                }
                if (ly <= 15) {
                    double rr = 3.3 - (ly - 5) * 0.08;
                    if (d >= rr) return null;
                    if (d >= rr - 1) return ly % 4 == 0 ? Build.log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y) : s(Blocks.SPRUCE_PLANKS);
                    return ly == 10 ? s(Blocks.SPRUCE_PLANKS) : s(Blocks.AIR);
                }
                double rr = 3.6 - (ly - 16) * 1.0;
                if (rr <= 0) return null;
                return d < rr ? s(Blocks.DARK_OAK_PLANKS) : null;
            });
            // 문
            int dx = face.getOffsetX(), dz = face.getOffsetZ();
            b.fill(mx + dx * 3, base + 1, mz + dz * 3, mx + dx * 3, base + 2, mz + dz * 3, s(Blocks.AIR));
            b.set(mx, base + 4, mz, s(Blocks.LANTERN).with(LanternBlock.HANGING, false));
            b.set(mx, base + 1, mz, s(Blocks.BARREL));
            // 날개 4개 (길 쪽 면)
            int hx = mx + dx * 3, hz = mz + dz * 3, hy = base + 12;
            int sx = dz != 0 ? 1 : 0, sz = dx != 0 ? 1 : 0; // 옆 방향
            b.set(hx, hy, hz, Build.log(Blocks.SPRUCE_LOG, face.getAxis()));
            int[][] arms = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}}; // (옆, 위)
            for (int a = 0; a < 4; a++) {
                int au = arms[a][1], as = arms[a][0];
                int wu = -as, ws = au; // 날개 천은 돌아가는 쪽 옆으로 한 칸
                for (int k = 1; k <= 7; k++) {
                    int x = hx + sx * as * k, y = hy + au * k, z = hz + sz * as * k;
                    b.set(x, y, z, k == 1 ? s(Blocks.SPRUCE_FENCE) : s(Blocks.STRIPPED_SPRUCE_LOG));
                    if (k >= 2) b.set(x + sx * ws, y + wu, z + sz * ws, s(Blocks.WHITE_WOOL));
                }
            }
            // 건초 · 자루 (통)
            b.set(mx + 4 * sx - dx, base + 1, mz + 4 * sz - dz, s(Blocks.HAY_BLOCK));
            b.set(mx - 4 * sx - dx, base + 1, mz - 4 * sz - dz, s(Blocks.BARREL));
            spawn(mx + dx * 5, base + 1, mz + dz * 5, w -> CapitalPlan.villager(w, VillagerProfession.FARMER));
        }

        // ---------------------------------------------------------------- 외딴 농가
        void farmstead(int x0, int z0, int x1, int z1, int base, Direction face) {
            Build.Palette p = new Build.Palette[]{Build.OAK, Build.SPRUCE, Build.BIRCH, Build.PLASTER, Build.DARK}[r.nextInt(5)];
            Build.house(b, x0 + 2, z0 + 2, 7, 7, base, r.nextFloat() < 0.4f ? 2 : 1, face, p, r);
            CapitalPlan.flowers(b, x0, z0, x1, z1, base, r, x0 + 1, z0 + 1, x0 + 9, z0 + 9, 8);
            b.set(x1, base + 1, z1, s(Blocks.HAY_BLOCK));
            b.set(x1 - 1, base + 1, z1, Build.log(Blocks.OAK_LOG, Direction.Axis.Y));
        }

        // ---------------------------------------------------------------- 길가 사당
        void shrine(int x, int z, int base) {
            b.fill(x - 2, base, z - 2, x + 2, base, z + 2, s(Blocks.STONE_BRICKS));
            b.fn(x - 2, base, z - 2, x + 2, base, z + 2, (xx, yy, zz) -> Build.weather(s(Blocks.STONE_BRICKS), xx, yy, zz, 40));
            b.fill(x, base + 1, z, x, base + 3, z, s(Blocks.CHISELED_STONE_BRICKS));
            b.set(x, base + 4, z, s(Blocks.LANTERN));
            for (int[] q : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}})
                b.set(x + q[0], base + 1, z + q[1], s(Blocks.STONE_BRICK_WALL));
            b.set(x + 2, base + 1, z, s(Blocks.POPPY));
            b.set(x - 2, base + 1, z, s(Blocks.CORNFLOWER));
            b.set(x, base + 1, z + 2, s(Blocks.CANDLE));
        }

        // ---------------------------------------------------------------- 길 끝 초소
        void post(int x, int z, int base) {
            Build.squareTower(b, x, z, base, 2, 9, Blocks.STONE_BRICKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, false, 20);
            b.set(x, base + 1, z + 2, s(Blocks.AIR));
            b.set(x, base + 2, z + 2, s(Blocks.AIR));
            b.set(x + 1, base + 10, z + 1, s(Blocks.CAMPFIRE).with(CampfireBlock.LIT, true));
            b.fill(x - 3, base + 1, z - 1, x - 3, base + 6, z - 1, s(Blocks.SPRUCE_FENCE));
            b.fill(x - 3, base + 4, z - 2, x - 3, base + 6, z - 2, s(Blocks.RED_WOOL));
            b.set(x + 3, base + 1, z + 3, s(Blocks.BARREL));
            b.set(x + 3, base + 1, z + 2, s(Blocks.HAY_BLOCK));
        }

        // ---------------------------------------------------------------- 외곽 마을 (집 다섯 채 + 우물 + 작은 밭)
        void hamlet(int x, int z, int base) {
            b.fn(x - 2, base, z - 13, x + 2, base, z + 13, (xx, yy, zz) -> CapitalPlan.pathBlock(xx, yy, zz));
            b.fn(x - 13, base, z - 2, x + 13, base, z + 2, (xx, yy, zz) -> CapitalPlan.pathBlock(xx, yy, zz));
            TownPlan.well(b, x, z, base);
            int[][] spots = {{-12, -12, 0}, {4, -12, 1}, {-12, 5, 2}, {5, 5, 3}};
            for (int[] q : spots) {
                Build.Palette p = Build.CITY[r.nextInt(Build.CITY.length)];
                int hx = x + q[0], hz = z + q[1];
                Direction door = q[1] < 0 ? Direction.SOUTH : Direction.NORTH;
                Build.house(b, hx, hz, 8, 7, base, r.nextFloat() < 0.4f ? 2 : 1, door, p, r);
            }
            for (int[] q : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) Build.lamp(b, x + q[0], z + q[1], base);
            for (int k = 0; k < 4; k++) {
                int vx = x + r.nextInt(7) - 3, vz = z + r.nextInt(7) - 3;
                if (Math.abs(vx - x) <= 2 && Math.abs(vz - z) <= 2) vx += 4;
                int fx = vx, fz = vz;
                spawn(fx, base + 1, fz, w -> CapitalPlan.villager(w, k2(fx, fz)));
            }
        }

        static VillagerProfession k2(int x, int z) {
            VillagerProfession[] ps = {VillagerProfession.NONE, VillagerProfession.FARMER, VillagerProfession.SHEPHERD, VillagerProfession.NONE};
            return ps[Math.floorMod(x * 31 + z, ps.length)];
        }
    }

    private LandsPlan() {
    }
}

package com.abilitywar.world;

import com.abilitywar.rpg.InstanceDungeon;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.abilitywar.world.Build.leaves;
import static com.abilitywar.world.Build.s;

/**
 * 테마 지역 (지름 약 220칸): 땅 덮개 (테마 흙 · 풀) + 테마 나무 숲 + 가장자리에서 가운데로 가는 오솔길 4개 (등불) +
 * 볼거리 7~9곳 (오두막 · 탑 · 무덤 · 연못 …) + 가운데 랜드마크 (던전 입구). 가장자리는 들쭉날쭉하게 원래 땅으로 이어짐.
 * <pre>
 *  요정의 숲   벚꽃 · 진달래 나무, 꽃밭, 요정 고리, 버섯 집, 가운데 요정의 샘 (치유) + 세계수
 *  고블린 숲   뒤틀린 짙은 참나무 · 가문비, 오두막 · 망루 · 토템 · 쓰레기 더미, 가운데 목책 전쟁 야영지 + 고블린 굴
 *  슬라임 숲   맹그로브 슬라임 나무 · 큰 버섯, 슬라임 웅덩이 · 연금술사 오두막, 가운데 슬라임 왕의 연못 + 슬라임 동굴
 *  거미 숲     거미줄 가문비 · 죽은 나무 · 알 주머니, 버려진 야영지 · 고치 나무, 가운데 여왕의 구덩이 + 거미 둥지
 *  망령의 숲   뒤틀린 죽은 나무 (영혼 등불) · 늘어진 나무, 묘지 · 납골당 · 촛불 고리, 가운데 무너진 예배당 + 광신도 묘실
 *  수정 숲     수정 나무 · 자수정 첨탑, 정동석 · 무너진 관측탑 · 골렘 석상, 가운데 거대 정동석 + 골렘 유적
 * </pre>
 */
public final class RegionPlan {
    static final int R = Regions.R;

    public static void build(Regions.Theme t, Blueprint b, int cx, int by, int cz, Random r, Blueprint.Heights H) {
        new Ctx(t, b, cx, by, cz, r, H).plan();
    }

    record Plot(int x0, int z0, int x1, int z1) {
        boolean overlaps(Plot o, int pad) {
            return x0 - pad <= o.x1 && x1 + pad >= o.x0 && z0 - pad <= o.z1 && z1 + pad >= o.z0;
        }

        boolean contains(int x, int z, int pad) {
            return x >= x0 - pad && x <= x1 + pad && z >= z0 - pad && z <= z1 + pad;
        }
    }

    static final class Ctx {
        final Regions.Theme t;
        final Blueprint b;
        final int cx, by, cz;
        final Random r;
        final Blueprint.Heights H;
        final int nseed;
        final List<Plot> plots = new ArrayList<>();
        final List<double[][]> trails = new ArrayList<>();
        double a0;
        int plazaR;

        Ctx(Regions.Theme t, Blueprint b, int cx, int by, int cz, Random r, Blueprint.Heights H) {
            this.t = t;
            this.b = b;
            this.cx = cx;
            this.by = by;
            this.cz = cz;
            this.r = r;
            this.H = H;
            this.nseed = r.nextInt();
        }

        // ================================================================ 순서
        void plan() {
            a0 = r.nextDouble() * Math.PI * 2;
            plazaR = switch (t) {
                case GOBLIN, HAUNTED -> 21;
                case SLIME -> 22;
                case CRYSTAL -> 20;
                default -> 18;
            };
            // 1) 오솔길 (가장자리 → 마당) 자리
            for (int k = 0; k < 4; k++) trails.add(trailPoints(a0 + k * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.5));
            // 2) 가운데 마당 + 랜드마크
            b.terraformCircle(cx, cz, plazaR, 18, by, 26, plazaGround(), s(Blocks.DIRT));
            plots.add(new Plot(cx - plazaR - 4, cz - plazaR - 4, cx + plazaR + 4, cz + plazaR + 4));
            center();
            // 3) 볼거리
            pois();
            // 4) 땅 덮개 (볼거리 · 마당은 건너뜀)
            groundCover();
            // 5) 오솔길 + 등불 + 입구 간판
            for (double[][] pts : trails) {
                trail(pts);
                lamps(pts);
                signpost(pts);
            }
            // 6) 숲
            forest();
        }

        // ================================================================ 잡음 · 도우미
        double noise(int x, int z, double scale) {
            double fx = x / scale, fz = z / scale;
            int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
            double tx = fx - ix, tz = fz - iz;
            tx = tx * tx * (3 - 2 * tx);
            tz = tz * tz * (3 - 2 * tz);
            double a = h01(ix, iz), b1 = h01(ix + 1, iz), c = h01(ix, iz + 1), d = h01(ix + 1, iz + 1);
            return (a + (b1 - a) * tx) + ((c + (d - c) * tx) - (a + (b1 - a) * tx)) * tz;
        }

        double h01(int x, int z) {
            return (Build.hash(x, nseed, z) & 0xffff) / 65535.0;
        }

        /** 가장자리까지 안쪽 거리 (들쭉날쭉). */
        double inside(int x, int z) {
            double dx = x - cx, dz = z - cz;
            return R - Math.sqrt(dx * dx + dz * dz) + (noise(x, z, 22) - 0.5) * 18;
        }

        boolean inPlot(int x, int z, int pad) {
            for (Plot p : plots) if (p.contains(x, z, pad)) return true;
            return false;
        }

        /** 볼거리 자리 (가운데 마당은 빼고). */
        boolean inPoi(int x, int z, int pad) {
            for (int i = 1; i < plots.size(); i++) if (plots.get(i).contains(x, z, pad)) return true;
            return false;
        }

        double trailDist(double x, double z) {
            double best = Double.MAX_VALUE;
            for (double[][] pts : trails) best = Math.min(best, LandsPlan.polyDist(pts, x, z));
            return best;
        }

        Direction toward(int x, int z, int tx, int tz) {
            int dx = tx - x, dz = tz - z;
            return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        }

        static boolean natural(BlockState s) {
            return s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.DIRT) || s.isOf(Blocks.COARSE_DIRT) || s.isOf(Blocks.PODZOL) || s.isOf(Blocks.ROOTED_DIRT)
                    || s.isOf(Blocks.MYCELIUM) || s.isOf(Blocks.SAND) || s.isOf(Blocks.RED_SAND) || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.MUD)
                    || s.isOf(Blocks.MOSS_BLOCK) || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.DIRT_PATH);
        }

        void tall(Blueprint.Placer p, int x, int y, int z, Block plant) {
            if (!p.get(x, y + 1, z).isAir()) return;
            p.set(x, y, z, s(plant).with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER));
            p.set(x, y + 1, z, s(plant).with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER));
        }

        // ================================================================ 땅 덮개
        BlockState plazaGround() {
            return switch (t) {
                case FAIRY -> s(Blocks.GRASS_BLOCK);
                case GOBLIN -> s(Blocks.COARSE_DIRT);
                case SLIME -> s(Blocks.MOSS_BLOCK);
                case SPIDER -> s(Blocks.PODZOL);
                case HAUNTED -> s(Blocks.PODZOL);
                case CRYSTAL -> s(Blocks.CALCITE);
            };
        }

        BlockState ground(int x, int z, int h) {
            double n = noise(x, z, 7), m = noise(x + 913, z - 377, 4.5);
            return switch (t) {
                case FAIRY -> n < 0.22 ? s(Blocks.MOSS_BLOCK) : s(Blocks.GRASS_BLOCK);
                case GOBLIN -> n < 0.3 ? s(Blocks.COARSE_DIRT) : n < 0.55 ? s(Blocks.PODZOL) : m < 0.14 ? s(Blocks.ROOTED_DIRT) : s(Blocks.GRASS_BLOCK);
                case SLIME -> n < 0.5 ? s(Blocks.MOSS_BLOCK) : n < 0.64 ? s(Blocks.MUD) : m < 0.08 ? s(Blocks.CLAY) : s(Blocks.GRASS_BLOCK);
                case SPIDER -> n < 0.4 ? s(Blocks.PODZOL) : n < 0.6 ? s(Blocks.COARSE_DIRT) : m < 0.1 ? s(Blocks.GRAVEL) : s(Blocks.GRASS_BLOCK);
                case HAUNTED -> n < 0.32 ? s(Blocks.PODZOL) : n < 0.46 ? s(Blocks.SOUL_SOIL) : n < 0.62 ? s(Blocks.COARSE_DIRT) : s(Blocks.GRASS_BLOCK);
                case CRYSTAL -> n < 0.22 ? s(Blocks.CALCITE) : n < 0.32 ? s(Blocks.TUFF) : m < 0.12 ? s(Blocks.MOSS_BLOCK) : s(Blocks.GRASS_BLOCK);
            };
        }

        double plantDensity() {
            return switch (t) {
                case FAIRY -> 0.55;
                case GOBLIN -> 0.32;
                case SLIME -> 0.36;
                case SPIDER -> 0.2;
                case HAUNTED -> 0.2;
                case CRYSTAL -> 0.22;
            };
        }

        void plant(Blueprint.Placer p, int x, int y, int z, int h, BlockState ground) {
            int k = Math.floorMod(h >>> 7, 100);
            switch (t) {
                case FAIRY -> {
                    Block[] flowers = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.LILY_OF_THE_VALLEY,
                            Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.DANDELION, Blocks.BLUE_ORCHID};
                    if (k < 30) p.set(x, y, z, s(Blocks.PINK_PETALS).with(FlowerbedBlock.FLOWER_AMOUNT, 1 + Math.floorMod(h, 4))
                            .with(FlowerbedBlock.FACING, Direction.fromHorizontal(Math.floorMod(h >>> 3, 4))));
                    else if (k < 62) p.set(x, y, z, s(flowers[Math.floorMod(h >>> 11, flowers.length)]));
                    else if (k < 68) tall(p, x, y, z, k < 65 ? Blocks.LILAC : Blocks.PEONY);
                    else if (k < 72) p.set(x, y, z, s(Blocks.FLOWERING_AZALEA));
                    else p.set(x, y, z, s(Blocks.GRASS));
                }
                case GOBLIN -> {
                    if (k < 42) p.set(x, y, z, s(Blocks.GRASS));
                    else if (k < 66) p.set(x, y, z, s(Blocks.FERN));
                    else if (k < 74) tall(p, x, y, z, Blocks.LARGE_FERN);
                    else if (k < 80) p.set(x, y, z, s(Blocks.DEAD_BUSH));
                    else if (k < 88 && ground.isOf(Blocks.PODZOL)) p.set(x, y, z, s(Blocks.BROWN_MUSHROOM));
                    else if (k < 92) p.set(x, y, z, s(Blocks.SWEET_BERRY_BUSH).with(SweetBerryBushBlock.AGE, 3));
                    else tall(p, x, y, z, Blocks.TALL_GRASS);
                }
                case SLIME -> {
                    if (ground.isOf(Blocks.MOSS_BLOCK) && k < 40) p.set(x, y, z, s(Blocks.MOSS_CARPET));
                    else if (k < 62) p.set(x, y, z, s(Blocks.GRASS));
                    else if (k < 74) p.set(x, y, z, s(Blocks.FERN));
                    else if (k < 80) p.set(x, y, z, s(Blocks.AZALEA));
                    else if (k < 84) p.set(x, y, z, s(Blocks.SLIME_BLOCK));
                    else if (k < 90 && ground.isOf(Blocks.MUD)) p.set(x, y, z, s(Blocks.BROWN_MUSHROOM));
                    else tall(p, x, y, z, Blocks.TALL_GRASS);
                }
                case SPIDER -> {
                    if (k < 22) p.set(x, y, z, s(Blocks.DEAD_BUSH));
                    else if (k < 50) p.set(x, y, z, s(Blocks.FERN));
                    else if (k < 70) p.set(x, y, z, s(Blocks.GRASS));
                    else if (k < 84) p.set(x, y, z, s(Blocks.COBWEB));
                    else if (k < 92 && ground.isOf(Blocks.PODZOL)) p.set(x, y, z, s(Blocks.BROWN_MUSHROOM));
                    else if (k < 95) p.set(x, y, z, s(Blocks.BONE_BLOCK));
                    else tall(p, x, y, z, Blocks.LARGE_FERN);
                }
                case HAUNTED -> {
                    if (k < 26) p.set(x, y, z, s(Blocks.DEAD_BUSH));
                    else if (k < 50) p.set(x, y, z, s(Blocks.FERN));
                    else if (k < 66) p.set(x, y, z, s(Blocks.GRASS));
                    else if (k < 74 && !ground.isOf(Blocks.SOUL_SOIL)) p.set(x, y, z, s(Blocks.RED_MUSHROOM));
                    else if (k < 80) p.set(x, y, z, s(Blocks.COBWEB));
                    else if (k < 84) p.set(x, y, z, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, Math.floorMod(h, 16)));
                    else if (k < 90) p.set(x, y, z, s(Blocks.CANDLE).with(CandleBlock.CANDLES, 1 + Math.floorMod(h, 3)).with(CandleBlock.LIT, true));
                    else tall(p, x, y, z, Blocks.LARGE_FERN);
                }
                case CRYSTAL -> {
                    if (k < 18) p.set(x, y, z, s(Blocks.SMALL_AMETHYST_BUD).with(AmethystClusterBlock.FACING, Direction.UP));
                    else if (k < 26) p.set(x, y, z, s(Blocks.MEDIUM_AMETHYST_BUD).with(AmethystClusterBlock.FACING, Direction.UP));
                    else if (k < 30) p.set(x, y, z, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
                    else if (k < 66) p.set(x, y, z, s(Blocks.GRASS));
                    else if (k < 80) p.set(x, y, z, s(Blocks.FERN));
                    else if (k < 86) p.set(x, y, z, s(Blocks.ALLIUM));
                    else tall(p, x, y, z, Blocks.TALL_GRASS);
                }
            }
        }

        void groundCover() {
            int E = R + 14;
            double dens = plantDensity();
            b.surface(cx - E, cz - E, cx + E, cz + E, -64, 320, (p, x, z) -> {
                double in = inside(x, z);
                if (in < -4 || inPoi(x, z, 1)) return;
                double w = Math.min(1, (in + 4) / 18.0);
                // 가운데 마당: 땅은 섞고 풀은 적게 (요정의 숲은 꽃밭 그대로), 랜드마크 바로 둘레 (6칸) 는 비움
                double pd = Math.hypot(x - cx, z - cz);
                double plant = 1;
                if (pd < plazaR + 2) {
                    if (pd < 6 && t != Regions.Theme.FAIRY) return;
                    plant = t == Regions.Theme.FAIRY ? 0.8 : 0.3;
                }
                int y = p.surface(x, z, p.top(x, z) + 1);
                int h = Build.hash(x, nseed, z);
                double g = Math.floorMod(h, 10000) / 10000.0, q = Math.floorMod(h >>> 13, 10000) / 10000.0;
                if (p.water(x, y, z)) {
                    if (p.get(x, y + 1, z).isAir() && q < w * (t == Regions.Theme.SLIME ? 0.25 : t == Regions.Theme.FAIRY ? 0.08 : 0.0))
                        p.set(x, y + 1, z, s(Blocks.LILY_PAD));
                    return;
                }
                BlockState cur = p.get(x, y, z);
                BlockState gr = cur;
                if (g < w && natural(cur)) {
                    gr = ground(x, z, h);
                    p.set(x, y, z, gr);
                }
                BlockState above = p.get(x, y + 1, z);
                if (above.isOf(Blocks.SNOW) && t != Regions.Theme.HAUNTED) {
                    p.set(x, y + 1, z, s(Blocks.AIR));
                    above = p.get(x, y + 1, z);
                }
                if (!above.isAir() || !(gr.isOf(Blocks.GRASS_BLOCK) || gr.isOf(Blocks.PODZOL) || gr.isOf(Blocks.COARSE_DIRT) || gr.isOf(Blocks.MOSS_BLOCK)
                        || gr.isOf(Blocks.MUD) || gr.isOf(Blocks.ROOTED_DIRT) || gr.isOf(Blocks.SOUL_SOIL) || gr.isOf(Blocks.CALCITE) || gr.isOf(Blocks.TUFF) || gr.isOf(Blocks.DIRT)))
                    return;
                if (q < w * dens * plant) plant(p, x, y + 1, z, h, gr);
            });
        }

        // ================================================================ 오솔길
        double[][] trailPoints(double ang) {
            int n = 7;
            double[][] pts = new double[n + 1][];
            double lat = 0;
            double start = R + 6, end = plazaR + 3;
            double dx = Math.cos(ang), dz = Math.sin(ang);
            for (int k = 0; k <= n; k++) {
                double rad = start + (end - start) * k / n;
                if (k >= 1 && k < n) lat = Math.max(-14, Math.min(14, lat + r.nextInt(11) - 5));
                else if (k == n) lat = 0;
                pts[k] = new double[]{cx + dx * rad - dz * lat, cz + dz * rad + dx * lat};
            }
            return pts;
        }

        BlockState trailBlock(int x, int y, int z, double d) {
            int h = Math.floorMod(Build.hash(x, y, z), 10);
            boolean edge = d > 1.4;
            return switch (t) {
                case FAIRY -> edge ? s(Blocks.MOSS_BLOCK) : h < 6 ? s(Blocks.DIRT_PATH) : s(Blocks.MOSSY_COBBLESTONE);
                case GOBLIN -> edge ? (h < 5 ? s(Blocks.COARSE_DIRT) : s(Blocks.GRAVEL)) : h < 5 ? s(Blocks.PACKED_MUD) : h < 8 ? s(Blocks.DIRT_PATH) : s(Blocks.COARSE_DIRT);
                case SLIME -> edge ? s(Blocks.MOSS_BLOCK) : h < 6 ? s(Blocks.MUD_BRICKS) : s(Blocks.PACKED_MUD);
                case SPIDER -> edge ? s(Blocks.COARSE_DIRT) : h < 6 ? s(Blocks.GRAVEL) : s(Blocks.COBBLED_DEEPSLATE);
                case HAUNTED -> edge ? s(Blocks.PODZOL) : h < 4 ? s(Blocks.COBBLESTONE) : h < 7 ? s(Blocks.MOSSY_COBBLESTONE) : s(Blocks.GRAVEL);
                case CRYSTAL -> edge ? s(Blocks.TUFF) : h < 7 ? s(Blocks.CALCITE) : s(Blocks.SMOOTH_BASALT);
            };
        }

        Block bridgeWood() {
            return switch (t) {
                case FAIRY -> Blocks.CHERRY_PLANKS;
                case SLIME -> Blocks.MANGROVE_PLANKS;
                case SPIDER, HAUNTED -> Blocks.DARK_OAK_PLANKS;
                case CRYSTAL -> Blocks.BIRCH_PLANKS;
                default -> Blocks.SPRUCE_PLANKS;
            };
        }

        void trail(double[][] pts) {
            int[] bb = LandsPlan.bbox(pts, 4);
            BlockState wood = s(bridgeWood());
            b.surface(bb[0], bb[1], bb[2], bb[3], -64, 320, (p, x, z) -> {
                double d = LandsPlan.polyDist(pts, x, z);
                if (d > 2.1) return;
                int y = p.surface(x, z, p.top(x, z) + 2);
                if (p.water(x, y, z)) {
                    p.set(x, y + 1, z, wood);
                    if (d > 1.5) p.set(x, y + 2, z, s(Blocks.SPRUCE_FENCE));
                    return;
                }
                p.set(x, y, z, trailBlock(x, y, z, d));
                for (int k = 1; k <= 4; k++) {
                    BlockState a = p.get(x, y + k, z);
                    if (!a.isAir() && !a.isIn(BlockTags.LOGS)) p.set(x, y + k, z, s(Blocks.AIR));
                }
            });
        }

        void lamps(double[][] pts) {
            double total = LandsPlan.length(pts);
            int side = 1;
            for (double tt = 14; tt < total - 6; tt += 22) {
                double[] at = LandsPlan.pointAt(pts, tt), dir = LandsPlan.dirAt(pts, tt);
                int lx = (int) Math.round(at[0] - dir[1] * side * 3.5), lz = (int) Math.round(at[1] + dir[0] * side * 3.5);
                side = -side;
                b.surface(lx, lz, lx, lz, -64, 320, (p, x, z) -> {
                    int y = p.surface(x, z, p.top(x, z) + 2);
                    if (p.water(x, y, z)) return;
                    lamp(p, x, y, z);
                });
            }
        }

        void lamp(Blueprint.Placer p, int x, int y, int z) {
            switch (t) {
                case FAIRY -> {
                    p.set(x, y + 1, z, s(Blocks.MOSSY_COBBLESTONE_WALL));
                    p.set(x, y + 2, z, s(Blocks.CHERRY_FENCE));
                    p.set(x, y + 3, z, s(Blocks.PEARLESCENT_FROGLIGHT));
                    p.set(x, y + 4, z, s(Blocks.PINK_PETALS));
                }
                case GOBLIN -> {
                    p.set(x, y + 1, z, s(Blocks.SPRUCE_FENCE));
                    p.set(x, y + 2, z, s(Blocks.SPRUCE_FENCE));
                    p.set(x, y + 3, z, (Math.floorMod(x + z, 3) == 0 ? s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, Math.floorMod(x * 7 + z, 16)) : s(Blocks.TORCH)));
                }
                case SLIME -> {
                    p.set(x, y + 1, z, s(Blocks.MUD_BRICK_WALL));
                    p.set(x, y + 2, z, s(Blocks.MANGROVE_FENCE));
                    p.set(x, y + 3, z, s(Blocks.VERDANT_FROGLIGHT));
                }
                case SPIDER -> {
                    p.set(x, y + 1, z, s(Blocks.DARK_OAK_FENCE));
                    p.set(x, y + 2, z, s(Blocks.DARK_OAK_FENCE));
                    p.set(x, y + 3, z, s(Blocks.DARK_OAK_FENCE));
                    p.set(x, y + 4, z, s(Blocks.LANTERN));
                    p.set(x + 1, y + 3, z, s(Blocks.COBWEB));
                }
                case HAUNTED -> {
                    p.set(x, y + 1, z, s(Blocks.COBBLED_DEEPSLATE_WALL));
                    p.set(x, y + 2, z, s(Blocks.DARK_OAK_FENCE));
                    p.set(x, y + 3, z, s(Blocks.DARK_OAK_FENCE));
                    p.set(x, y + 4, z, s(Blocks.SOUL_LANTERN));
                }
                case CRYSTAL -> {
                    p.set(x, y + 1, z, s(Blocks.CALCITE));
                    p.set(x, y + 2, z, s(Blocks.AMETHYST_BLOCK));
                    p.set(x, y + 3, z, s(Blocks.END_ROD));
                }
            }
        }

        /** 오솔길 시작 (지역 가장자리) 옆 간판: 지역 이름 · 권장 레벨 · 경고. */
        void signpost(double[][] pts) {
            double[] at = LandsPlan.pointAt(pts, 6), dir = LandsPlan.dirAt(pts, 6);
            int px = (int) Math.round(at[0] - dir[1] * 3.5), pz = (int) Math.round(at[1] + dir[0] * 3.5);
            if (H.water(px, pz)) return;
            int y = H.ground(px, pz);
            // 간판은 지역 바깥 (오솔길이 들어오는 쪽) 을 봄
            Direction face = Math.abs(dir[0]) > Math.abs(dir[1]) ? (dir[0] > 0 ? Direction.WEST : Direction.EAST) : (dir[1] > 0 ? Direction.NORTH : Direction.SOUTH);
            b.fill(px, y + 1, pz, px, y + 3, pz, s(Blocks.SPRUCE_LOG));
            b.set(px, y + 4, pz, s(Blocks.LANTERN));
            BlockPos sp = new BlockPos(px + face.getOffsetX(), y + 2, pz + face.getOffsetZ());
            b.signs.add(new Blueprint.Sign(sp, face, new String[]{"region.abilitywar.sign.top", "region.abilitywar.region." + t.key,
                    "region.abilitywar.sign.lv." + t.key, "region.abilitywar.sign.warn." + t.key}));
        }

        // ================================================================ 숲
        int spacing() {
            return switch (t) {
                case GOBLIN, SPIDER -> 6;
                case SLIME, HAUNTED -> 7;
                case FAIRY, CRYSTAL -> 8;
            };
        }

        void forest() {
            int S = spacing();
            for (int gx = cx - R - 10; gx <= cx + R + 10; gx += S)
                for (int gz = cz - R - 10; gz <= cz + R + 10; gz += S) {
                    int h = Build.hash(gx, nseed ^ 0x51, gz);
                    int x = gx + Math.floorMod(h, S - 2) - (S - 2) / 2, z = gz + Math.floorMod(h >>> 8, S - 2) - (S - 2) / 2;
                    double in = inside(x, z);
                    if (in < 1) continue;
                    double prob = Math.min(1, 0.25 + in / 22.0) * (t == Regions.Theme.FAIRY ? 0.62 : 0.9);
                    if (Math.floorMod(h >>> 16, 1000) / 1000.0 > prob) continue;
                    if (inPlot(x, z, 7) || trailDist(x, z) < 4.5) continue;
                    if (H.water(x, z)) continue;
                    int y = H.ground(x, z);
                    if (Math.abs(H.ground(x + 3, z) - y) > 4 || Math.abs(H.ground(x, z + 3) - y) > 4) continue;
                    tree(x, y, z, new Random(h * 31L + x));
                }
        }

        void tree(int x, int y, int z, Random q) {
            int k = q.nextInt(100);
            Shape sh = new Shape();
            switch (t) {
                case FAIRY -> {
                    if (k < 42) cherry(sh, x, y, z, q);
                    else if (k < 76) azaleaTree(sh, x, y, z, q);
                    else birch(sh, x, y, z, q, Blocks.BIRCH_LEAVES);
                }
                case GOBLIN -> {
                    if (k < 50) gnarled(sh, x, y, z, q);
                    else if (k < 86) spruce(sh, x, y, z, q, 0, 0.2);
                    else fallenLog(sh, x, y, z, q, Blocks.SPRUCE_LOG);
                }
                case SLIME -> {
                    if (k < 45) slimeTree(sh, x, y, z, q);
                    else if (k < 78) giantMushroom(sh, x, y, z, q, q.nextBoolean());
                    else slimeBulb(sh, x, y, z, q);
                }
                case SPIDER -> {
                    if (k < 45) spruce(sh, x, y, z, q, 0.16, 0.42);
                    else if (k < 86) deadTree(sh, x, y, z, q, Blocks.DARK_OAK_LOG, true);
                    else eggSacs(sh, x, y, z, q);
                }
                case HAUNTED -> {
                    if (k < 56) twisted(sh, x, y, z, q);
                    else if (k < 92) willow(sh, x, y, z, q);
                    else fallenLog(sh, x, y, z, q, Blocks.DARK_OAK_LOG);
                }
                case CRYSTAL -> {
                    if (k < 40) crystalTree(sh, x, y, z, q);
                    else if (k < 75) spire(sh, x, y, z, q);
                    else birch(sh, x, y, z, q, Blocks.AZALEA_LEAVES);
                }
            }
            sh.addTo(b);
        }

        // ---------------------------------------------------------------- 나무 모양
        void gnarled(Shape sh, int x, int y, int z, Random q) {
            int h = 5 + q.nextInt(4);
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) sh.line(x + dx, y + 1, z + dz, x + dx, y + h, z + dz, Blocks.DARK_OAK_LOG);
            // 뿌리
            for (int[] c : new int[][]{{-1, 0}, {2, 1}, {0, 2}, {1, -1}})
                if (q.nextFloat() < 0.6f) sh.line(x + c[0], y + 1, z + c[1], x + c[0], y + 1 + q.nextInt(2), z + c[1], Blocks.DARK_OAK_LOG);
            BlockState leaf = leaves(Blocks.DARK_OAK_LEAVES);
            int nb = 2 + q.nextInt(3);
            for (int i = 0; i < nb; i++) {
                double a = q.nextDouble() * Math.PI * 2, len = 3 + q.nextInt(3);
                int sy = y + h - q.nextInt(3);
                double ex = x + 0.5 + Math.cos(a) * len, ez = z + 0.5 + Math.sin(a) * len, ey = sy + 1 + q.nextInt(3);
                sh.line(x + 0.5, sy, z + 0.5, ex, ey, ez, Blocks.DARK_OAK_LOG);
                sh.ball(ex, ey + 1, ez, 2.6 + q.nextDouble(), 1.8, 2.6 + q.nextDouble(), leaf, q, 0.3);
            }
            sh.ball(x + 0.5, y + h + 1.5, z + 0.5, 3.6, 2.2, 3.6, leaf, q, 0.25);
        }

        void spruce(Shape sh, int x, int y, int z, Random q, double webs, double holes) {
            int h = 9 + q.nextInt(6);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.SPRUCE_LOG);
            BlockState leaf = leaves(Blocks.SPRUCE_LEAVES);
            for (int k = 0; k < h - 2; k++) {
                int ly = y + h + 1 - k;
                double rad = Math.min(3.3, 0.5 + k * 0.32) * (k % 2 == 0 ? 1 : 0.65);
                int ri = (int) Math.ceil(rad);
                for (int dx = -ri; dx <= ri; dx++)
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (dx * dx + dz * dz > rad * rad + 0.3) continue;
                        if (q.nextDouble() < holes * 0.5) continue;
                        sh.soft(x + dx, ly, z + dz, q.nextDouble() < webs ? s(Blocks.COBWEB) : leaf);
                    }
            }
            sh.soft(x, y + h + 1, z, leaf).soft(x, y + h + 2, z, leaf);
            if (webs > 0)
                for (int i = 0; i < 3; i++) {
                    int dx = q.nextInt(5) - 2, dz = q.nextInt(5) - 2;
                    int top = y + 3 + q.nextInt(Math.max(1, h / 2));
                    sh.strand(x + dx, top, z + dz, x + dx, top - 2 - q.nextInt(2), z + dz, s(Blocks.COBWEB), true);
                }
        }

        void fallenLog(Shape sh, int x, int y, int z, Random q, Block log) {
            boolean alongX = q.nextBoolean();
            int len = 4 + q.nextInt(3);
            sh.line(x, y + 1, z, x + (alongX ? len : 0), y + 1, z + (alongX ? 0 : len), log);
            for (int i = 0; i <= len; i += 2) {
                int px = x + (alongX ? i : 0), pz = z + (alongX ? 0 : i);
                sh.set(px, y + 2, pz, s(q.nextBoolean() ? Blocks.MOSS_CARPET : Blocks.BROWN_MUSHROOM));
            }
        }

        void cherry(Shape sh, int x, int y, int z, Random q) {
            int h = 5 + q.nextInt(4);
            double lx = (q.nextDouble() - 0.5) * 3, lz = (q.nextDouble() - 0.5) * 3;
            double tx = x + lx, tz = z + lz;
            sh.line(x, y + 1, z, x, y + 2, z, Blocks.CHERRY_LOG);
            sh.line(x, y + 2, z, tx, y + h, tz, Blocks.CHERRY_LOG);
            BlockState leaf = leaves(Blocks.CHERRY_LEAVES);
            sh.ball(tx, y + h + 1, tz, 3.4 + q.nextDouble(), 2.2, 3.4 + q.nextDouble(), leaf, q, 0.22);
            int nb = 1 + q.nextInt(2);
            for (int i = 0; i < nb; i++) {
                double a = q.nextDouble() * Math.PI * 2;
                double bx = tx + Math.cos(a) * 3, bz = tz + Math.sin(a) * 3;
                sh.line(tx, y + h - 1, tz, bx, y + h, bz, Blocks.CHERRY_LOG);
                sh.ball(bx, y + h + 1, bz, 2.6, 1.8, 2.6, leaf, q, 0.25);
            }
        }

        void azaleaTree(Shape sh, int x, int y, int z, Random q) {
            int h = 4 + q.nextInt(3);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.OAK_LOG);
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = -1; dy <= 2; dy++)
                    for (int dz = -3; dz <= 3; dz++) {
                        double d = (dx * dx + dz * dz) / 10.5 + (dy - 0.3) * (dy - 0.3) / 3.2;
                        if (d > 1 || (d > 0.65 && q.nextFloat() < 0.3f)) continue;
                        sh.soft(x + dx, y + h + dy, z + dz, leaves(q.nextFloat() < 0.45f ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
                    }
            sh.set(x, y + h + 1, z, s(Blocks.SHROOMLIGHT));
        }

        void birch(Shape sh, int x, int y, int z, Random q, Block leafB) {
            int h = 6 + q.nextInt(4);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.BIRCH_LOG);
            BlockState leaf = leaves(leafB);
            sh.ball(x, y + h - 0.5, z, 2.4, 3.0, 2.4, leaf, q, 0.25);
            if (t == Regions.Theme.CRYSTAL && q.nextBoolean()) sh.set(x, y + h + 2, z, s(Blocks.END_ROD));
        }

        void slimeTree(Shape sh, int x, int y, int z, Random q) {
            int h = 4 + q.nextInt(3);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.MANGROVE_LOG);
            // 뿌리 (줄기에서 비스듬히 땅으로)
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + q.nextDouble() * 0.6;
                int rx = x + (int) Math.round(Math.cos(a) * 2), rz = z + (int) Math.round(Math.sin(a) * 2);
                sh.strand(x, y + 2, z, rx, y, rz, s(Blocks.MANGROVE_ROOTS), true);
            }
            BlockState leaf = leaves(Blocks.MANGROVE_LEAVES);
            for (int i = 0; i < 3; i++) {
                double ox = (q.nextDouble() - 0.5) * 3, oz = (q.nextDouble() - 0.5) * 3;
                sh.ball(x + ox, y + h + 1 + q.nextInt(2), z + oz, 2.7, 1.8, 2.7, leaf, q, 0.25);
            }
            // 슬라임 방울이 맺혀 떨어지는 중
            for (int i = 0; i < 3; i++) {
                int dx = q.nextInt(5) - 2, dz = q.nextInt(5) - 2;
                if (dx == 0 && dz == 0) continue;
                int sy = y + h - 1;
                sh.set(x + dx, sy, z + dz, s(Blocks.SLIME_BLOCK));
                if (q.nextBoolean()) sh.set(x + dx, sy - 1, z + dz, s(Blocks.SLIME_BLOCK));
            }
        }

        void giantMushroom(Shape sh, int x, int y, int z, Random q, boolean red) {
            int h = 4 + q.nextInt(4);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.MUSHROOM_STEM);
            if (red) {
                for (int dx = -3; dx <= 3; dx++)
                    for (int dy = -2; dy <= 1; dy++)
                        for (int dz = -3; dz <= 3; dz++) {
                            double d = (dx * dx + dz * dz) / 9.0 + (dy - 1) * (dy - 1) / 9.0;
                            if (d > 1 || d < 0.55) continue;
                            if (dy < -1 && dx * dx + dz * dz < 6) continue;
                            sh.set(x + dx, y + h + dy, z + dz, s(Blocks.RED_MUSHROOM_BLOCK));
                        }
            } else {
                int rr = 3 + q.nextInt(2);
                for (int dx = -rr; dx <= rr; dx++)
                    for (int dz = -rr; dz <= rr; dz++)
                        if (dx * dx + dz * dz <= rr * rr + 1) sh.set(x + dx, y + h + 1, z + dz, s(Blocks.BROWN_MUSHROOM_BLOCK));
            }
        }

        void slimeBulb(Shape sh, int x, int y, int z, Random q) {
            int h = 1 + q.nextInt(2);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.MANGROVE_LOG);
            sh.ball(x, y + h + 1.5, z, 1.7, 1.6, 1.7, s(Blocks.SLIME_BLOCK), q, 0.1);
            sh.set(x, y + h + 3, z, s(Blocks.VERDANT_FROGLIGHT));
        }

        void deadTree(Shape sh, int x, int y, int z, Random q, Block log, boolean webs) {
            int h = 6 + q.nextInt(5);
            sh.line(x, y + 1, z, x, y + h, z, log);
            List<double[]> tips = new ArrayList<>();
            int nb = 3 + q.nextInt(3);
            for (int i = 0; i < nb; i++) {
                double a = q.nextDouble() * Math.PI * 2, len = 2 + q.nextInt(4);
                int sy = y + 3 + q.nextInt(Math.max(1, h - 3));
                double ex = x + Math.cos(a) * len, ez = z + Math.sin(a) * len, ey = sy + 1 + q.nextInt(3);
                sh.line(x, sy, z, ex, ey, ez, q.nextFloat() < 0.4f ? Blocks.STRIPPED_DARK_OAK_LOG : log);
                tips.add(new double[]{ex, ey, ez});
            }
            if (webs)
                for (int i = 0; i + 1 < tips.size(); i++)
                    if (q.nextFloat() < 0.6f) {
                        double[] a = tips.get(i), c = tips.get(i + 1);
                        sh.strand(a[0], a[1] - 1, a[2], c[0], c[1] - 1, c[2], s(Blocks.COBWEB), true);
                    }
        }

        void eggSacs(Shape sh, int x, int y, int z, Random q) {
            int n = 2 + q.nextInt(3);
            for (int i = 0; i < n; i++) {
                double ox = (q.nextDouble() - 0.5) * 4, oz = (q.nextDouble() - 0.5) * 4;
                sh.ball(x + ox, y + 1.2, z + oz, 1.3, 1.1, 1.3, s(q.nextBoolean() ? Blocks.MUSHROOM_STEM : Blocks.WHITE_WOOL), q, 0.15);
            }
            for (int i = 0; i < 6; i++) sh.soft(x + q.nextInt(7) - 3, y + 1 + q.nextInt(3), z + q.nextInt(7) - 3, s(Blocks.COBWEB));
        }

        void twisted(Shape sh, int x, int y, int z, Random q) {
            int h = 6 + q.nextInt(5);
            double lx = (q.nextDouble() - 0.5) * 4, lz = (q.nextDouble() - 0.5) * 4;
            Block log = q.nextFloat() < 0.3f ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG;
            double px = x, pz = z;
            for (int i = 1; i <= h; i++) {
                double f = (double) i / h;
                double nx = x + lx * f * f + Math.sin(f * 5 + x) * 0.6, nz = z + lz * f * f + Math.cos(f * 4 + z) * 0.6;
                sh.line(px, y + i, pz, nx, y + i, nz, log);
                sh.line(nx, y + i, nz, nx, y + i, nz, log);
                px = nx;
                pz = nz;
            }
            double tx = px, tz = pz;
            int nb = 2 + q.nextInt(3);
            for (int i = 0; i < nb; i++) {
                double a = q.nextDouble() * Math.PI * 2, len = 2 + q.nextInt(3);
                int sy = y + h - q.nextInt(3);
                double ex = tx + Math.cos(a) * len, ez = tz + Math.sin(a) * len;
                sh.line(tx, sy, tz, ex, sy + 1 + q.nextInt(2), ez, log);
                if (i == 0 && q.nextFloat() < 0.55f) {
                    int hx = (int) Math.round(ex), hz = (int) Math.round(ez), hy = sy;
                    sh.set(hx, hy - 1, hz, s(Blocks.CHAIN));
                    sh.set(hx, hy - 2, hz, s(Blocks.SOUL_LANTERN).with(LanternBlock.HANGING, true));
                }
            }
            if (q.nextFloat() < 0.35f) sh.ball(tx, y + h + 1, tz, 1.8, 1.2, 1.8, leaves(Blocks.DARK_OAK_LEAVES), q, 0.55);
        }

        void willow(Shape sh, int x, int y, int z, Random q) {
            int h = 5 + q.nextInt(3);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.DARK_OAK_LOG);
            BlockState leaf = leaves(Blocks.DARK_OAK_LEAVES);
            sh.ball(x, y + h + 1, z, 3.6, 2.0, 3.6, leaf, q, 0.25);
            for (int i = 0; i < 12; i++) {
                double a = q.nextDouble() * Math.PI * 2;
                int dx = (int) Math.round(Math.cos(a) * 3.2), dz = (int) Math.round(Math.sin(a) * 3.2);
                int len = 2 + q.nextInt(4);
                for (int k = 0; k < len; k++) sh.soft(x + dx, y + h - k, z + dz, leaf);
            }
        }

        void crystalTree(Shape sh, int x, int y, int z, Random q) {
            int h = 5 + q.nextInt(4);
            sh.line(x, y + 1, z, x, y + h, z, Blocks.BIRCH_LOG);
            Block[] mats = {Blocks.AMETHYST_BLOCK, Blocks.PURPLE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS};
            double rr = 2.6 + q.nextDouble();
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = -2; dy <= 3; dy++)
                    for (int dz = -3; dz <= 3; dz++) {
                        double d = (dx * dx + dz * dz) / (rr * rr) + (dy - 0.5) * (dy - 0.5) / 4.0;
                        if (d > 1 || d < 0.45 || q.nextFloat() < 0.3f) continue;
                        sh.soft(x + dx, y + h + dy, z + dz, s(mats[q.nextInt(mats.length)]));
                    }
            for (int i = 0; i < 3; i++) {
                int dx = q.nextInt(3) - 1, dz = q.nextInt(3) - 1;
                int top = y + h + 3;
                if (sh.get(x + dx, top, z + dz) == null) sh.set(x + dx, top, z + dz, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
            }
            sh.set(x, y + h + 1, z, s(Blocks.PEARLESCENT_FROGLIGHT));
        }

        void spire(Shape sh, int x, int y, int z, Random q) {
            int h = 5 + q.nextInt(6);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) if (q.nextFloat() < 0.8f) sh.set(x + dx, y + 1, z + dz, s(Blocks.CALCITE));
            for (int i = 1; i <= h; i++) {
                sh.set(x, y + i, z, s(i % 4 == 2 ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK));
                if (i <= h / 2) {
                    sh.set(x + 1, y + i, z, s(Blocks.AMETHYST_BLOCK));
                    sh.set(x, y + i, z + 1, s(Blocks.AMETHYST_BLOCK));
                    sh.set(x + 1, y + i, z + 1, s(i == h / 2 ? Blocks.AMETHYST_CLUSTER : Blocks.AMETHYST_BLOCK));
                }
            }
            sh.set(x, y + h + 1, z, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
            // 곁가지 수정
            for (int i = 0; i < 2; i++) {
                int ox = q.nextBoolean() ? -1 : 2, oz = q.nextInt(2);
                int sh0 = 1 + q.nextInt(Math.max(1, h / 2));
                sh.set(x + ox, y + sh0, z + oz, s(Blocks.AMETHYST_BLOCK));
                sh.set(x + ox, y + sh0 + 1, z + oz, s(Blocks.LARGE_AMETHYST_BUD).with(AmethystClusterBlock.FACING, Direction.UP));
            }
        }

        // ================================================================ 볼거리
        record Poi(String type, int w, int d) {
        }

        Poi[] poiTypes() {
            return switch (t) {
                case FAIRY -> new Poi[]{new Poi("ring", 11, 11), new Poi("mushhouse", 9, 9), new Poi("stones", 11, 11), new Poi("pond", 11, 9), new Poi("ring", 11, 11), new Poi("mushhouse", 9, 9)};
                case GOBLIN -> new Poi[]{new Poi("hut", 9, 9), new Poi("tower", 7, 7), new Poi("pit", 9, 9), new Poi("hut", 9, 9), new Poi("totems", 7, 7), new Poi("trash", 9, 9), new Poi("hut", 9, 9)};
                case SLIME -> new Poi[]{new Poi("pool", 13, 11), new Poi("alchemist", 9, 9), new Poi("mound", 9, 9), new Poi("pool", 13, 11), new Poi("shrooms", 11, 11)};
                case SPIDER -> new Poi[]{new Poi("nest", 11, 11), new Poi("camp", 9, 9), new Poi("cocoons", 9, 9), new Poi("bones", 7, 7), new Poi("nest", 11, 11)};
                case HAUNTED -> new Poi[]{new Poi("graves", 13, 11), new Poi("mausoleum", 9, 9), new Poi("circle", 11, 11), new Poi("gallows", 7, 7), new Poi("graves", 13, 11)};
                case CRYSTAL -> new Poi[]{new Poi("geode", 11, 11), new Poi("observatory", 11, 11), new Poi("statue", 7, 7), new Poi("cluster", 9, 9), new Poi("geode", 11, 11)};
            };
        }

        void pois() {
            Poi[] types = poiTypes();
            int want = 7 + r.nextInt(3), made = 0;
            for (int attempt = 0; attempt < 90 && made < want; attempt++) {
                Poi poi = types[made % types.length];
                double a = r.nextDouble() * Math.PI * 2, dist = plazaR + 16 + r.nextDouble() * (R - plazaR - 30);
                int px = cx + (int) Math.round(Math.cos(a) * dist), pz = cz + (int) Math.round(Math.sin(a) * dist);
                int x0 = px - poi.w / 2, z0 = pz - poi.d / 2, x1 = x0 + poi.w - 1, z1 = z0 + poi.d - 1;
                Plot pl = new Plot(x0, z0, x1, z1);
                boolean ok = true;
                for (Plot u : plots)
                    if (u.overlaps(pl, 8)) {
                        ok = false;
                        break;
                    }
                if (!ok || trailDist(px, pz) < Math.hypot(poi.w, poi.d) / 2 + 3) continue;
                int[] hs = new int[9];
                int i = 0;
                for (int ix = 0; ix <= 2 && ok; ix++)
                    for (int iz = 0; iz <= 2; iz++) {
                        int sx = x0 + (x1 - x0) * ix / 2, sz = z0 + (z1 - z0) * iz / 2;
                        if (H.water(sx, sz)) {
                            ok = false;
                            break;
                        }
                        hs[i++] = H.ground(sx, sz);
                    }
                if (!ok) continue;
                java.util.Arrays.sort(hs);
                if (hs[8] - hs[0] > 8) continue;
                int base = hs[4];
                plots.add(pl);
                b.terraformRect(x0, z0, x1, z1, 6, base, 14, plazaGround(), s(Blocks.DIRT));
                Direction face = toward(px, pz, cx, cz);
                poi(poi.type, px, base, pz, x0, z0, x1, z1, face);
                made++;
            }
        }

        void poi(String type, int px, int y, int pz, int x0, int z0, int x1, int z1, Direction f) {
            GatePlan.L l = new GatePlan.L(px, pz, f);
            switch (type) {
                // ---------------------------------------------------------------- 요정
                case "ring" -> {
                    for (int dx = -5; dx <= 5; dx++)
                        for (int dz = -5; dz <= 5; dz++) {
                            double d = Math.sqrt(dx * dx + dz * dz);
                            if (Math.abs(d - 4) < 0.6) b.set(px + dx, y + 1, pz + dz, Math.floorMod(dx + dz, 2) == 0 ? s(Blocks.RED_MUSHROOM) : s(Blocks.PINK_PETALS).with(FlowerbedBlock.FLOWER_AMOUNT, 4));
                            else if (d < 3.4 && r.nextFloat() < 0.4f) b.set(px + dx, y + 1, pz + dz, s(r.nextBoolean() ? Blocks.LILY_OF_THE_VALLEY : Blocks.AZURE_BLUET));
                        }
                    b.set(px, y, pz, s(Blocks.MOSS_BLOCK));
                    b.set(px, y + 1, pz, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
                    b.set(px, y, pz + 1, s(Blocks.GLOWSTONE));
                }
                case "mushhouse" -> {
                    // 버섯 집: 줄기 벽 (둥근) + 빨간 버섯 지붕 + 문 + 창
                    for (int dx = -3; dx <= 3; dx++)
                        for (int dz = -3; dz <= 3; dz++)
                            for (int dy = 1; dy <= 7; dy++) {
                                double d = Math.sqrt(dx * dx + dz * dz);
                                BlockState st = null;
                                if (dy <= 4 && d <= 2.6 && d > 1.6) st = s(Blocks.MUSHROOM_STEM);
                                else if (dy <= 4 && d <= 1.6) st = s(Blocks.AIR);
                                else if (dy >= 5) {
                                    double dd = d * d / 12.0 + (dy - 5) * (dy - 5) / 6.0;
                                    if (dd <= 1 && dd > 0.45) st = s(Blocks.RED_MUSHROOM_BLOCK);
                                    else if (dd <= 0.45) st = s(Blocks.AIR);
                                }
                                if (st != null) b.set(px + dx, y + dy, pz + dz, st);
                            }
                    int[] door = l.at(0, -2);
                    b.set(door[0], y + 1, door[1], s(Blocks.AIR));
                    b.set(door[0], y + 2, door[1], s(Blocks.AIR));
                    int[] win = l.at(2, 0);
                    b.set(win[0], y + 3, win[1], s(Blocks.GLASS_PANE));
                    b.set(px, y + 4, pz, s(Blocks.SHROOMLIGHT));
                    int[] ch = l.at(0, 1);
                    b.chests.add(new Blueprint.Chest(new BlockPos(ch[0], y + 1, ch[1]), f, 0));
                }
                case "stones" -> {
                    for (int k = 0; k < 6; k++) {
                        double a = k * Math.PI / 3;
                        int sx = px + (int) Math.round(Math.cos(a) * 4), sz = pz + (int) Math.round(Math.sin(a) * 4);
                        int hh = 2 + r.nextInt(2);
                        for (int dy = 1; dy <= hh; dy++) b.set(sx, y + dy, sz, s(dy == hh ? Blocks.CHISELED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS));
                        b.set(sx, y + hh + 1, sz, s(Blocks.LANTERN));
                    }
                    b.set(px, y + 1, pz, s(Blocks.MOSSY_COBBLESTONE));
                    b.set(px, y + 2, pz, s(Blocks.FLOWERING_AZALEA));
                }
                case "pond" -> {
                    for (int dx = -5; dx <= 5; dx++)
                        for (int dz = -4; dz <= 4; dz++) {
                            double d = dx * dx / 25.0 + dz * dz / 16.0;
                            if (d > 1) continue;
                            if (d > 0.7) b.set(px + dx, y, pz + dz, s(Blocks.MOSS_BLOCK));
                            else {
                                b.set(px + dx, y - 1, pz + dz, s(Blocks.CLAY));
                                b.set(px + dx, y, pz + dz, s(Blocks.WATER));
                                if (r.nextFloat() < 0.18f) b.set(px + dx, y + 1, pz + dz, s(Blocks.LILY_PAD));
                            }
                        }
                    b.set(px - 5, y + 1, pz, s(Blocks.PINK_PETALS).with(FlowerbedBlock.FLOWER_AMOUNT, 4));
                    b.set(px + 5, y + 1, pz, s(Blocks.BLUE_ORCHID));
                }
                // ---------------------------------------------------------------- 고블린
                case "hut" -> {
                    for (int dx = -3; dx <= 3; dx++)
                        for (int dz = -3; dz <= 3; dz++)
                            for (int dy = 1; dy <= 6; dy++) {
                                double d = Math.sqrt(dx * dx + dz * dz);
                                BlockState st = null;
                                if (dy <= 3 && d <= 3.1 && d > 2.2) st = s(r.nextFloat() < 0.15f ? Blocks.MUD_BRICKS : Blocks.PACKED_MUD);
                                else if (dy <= 3 && d <= 2.2) st = s(Blocks.AIR);
                                else if (dy >= 4) {
                                    double rr = 3.6 - (dy - 4) * 1.2;
                                    if (d <= rr) st = s(Blocks.HAY_BLOCK);
                                }
                                if (st != null) b.set(px + dx, y + dy, pz + dz, st);
                            }
                    int[] dp = l.at(0, -3);
                    b.set(dp[0], y + 1, dp[1], s(Blocks.AIR));
                    b.set(dp[0], y + 2, dp[1], s(Blocks.AIR));
                    int[] fire = l.at(2, -5);
                    b.set(fire[0], y + 1, fire[1], s(Blocks.CAMPFIRE));
                    int[] ch = l.at(-1, 1);
                    b.chests.add(new Blueprint.Chest(new BlockPos(ch[0], y + 1, ch[1]), f, r.nextFloat() < 0.3f ? 1 : 0));
                    int[] bar = l.at(1, 1);
                    b.set(bar[0], y + 1, bar[1], s(Blocks.BARREL));
                    b.set(px, y + 3, pz, s(Blocks.LANTERN).with(LanternBlock.HANGING, true));
                    b.spawns.add(new Blueprint.Spawn(new BlockPos(fire[0] + 1, y + 1, fire[1]), w -> com.abilitywar.mob.AWMobs.GOBLIN.create(w)));
                }
                case "tower" -> {
                    for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.fill(px + c[0], y + 1, pz + c[1], px + c[0], y + 7, pz + c[1], s(Blocks.SPRUCE_LOG));
                    b.fill(px - 2, y + 8, pz - 2, px + 2, y + 8, pz + 2, s(Blocks.SPRUCE_PLANKS));
                    b.fn(px - 2, y + 9, pz - 2, px + 2, y + 9, pz + 2, (x, yy, z) -> Math.abs(x - px) == 2 || Math.abs(z - pz) == 2 ? s(Blocks.SPRUCE_FENCE) : null);
                    for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                        b.set(px + c[0], y + 10, pz + c[1], s(Blocks.SPRUCE_FENCE));
                        b.set(px + c[0], y + 11, pz + c[1], s(Blocks.SPRUCE_SLAB));
                    }
                    b.fill(px - 1, y + 11, pz - 1, px + 1, y + 11, pz + 1, s(Blocks.SPRUCE_SLAB));
                    // 가운데 기둥 + 앞쪽 사다리 (발판에 구멍)
                    b.fill(px, y + 1, pz, px, y + 8, pz, s(Blocks.SPRUCE_LOG));
                    int[] ladIn = l.at(0, -1);
                    for (int dy = 1; dy <= 8; dy++)
                        b.set(ladIn[0], y + dy, ladIn[1], s(Blocks.LADDER).with(net.minecraft.block.LadderBlock.FACING, f));
                    b.set(px, y + 9, pz, s(Blocks.CAMPFIRE).with(net.minecraft.block.CampfireBlock.SIGNAL_FIRE, false));
                    b.set(px + 1, y + 9, pz + 1, s(Blocks.BARREL));
                    b.chests.add(new Blueprint.Chest(new BlockPos(px - 1, y + 9, pz - 1), f, 1));
                    b.set(px - 1, y + 9, pz + 1, s(Blocks.RED_BANNER));
                }
                case "pit" -> {
                    b.set(px, y, pz, s(Blocks.NETHERRACK));
                    b.set(px, y + 1, pz, s(Blocks.CAMPFIRE));
                    for (int[] c : new int[][]{{-1, -3}, {1, -3}, {-1, 3}, {1, 3}}) b.set(px + c[0], y + 1, pz + c[1], s(Blocks.SPRUCE_FENCE));
                    b.fill(px - 1, y + 2, pz - 3, px + 1, y + 2, pz - 3, s(Blocks.SPRUCE_FENCE));
                    b.set(px, y + 1, pz - 3, s(Blocks.WATER_CAULDRON).with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
                    for (int k = 0; k < 4; k++) {
                        double a = k * Math.PI / 2 + 0.4;
                        int sx = px + (int) Math.round(Math.cos(a) * 3), sz = pz + (int) Math.round(Math.sin(a) * 3);
                        b.set(sx, y + 1, sz, Build.log(Blocks.STRIPPED_SPRUCE_LOG, Math.abs(Math.cos(a)) > 0.5 ? Direction.Axis.Z : Direction.Axis.X));
                    }
                    b.set(px + 3, y + 1, pz - 2, s(Blocks.BONE_BLOCK));
                    b.set(px - 3, y + 1, pz + 2, s(Blocks.HAY_BLOCK));
                    b.spawns.add(new Blueprint.Spawn(new BlockPos(px + 2, y + 1, pz + 2), w -> com.abilitywar.mob.AWMobs.GOBLIN.create(w)));
                    b.spawns.add(new Blueprint.Spawn(new BlockPos(px - 2, y + 1, pz - 1), w -> com.abilitywar.mob.AWMobs.GOBLIN.create(w)));
                }
                case "totems" -> {
                    for (int[] c : new int[][]{{-2, 0}, {2, 0}, {0, 2}}) {
                        int hh = 3 + r.nextInt(3);
                        for (int dy = 1; dy <= hh; dy++)
                            b.set(px + c[0], y + dy, pz + c[1], s(dy % 2 == 0 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_LOG));
                        b.set(px + c[0], y + hh + 1, pz + c[1], s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
                        b.set(px + c[0] + (c[0] == 0 ? 1 : 0), y + hh - 1, pz + c[1] + (c[0] == 0 ? 0 : 1), s(Blocks.RED_WOOL));
                    }
                    b.set(px, y + 1, pz - 1, s(Blocks.BONE_BLOCK));
                    b.set(px, y + 1, pz, s(Blocks.JACK_O_LANTERN).with(net.minecraft.block.CarvedPumpkinBlock.FACING, f));
                }
                case "trash" -> {
                    for (int dx = -4; dx <= 4; dx++)
                        for (int dz = -4; dz <= 4; dz++) {
                            double d = Math.sqrt(dx * dx + dz * dz);
                            int hh = (int) Math.round(3 - d * 0.7 + r.nextDouble());
                            for (int dy = 1; dy <= hh; dy++) {
                                Block[] junk = {Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.BONE_BLOCK, Blocks.SPRUCE_PLANKS, Blocks.HAY_BLOCK, Blocks.COMPOSTER};
                                b.set(px + dx, y + dy, pz + dz, s(junk[r.nextInt(junk.length)]));
                            }
                        }
                    int[] ch = l.at(0, -3);
                    b.chests.add(new Blueprint.Chest(new BlockPos(ch[0], y + 1, ch[1]), f, 1));
                    b.set(px + 3, y + 1, pz + 3, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
                }
                // ---------------------------------------------------------------- 슬라임
                case "pool" -> {
                    for (int dx = -6; dx <= 6; dx++)
                        for (int dz = -5; dz <= 5; dz++) {
                            double d = dx * dx / 36.0 + dz * dz / 25.0;
                            if (d > 1) continue;
                            if (d > 0.72) b.set(px + dx, y, pz + dz, s(r.nextFloat() < 0.4f ? Blocks.MUD : Blocks.MOSS_BLOCK));
                            else {
                                b.set(px + dx, y - 2, pz + dz, s(Blocks.MUD));
                                b.set(px + dx, y - 1, pz + dz, d < 0.3 ? s(Blocks.SLIME_BLOCK) : s(Blocks.WATER));
                                b.set(px + dx, y, pz + dz, s(Blocks.WATER));
                                float k = r.nextFloat();
                                if (k < 0.12f) b.set(px + dx, y + 1, pz + dz, s(Blocks.LILY_PAD));
                                else if (k < 0.18f) b.set(px + dx, y, pz + dz, s(Blocks.SLIME_BLOCK));
                            }
                        }
                    b.set(px + 6, y + 1, pz, s(Blocks.SLIME_BLOCK));
                    b.set(px - 6, y + 1, pz + 1, s(Blocks.VERDANT_FROGLIGHT));
                }
                case "alchemist" -> {
                    b.fn(px - 3, y + 1, pz - 3, px + 3, y + 5, pz + 3, (x, yy, z) -> {
                        int dx = Math.abs(x - px), dz = Math.abs(z - pz);
                        boolean wall = dx == 3 || dz == 3;
                        if (yy == y + 5) return dx <= 3 && dz <= 3 ? s(Blocks.MANGROVE_PLANKS) : null;
                        if (!wall) return s(Blocks.AIR);
                        if (dx == 3 && dz == 3) return s(Blocks.MANGROVE_LOG);
                        if (yy == y + 3 && (dx == 0 || dz == 0)) return s(Blocks.GREEN_STAINED_GLASS_PANE);
                        return s(Blocks.MUD_BRICKS);
                    });
                    b.fill(px - 2, y + 6, pz - 2, px + 2, y + 6, pz + 2, s(Blocks.MANGROVE_SLAB));
                    b.set(px, y + 7, pz, s(Blocks.MOSS_BLOCK));
                    int[] door = l.at(0, -3);
                    b.set(door[0], y + 1, door[1], s(Blocks.AIR));
                    b.set(door[0], y + 2, door[1], s(Blocks.AIR));
                    int[] a1 = l.at(-2, 2), a2 = l.at(2, 2), a3 = l.at(0, 2);
                    b.set(a1[0], y + 1, a1[1], s(Blocks.BREWING_STAND));
                    b.set(a2[0], y + 1, a2[1], s(Blocks.WATER_CAULDRON).with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
                    b.chests.add(new Blueprint.Chest(new BlockPos(a3[0], y + 1, a3[1]), f, 1));
                    b.set(px, y + 4, pz, s(Blocks.VERDANT_FROGLIGHT));
                    int[] wd = l.at(-2, -1);
                    b.set(wd[0], y + 1, wd[1], s(Blocks.SLIME_BLOCK));
                }
                case "mound" -> {
                    Shape sh = new Shape();
                    sh.ball(px, y, pz, 3.6, 3.4, 3.6, s(Blocks.SLIME_BLOCK), r, 0.08);
                    for (int dx = -4; dx <= 4; dx++)
                        for (int dz = -4; dz <= 4; dz++) if (r.nextFloat() < 0.25f && dx * dx + dz * dz > 14) sh.set(px + dx, y + 1, pz + dz, s(Blocks.MOSS_CARPET));
                    sh.set(px, y + 4, pz, s(Blocks.VERDANT_FROGLIGHT));
                    sh.addTo(b);
                }
                case "shrooms" -> {
                    Shape sh = new Shape();
                    giantMushroom(sh, px - 2, y, pz - 2, r, true);
                    giantMushroom(sh, px + 3, y, pz + 2, r, false);
                    slimeBulb(sh, px - 3, y, pz + 3, r);
                    sh.addTo(b);
                }
                // ---------------------------------------------------------------- 거미
                case "nest" -> {
                    Shape sh = new Shape();
                    for (int k = 0; k < 7; k++) {
                        double a = k * Math.PI * 2 / 7;
                        sh.ball(px + Math.cos(a) * 3.2, y + 1.2, pz + Math.sin(a) * 3.2, 1.3, 1.2, 1.3, s(k % 2 == 0 ? Blocks.MUSHROOM_STEM : Blocks.WHITE_WOOL), r, 0.1);
                    }
                    for (int i = 0; i < 26; i++) sh.soft(px + r.nextInt(11) - 5, y + 1 + r.nextInt(4), pz + r.nextInt(11) - 5, s(Blocks.COBWEB));
                    sh.set(px, y + 1, pz, s(Blocks.BONE_BLOCK));
                    sh.set(px, y + 2, pz, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
                    sh.addTo(b);
                    b.chests.add(new Blueprint.Chest(new BlockPos(px + 1, y + 1, pz), f, 1));
                }
                case "camp" -> {
                    // 무너진 천막 (모직) + 꺼진 모닥불 + 뼈
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 1; dz++) {
                            int hh = 3 - Math.abs(dx);
                            if (hh >= 1) b.set(px + dx, y + hh, pz + dz, s(r.nextFloat() < 0.2f ? Blocks.COBWEB : Blocks.WHITE_WOOL));
                        }
                    b.set(px, y + 1, pz - 1, s(Blocks.WHITE_CARPET));
                    b.set(px, y + 1, pz, s(Blocks.WHITE_CARPET));
                    b.set(px, y + 1, pz + 3, s(Blocks.CAMPFIRE).with(net.minecraft.block.CampfireBlock.LIT, false));
                    b.chests.add(new Blueprint.Chest(new BlockPos(px + 2, y + 1, pz + 3), f, 1));
                    b.set(px - 3, y + 1, pz + 2, s(Blocks.BONE_BLOCK));
                    b.set(px - 3, y + 2, pz + 2, s(Blocks.COBWEB));
                }
                case "cocoons" -> {
                    Shape sh = new Shape();
                    deadTree(sh, px, y, pz, r, Blocks.DARK_OAK_LOG, true);
                    for (int i = 0; i < 4; i++) {
                        int hx = px + r.nextInt(5) - 2, hz = pz + r.nextInt(5) - 2, hy = y + 4 + r.nextInt(3);
                        if (hx == px && hz == pz) continue;
                        sh.set(hx, hy, hz, s(Blocks.COBWEB));
                        sh.set(hx, hy - 1, hz, s(Blocks.WHITE_WOOL));
                        sh.set(hx, hy - 2, hz, s(Blocks.WHITE_WOOL));
                    }
                    sh.addTo(b);
                }
                case "bones" -> {
                    for (int i = 0; i < 9; i++) {
                        int dx = r.nextInt(5) - 2, dz = r.nextInt(5) - 2;
                        b.set(px + dx, y + 1, pz + dz, r.nextFloat() < 0.7f ? Build.log(Blocks.BONE_BLOCK, r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z) : s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
                    }
                    b.set(px, y + 2, pz, s(Blocks.COBWEB));
                }
                // ---------------------------------------------------------------- 망령
                case "graves" -> {
                    b.fn(x0, y + 1, z0, x1, y + 1, z1, (x, yy, z) -> x == x0 || x == x1 || z == z0 || z == z1 ? s(Blocks.IRON_BARS) : null);
                    int gx0 = f.getAxis() == Direction.Axis.X ? (f == Direction.EAST ? x1 : x0) : px;
                    int gz0 = f.getAxis() == Direction.Axis.Z ? (f == Direction.SOUTH ? z1 : z0) : pz;
                    b.set(gx0, y + 1, gz0, s(Blocks.AIR));
                    for (int gx = x0 + 2; gx <= x1 - 2; gx += 3)
                        for (int gz = z0 + 2; gz <= z1 - 2; gz += 3) {
                            b.set(gx, y + 1, gz, s(r.nextFloat() < 0.5f ? Blocks.COBBLESTONE_WALL : Blocks.MOSSY_COBBLESTONE_WALL));
                            if (r.nextFloat() < 0.4f) b.set(gx, y + 2, gz, s(Blocks.CANDLE).with(CandleBlock.CANDLES, 1 + r.nextInt(3)).with(CandleBlock.LIT, true));
                            b.set(gx, y, gz + 1, s(Blocks.PODZOL));
                            b.set(gx, y + 1, gz + 1, s(Blocks.STONE_SLAB));
                        }
                    b.set(x0, y + 2, z0, s(Blocks.SOUL_LANTERN));
                    b.set(x1, y + 2, z1, s(Blocks.SOUL_LANTERN));
                }
                case "mausoleum" -> {
                    b.fn(px - 3, y + 1, pz - 3, px + 3, y + 5, pz + 3, (x, yy, z) -> {
                        int dx = Math.abs(x - px), dz = Math.abs(z - pz);
                        if (yy == y + 5) return s(Blocks.DEEPSLATE_TILES);
                        if (dx < 3 && dz < 3) return s(Blocks.AIR);
                        if (dx == 3 && dz == 3) return s(Blocks.POLISHED_DEEPSLATE);
                        return s(r.nextFloat() < 0.15f ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS);
                    });
                    b.fill(px - 2, y + 6, pz - 2, px + 2, y + 6, pz + 2, s(Blocks.DEEPSLATE_TILE_SLAB));
                    b.set(px, y + 7, pz, s(Blocks.CHISELED_DEEPSLATE));
                    int[] door = l.at(0, -3);
                    b.set(door[0], y + 1, door[1], s(Blocks.AIR));
                    b.set(door[0], y + 2, door[1], s(Blocks.AIR));
                    int[] ch = l.at(0, 2);
                    b.chests.add(new Blueprint.Chest(new BlockPos(ch[0], y + 1, ch[1]), f, 1));
                    b.set(px, y + 4, pz, s(Blocks.SOUL_LANTERN).with(LanternBlock.HANGING, true));
                    int[] c1 = l.at(-2, 2), c2 = l.at(2, 2);
                    b.set(c1[0], y + 1, c1[1], s(Blocks.CANDLE).with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true));
                    b.set(c2[0], y + 1, c2[1], s(Blocks.CANDLE).with(CandleBlock.CANDLES, 2).with(CandleBlock.LIT, true));
                    b.spawners.add(new Blueprint.Spawner(new BlockPos(px, y + 1, pz), com.abilitywar.mob.AWMobs.WRAITH));
                }
                case "circle" -> {
                    for (int k = 0; k < 8; k++) {
                        double a = k * Math.PI / 4;
                        int sx = px + (int) Math.round(Math.cos(a) * 4), sz = pz + (int) Math.round(Math.sin(a) * 4);
                        int hh = 1 + r.nextInt(3);
                        for (int dy = 1; dy <= hh; dy++) b.set(sx, y + dy, sz, s(r.nextBoolean() ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE_BRICKS));
                        b.set(sx, y + hh + 1, sz, s(Blocks.CANDLE).with(CandleBlock.CANDLES, 1 + r.nextInt(4)).with(CandleBlock.LIT, true));
                    }
                    b.set(px, y + 1, pz, s(Blocks.CRYING_OBSIDIAN));
                    b.set(px, y + 2, pz, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) == 2) b.set(px + dx, y, pz + dz, s(Blocks.SOUL_SOIL));
                }
                case "gallows" -> {
                    for (int dy = 1; dy <= 5; dy++) b.set(px - 2, y + dy, pz, s(Blocks.DARK_OAK_LOG));
                    b.fill(px - 2, y + 6, pz, px + 1, y + 6, pz, Build.log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
                    b.fill(px + 1, y + 4, pz, px + 1, y + 5, pz, s(Blocks.CHAIN));
                    b.set(px + 1, y + 3, pz, s(Blocks.SOUL_LANTERN).with(LanternBlock.HANGING, true));
                    b.set(px - 1, y + 1, pz + 1, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
                }
                // ---------------------------------------------------------------- 수정
                case "geode" -> geode(px, y - 1, pz, 4.6, true);
                case "observatory" -> {
                    for (int dy = 1; dy <= 9; dy++) {
                        int top = dy;
                        b.fn(px - 4, y + dy, pz - 4, px + 4, y + dy, pz + 4, (x, yy, z) -> {
                            double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                            if (d > 4.3) return null;
                            if (d > 3.3) {
                                // 무너진 쪽: 위로 갈수록 성기게
                                if (top > 5 && Math.floorMod(Build.hash(x, yy, z), 10) < top - 4) return null;
                                return s(Math.floorMod(Build.hash(x, yy, z), 5) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
                            }
                            return s(Blocks.AIR);
                        });
                    }
                    int[] door = l.at(0, -4);
                    b.fill(door[0], y + 1, door[1], door[0], y + 2, door[1], s(Blocks.AIR));
                    b.set(px, y + 1, pz, s(Blocks.LODESTONE));
                    b.set(px, y + 2, pz, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
                    b.chests.add(new Blueprint.Chest(new BlockPos(px + 2, y + 1, pz), f, 1));
                    b.set(px - 2, y + 1, pz, s(Blocks.CARTOGRAPHY_TABLE));
                }
                case "statue" -> {
                    // 골렘 석상: 다리 둘 + 몸 + 팔 + 머리 (자수정 눈)
                    b.set(px - 1, y + 1, pz, s(Blocks.POLISHED_ANDESITE));
                    b.set(px + 1, y + 1, pz, s(Blocks.POLISHED_ANDESITE));
                    b.set(px - 1, y + 2, pz, s(Blocks.POLISHED_ANDESITE));
                    b.set(px + 1, y + 2, pz, s(Blocks.POLISHED_ANDESITE));
                    b.fill(px - 1, y + 3, pz, px + 1, y + 4, pz, s(Blocks.STONE_BRICKS));
                    b.set(px - 2, y + 4, pz, s(Blocks.MOSSY_STONE_BRICKS));
                    b.set(px + 2, y + 4, pz, s(Blocks.MOSSY_STONE_BRICKS));
                    b.set(px - 2, y + 3, pz, s(Blocks.STONE_BRICK_WALL));
                    b.set(px + 2, y + 3, pz, s(Blocks.STONE_BRICK_WALL));
                    b.set(px, y + 5, pz, s(Blocks.CHISELED_STONE_BRICKS));
                    int[] eye = l.at(0, -1);
                    b.set(eye[0], y + 5, eye[1], s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, f));
                    b.set(px, y + 3, pz, s(Blocks.AMETHYST_BLOCK));
                }
                case "cluster" -> {
                    Shape sh = new Shape();
                    for (int i = 0; i < 4; i++) spire(sh, px + r.nextInt(5) - 2, y, pz + r.nextInt(5) - 2, r);
                    sh.addTo(b);
                }
                default -> {
                }
            }
        }

        /** 정동석: 겉 현무암 · 가운데 방해석 · 안 자수정 (+ 싹 트는 자수정, 송이). open = 위 · 앞이 깨져 열림. */
        void geode(int gx, int gy, int gz, double rad, boolean open) {
            Shape sh = new Shape();
            int ri = (int) Math.ceil(rad) + 1;
            for (int dx = -ri; dx <= ri; dx++)
                for (int dy = -ri; dy <= ri; dy++)
                    for (int dz = -ri; dz <= ri; dz++) {
                        double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);
                        if (d > rad + 0.5) continue;
                        if (open && dy > rad * 0.35 && Math.floorMod(Build.hash(gx + dx, dy, gz + dz), 3) != 0) continue;
                        BlockState st;
                        if (d > rad - 0.5) st = s(Blocks.SMOOTH_BASALT);
                        else if (d > rad - 1.4) st = s(Blocks.CALCITE);
                        else if (d > rad - 2.3) st = s(Math.floorMod(Build.hash(dx, dy, dz), 6) == 0 ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK);
                        else if (dy < -rad * 0.4) st = s(Blocks.AMETHYST_BLOCK);
                        else st = s(Blocks.AIR);
                        sh.set(gx + dx, gy + dy, gz + dz, st);
                    }
            // 안쪽 바닥에 송이
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.floorMod(Build.hash(dx, 7, dz), 3) == 0) {
                        int fy = gy - (int) Math.round(rad * 0.4);
                        if (sh.get(gx + dx, fy, gz + dz) != null && sh.get(gx + dx, fy, gz + dz).isAir())
                            sh.set(gx + dx, fy, gz + dz, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
                    }
            sh.addTo(b);
        }

        // ================================================================ 가운데 랜드마크
        /** 던전 입구 자리: 오솔길 사이 (a0 + 45°) 의 dist 칸, 가운데를 봄. */
        int[] gateSpot(int dist) {
            double a = a0 + Math.PI / 4;
            return new int[]{cx + (int) Math.round(Math.cos(a) * dist), cz + (int) Math.round(Math.sin(a) * dist)};
        }

        void gate(InstanceDungeon.Theme th, int dist, int y, boolean sign) {
            int[] g = gateSpot(dist);
            GatePlan.site(th, b, g[0], y, g[1], toward(g[0], g[1], cx, cz), r, sign);
        }

        InstanceDungeon.Theme dungeon() {
            return t.dungeon == null ? null : InstanceDungeon.Theme.byKey(t.dungeon);
        }

        void center() {
            switch (t) {
                case FAIRY -> fairySpring();
                case GOBLIN -> warCamp();
                case SLIME -> slimePond();
                case SPIDER -> queenHollow();
                case HAUNTED -> chapel();
                case CRYSTAL -> crystalTemple();
            }
        }

        void fairySpring() {
            // 샘: 둥근 연못 (방해석 · 석영 테두리) + 가운데 자수정 받침 + 빛
            b.fn(cx - 7, by - 2, cz - 7, cx + 7, by + 1, cz + 7, (x, y, z) -> {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > 7.2) return null;
                if (d > 6.2) return y == by ? s(Blocks.CALCITE) : y == by + 1 && Math.floorMod(Build.hash(x, y, z), 3) == 0 ? s(Blocks.PINK_PETALS).with(FlowerbedBlock.FLOWER_AMOUNT, 4) : null;
                if (y == by - 2) return s(Blocks.QUARTZ_BLOCK);
                if (y <= by) return s(Blocks.WATER);
                return null;
            });
            b.fill(cx, by - 1, cz, cx, by + 1, cz, s(Blocks.QUARTZ_PILLAR));
            b.set(cx, by + 2, cz, s(Blocks.AMETHYST_BLOCK));
            b.set(cx, by + 3, cz, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
            for (int[] c : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) b.set(cx + c[0], by - 1, cz + c[1], s(Blocks.SEA_LANTERN));
            // 세계수: 굵은 벚나무
            int[] g = gateSpot(12);
            Shape sh = new Shape();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (Math.abs(dx) + Math.abs(dz) < 2) sh.line(g[0] + dx, by + 1, g[1] + dz, g[0] + dx, by + 12, g[1] + dz, Blocks.CHERRY_LOG);
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5 + r.nextDouble() * 0.5;
                double ex = g[0] + Math.cos(a) * 6, ez = g[1] + Math.sin(a) * 6;
                sh.line(g[0], by + 9 + r.nextInt(3), g[1], ex, by + 13 + r.nextInt(2), ez, Blocks.CHERRY_LOG);
                sh.ball(ex, by + 14, ez, 4, 2.6, 4, leaves(Blocks.CHERRY_LEAVES), r, 0.2);
            }
            sh.ball(g[0], by + 15, g[1], 5.5, 3.2, 5.5, leaves(Blocks.CHERRY_LEAVES), r, 0.15);
            for (int i = 0; i < 6; i++) sh.set(g[0] + r.nextInt(9) - 4, by + 13 + r.nextInt(3), g[1] + r.nextInt(9) - 4, s(Blocks.SHROOMLIGHT));
            sh.addTo(b);
            // 요정 고리 + 간판
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                int fx = cx + (int) Math.round(Math.cos(a) * 11), fz = cz + (int) Math.round(Math.sin(a) * 11);
                b.set(fx, by + 1, fz, k % 2 == 0 ? s(Blocks.RED_MUSHROOM) : s(Blocks.LILY_OF_THE_VALLEY));
            }
            int[] sg = gateSpot(-9);
            b.fill(sg[0], by + 1, sg[1], sg[0], by + 2, sg[1], s(Blocks.CHERRY_LOG));
            Direction face = toward(sg[0], sg[1], cx, cz);
            b.signs.add(new Blueprint.Sign(new BlockPos(sg[0] + face.getOffsetX(), by + 2, sg[1] + face.getOffsetZ()), face,
                    new String[]{"region.abilitywar.sign.top", "region.abilitywar.spring.1", "region.abilitywar.spring.2", "region.abilitywar.spring.3"}));
        }

        void warCamp() {
            // 목책: 반지름 17, 오솔길 4곳은 문
            int pr = 17;
            double[] gaps = new double[trails.size()];
            for (int k = 0; k < gaps.length; k++) {
                double[] e = trails.get(k)[trails.get(k).length - 1];
                gaps[k] = Math.atan2(e[1] - cz, e[0] - cx);
            }
            b.fn(cx - pr - 1, by + 1, cz - pr - 1, cx + pr + 1, by + 6, cz + pr + 1, (x, y, z) -> {
                double dx = x - cx, dz = z - cz, d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - pr) > 0.55) return null;
                double a = Math.atan2(dz, dx);
                for (double ta : gaps) {
                    double da = Math.abs(Math.atan2(Math.sin(a - ta), Math.cos(a - ta)));
                    if (da * pr < 2.6) return null;
                }
                int top = by + 3 + Math.floorMod(Build.hash(x, 0, z), 3);
                if (y < top) return s(Blocks.SPRUCE_LOG);
                if (y == top) return s(Blocks.SPRUCE_FENCE);
                return null;
            });
            // 족장 오두막 (큰 둥근 집, 2층 지붕)
            int[] hutAt = gateSpot(-10);
            int hx = hutAt[0], hz = hutAt[1];
            Direction hf = toward(hx, hz, cx, cz);
            b.fn(hx - 5, by + 1, hz - 5, hx + 5, by + 9, hz + 5, (x, y, z) -> {
                double d = Math.sqrt((x - hx) * (x - hx) + (z - hz) * (z - hz));
                if (y <= by + 4) {
                    if (d <= 5.1 && d > 4.1) return s(Math.floorMod(Build.hash(x, y, z), 6) == 0 ? Blocks.MUD_BRICKS : Blocks.PACKED_MUD);
                    if (d <= 4.1) return s(Blocks.AIR);
                    return null;
                }
                double rr = 6 - (y - by - 5) * 1.4;
                return d <= rr ? s(y == by + 9 ? Blocks.SPRUCE_PLANKS : Blocks.HAY_BLOCK) : null;
            });
            GatePlan.L hl = new GatePlan.L(hx, hz, hf);
            int[] hd = hl.at(0, -5), hd2 = hl.at(1, -5);
            b.fill(hd[0], by + 1, hd[1], hd[0], by + 3, hd[1], s(Blocks.AIR));
            b.fill(hd2[0], by + 1, hd2[1], hd2[0], by + 3, hd2[1], s(Blocks.AIR));
            b.set(hx, by + 1, hz, s(Blocks.RED_CARPET));
            int[] throne = hl.at(0, 3);
            b.set(throne[0], by + 1, throne[1], Build.stairs(Blocks.SPRUCE_STAIRS, hf.getOpposite(), false));
            int[] tc = hl.at(2, 3);
            b.chests.add(new Blueprint.Chest(new BlockPos(tc[0], by + 1, tc[1]), hf, 1));
            b.set(hx, by + 4, hz, s(Blocks.LANTERN).with(LanternBlock.HANGING, true));
            // 모닥불 + 해골 장대 + 북 (소리 블록)
            b.fill(cx - 1, by, cz - 1, cx + 1, by, cz + 1, s(Blocks.NETHERRACK));
            b.fill(cx - 1, by + 1, cz - 1, cx + 1, by + 1, cz + 1, s(Blocks.FIRE));
            b.set(cx, by + 1, cz, s(Blocks.NETHERRACK));
            b.set(cx, by + 2, cz, s(Blocks.FIRE));
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + 0.3;
                int sx = cx + (int) Math.round(Math.cos(a) * 4), sz = cz + (int) Math.round(Math.sin(a) * 4);
                b.fill(sx, by + 1, sz, sx, by + 2, sz, s(Blocks.SPRUCE_FENCE));
                b.set(sx, by + 3, sz, s(Blocks.SKELETON_SKULL).with(SkullBlock.ROTATION, r.nextInt(16)));
            }
            for (int k = 0; k < 2; k++) {
                double a = a0 + Math.PI * 3 / 4 + k * Math.PI;
                int sx = cx + (int) Math.round(Math.cos(a) * 11), sz = cz + (int) Math.round(Math.sin(a) * 11);
                b.set(sx, by + 1, sz, s(k % 2 == 0 ? Blocks.NOTE_BLOCK : Blocks.BARREL));
                b.set(sx + 1, by + 1, sz, s(Blocks.HAY_BLOCK));
            }
            gate(InstanceDungeon.Theme.GOBLIN, 7, by, true);
            for (int i = 0; i < 3; i++) {
                int sx = cx + r.nextInt(11) - 5, sz = cz + r.nextInt(11) - 5;
                b.spawns.add(new Blueprint.Spawn(new BlockPos(sx, by + 1, sz), w -> com.abilitywar.mob.AWMobs.GOBLIN.create(w)));
            }
        }

        void slimePond() {
            // 연못 (반지름 9, 깊이 3) + 가운데 섬 + 슬라임 왕 석상
            b.fn(cx - 10, by - 3, cz - 10, cx + 10, by + 1, cz + 10, (x, y, z) -> {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > 10.2) return null;
                if (d > 9.2) return y == by ? s(Blocks.MUD) : y == by + 1 && Math.floorMod(Build.hash(x, y, z), 4) == 0 ? s(Blocks.SLIME_BLOCK) : null;
                if (d <= 3.4) return y <= by ? s(Blocks.MOSS_BLOCK) : null;
                if (y == by - 3) return s(Blocks.MUD);
                if (y <= by) return s(Blocks.WATER);
                return y == by + 1 && Math.floorMod(Build.hash(x, y, z), 9) == 0 ? s(Blocks.LILY_PAD) : null;
            });
            Shape king = new Shape();
            king.ball(cx, by + 3.2, cz, 3.2, 2.8, 3.2, s(Blocks.SLIME_BLOCK), r, 0.0);
            Direction face = toward(cx, cz, gateSpot(12)[0], gateSpot(12)[1]).getOpposite();
            GatePlan.L l = new GatePlan.L(cx, cz, face);
            int[] e1 = l.at(-1, -3), e2 = l.at(1, -3), m1 = l.at(0, -3);
            king.set(e1[0], by + 4, e1[1], s(Blocks.BLACK_CONCRETE));
            king.set(e2[0], by + 4, e2[1], s(Blocks.BLACK_CONCRETE));
            king.set(m1[0], by + 2, m1[1], s(Blocks.GREEN_CONCRETE));
            // 왕관
            for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, 0}}) king.set(cx + c[0], by + 6, cz + c[1], s(Blocks.GOLD_BLOCK));
            king.set(cx, by + 7, cz, s(Blocks.GOLD_BLOCK));
            king.addTo(b);
            // 연못 둘레 버섯
            Shape sh = new Shape();
            for (int k = 0; k < 4; k++) {
                double a = a0 + Math.PI / 4 + k * Math.PI / 2 + 0.9;
                giantMushroom(sh, cx + (int) Math.round(Math.cos(a) * 15), by, cz + (int) Math.round(Math.sin(a) * 15), r, k % 2 == 0);
            }
            sh.addTo(b);
            gate(InstanceDungeon.Theme.SLIME, 13, by, true);
        }

        void queenHollow() {
            // 그릇 모양 구덩이 (반지름 11, 깊이 5): 바닥 자갈 · 뼈 · 거미줄, 가장자리에 큰 죽은 나무
            b.fn(cx - 12, by - 6, cz - 12, cx + 12, by + 8, cz + 12, (x, y, z) -> {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > 11.5) return null;
                int floor = by - (int) Math.round(5 * (1 - (d / 11.5) * (d / 11.5)));
                if (y < floor) return null;
                if (y == floor) return s(Math.floorMod(Build.hash(x, y, z), 7) == 0 ? Blocks.BONE_BLOCK : Math.floorMod(Build.hash(x, y, z), 3) == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT);
                if (y == floor + 1 && Math.floorMod(Build.hash(x, y, z), 9) == 0) return s(Blocks.COBWEB);
                return s(Blocks.AIR);
            });
            Shape sh = new Shape();
            for (int k = 0; k < 6; k++) {
                double a = a0 + k * Math.PI / 3 + Math.PI / 6;
                int tx = cx + (int) Math.round(Math.cos(a) * 15), tz = cz + (int) Math.round(Math.sin(a) * 15);
                deadTree(sh, tx, by, tz, r, Blocks.DARK_OAK_LOG, true);
                double a2 = a + Math.PI / 3;
                int ux = cx + (int) Math.round(Math.cos(a2) * 15), uz = cz + (int) Math.round(Math.sin(a2) * 15);
                sh.strand(tx, by + 7, tz, ux, by + 7, uz, s(Blocks.COBWEB), true);
                sh.strand(tx, by + 6, tz, cx, by + 2, cz, s(Blocks.COBWEB), true);
            }
            for (int i = 0; i < 5; i++) sh.ball(cx + r.nextInt(9) - 4, by - 3.5, cz + r.nextInt(9) - 4, 1.2, 1.0, 1.2, s(Blocks.WHITE_WOOL), r, 0.1);
            sh.addTo(b);
            gate(InstanceDungeon.Theme.SPIDER, 7, by - 3, false);
        }

        void chapel() {
            // 무너진 예배당 (11 × 17, 심층암 벽돌) — 축은 오솔길 사이
            Direction f = toward(cx, cz, gateSpot(10)[0], gateSpot(10)[1]).getOpposite();
            GatePlan.L l = new GatePlan.L(cx, cz, f);
            for (int u = -5; u <= 5; u++)
                for (int v = -8; v <= 8; v++)
                    for (int h = 0; h <= 9; h++) {
                        boolean wall = Math.abs(u) == 5 || Math.abs(v) == 8;
                        int[] p = l.at(u, v);
                        int hb = Build.hash(p[0], h, p[1]);
                        BlockState st;
                        if (h == 0) st = s(Math.floorMod(hb, 5) == 0 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES);
                        else if (wall) {
                            // 위쪽은 무너져 들쭉날쭉
                            int ruin = 5 + Math.floorMod(Build.hash(p[0], 0, p[1]), 5);
                            if (h > ruin) continue;
                            if (h >= 3 && h <= 5 && Math.abs(u) == 5 && Math.floorMod(v, 4) == 0) st = s(Blocks.PURPLE_STAINED_GLASS_PANE);
                            else st = s(Math.floorMod(hb, 6) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS);
                        } else st = s(Blocks.AIR);
                        b.set(p[0], by + h, p[1], st);
                    }
            // 문 · 제단 · 의자 · 촛불
            for (int u = -1; u <= 1; u++)
                for (int h = 1; h <= 4; h++) l.set(b, u, by + h, -8, s(Blocks.AIR));
            l.set(b, 0, by + 1, 6, s(Blocks.CHISELED_DEEPSLATE));
            l.set(b, 0, by + 2, 6, s(Blocks.CANDLE).with(CandleBlock.CANDLES, 4).with(CandleBlock.LIT, true));
            l.set(b, -2, by + 1, 6, s(Blocks.SOUL_LANTERN));
            l.set(b, 2, by + 1, 6, s(Blocks.SOUL_LANTERN));
            for (int v = -5; v <= 3; v += 2)
                for (int u : new int[]{-3, -2, 2, 3})
                    if (Math.floorMod(Build.hash(u, v, 9), 4) != 0) l.set(b, u, by + 1, v, Build.stairs(Blocks.DARK_OAK_STAIRS, f, false));
            int[] ch = l.at(3, 6);
            b.chests.add(new Blueprint.Chest(new BlockPos(ch[0], by + 1, ch[1]), f, 1));
            // 묘지 (예배당 앞)
            for (int u = -6; u <= 6; u += 3)
                for (int v = -16; v <= -11; v += 3) {
                    l.set(b, u, by + 1, v, s(Math.floorMod(u + v, 2) == 0 ? Blocks.MOSSY_COBBLESTONE_WALL : Blocks.COBBLESTONE_WALL));
                    l.set(b, u, by + 1, v - 1, s(Blocks.STONE_SLAB).with(SlabBlock.TYPE, SlabType.BOTTOM));
                }
            gate(InstanceDungeon.Theme.CULT, 15, by, true);
            b.spawners.add(new Blueprint.Spawner(new BlockPos(cx, by + 1, cz), com.abilitywar.mob.AWMobs.WRAITH));
        }

        void crystalTemple() {
            geode(cx, by + 3, cz, 8.5, true);
            // 정동석 앞 입구를 틈
            Direction f = toward(cx, cz, gateSpot(13)[0], gateSpot(13)[1]);
            GatePlan.L l = new GatePlan.L(cx, cz, f.getOpposite());
            for (int v = -10; v <= -4; v++)
                for (int u = -1; u <= 1; u++)
                    for (int h = 1; h <= 4; h++) l.set(b, u, by + h, v, s(Blocks.AIR));
            for (int v = -10; v <= -4; v++)
                for (int u = -1; u <= 1; u++) l.set(b, u, by, v, s(Blocks.CALCITE));
            // 안: 골렘 제단 (석영 + 자수정) + 상자
            b.fill(cx - 1, by - 1, cz - 1, cx + 1, by - 1, cz + 1, s(Blocks.CHISELED_QUARTZ_BLOCK));
            b.set(cx, by, cz, s(Blocks.QUARTZ_PILLAR));
            b.set(cx, by + 1, cz, s(Blocks.AMETHYST_BLOCK));
            b.set(cx, by + 2, cz, s(Blocks.AMETHYST_CLUSTER).with(AmethystClusterBlock.FACING, Direction.UP));
            b.chests.add(new Blueprint.Chest(new BlockPos(cx + 2, by, cz), f, 1));
            // 둘레 첨탑
            Shape sh = new Shape();
            for (int k = 0; k < 5; k++) {
                double a = a0 + Math.PI / 4 + Math.PI / 5 + k * Math.PI * 2 / 5;
                spire(sh, cx + (int) Math.round(Math.cos(a) * 15), by, cz + (int) Math.round(Math.sin(a) * 15), r);
            }
            sh.addTo(b);
            gate(InstanceDungeon.Theme.GOLEM, 14, by, true);
        }
    }

    private RegionPlan() {
    }
}

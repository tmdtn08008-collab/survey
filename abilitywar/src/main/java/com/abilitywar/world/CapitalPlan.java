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
 * 수도 「아르덴」: 가로세로 230칸 성벽 도시.
 * <pre>
 *  북쪽: 왕성 (성벽 + 탑 4 + 큰 탑 2 + 왕좌의 방, 왕)
 *  가운데: 분수 광장 (스폰), 큰길 십자
 *  동쪽: 전직 교관의 길드 회관 · 서쪽: 신전 · 남쪽: 시장, 여관, 대장간
 *  나머지: 집과 정원 (골목, 가로등), 시민 (주민), 성문 경비 (철 골렘)
 * </pre>
 */
public final class CapitalPlan {
    static final int R = 112;

    record Rect(int x0, int z0, int x1, int z1) {
        boolean overlaps(int ax0, int az0, int ax1, int az1) {
            return ax0 <= x1 && ax1 >= x0 && az0 <= z1 && az1 >= z0;
        }
    }

    public static void build(Blueprint b, int cx, int by, int cz, Random r) {
        List<Rect> reserved = new ArrayList<>();
        // ---- 땅 고르기 (성벽 바깥 6칸까지)
        b.terraformRect(cx - R - 6, cz - R - 6, cx + R + 6, cz + R + 6, 12, by, 45, s(Blocks.GRASS_BLOCK), s(Blocks.DIRT));

        // ---- 골목 (집 구역): 24칸마다
        for (int k = -4; k <= 4; k++) {
            int lx = cx + k * 24 - 12;
            b.fn(lx - 1, by, cz - R + 6, lx + 1, by, cz + R - 6, (x, y, z) -> pathBlock(x, y, z));
            int lz = cz + k * 24 - 12;
            b.fn(cx - R + 6, by, lz - 1, cx + R - 6, by, lz + 1, (x, y, z) -> pathBlock(x, y, z));
        }
        // ---- 큰길: 남북 (남문 → 왕성 문), 동서 (서문 → 동문)
        b.fn(cx - 3, by, cz - 60, cx + 3, by, cz + R + 2, (x, y, z) -> roadBlock(x - cx, x, y, z));
        b.fn(cx - R - 2, by, cz - 3, cx + R + 2, by, cz + 3, (x, y, z) -> roadBlock(z - cz, x, y, z));
        reserved.add(new Rect(cx - 6, cz - 110, cx + 6, cz + 110));
        reserved.add(new Rect(cx - 110, cz - 6, cx + 110, cz + 6));

        // ---- 광장 + 분수 + 가로등
        b.fn(cx - 25, by, cz - 25, cx + 25, by, cz + 25, (x, y, z) -> {
            double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (d > 25) return null;
            if (d > 23.5) return s(Blocks.POLISHED_ANDESITE);
            int ring = (int) d;
            if (ring == 8 || ring == 16) return s(Blocks.POLISHED_DIORITE);
            return ((x + z) & 1) == 0 ? s(Blocks.SMOOTH_STONE) : s(Blocks.STONE_BRICKS);
        });
        Build.fountain(b, cx, cz, by, 5);
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6 + Math.PI / 12;
            Build.lamp(b, cx + (int) Math.round(Math.cos(a) * 21), cz + (int) Math.round(Math.sin(a) * 21), by);
        }
        // 광장 벤치
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            int bx = cx + (int) Math.round(Math.cos(a) * 12), bz = cz + (int) Math.round(Math.sin(a) * 12);
            b.set(bx, by + 1, bz, Build.stairs(Blocks.SPRUCE_STAIRS, Direction.fromRotation(Math.toDegrees(a) - 90 + 180), false));
        }
        reserved.add(new Rect(cx - 28, cz - 28, cx + 28, cz + 28));

        // ---- 바깥 성벽 + 모서리 탑 + 문 (동·서·남), 북쪽은 왕성 뒷벽
        int h = 12;
        Build.wall(b, cx - R, cz - R, cx + R, cz - R, by, h, 5, Blocks.STONE_BRICKS, 12);
        Build.wall(b, cx - R, cz + R, cx + R, cz + R, by, h, 5, Blocks.STONE_BRICKS, 12);
        Build.wall(b, cx - R, cz - R, cx - R, cz + R, by, h, 5, Blocks.STONE_BRICKS, 12);
        Build.wall(b, cx + R, cz - R, cx + R, cz + R, by, h, 5, Blocks.STONE_BRICKS, 12);
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) Build.tower(b, cx + sx * R, cz + sz * R, by, 8, 22, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, true, null, 10);
        gate(b, cx, cz + R, by, true, r);   // 남문
        gate(b, cx - R, cz, by, false, r);  // 서문
        gate(b, cx + R, cz, by, false, r);  // 동문
        // 성벽 위를 따라 가로등
        for (int k = -R + 12; k <= R - 12; k += 16) {
            b.set(cx + k, by + h + 1, cz + R, s(Blocks.LANTERN));
            b.set(cx + k, by + h + 1, cz - R, s(Blocks.LANTERN));
            b.set(cx + R, by + h + 1, cz + k, s(Blocks.LANTERN));
            b.set(cx - R, by + h + 1, cz + k, s(Blocks.LANTERN));
        }

        // ---- 왕성
        castle(b, cx, cz, by, r);
        reserved.add(new Rect(cx - 46, cz - R, cx + 46, cz - 38));

        // ---- 전직 교관 길드 (동쪽, 큰길 남쪽) / 신전 (서쪽) / 시장 · 여관 · 대장간 (남쪽)
        Build.Palette guild = new Build.Palette(Blocks.STONE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.PURPUR_STAIRS, Blocks.PURPUR_BLOCK, Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.POLISHED_ANDESITE);
        Build.house(b, cx + 32, cz + 8, 22, 15, by, 2, Direction.NORTH, guild, r);
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx + 43, by + 1, cz + 11), w -> named(AWEntities.JOB_MASTER.create(w), 0)));
        // 길드 앞 수련장: 허수아비 (갑옷 거치대) + 과녁
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

        Block[][] wools = {{Blocks.RED_WOOL, Blocks.WHITE_WOOL}, {Blocks.BLUE_WOOL, Blocks.WHITE_WOOL}, {Blocks.YELLOW_WOOL, Blocks.ORANGE_WOOL}, {Blocks.GREEN_WOOL, Blocks.LIME_WOOL}};
        VillagerProfession[] trades = {VillagerProfession.FARMER, VillagerProfession.FLETCHER, VillagerProfession.LIBRARIAN, VillagerProfession.ARMORER,
                VillagerProfession.WEAPONSMITH, VillagerProfession.BUTCHER, VillagerProfession.CARTOGRAPHER, VillagerProfession.TOOLSMITH};
        int stall = 0;
        for (int row = 0; row < 2; row++)
            for (int k = -4; k <= 3; k++) {
                int sx = cx + k * 7 + (k >= 0 ? 7 : 0) - 3, sz = cz + 32 + row * 9;
                Block[] wl = wools[(stall) % wools.length];
                Build.stall(b, sx, sz, by, wl[0], wl[1], r);
                VillagerProfession pr = trades[stall % trades.length];
                b.spawns.add(new Blueprint.Spawn(new BlockPos(sx + 1, by + 1, sz + 2), w -> villager(w, pr)));
                stall++;
            }
        reserved.add(new Rect(cx - 36, cz + 28, cx + 36, cz + 48));

        Build.house(b, cx + 10, cz + 54, 16, 12, by, 2, Direction.NORTH, Build.SPRUCE, r);
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx + 17, by + 1, cz + 58), w -> villager(w, VillagerProfession.NONE)));
        Build.house(b, cx - 26, cz + 54, 12, 10, by, 1, Direction.NORTH, Build.STONE, r);
        b.set(cx - 22, by + 1, cz + 52, s(Blocks.ANVIL));
        b.set(cx - 19, by + 1, cz + 52, s(Blocks.BLAST_FURNACE));
        b.set(cx - 18, by + 1, cz + 52, s(Blocks.LAVA_CAULDRON));
        b.set(cx - 17, by + 1, cz + 52, s(Blocks.GRINDSTONE));
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx - 20, by + 1, cz + 51), w -> villager(w, VillagerProfession.WEAPONSMITH)));
        reserved.add(new Rect(cx - 30, cz + 50, cx + 30, cz + 68));

        // ---- 집과 정원: 24칸 블록마다 10칸 땅 넷 (집이 빽빽한 도시)
        for (int gx = -4; gx <= 3; gx++)
            for (int gz = -4; gz <= 3; gz++) {
                int lx0 = cx + gx * 24 - 10, lz0 = cz + gz * 24 - 10, lx1 = lx0 + 20, lz1 = lz0 + 20;
                if (Math.abs(lx0 - cx) > R - 4 || Math.abs(lx1 - cx) > R - 4 || Math.abs(lz0 - cz) > R - 4 || Math.abs(lz1 - cz) > R - 4) continue;
                boolean any = false;
                for (Rect rc : reserved) if (rc.overlaps(lx0, lz0, lx1, lz1)) any = true;
                if (!any && r.nextFloat() < 0.1f) {
                    garden(b, lx0 + 2, lz0 + 2, lx1 - 2, lz1 - 2, by, r);
                    continue;
                }
                for (int qx = 0; qx <= 1; qx++)
                    for (int qz = 0; qz <= 1; qz++) {
                        int sx0 = lx0 + qx * 11, sz0 = lz0 + qz * 11, sx1 = sx0 + 9, sz1 = sz0 + 9;
                        boolean blocked = false;
                        for (Rect rc : reserved) if (rc.overlaps(sx0 - 1, sz0 - 1, sx1 + 1, sz1 + 1)) blocked = true;
                        if (blocked) continue;
                        if (r.nextFloat() < 0.15f) {
                            Build.tree(b, (sx0 + sx1) / 2, (sz0 + sz1) / 2, by, r, r.nextBoolean() ? Blocks.OAK_LOG : Blocks.BIRCH_LOG, r.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.BIRCH_LEAVES);
                            flowers(b, sx0, sz0, sx1, sz1, by, r, 0, 0, -1, -1, 8);
                        } else subLot(b, sx0, sz0, qx, qz, by, r);
                    }
                // 골목 교차점 등불
                Build.lamp(b, lx0 - 1, lz0 - 1, by);
            }

        // ---- 큰길 가로등 + 시민 + 경비
        for (int k = -R + 8; k <= R - 8; k += 12) {
            if (Math.abs(k) < 28) continue;
            Build.lamp(b, cx + 5, cz + k, by);
            Build.lamp(b, cx - 5, cz + k, by);
            Build.lamp(b, cx + k, cz + 5, by);
            Build.lamp(b, cx + k, cz - 5, by);
        }
        for (int k = 0; k < 18; k++) {
            int px, pz;
            if (r.nextBoolean()) {
                px = cx + r.nextInt(5) - 2;
                pz = cz + 30 + r.nextInt(R - 40) * (r.nextBoolean() ? 1 : -1) / 2;
            } else {
                px = cx + (r.nextInt(R - 40) + 30) * (r.nextBoolean() ? 1 : -1);
                pz = cz + r.nextInt(5) - 2;
            }
            b.spawns.add(new Blueprint.Spawn(new BlockPos(px, by + 1, pz), w -> villager(w, VillagerProfession.NONE)));
        }
        for (int[] g : new int[][]{{cx - 5, cz + R - 8}, {cx + 5, cz + R - 8}, {cx - R + 8, cz + 5}, {cx + R - 8, cz - 5}, {cx - 4, cz - 48}, {cx + 4, cz - 48}})
            b.spawns.add(new Blueprint.Spawn(new BlockPos(g[0], by + 1, g[1]), w -> {
                IronGolemEntity e = EntityType.IRON_GOLEM.create(w);
                if (e != null) e.setPlayerCreated(true);
                return e;
            }));
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

    /** 성문: 벽에 구멍 + 양쪽 탑 + 아치 + 내리닫이 쇠창살. ns = 남·북 벽 (x 방향 벽). */
    static void gate(Blueprint b, int gx, int gz, int by, boolean ns, Random r) {
        int w = 3;
        if (ns) {
            b.fill(gx - w, by + 1, gz - 3, gx + w, by + 8, gz + 3, s(Blocks.AIR));
            b.fill(gx - w, by + 9, gz - 3, gx + w, by + 9, gz + 3, s(Blocks.CHISELED_STONE_BRICKS));
            b.fill(gx - w, by + 8, gz, gx + w, by + 8, gz, s(Blocks.IRON_BARS));
            b.fn(gx - w, by, gz - 3, gx + w, by, gz + 3, (x, y, z) -> roadBlock(x - gx, x, y, z));
            Build.tower(b, gx - 9, gz, by, 5, 16, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, false, null, 10);
            Build.tower(b, gx + 9, gz, by, 5, 16, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, false, null, 10);
        } else {
            b.fill(gx - 3, by + 1, gz - w, gx + 3, by + 8, gz + w, s(Blocks.AIR));
            b.fill(gx - 3, by + 9, gz - w, gx + 3, by + 9, gz + w, s(Blocks.CHISELED_STONE_BRICKS));
            b.fill(gx, by + 8, gz - w, gx, by + 8, gz + w, s(Blocks.IRON_BARS));
            b.fn(gx - 3, by, gz - w, gx + 3, by, gz + w, (x, y, z) -> roadBlock(z - gz, x, y, z));
            Build.tower(b, gx, gz - 9, by, 5, 16, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, false, null, 10);
            Build.tower(b, gx, gz + 9, by, 5, 16, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, false, null, 10);
        }
    }

    /** 10x10 땅에 집 한 채: 문은 바깥 (골목) 쪽, 문 앞 작은 길. */
    static void subLot(Blueprint b, int x0, int z0, int qx, int qz, int by, Random r) {
        int w = 6 + r.nextInt(3), d = 6 + r.nextInt(3);
        Direction door = r.nextBoolean() ? (qx == 0 ? Direction.WEST : Direction.EAST) : (qz == 0 ? Direction.NORTH : Direction.SOUTH);
        // 문 쪽 벽을 땅 가장자리에서 1칸 띄움
        int hx = door == Direction.WEST ? x0 + 1 : door == Direction.EAST ? x0 + 9 - w : x0 + 1 + r.nextInt(10 - w - 1);
        int hz = door == Direction.NORTH ? z0 + 1 : door == Direction.SOUTH ? z0 + 9 - d : z0 + 1 + r.nextInt(10 - d - 1);
        Build.Palette p = Build.HOUSES[r.nextInt(Build.HOUSES.length)];
        Build.house(b, hx, hz, w, d, by, r.nextFloat() < 0.5f ? 2 : 1, door, p, r);
        int[] o = Build.doorOutside(hx, hz, w, d, door);
        for (int k = 0; k < 3; k++) b.set(o[0] + door.getOffsetX() * k, by, o[1] + door.getOffsetZ() * k, pathBlock(o[0] + k, by, o[1] + k));
        if (r.nextFloat() < 0.5f) {
            int fx = door.getAxis() == Direction.Axis.X ? o[0] : o[0] + 2, fz = door.getAxis() == Direction.Axis.Z ? o[1] : o[1] + 2;
            b.set(fx, by + 1, fz, r.nextBoolean() ? s(Blocks.POPPY) : r.nextBoolean() ? s(Blocks.BARREL) : s(Blocks.HAY_BLOCK));
        }
        if (r.nextFloat() < 0.35f)
            b.spawns.add(new Blueprint.Spawn(new BlockPos(hx + w / 2, by + 1, hz + d / 2), wd -> villager(wd, VillagerProfession.NONE)));
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

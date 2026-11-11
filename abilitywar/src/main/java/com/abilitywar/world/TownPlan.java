package com.abilitywar.world;

import com.abilitywar.registry.AWEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;

import java.util.Random;

import static com.abilitywar.world.Build.s;

/**
 * 마을: 지름 70칸 원형 목책 + 문 4개 + 망루 4개, 십자 길, 가운데 우물 광장,
 * 여관 · 대장간 · 예배당 · 전직 교관 회관 + 집들 + 밭, 주민 · 철 골렘.
 */
public final class TownPlan {
    static final int R = 35;

    /** 사분면 하나 (+x, +z 기준) 의 땅. 거울로 뒤집어서 네 곳에 씀. */
    record Lot(int x, int z, int w, int d, Direction door) {
    }

    static final Lot[] LOTS = {
            new Lot(5, 6, 10, 8, Direction.WEST),   // A: 특별 건물 (광장 옆)
            new Lot(18, 5, 8, 7, Direction.NORTH),  // B
            new Lot(5, 18, 7, 9, Direction.WEST),   // C
            new Lot(16, 16, 9, 8, Direction.NORTH), // D
    };

    public static void build(Blueprint b, int cx, int by, int cz, Random r) {
        b.terraformCircle(cx, cz, R + 3, 10, by, 30, s(Blocks.GRASS_BLOCK), s(Blocks.DIRT));

        // ---- 길 (십자) + 광장
        b.fn(cx - 2, by, cz - R - 3, cx + 2, by, cz + R + 3, (x, y, z) -> road(x, y, z, Math.abs(x - cx) == 2));
        b.fn(cx - R - 3, by, cz - 2, cx + R + 3, by, cz + 2, (x, y, z) -> road(x, y, z, Math.abs(z - cz) == 2));
        b.fn(cx - 7, by, cz - 7, cx + 7, by, cz + 7, (x, y, z) -> {
            int dx = x - cx, dz = z - cz;
            if (dx * dx + dz * dz > 52) return null;
            return ((x + z) & 1) == 0 ? s(Blocks.COBBLESTONE) : s(Blocks.STONE_BRICKS);
        });
        well(b, cx, cz, by);
        for (int[] l : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) Build.lamp(b, cx + l[0], cz + l[1], by);

        // ---- 목책 + 문 + 망루
        b.fn(cx - R - 1, by + 1, cz - R - 1, cx + R + 1, by + 5, cz + R + 1, (x, y, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < R - 0.5 || d >= R + 0.5) return null;
            if (Math.abs(dx) <= 2 || Math.abs(dz) <= 2) return null;
            int ly = y - by;
            if (ly <= 4) return Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
            return Math.floorMod(dx + dz, 2) == 0 ? s(Blocks.SPRUCE_FENCE) : null;
        });
        for (int k = 0; k < 4; k++) {
            Direction dir = Direction.fromHorizontal(k);
            int gx = cx + dir.getOffsetX() * R, gz = cz + dir.getOffsetZ() * R;
            // 문 기둥 + 위 들보 + 등불
            int px = dir.getOffsetZ() != 0 ? 3 : 0, pz = dir.getOffsetX() != 0 ? 3 : 0;
            b.fill(gx - px, by + 1, gz - pz, gx - px, by + 6, gz - pz, Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            b.fill(gx + px, by + 1, gz + pz, gx + px, by + 6, gz + pz, Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            b.fill(gx - px, by + 7, gz - pz, gx + px, by + 7, gz + pz, Build.log(Blocks.SPRUCE_LOG, dir.getOffsetZ() != 0 ? Direction.Axis.X : Direction.Axis.Z));
            b.set(gx - px, by + 8, gz - pz, s(Blocks.LANTERN));
            b.set(gx + px, by + 8, gz + pz, s(Blocks.LANTERN));
            // 망루 (대각선)
            double a = Math.PI / 4 + k * Math.PI / 2;
            int tx = cx + (int) Math.round(Math.cos(a) * (R - 3)), tz = cz + (int) Math.round(Math.sin(a) * (R - 3));
            watchtower(b, tx, tz, by);
        }

        // ---- 건물 (사분면마다 A 는 특별 건물)
        int q = 0;
        for (int sx = 1; sx >= -1; sx -= 2)
            for (int sz = 1; sz >= -1; sz -= 2) {
                for (int i = 0; i < LOTS.length; i++) {
                    Lot l = LOTS[i];
                    int x0 = sx > 0 ? cx + l.x : cx - l.x - l.w + 1;
                    int z0 = sz > 0 ? cz + l.z : cz - l.z - l.d + 1;
                    Direction door = mirror(l.door, sx, sz);
                    if (i == 0) special(b, q, x0, z0, l.w, l.d, by, door, cx, cz, r);
                    else if (i == 3 && (q == 0 || q == 3)) farm(b, x0, z0, l.w, l.d, by, r);
                    else if (r.nextFloat() < 0.12f) {
                        for (int k = 0; k < 2; k++) Build.tree(b, x0 + 1 + r.nextInt(Math.max(1, l.w - 2)), z0 + 1 + r.nextInt(Math.max(1, l.d - 2)), by, r, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
                    } else {
                        Build.Palette p = Build.HOUSES[r.nextInt(Build.HOUSES.length)];
                        int w = l.w - r.nextInt(2), d = l.d - r.nextInt(2);
                        Build.house(b, x0, z0, w, d, by, r.nextFloat() < 0.35f ? 2 : 1, door, p, r);
                        pathFromDoor(b, x0, z0, w, d, door, cx, cz, by);
                        if (r.nextFloat() < 0.6f)
                            b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + w / 2, by + 1, z0 + d / 2), w2 -> CapitalPlan.villager(w2, VillagerProfession.NONE)));
                    }
                }
                q++;
            }

        // ---- 길가 등불 + 꽃 + 주민 + 골렘
        for (int k = 10; k <= R - 4; k += 8)
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Build.lamp(b, cx + 3, cz + sgn * k, by);
                Build.lamp(b, cx + sgn * k, cz - 3, by);
            }
        Block[] fl = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.GRASS, Blocks.GRASS, Blocks.GRASS};
        for (int k = 0; k < 60; k++) {
            int x = cx + r.nextInt(R * 2) - R, z = cz + r.nextInt(R * 2) - R;
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > (R - 2) * (R - 2) || Math.abs(x - cx) <= 3 || Math.abs(z - cz) <= 3) continue;
            b.set(x, by + 1, z, s(fl[r.nextInt(fl.length)]));
        }
        for (int k = 0; k < 4; k++) {
            int px = cx + r.nextInt(9) - 4, pz = cz + r.nextInt(9) - 4;
            if (Math.abs(px - cx) <= 1 && Math.abs(pz - cz) <= 1) px += 3;
            final int fx = px, fz = pz;
            b.spawns.add(new Blueprint.Spawn(new BlockPos(fx, by + 1, fz), w -> CapitalPlan.villager(w, VillagerProfession.NONE)));
        }
        b.spawns.add(new Blueprint.Spawn(new BlockPos(cx + 4, by + 1, cz - 9), w -> {
            IronGolemEntity e = EntityType.IRON_GOLEM.create(w);
            if (e != null) e.setPlayerCreated(true);
            return e;
        }));
    }

    static Direction mirror(Direction d, int sx, int sz) {
        if (sx < 0 && d.getAxis() == Direction.Axis.X) d = d.getOpposite();
        if (sz < 0 && d.getAxis() == Direction.Axis.Z) d = d.getOpposite();
        return d;
    }

    static BlockState road(int x, int y, int z, boolean edge) {
        int h = Math.floorMod(Build.hash(x, y, z), 10);
        if (edge) return h < 5 ? s(Blocks.COARSE_DIRT) : s(Blocks.DIRT_PATH);
        return h < 7 ? s(Blocks.DIRT_PATH) : h < 9 ? s(Blocks.GRAVEL) : s(Blocks.COBBLESTONE);
    }

    /** 문 앞에서 가까운 큰길까지 흙길. */
    static void pathFromDoor(Blueprint b, int x0, int z0, int w, int d, Direction door, int cx, int cz, int by) {
        int[] o = Build.doorOutside(x0, z0, w, d, door);
        int x = o[0], z = o[1];
        if (door.getAxis() == Direction.Axis.X) {
            int tx = cx + (x > cx ? 2 : -2);
            b.fn(Math.min(x, tx), by, z, Math.max(x, tx), by, z, (xx, yy, zz) -> road(xx, yy, zz, false));
        } else {
            int tz = cz + (z > cz ? 2 : -2);
            b.fn(x, by, Math.min(z, tz), x, by, Math.max(z, tz), (xx, yy, zz) -> road(xx, yy, zz, false));
        }
    }

    static void well(Blueprint b, int cx, int cz, int by) {
        b.fill(cx - 2, by - 3, cz - 2, cx + 2, by + 1, cz + 2, s(Blocks.COBBLESTONE));
        b.fill(cx - 1, by - 2, cz - 1, cx + 1, by + 1, cz + 1, s(Blocks.WATER));
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.fill(cx + c[0], by + 2, cz + c[1], cx + c[0], by + 4, cz + c[1], s(Blocks.OAK_FENCE));
        b.fill(cx - 2, by + 5, cz - 2, cx + 2, by + 5, cz + 2, s(Blocks.COBBLESTONE_SLAB));
        b.set(cx, by + 5, cz, s(Blocks.COBBLESTONE));
        b.set(cx, by + 4, cz, s(Blocks.LANTERN).with(LanternBlock.HANGING, true));
    }

    static void watchtower(Blueprint b, int x, int z, int by) {
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.fill(x + c[0], by + 1, z + c[1], x + c[0], by + 9, z + c[1], Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        b.fill(x - 2, by + 7, z - 2, x + 2, by + 7, z + 2, s(Blocks.SPRUCE_PLANKS));
        b.fn(x - 2, by + 8, z - 2, x + 2, by + 8, z + 2, (xx, yy, zz) -> (Math.abs(xx - x) == 2 || Math.abs(zz - z) == 2) && !(Math.abs(xx - x) == 2 && Math.abs(zz - z) == 2) ? s(Blocks.SPRUCE_FENCE) : null);
        b.fn(x - 3, by + 10, z - 3, x + 3, by + 10, z + 3, (xx, yy, zz) -> Math.abs(xx - x) + Math.abs(zz - z) <= 4 ? s(Blocks.DARK_OAK_SLAB) : null);
        b.fill(x - 1, by + 11, z - 1, x + 1, by + 11, z + 1, s(Blocks.DARK_OAK_SLAB));
        b.set(x, by + 11, z, s(Blocks.DARK_OAK_PLANKS));
        b.set(x, by + 9, z, s(Blocks.LANTERN).with(LanternBlock.HANGING, true));
        // 가운데 기둥 + 사다리 (북쪽 면)
        b.fill(x, by + 1, z, x, by + 6, z, Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        b.fill(x, by + 1, z - 1, x, by + 7, z - 1, s(Blocks.LADDER).with(net.minecraft.block.LadderBlock.FACING, Direction.NORTH));
    }

    static void farm(Blueprint b, int x0, int z0, int w, int d, int by, Random r) {
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        int mid = (z0 + z1) / 2;
        Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        Block crop = crops[r.nextInt(crops.length)];
        int age = 3 + r.nextInt(5);
        b.fn(x0, by, z0, x1, by + 1, z1, (x, y, z) -> {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (y == by) {
                if (edge) return Build.log(Blocks.OAK_LOG, (z == z0 || z == z1) ? Direction.Axis.X : Direction.Axis.Z);
                if (z == mid) return s(Blocks.WATER);
                return s(Blocks.FARMLAND).with(FarmlandBlock.MOISTURE, 7);
            }
            if (edge || z == mid) return null;
            int max = ((CropBlock) crop).getMaxAge();
            return ((CropBlock) crop).withAge(Math.min(max, age * max / 7));
        });
        b.set(x0 - 1, by + 1, z0, s(Blocks.COMPOSTER));
        b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 - 1, by + 1, z0 + 2), w2 -> CapitalPlan.villager(w2, VillagerProfession.FARMER)));
    }

    /** 사분면별 특별 건물: 0 여관 · 1 대장간 · 2 예배당 · 3 전직 교관 회관. */
    static void special(Blueprint b, int q, int x0, int z0, int w, int d, int by, Direction door, int cx, int cz, Random r) {
        switch (q) {
            case 0 -> {
                Build.house(b, x0, z0, w, d, by, 2, door, Build.SPRUCE, r);
                // 여관 간판 + 바깥 테이블
                int[] o = Build.doorOutside(x0, z0, w, d, door);
                b.set(o[0], by + 4, o[1] + 1, s(Blocks.LANTERN));
                b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + w / 2, by + 1, z0 + d / 2), w2 -> CapitalPlan.villager(w2, VillagerProfession.NONE)));
                // 모험가 길드 지부 (여관 안 접수원)
                b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + 2, by + 1, z0 + 2), w2 -> AWEntities.GUILD_CLERK.create(w2)));
                // 물약 상인 (여관 안 반대쪽 구석)
                b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + w - 3, by + 1, z0 + d - 3), w2 -> AWEntities.APOTHECARY.create(w2)));
            }
            case 1 -> {
                Build.house(b, x0, z0, w - 2, d, by, 1, door, Build.STONE, r);
                // 대장간 앞 작업장 (지붕 없는 쪽)
                // 옆 마당 (2칸): 모루 · 숫돌 · 용광로 · 용암 가마솥, 덮개
                int ox = door == Direction.WEST ? x0 + w - 2 : x0 + w - 2;
                b.set(ox, by + 1, z0 + 1, s(Blocks.ANVIL));
                b.set(ox + 1, by + 1, z0 + 1, s(Blocks.GRINDSTONE));
                b.set(ox, by + 1, z0 + d - 2, s(Blocks.BLAST_FURNACE));
                b.set(ox + 1, by + 1, z0 + d - 2, s(Blocks.LAVA_CAULDRON));
                b.set(ox + 1, by + 1, z0 + d / 2, s(Blocks.SMITHING_TABLE));
                b.fill(ox, by + 1, z0, ox + 1, by + 3, z0, Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                b.fill(ox + 1, by + 1, z0 + d - 1, ox + 1, by + 3, z0 + d - 1, Build.log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                b.fill(ox, by + 4, z0, ox + 1, by + 4, z0 + d - 1, s(Blocks.SPRUCE_SLAB));
                b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + 2, by + 1, z0 + d / 2), w2 -> CapitalPlan.villager(w2, VillagerProfession.WEAPONSMITH)));
                // 대장장이 (강화 · 승급)
                b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + 3, by + 1, z0 + d / 2), w2 -> AWEntities.BLACKSMITH.create(w2)));
            }
            case 2 -> {
                Build.Palette chapel = new Build.Palette(Blocks.WHITE_TERRACOTTA, Blocks.STRIPPED_BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS,
                        Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.BIRCH_DOOR, Blocks.STONE_BRICKS);
                Build.house(b, x0, z0, w, d, by, 2, door, chapel, r);
                // 종탑
                int tx = x0 + w - 2, tz = z0 + 1;
                b.fill(tx - 1, by + 1, tz - 1, tx + 1, by + 14, tz + 1, s(Blocks.STONE_BRICKS));
                b.fill(tx, by + 11, tz - 1, tx, by + 12, tz + 1, s(Blocks.AIR));
                b.fill(tx - 1, by + 11, tz, tx + 1, by + 12, tz, s(Blocks.AIR));
                b.set(tx, by + 12, tz, s(Blocks.BELL));
                b.fill(tx - 1, by + 15, tz - 1, tx + 1, by + 15, tz + 1, s(Blocks.STONE_BRICK_SLAB));
                b.set(tx, by + 15, tz, s(Blocks.STONE_BRICKS));
                b.set(tx, by + 16, tz, s(Blocks.LIGHTNING_ROD));
                b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + w / 2, by + 1, z0 + d / 2), w2 -> CapitalPlan.villager(w2, VillagerProfession.CLERIC)));
            }
            default -> {
                Build.Palette guild = new Build.Palette(Blocks.STONE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.PURPUR_STAIRS, Blocks.PURPUR_BLOCK,
                        Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.POLISHED_ANDESITE);
                Build.house(b, x0, z0, w, d, by, 1, door, guild, r);
                b.spawns.add(new Blueprint.Spawn(new BlockPos(x0 + w / 2, by + 1, z0 + d / 2), w2 -> AWEntities.JOB_MASTER.create(w2)));
                // 회관 앞 깃발 기둥
                int[] o = Build.doorOutside(x0, z0, w, d, door);
                int fx = o[0] + (door.getAxis() == Direction.Axis.Z ? 2 : 0), fz = o[1] + (door.getAxis() == Direction.Axis.X ? 2 : 0);
                b.fill(fx, by + 1, fz, fx, by + 5, fz, s(Blocks.DARK_OAK_FENCE));
                b.set(fx, by + 6, fz, s(Blocks.PURPLE_WOOL));
            }
        }
        pathFromDoor(b, x0, z0, q == 1 ? w - 2 : w, d, door, cx, cz, by);
    }

    private TownPlan() {
    }
}

package com.abilitywar.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 구조물 설계도: 블록을 직접 들고 있지 않고 "조각" (상자 채우기, 함수) 으로 들고 있다가
 * 청크가 생길 때마다 그 청크에 걸치는 조각만 그린다. 같은 설계도는 시드로 똑같이 다시 만들 수 있음.
 */
public final class Blueprint {
    /** 블록 하나 정하는 함수 (null = 건드리지 않음). */
    public interface BlockFn {
        BlockState at(int x, int y, int z);
    }

    public interface Prim {
        BlockBox box();

        void place(Placer p, BlockBox clip);
    }

    /** 실제로 놓는 쪽 (청크 범위로 잘림). */
    public static final class Placer {
        final StructureWorldAccess world;
        final BlockBox chunk;
        final BlockPos.Mutable m = new BlockPos.Mutable();

        Placer(StructureWorldAccess world, BlockBox chunk) {
            this.world = world;
            this.chunk = chunk;
        }

        public void set(int x, int y, int z, BlockState s) {
            if (s == null) return;
            m.set(x, y, z);
            if (!chunk.contains(m)) return;
            world.setBlockState(m, s, Block.NOTIFY_LISTENERS);
            Block b = s.getBlock();
            if (b instanceof FenceBlock || b instanceof WallBlock || b instanceof PaneBlock || b instanceof StairsBlock || b instanceof FenceGateBlock
                    || b instanceof LadderBlock || b == Blocks.IRON_BARS || b instanceof ChestBlock)
                world.getChunk(m).markBlockForPostProcessing(m);
        }

        public BlockState get(int x, int y, int z) {
            return world.getBlockState(m.set(x, y, z));
        }

        public int top(int x, int z) {
            return world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
        }
    }

    record Fill(BlockBox box, BlockState state) implements Prim {
        @Override
        public void place(Placer p, BlockBox clip) {
            for (int x = Math.max(box.getMinX(), clip.getMinX()); x <= Math.min(box.getMaxX(), clip.getMaxX()); x++)
                for (int z = Math.max(box.getMinZ(), clip.getMinZ()); z <= Math.min(box.getMaxZ(), clip.getMaxZ()); z++)
                    for (int y = Math.max(box.getMinY(), clip.getMinY()); y <= Math.min(box.getMaxY(), clip.getMaxY()); y++) p.set(x, y, z, state);
        }
    }

    record Fn(BlockBox box, BlockFn fn) implements Prim {
        @Override
        public void place(Placer p, BlockBox clip) {
            for (int x = Math.max(box.getMinX(), clip.getMinX()); x <= Math.min(box.getMaxX(), clip.getMaxX()); x++)
                for (int z = Math.max(box.getMinZ(), clip.getMinZ()); z <= Math.min(box.getMaxZ(), clip.getMaxZ()); z++)
                    for (int y = Math.max(box.getMinY(), clip.getMinY()); y <= Math.min(box.getMaxY(), clip.getMaxY()); y++) {
                        BlockState s = fn.at(x, y, z);
                        if (s != null) p.set(x, y, z, s);
                    }
        }
    }

    /** 경계까지 안쪽 거리 (0 미만 = 바깥). */
    public interface EdgeFn {
        double inside(int x, int z);
    }

    /**
     * 땅 고르기: 안쪽 (경계에서 blend 칸 이상) 은 바닥 높이 baseY 로 평평하게, 가장자리 blend 칸은 원래 땅으로 부드럽게 이어지는 비탈.
     * 위는 비우고 (안쪽은 clear 칸까지, 물도), 바닥은 ground, 그 밑은 땅까지 흙/돌로 채움 (뜬 곳 없게).
     */
    record Terraform(BlockBox box, int baseY, int clear, BlockState ground, BlockState sub, EdgeFn edge, int blend) implements Prim {
        @Override
        public void place(Placer p, BlockBox clip) {
            BlockState air = Blocks.AIR.getDefaultState();
            for (int x = Math.max(box.getMinX(), clip.getMinX()); x <= Math.min(box.getMaxX(), clip.getMaxX()); x++)
                for (int z = Math.max(box.getMinZ(), clip.getMinZ()); z <= Math.min(box.getMaxZ(), clip.getMaxZ()); z++) {
                    double e = edge.inside(x, z);
                    if (e < 0) continue;
                    int natural = p.top(x, z);
                    int target = baseY, top;
                    BlockState g = ground;
                    if (blend > 0 && e < blend) {
                        // 비탈: 원래 땅의 겉 블록 (풀·모래·눈 …) 을 그대로 씀
                        BlockState nat = p.get(x, natural, z);
                        if (nat.isOpaqueFullCube(p.world, p.m) && nat.getFluidState().isEmpty() && !nat.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
                                && !nat.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) g = nat;
                        double t = e / blend;
                        t = t * t * (3 - 2 * t);
                        target = (int) Math.round(natural + (baseY - natural) * t);
                        top = Math.max(natural, target) + 2;
                    } else top = Math.max(natural, baseY + clear);
                    for (int y = target + 1; y <= Math.min(top, clip.getMaxY()); y++) p.set(x, y, z, air);
                    if (g != null) p.set(x, target, z, g);
                    for (int y = target - 1; y >= target - 48 && y >= clip.getMinY(); y--) {
                        BlockState s = p.get(x, y, z);
                        if (y < target - 3 && s.isSolidBlock(p.world, p.m) && s.getFluidState().isEmpty()) break;
                        p.set(x, y, z, y >= target - 3 ? sub : Blocks.STONE.getDefaultState());
                    }
                }
        }
    }

    /** 생성할 때 놓을 것: 엔티티, 상자, 몹 생성기, 보스 제단. */
    public record Spawn(BlockPos pos, Function<ServerWorld, Entity> create) {
    }

    public record Chest(BlockPos pos, Direction facing, int tier) {
    }

    public record Spawner(BlockPos pos, EntityType<?> type) {
    }

    public record Altar(BlockPos pos, Identifier boss) {
    }

    final List<Prim> prims = new ArrayList<>();
    final Map<Long, List<Prim>> buckets = new HashMap<>();
    public final List<Spawn> spawns = new ArrayList<>();
    public final List<Chest> chests = new ArrayList<>();
    public final List<Spawner> spawners = new ArrayList<>();
    public final List<Altar> altars = new ArrayList<>();
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public void add(Prim p) {
        prims.add(p);
        BlockBox b = p.box();
        minX = Math.min(minX, b.getMinX());
        minY = Math.min(minY, b.getMinY());
        minZ = Math.min(minZ, b.getMinZ());
        maxX = Math.max(maxX, b.getMaxX());
        maxY = Math.max(maxY, b.getMaxY());
        maxZ = Math.max(maxZ, b.getMaxZ());
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        add(new Fill(BlockBox.create(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1)), s));
    }

    public void fn(int x0, int y0, int z0, int x1, int y1, int z1, BlockFn f) {
        add(new Fn(BlockBox.create(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1)), f));
    }

    public void set(int x, int y, int z, BlockState s) {
        fill(x, y, z, x, y, z, s);
    }

    /** 원: 반지름 r 안은 평평, 바깥 blend 칸은 비탈. */
    public void terraformCircle(int cx, int cz, int r, int blend, int baseY, int clear, BlockState ground, BlockState sub) {
        int R = r + blend;
        add(new Terraform(BlockBox.create(new BlockPos(cx - R, baseY - 48, cz - R), new BlockPos(cx + R, baseY + clear, cz + R)), baseY, clear, ground, sub,
                (x, z) -> R - Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz)), blend));
    }

    /** 사각형: 안은 평평, 바깥 blend 칸은 비탈. */
    public void terraformRect(int x0, int z0, int x1, int z1, int blend, int baseY, int clear, BlockState ground, BlockState sub) {
        int X0 = x0 - blend, Z0 = z0 - blend, X1 = x1 + blend, Z1 = z1 + blend;
        add(new Terraform(BlockBox.create(new BlockPos(X0, baseY - 48, Z0), new BlockPos(X1, baseY + clear, Z1)), baseY, clear, ground, sub,
                (x, z) -> Math.min(Math.min(x - X0, X1 - x), Math.min(z - Z0, Z1 - z)), blend));
    }

    public BlockBox bounds() {
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 다 만든 뒤: 청크별로 나눠 둠. */
    public Blueprint finish() {
        for (Prim p : prims) {
            BlockBox b = p.box();
            for (int cx = b.getMinX() >> 4; cx <= b.getMaxX() >> 4; cx++)
                for (int cz = b.getMinZ() >> 4; cz <= b.getMaxZ() >> 4; cz++)
                    buckets.computeIfAbsent(ChunkPos.toLong(cx, cz), k -> new ArrayList<>()).add(p);
        }
        return this;
    }

    /** 이 청크 (chunkBox) 에 걸치는 부분만 놓음. */
    public void place(StructureWorldAccess world, BlockBox chunkBox, Random random) {
        Placer p = new Placer(world, chunkBox);
        List<Prim> list = buckets.get(ChunkPos.toLong(chunkBox.getMinX() >> 4, chunkBox.getMinZ() >> 4));
        if (list != null) for (Prim pr : list) if (pr.box().intersects(chunkBox)) pr.place(p, chunkBox);
        ServerWorld sw = world.toServerWorld();
        for (Chest c : chests) {
            if (!chunkBox.contains(c.pos)) continue;
            p.set(c.pos.getX(), c.pos.getY(), c.pos.getZ(), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, c.facing));
            if (world.getBlockEntity(c.pos) instanceof ChestBlockEntity be) Loot.fill(be, c.tier, random);
        }
        for (Spawner s : spawners) {
            if (!chunkBox.contains(s.pos)) continue;
            p.set(s.pos.getX(), s.pos.getY(), s.pos.getZ(), Blocks.SPAWNER.getDefaultState());
            if (world.getBlockEntity(s.pos) instanceof MobSpawnerBlockEntity be) be.readNbt(spawnerNbt(s.type));
        }
        for (Altar a : altars) {
            if (!chunkBox.contains(a.pos)) continue;
            p.set(a.pos.getX(), a.pos.getY(), a.pos.getZ(), com.abilitywar.mob.BossAltar.BLOCK.getDefaultState());
            if (world.getBlockEntity(a.pos) instanceof com.abilitywar.mob.BossAltar.AltarEntity be) {
                be.readNbt(withBoss(a.boss));
                be.markDirty();
            }
        }
        for (Spawn s : spawns) {
            if (!chunkBox.contains(s.pos)) continue;
            Entity e = s.create.apply(sw);
            if (e == null) continue;
            e.refreshPositionAndAngles(s.pos.getX() + 0.5, s.pos.getY(), s.pos.getZ() + 0.5, e.getYaw(), 0);
            if (e instanceof net.minecraft.entity.mob.MobEntity mob) mob.setPersistent();
            world.spawnEntityAndPassengers(e);
        }
    }

    /** 생성기: 낮에도 (빛과 상관없이) 생성, 천천히 · 적게. */
    static net.minecraft.nbt.NbtCompound spawnerNbt(EntityType<?> type) {
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        net.minecraft.nbt.NbtCompound ent = new net.minecraft.nbt.NbtCompound();
        ent.putString("id", net.minecraft.registry.Registries.ENTITY_TYPE.getId(type).toString());
        var rules = new net.minecraft.world.MobSpawnerEntry.CustomSpawnRules(new net.minecraft.util.dynamic.Range<>(0, 15), new net.minecraft.util.dynamic.Range<>(0, 15));
        var entry = new net.minecraft.world.MobSpawnerEntry(ent, java.util.Optional.of(rules));
        n.put("SpawnData", net.minecraft.world.MobSpawnerEntry.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, entry).result().orElseThrow());
        n.putShort("Delay", (short) 40);
        n.putShort("MinSpawnDelay", (short) 300);
        n.putShort("MaxSpawnDelay", (short) 700);
        n.putShort("SpawnCount", (short) 2);
        n.putShort("MaxNearbyEntities", (short) 4);
        n.putShort("RequiredPlayerRange", (short) 14);
        n.putShort("SpawnRange", (short) 4);
        return n;
    }

    static net.minecraft.nbt.NbtCompound withBoss(Identifier boss) {
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        n.putString("Boss", boss.toString());
        return n;
    }

    /** 상자 보상. tier 0 마을 (재료·음식) · 1 던전 (장비 희귀~영웅) · 2 보스방 (전설~신화). */
    public static final class Loot {
        static void fill(ChestBlockEntity be, int tier, Random r) {
            List<ItemStack> items = new ArrayList<>();
            net.minecraft.item.Item[] basic = {net.minecraft.item.Items.BREAD, net.minecraft.item.Items.COOKED_BEEF, net.minecraft.item.Items.APPLE,
                    net.minecraft.item.Items.IRON_INGOT, net.minecraft.item.Items.GOLD_INGOT, net.minecraft.item.Items.TORCH, net.minecraft.item.Items.ARROW,
                    net.minecraft.item.Items.EMERALD, net.minecraft.item.Items.COAL};
            int n = 3 + r.nextInt(4);
            for (int i = 0; i < n; i++) items.add(new ItemStack(basic[r.nextInt(basic.length)], 1 + r.nextInt(tier == 0 ? 6 : 10)));
            int gear = tier == 0 ? (r.nextFloat() < 0.4f ? 1 : 0) : tier == 1 ? 1 + r.nextInt(2) : 2 + r.nextInt(2);
            for (int i = 0; i < gear; i++) {
                com.abilitywar.gear.GearGrade g = switch (tier) {
                    case 0 -> r.nextFloat() < 0.7f ? com.abilitywar.gear.GearGrade.COMMON : com.abilitywar.gear.GearGrade.GOOD;
                    case 1 -> r.nextFloat() < 0.6f ? com.abilitywar.gear.GearGrade.RARE : com.abilitywar.gear.GearGrade.HERO;
                    default -> r.nextFloat() < 0.65f ? com.abilitywar.gear.GearGrade.LEGENDARY : com.abilitywar.gear.GearGrade.MYTHIC;
                };
                var list = com.abilitywar.gear.GearItems.WEAPONS.get(g);
                if (list != null && !list.isEmpty()) items.add(new ItemStack(list.get(r.nextInt(list.size()))));
            }
            if (tier >= 1 && r.nextFloat() < 0.5f) items.add(new ItemStack(net.minecraft.item.Items.DIAMOND, 1 + r.nextInt(3)));
            if (tier == 2) items.add(new ItemStack(net.minecraft.item.Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
            for (ItemStack st : items) {
                for (int tries = 0; tries < 10; tries++) {
                    int slot = r.nextInt(be.size());
                    if (be.getStack(slot).isEmpty()) {
                        be.setStack(slot, st);
                        break;
                    }
                }
            }
        }
    }
}

package com.abilitywar.mob;

import com.abilitywar.AbilityWar;
import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 보스 제단: 던전 보스 방 가운데. 플레이어 (생존·모험) 가 16칸 안에 오면 보스가 나타나고,
 * 보스가 죽으면 15분 뒤에 다시 나옴. 부술 수 없음.
 */
public final class BossAltar {
    public static final Block BLOCK = Registry.register(Registries.BLOCK, AbilityWar.id("boss_altar"),
            new AltarBlock(Block.Settings.copy(Blocks.BEDROCK).luminance(s -> 10)));
    public static final Item ITEM = Registry.register(Registries.ITEM, AbilityWar.id("boss_altar"), new BlockItem(BLOCK, new Item.Settings()));
    public static final BlockEntityType<AltarEntity> TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE, AbilityWar.id("boss_altar"),
            FabricBlockEntityTypeBuilder.create(AltarEntity::new, BLOCK).build());
    static final int COOLDOWN = 20 * 60 * 15;

    public static void init() {
    }

    /** 제단에 보스 정하기 (구조물 생성 / 명령어). */
    public static void setBoss(World w, BlockPos pos, Identifier boss) {
        if (w.getBlockEntity(pos) instanceof AltarEntity a) {
            a.boss = boss.toString();
            a.markDirty();
        }
    }

    /**
     * 다른 모드 보스 (설치돼 있을 때만): 제단에 가끔 이 보스들 중 하나가 대신 나옴.
     * 설치된 대체 보스가 1 마리면 3번에 1번, 2 마리 이상이면 2번에 1번 (그중 하나를 고름).
     * Bosses of Mass Destruction: 리치 · 건틀릿 · 옵시딜리스. Soulslike Weaponry: 썩어가는 왕 · 귀환한 기사 · 옛 챔피언의 유해 · 혼돈의 군주 · 낮의 추적자 · 타락한 성상.
     * Archon: 타르 · 레벤 · 이니고 · 아일라.
     */
    static final java.util.Map<String, java.util.List<String>> ALT = java.util.Map.of(
            "abilitywar:fallen_king", java.util.List.of("bosses_of_mass_destruction:lich", "soulsweapons:accursed_lord_boss", "soulsweapons:returning_knight", "archon:tar"),
            "abilitywar:frost_witch", java.util.List.of("bosses_of_mass_destruction:lich", "soulsweapons:draugr_boss", "archon:leven"),
            "abilitywar:ember_colossus", java.util.List.of("bosses_of_mass_destruction:gauntlet", "soulsweapons:chaos_monarch", "soulsweapons:day_stalker", "archon:inigo"),
            "abilitywar:ancient_wyvern", java.util.List.of("bosses_of_mass_destruction:obsidilith", "soulsweapons:moonknight", "archon:ayla"));
    /** 제단이 부른 다른 모드 보스 → 제단 자리 (죽으면 제단 대기 시간). */
    static final java.util.Map<UUID, net.minecraft.util.math.GlobalPos> ALT_HOMES = new java.util.concurrent.ConcurrentHashMap<>();

    static Identifier pick(String boss, net.minecraft.util.math.random.Random random) {
        java.util.List<Identifier> have = new java.util.ArrayList<>();
        for (String alt : ALT.getOrDefault(boss, java.util.List.of())) {
            Identifier id = new Identifier(alt);
            if (Registries.ENTITY_TYPE.containsId(id)) have.add(id);
        }
        int n = Math.min(have.size(), 2);
        if (n > 0 && random.nextInt(n + 2) < n) return have.get(random.nextInt(have.size()));
        return new Identifier(boss);
    }

    /** 아무 보스나 죽음 (RpgManager): 제단이 부른 다른 모드 보스면 제단 대기 시간 시작. */
    public static void onAnyBossDeath(net.minecraft.entity.LivingEntity dead) {
        var home = ALT_HOMES.remove(dead.getUuid());
        if (home != null && dead.getWorld() instanceof ServerWorld sw && sw.getRegistryKey() == home.getDimension()) onBossDied(sw, home.getPos());
    }

    static void onBossDied(ServerWorld w, BlockPos home) {
        if (w.getBlockEntity(home) instanceof AltarEntity a) {
            a.cooldownUntil = w.getTime() + COOLDOWN;
            a.bossUuid = null;
            a.markDirty();
        }
    }

    public static final class AltarBlock extends BlockWithEntity {
        AltarBlock(Settings s) {
            super(s);
        }

        @Override
        public BlockRenderType getRenderType(BlockState state) {
            return BlockRenderType.MODEL;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return new AltarEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
            return world.isClient ? null : checkType(type, TYPE, (w, p, s, be) -> be.serverTick((ServerWorld) w, p));
        }
    }

    public static final class AltarEntity extends BlockEntity {
        String boss = "abilitywar:fallen_king";
        long cooldownUntil;
        @Nullable
        UUID bossUuid;

        public AltarEntity(BlockPos pos, BlockState state) {
            super(TYPE, pos, state);
        }

        void serverTick(ServerWorld w, BlockPos pos) {
            if (w.getTime() % 20 != 0) return;
            if (bossUuid != null) {
                Entity e = w.getEntity(bossUuid);
                if (e != null && e.isAlive()) return;
                // 보스가 사라짐 (청크 밖에서 저장됐거나 죽음): 죽음 처리가 안 됐으면 짧게 쉼
                if (e == null && cooldownUntil < w.getTime()) cooldownUntil = w.getTime() + 20 * 30;
                bossUuid = null;
                markDirty();
            }
            if (w.getTime() < cooldownUntil) return;
            ServerPlayerEntity near = null;
            for (ServerPlayerEntity p : w.getEntitiesByClass(ServerPlayerEntity.class, new Box(pos).expand(16), p -> !p.isSpectator() && !p.isCreative())) {
                near = p;
                break;
            }
            if (near == null) return;
            EntityType<?> type = Registries.ENTITY_TYPE.get(pick(boss, w.getRandom()));
            Entity e = type.create(w);
            if (e == null) return;
            Vec3d at = Vec3d.ofBottomCenter(pos.up(e instanceof AncientWyvernEntity ? 8 : 1));
            e.refreshPositionAndAngles(at.x, at.y, at.z, near.getYaw() + 180, 0);
            if (e instanceof MobEntity m) m.initialize(w, w.getLocalDifficulty(pos), SpawnReason.STRUCTURE, null, null);
            if (e instanceof BossEntity b) b.setHome(pos);
            else ALT_HOMES.put(e.getUuid(), net.minecraft.util.math.GlobalPos.create(w.getRegistryKey(), pos.toImmutable()));
            w.spawnEntity(e);
            NbtCompound n = new NbtCompound();
            n.putFloat("s", 2f);
            AWNet.fx(w, at, "paladin_pillar", n);
            bossUuid = e.getUuid();
            markDirty();
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putString("Boss", boss);
            nbt.putLong("Cooldown", cooldownUntil);
            if (bossUuid != null) nbt.putUuid("BossUuid", bossUuid);
        }

        @Override
        public void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            if (nbt.contains("Boss")) boss = nbt.getString("Boss");
            cooldownUntil = nbt.getLong("Cooldown");
            bossUuid = nbt.containsUuid("BossUuid") ? nbt.getUuid("BossUuid") : null;
        }
    }

    private BossAltar() {
    }
}

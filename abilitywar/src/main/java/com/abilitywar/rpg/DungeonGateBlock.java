package com.abilitywar.rpg;

import com.abilitywar.AbilityWar;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 던전 입구 (소용돌이 문): 야생의 던전 입구 · 테마 지역 가운데에 있음. 우클릭하거나 걸어 들어가면 그 테마의 인스턴스 던전 고르기 창.
 * THEME = InstanceDungeon.Theme 번호. 부서지지 않음.
 */
public class DungeonGateBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;
    public static final IntProperty THEME = IntProperty.of("theme", 0, InstanceDungeon.Theme.values().length - 1);
    static final VoxelShape X_SHAPE = Block.createCuboidShape(0, 0, 6, 16, 16, 10), Z_SHAPE = Block.createCuboidShape(6, 0, 0, 10, 16, 16);
    public static final Identifier S2C_OPEN_GATE = AbilityWar.id("open_gate");

    public static final Block BLOCK = Registry.register(Registries.BLOCK, AbilityWar.id("dungeon_gate"),
            new DungeonGateBlock(AbstractBlock.Settings.create().noCollision().strength(-1f, 3600000f).dropsNothing().nonOpaque()
                    .luminance(s -> 12).sounds(BlockSoundGroup.AMETHYST_BLOCK).pistonBehavior(PistonBehavior.BLOCK)));
    public static final Item ITEM = Registry.register(Registries.ITEM, AbilityWar.id("dungeon_gate"), new BlockItem(BLOCK, new Item.Settings()));

    static final Map<UUID, Long> NEXT_OPEN = new HashMap<>();

    public DungeonGateBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(AXIS, Direction.Axis.X).with(THEME, 0));
    }

    public static void init() {
        AbilityWar.LOG.info("[rpg] dungeon gate: {}", Registries.BLOCK.getId(BLOCK));
    }

    public static BlockState state(InstanceDungeon.Theme t, Direction.Axis axis) {
        return BLOCK.getDefaultState().with(THEME, t.ordinal()).with(AXIS, axis);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        b.add(AXIS, THEME);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return state.get(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return VoxelShapes.empty();
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp) open(sp, pos, state.get(THEME));
        return ActionResult.success(world.isClient);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!(entity instanceof ServerPlayerEntity sp) || sp.isSpectator()) return;
        long now = world.getTime();
        Long next = NEXT_OPEN.get(sp.getUuid());
        if (next != null && now < next) {
            NEXT_OPEN.put(sp.getUuid(), now + 60);
            return;
        }
        NEXT_OPEN.put(sp.getUuid(), now + 60);
        open(sp, pos, state.get(THEME));
    }

    /** 고르기 창 열기 (자리 · 테마). */
    public static void open(ServerPlayerEntity p, BlockPos pos, int theme) {
        if (InstanceDungeon.inDungeon(p)) return;
        RpgManager.sync(p);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(theme);
        if (ServerPlayNetworking.canSend(p, S2C_OPEN_GATE)) ServerPlayNetworking.send(p, S2C_OPEN_GATE, buf);
    }

    /** 이 자리 둘레 (6칸) 에 이 테마의 입구가 있는지 (서버 확인용). */
    public static boolean near(World w, BlockPos at, InstanceDungeon.Theme t) {
        for (BlockPos q : BlockPos.iterate(at.add(-6, -4, -6), at.add(6, 4, 6))) {
            BlockState s = w.getBlockState(q);
            if (s.isOf(BLOCK) && s.get(THEME) == t.ordinal()) return true;
        }
        return false;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        int c = InstanceDungeon.Theme.values()[state.get(THEME)].color;
        Vector3f col = new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
            world.addParticle(new DustParticleEffect(col, 1.2f), x, y, z, 0, 0.02, 0);
        }
        if (random.nextInt(3) == 0)
            world.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.03, 0);
    }
}

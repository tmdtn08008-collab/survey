package com.abilitywar.mob;

import com.abilitywar.item.RpgItems;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 인어 (세이렌): 바다의 밤. 물속을 빠르게 헤엄치고 삼지창을 던짐 (물에 빠진 자의 헤엄 · 삼지창 AI).
 * 세이렌의 노래: 가까운 (14칸) 플레이어를 끌어당기고 느려지게 함. 햇빛에 타지 않음, 아기 · 지원군 없음.
 */
public class MermaidEntity extends DrownedEntity implements TrophyMob {
    int songCd = 120;

    public MermaidEntity(EntityType<? extends DrownedEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return ZombieEntity.createZombieAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 34).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26).add(EntityAttributes.GENERIC_ARMOR, 2).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        if (data == null) data = new ZombieEntity.ZombieData(false, false);
        EntityData out = super.initialize(world, difficulty, reason, data, nbt);
        EntityAttributeInstance r = getAttributeInstance(EntityAttributes.ZOMBIE_SPAWN_REINFORCEMENTS);
        if (r != null) {
            r.clearModifiers();
            r.setBaseValue(0);
        }
        setBaby(false);
        return out;
    }

    @Override
    protected void initEquipment(Random random, LocalDifficulty difficulty) {
        if (random.nextFloat() < 0.7f) equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.06f);
    }

    @Override
    protected void initAttributes() {
        EntityAttributeInstance r = getAttributeInstance(EntityAttributes.ZOMBIE_SPAWN_REINFORCEMENTS);
        if (r != null) r.setBaseValue(0);
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    @Override
    protected boolean burnsInDaylight() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        LivingEntity t = getTarget();
        if (t instanceof PlayerEntity p && --songCd <= 0 && distanceTo(p) < 14 && (isTouchingWater() || p.isTouchingWater())) {
            songCd = 180 + random.nextInt(80);
            playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 2.5f, 1.2f);
            playSound(SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 2f, 0.7f);
            for (int i = 0; i < 12; i++)
                w.spawnParticles(ParticleTypes.NOTE, getX() + random.nextGaussian() * 0.6, getY() + 1.8, getZ() + random.nextGaussian() * 0.6, 0, random.nextFloat(), 0, 0, 1);
            Vec3d pull = getPos().subtract(p.getPos());
            if (pull.lengthSquared() > 1) {
                p.addVelocity(pull.normalize().multiply(0.6).add(0, 0.1, 0));
                p.velocityModified = true;
            }
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), this);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), this);
        }
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        int n = random.nextInt(3);
        if (n > 0) dropStack(new ItemStack(Items.PRISMARINE_SHARD, n));
        if (random.nextInt(3) == 0) dropStack(new ItemStack(Items.COD, 1 + random.nextInt(2)));
        if (random.nextInt(20) == 0) dropStack(new ItemStack(Items.HEART_OF_THE_SEA));
    }

    @Override
    public Item trophy() {
        return RpgItems.MERMAID_TEAR;
    }

    @Override
    public int trophyChance() {
        return 30;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isTouchingWater() ? SoundEvents.ENTITY_DOLPHIN_AMBIENT_WATER : SoundEvents.ENTITY_DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_DOLPHIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_DOLPHIN_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.ENTITY_DROWNED_STEP;
    }
}

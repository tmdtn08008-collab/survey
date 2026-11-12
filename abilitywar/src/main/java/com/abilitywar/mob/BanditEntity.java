package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.item.RpgItems;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/** 산적: 들판 · 숲 · 사바나에 무리 (2~4) 로 다님, 낮에도 나옴. 하나를 때리면 다 같이 덤빔. 가끔 몸을 날려 덮침. */
public class BanditEntity extends AWMonster implements TrophyMob {
    int pounceCd = 60;

    public BanditEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 8;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 26).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3).add(EntityAttributes.GENERIC_ARMOR, 3).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        targetSelector.add(0, new RevengeGoal(this).setGroupRevenge());
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(random.nextInt(3) == 0 ? Items.IRON_AXE : random.nextBoolean() ? Items.IRON_SWORD : Items.STONE_SWORD));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.04f);
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || --pounceCd > 0) return;
        double d = distanceTo(t);
        if (d > 3.5 && d < 9 && isOnGround() && Combat.hasLineOfSight(this, t)) {
            pounceCd = 80 + random.nextInt(40);
            Vec3d v = new Vec3d(t.getX() - getX(), 0, t.getZ() - getZ()).normalize().multiply(0.9);
            setVelocity(v.x, 0.42, v.z);
            velocityModified = true;
            playSound(SoundEvents.ENTITY_VINDICATOR_CELEBRATE, 1f, 1.2f);
        }
    }

    /** 낮에도 들판에 나올 수 있게 (밝아도 스폰). */
    @Override
    public float getPathfindingFavor(BlockPos pos, WorldView world) {
        return 0f;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.GOLD_NUGGET, 1, 4);
        drop(Items.LEATHER, 0, 1);
        if (random.nextInt(8) == 0) drop(Items.EMERALD, 1, 1);
    }

    @Override
    protected float gearChance() {
        return 0.05f;
    }

    @Override
    public Item trophy() {
        return RpgItems.BANDIT_SEAL;
    }

    @Override
    public int trophyChance() {
        return 35;
    }

    @Override
    public long goldBonus() {
        return 15 + random.nextInt(25);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VINDICATOR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VINDICATOR_DEATH;
    }
}

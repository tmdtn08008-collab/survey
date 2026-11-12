package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.item.RpgItems;
import com.abilitywar.net.AWNet;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/** 해적: 바닷가 (해변 · 돌 해안) 에 무리로. 해적 칼 + 화승총 (장전 소리 → 탕!). 금화를 지님. */
public class PirateEntity extends AWMonster implements TrophyMob {
    int shootCd = 60, aimAt = -1;
    int aimTarget = -1;

    public PirateEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 9;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28).add(EntityAttributes.GENERIC_ARMOR, 2).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        targetSelector.add(0, new RevengeGoal(this).setGroupRevenge());
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.04f);
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        LivingEntity t = getTarget();
        if (aimAt >= 0) {
            if (age >= aimAt) {
                aimAt = -1;
                if (w.getEntityById(aimTarget) instanceof LivingEntity le && le.isAlive() && Combat.hasLineOfSight(this, le) && distanceTo(le) < 20) {
                    Vec3d muzzle = getPos().add(0, 1.4, 0).add(getRotationVec(1f).multiply(0.8));
                    w.spawnParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.02);
                    w.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.02);
                    MobFx.bolt(w, this, le, 0xFFE6A0, 0.35f, 3);
                    playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.9f);
                    Combat.damage(le, getDamageSources().mobProjectile(this, this), 7, AWNet.DMG_SKILL);
                }
            }
            return;
        }
        if (t == null || !t.isAlive() || --shootCd > 0) return;
        double d = distanceTo(t);
        if (d > 4.5 && d < 16 && Combat.hasLineOfSight(this, t)) {
            shootCd = 90 + random.nextInt(50);
            aimAt = age + 14;
            aimTarget = t.getId();
            getNavigation().stop();
            playSound(SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE, 1.2f, 0.7f);
        }
    }

    @Override
    public float getPathfindingFavor(BlockPos pos, WorldView world) {
        return 0f;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.GOLD_NUGGET, 2, 5);
        if (random.nextInt(12) == 0) drop(Items.GOLD_INGOT, 1, 1);
        if (random.nextInt(6) == 0) drop(Items.GUNPOWDER, 1, 2);
    }

    @Override
    protected float gearChance() {
        return 0.05f;
    }

    @Override
    public Item trophy() {
        return RpgItems.PIRATE_DOUBLOON;
    }

    @Override
    public int trophyChance() {
        return 40;
    }

    @Override
    public long goldBonus() {
        return 25 + random.nextInt(35);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PILLAGER_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.85f;
    }
}

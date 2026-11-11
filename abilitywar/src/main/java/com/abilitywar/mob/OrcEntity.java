package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.net.AWNet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** 오크 전사: 크고 느리고 아픔. 세 번째 공격마다 땅을 내려쳐 주변을 밀어냄. */
public class OrcEntity extends AWMonster {
    int hits;

    public OrcEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 40).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.24).add(EntityAttributes.GENERIC_ARMOR, 6)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28);
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.IRON_AXE : Items.STONE_AXE));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05f);
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean ok = super.tryAttack(target);
        if (ok && ++hits % 3 == 0 && getWorld() instanceof ServerWorld sw) {
            NbtCompound n = new NbtCompound();
            n.putFloat("r", 2.8f);
            n.putInt("col", 0x9A7A50);
            AWNet.fx(sw, getPos(), "boss_slam", n);
            playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.6f);
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(getBlockPos()).expand(3), e -> e instanceof PlayerEntity && e != this)) {
                Vec3d d = e.getPos().subtract(getPos());
                Combat.setVelocity(e, new Vec3d(d.x, 0, d.z).normalize().multiply(0.9).add(0, 0.45, 0));
            }
        }
        return ok;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.LEATHER, 0, 2);
        drop(Items.IRON_NUGGET, 1, 4);
    }

    @Override
    protected float gearChance() {
        return 0.06f;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_HOGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_HOGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_HOGLIN_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.8f + random.nextFloat() * 0.1f;
    }
}

package com.abilitywar.mob;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** 해골 기사: 갑옷 입은 언데드 기사. 검과 방패, 앞에서 오는 공격을 가끔 막음. 햇빛에 타지 않음. */
public class SkeletonKnightEntity extends AWMonster {
    public SkeletonKnightEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26).add(EntityAttributes.GENERIC_ARMOR, 10).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28);
    }

    @Override
    public EntityGroup getGroup() {
        return EntityGroup.UNDEAD;
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05f);
        setEquipmentDropChance(EquipmentSlot.OFFHAND, 0.03f);
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // 앞에서 오는 공격은 25% 확률로 방패로 막음 (공격 중이 아닐 때)
        if (!getWorld().isClient && source.getAttacker() != null && handSwingTicks == 0 && random.nextFloat() < 0.25f && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_SHIELD)) {
            Vec3d to = source.getAttacker().getPos().subtract(getPos()).normalize();
            Vec3d look = getRotationVec(1f);
            if (to.x * look.x + to.z * look.z > 0.3) {
                playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1f, 0.9f + random.nextFloat() * 0.2f);
                return false;
            }
        }
        return super.damage(source, amount);
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.BONE, 1, 3);
        if (random.nextFloat() < 0.15f) drop(Items.IRON_INGOT, 1, 1);
    }

    @Override
    protected float gearChance() {
        return 0.05f;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_SKELETON_DEATH;
    }
}

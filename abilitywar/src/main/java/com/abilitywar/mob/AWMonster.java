package com.abilitywar.mob;

import com.abilitywar.gear.GearGrade;
import com.abilitywar.gear.GearItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** 이 모드의 일반 몬스터 공통: 근접 공격 AI + 가끔 RPG 장비 (일반·좋음) 를 떨어뜨림. */
public abstract class AWMonster extends HostileEntity {
    protected AWMonster(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, attackSpeed(), false));
        goalSelector.add(7, new WanderAroundFarGoal(this, 1.0));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(8, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, false));
    }

    protected double attackSpeed() {
        return 1.1;
    }

    /** RPG 장비를 떨어뜨릴 확률. */
    protected float gearChance() {
        return 0.03f;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        if (!causedByPlayer || random.nextFloat() >= gearChance()) return;
        GearGrade g = random.nextFloat() < 0.25f ? GearGrade.GOOD : GearGrade.COMMON;
        var list = GearItems.WEAPONS.get(g);
        if (list != null && !list.isEmpty()) dropStack(new ItemStack(list.get(random.nextInt(list.size()))));
    }

    protected void drop(Item item, int min, int max) {
        int n = min + random.nextInt(max - min + 1);
        if (n > 0) dropStack(new ItemStack(item, n));
    }
}

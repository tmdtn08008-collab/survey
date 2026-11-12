package com.abilitywar.mob;

import com.abilitywar.gear.GearGrade;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 정예 몹 (고블린 로드 · 오크 로드 · 강화 골렘): 보스처럼 공격 패턴과 체력바가 있지만 보스는 아님.
 * 자연 스폰 · 멀어지면 사라짐 · 가까이서 싸울 때만 체력바 · 처음 노릴 때 이름 카드 · 장비는 희귀/영웅 하나 (50%).
 */
public abstract class EliteEntity extends BossEntity implements TrophyMob {
    int swingCd;

    protected EliteEntity(EntityType<? extends HostileEntity> type, World world, BossBar.Color color) {
        super(type, world, color);
        this.experiencePoints = 40;
    }

    @Override
    protected boolean elite() {
        return true;
    }

    protected double reach() {
        return 2.2 + getWidth() * 0.6;
    }

    protected double chaseSpeed() {
        return 1.0;
    }

    /** 공격 패턴 사이: 쫓아가서 평타. */
    @Override
    protected void idleMove(LivingEntity target, double dist) {
        if (dist > reach() - 0.5) getNavigation().startMovingTo(target, chaseSpeed());
        else getNavigation().stop();
        if (--swingCd <= 0 && dist < reach() && canSee(target)) {
            swingCd = 20;
            swingHand(Hand.MAIN_HAND);
            tryAttack(target);
        }
    }

    @Override
    protected void gearLoot() {
        if (random.nextFloat() < 0.5f) dropGear(random.nextFloat() < 0.3f ? GearGrade.HERO : GearGrade.RARE);
    }

    @Override
    protected String introSubtitleKey() {
        return "elite.abilitywar." + Registries.ENTITY_TYPE.getId(getType()).getPath() + ".sub";
    }

    @Override
    public long goldBonus() {
        return (long) (getMaxHealth() * 0.8);
    }
}

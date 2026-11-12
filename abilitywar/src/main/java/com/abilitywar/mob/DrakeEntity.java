package com.abilitywar.mob;

import com.abilitywar.gear.GearGrade;
import com.abilitywar.item.RpgItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * 비룡 (드레이크): 산 위를 맴도는 작은 야생 용. 고대 비룡처럼 급강하 · 불 숨결 · 불덩이를 쓰지만 훨씬 약함.
 * 보스가 아님 (체력바 없음 · 멀어지면 사라짐). 용의 둥지 둘레에 많음. 모델은 녹옥빛 고룡을 작게 그림 (BossFx).
 */
public class DrakeEntity extends AncientWyvernEntity implements TrophyMob {
    public static final int JADE_FIRE = 0x9AF060;

    public DrakeEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.GREEN);
        this.experiencePoints = 20;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 60).add(EntityAttributes.GENERIC_ARMOR, 4)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
    }

    @Override
    protected boolean elite() {
        return true;
    }

    @Override
    protected boolean announces() {
        return false;
    }

    @Override
    protected boolean showsBossBar() {
        return false;
    }

    @Override
    protected int fireColor() {
        return JADE_FIRE;
    }

    @Override
    protected double orbitRadius() {
        return 9;
    }

    @Override
    protected double orbitHeight() {
        return 6;
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (count % 3 == 0) return BREATH;
        return random.nextInt(3) == 0 ? BARRAGE : SWOOP;
    }

    @Override
    protected int restAfter(int id) {
        return 70 + random.nextInt(30);
    }

    /** 고대 비룡 기술을 쓰되 피해는 40%. */
    @Override
    protected void hurt(LivingEntity t, float amount, boolean ult) {
        super.hurt(t, amount * 0.4f, ult);
    }

    @Override
    protected void gearLoot() {
        if (random.nextFloat() < 0.15f) dropGear(GearGrade.RARE);
    }

    @Override
    public float getPathfindingFavor(BlockPos pos, WorldView world) {
        return 0f;
    }

    @Override
    protected String introSubtitleKey() {
        return "boss.abilitywar.ancient_wyvern.sub";
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
    }

    @Override
    public Item trophy() {
        return RpgItems.DRAKE_SCALE;
    }

    @Override
    public int trophyChance() {
        return 60;
    }

    @Override
    public int trophyCount(net.minecraft.util.math.random.Random r) {
        return 1 + r.nextInt(2);
    }

    @Override
    public float getSoundPitch() {
        return 1.5f;
    }

    @Override
    protected float getSoundVolume() {
        return 1.2f;
    }
}

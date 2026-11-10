package com.abilitywar.rpg;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/** 왕: 특별 직업 (성기사) 퀘스트를 줌. 전직 교관과 같은 대화 화면을 씀. */
public class KingEntity extends JobMasterEntity {
    public KingEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    public String giver() {
        return JobQuests.KING;
    }

    @Override
    protected String nameKey() {
        return "entity.abilitywar.king";
    }
}

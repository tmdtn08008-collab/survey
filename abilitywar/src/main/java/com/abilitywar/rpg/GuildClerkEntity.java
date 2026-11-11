package com.abilitywar.rpg;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/** 모험가 길드 접수원: 길드 가입 · 등급별 퀘스트 · 승급 심사. */
public class GuildClerkEntity extends JobMasterEntity {
    public GuildClerkEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    public String giver() {
        return Guild.GIVER;
    }

    @Override
    protected String nameKey() {
        return "entity.abilitywar.guild_clerk";
    }
}

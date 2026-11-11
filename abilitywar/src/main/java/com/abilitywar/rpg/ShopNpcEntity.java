package com.abilitywar.rpg;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/** 상점 NPC: 물약 상인 (물약) · 대장장이 (강화 · 승급 · 재료). 퀘스트는 없음. */
public class ShopNpcEntity extends JobMasterEntity {
    public static final String APOTHECARY = "apothecary", BLACKSMITH = "blacksmith";
    final String kind;

    public ShopNpcEntity(EntityType<? extends PathAwareEntity> type, World world, String kind) {
        super(type, world);
        this.kind = kind;
        setCustomName(net.minecraft.text.Text.translatable(nameKey()));
    }

    public static ShopNpcEntity apothecary(EntityType<? extends PathAwareEntity> type, World world) {
        return new ShopNpcEntity(type, world, APOTHECARY);
    }

    public static ShopNpcEntity blacksmith(EntityType<? extends PathAwareEntity> type, World world) {
        return new ShopNpcEntity(type, world, BLACKSMITH);
    }

    public String kind() {
        return kind;
    }

    @Override
    public String giver() {
        return "shop:" + kind;
    }

    @Override
    protected String nameKey() {
        return kind == null ? "entity.abilitywar.apothecary" : "entity.abilitywar." + kind;
    }
}

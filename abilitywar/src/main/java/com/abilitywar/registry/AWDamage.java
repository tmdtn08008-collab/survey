package com.abilitywar.registry;

import com.abilitywar.AbilityWar;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.World;

public final class AWDamage {
    /** 평타 (막기로 80% 감소). */
    public static final RegistryKey<DamageType> PUNCH = key("ability_punch");
    /** 일반 스킬 (막기로 50% 감소). */
    public static final RegistryKey<DamageType> SKILL = key("ability_skill");
    /** 잡기/연타 계열 스킬 (막기 불가). */
    public static final RegistryKey<DamageType> GRAB = key("ability_grab");
    /** 궁극기 (막기/반격 불가, 방어 무시). */
    public static final RegistryKey<DamageType> ULTIMATE = key("ability_ultimate");

    public static final TagKey<DamageType> TAG_ULTIMATE = TagKey.of(RegistryKeys.DAMAGE_TYPE, AbilityWar.id("ultimate"));
    public static final TagKey<DamageType> TAG_UNBLOCKABLE = TagKey.of(RegistryKeys.DAMAGE_TYPE, AbilityWar.id("unblockable"));
    public static final TagKey<DamageType> TAG_ABILITY = TagKey.of(RegistryKeys.DAMAGE_TYPE, AbilityWar.id("ability"));
    public static final TagKey<DamageType> TAG_ABILITY_SKILL = TagKey.of(RegistryKeys.DAMAGE_TYPE, AbilityWar.id("ability_skill"));

    private static RegistryKey<DamageType> key(String path) {
        return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, AbilityWar.id(path));
    }

    public static DamageSource source(World world, RegistryKey<DamageType> type, Entity attacker) {
        return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(type), attacker, attacker);
    }

    private AWDamage() {
    }
}

package com.abilitywar.registry;

import com.abilitywar.AbilityWar;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class AWSounds {
    public static final SoundEvent VOID_WHOOSH = reg("void.whoosh");
    public static final SoundEvent VOID_GRAB = reg("void.grab");
    public static final SoundEvent VOID_PULL = reg("void.pull");
    public static final SoundEvent VOID_CRIT = reg("void.crit");
    public static final SoundEvent VOID_PUNCH = reg("void.punch");
    public static final SoundEvent VOID_PUNCH_HEAVY = reg("void.punch_heavy");
    public static final SoundEvent VOID_CHARGE = reg("void.charge");
    public static final SoundEvent VOID_BLAST = reg("void.blast");
    public static final SoundEvent VOID_IMPACT = reg("void.impact");
    public static final SoundEvent VOID_SLAM = reg("void.slam");
    public static final SoundEvent VOID_TELEPORT = reg("void.teleport");
    public static final SoundEvent VOID_COUNTER_STANCE = reg("void.counter_stance");
    public static final SoundEvent VOID_COUNTER_SUCCESS = reg("void.counter_success");
    public static final SoundEvent VOID_COUNTER_FAIL = reg("void.counter_fail");
    public static final SoundEvent VOID_ULT_CAST = reg("void.ult_cast");
    public static final SoundEvent VOID_ULT_RISE = reg("void.ult_rise");
    public static final SoundEvent VOID_BLACKHOLE = reg("void.blackhole");
    public static final SoundEvent VOID_COLLAPSE = reg("void.collapse");
    public static final SoundEvent VOID_SHADOW_ENTER = reg("void.shadow_enter");
    public static final SoundEvent VOID_SHADOW_EXIT = reg("void.shadow_exit");
    public static final SoundEvent VOID_BLOCK = reg("void.block");
    public static final SoundEvent VOID_SWING = reg("void.swing");
    public static final SoundEvent DASH = reg("dash");
    public static final SoundEvent DRAGON_CLAW = reg("dragon.claw");
    public static final SoundEvent DRAGON_CLAW_HEAVY = reg("dragon.claw_heavy");
    public static final SoundEvent DRAGON_DASH = reg("dragon.dash");
    public static final SoundEvent DRAGON_WING = reg("dragon.wing");
    public static final SoundEvent DRAGON_TAIL = reg("dragon.tail");
    public static final SoundEvent DRAGON_ROAR = reg("dragon.roar");
    public static final SoundEvent DRAGON_BREATH = reg("dragon.breath");
    public static final SoundEvent DRAGON_EXPLOSION = reg("dragon.explosion");
    public static final SoundEvent DRAGON_SCALES = reg("dragon.scales");
    public static final SoundEvent DRAGON_MARK = reg("dragon.mark");
    public static final SoundEvent DRAGON_TRANSFORM = reg("dragon.transform");
    public static final SoundEvent DRAGON_FIRE = reg("dragon.fire");
    public static final SoundEvent CONCUSSION_RING = reg("concussion.ring");
    public static final SoundEvent UI_READY = reg("ui.ready");
    public static final SoundEvent UI_DENY = reg("ui.deny");
    public static final SoundEvent UI_ULT_READY = reg("ui.ult_ready");
    public static final SoundEvent UI_CHARGE = reg("ui.charge");
    public static final SoundEvent BLADE_SLASH = reg("blade.slash");
    public static final SoundEvent BLADE_SLASH_HEAVY = reg("blade.slash_heavy");
    public static final SoundEvent BLADE_DRAW = reg("blade.draw");
    public static final SoundEvent BLADE_CHARGE = reg("blade.charge");
    public static final SoundEvent BLADE_CRESCENT = reg("blade.crescent");
    public static final SoundEvent BLADE_RIFT = reg("blade.rift");
    public static final SoundEvent BLADE_TELEPORT = reg("blade.teleport");
    public static final SoundEvent BLADE_PARRY = reg("blade.parry");
    public static final SoundEvent BLADE_EXPLODE = reg("blade.explode");
    public static final SoundEvent BLADE_GROW = reg("blade.grow");
    public static final SoundEvent BLADE_WAVE = reg("blade.wave");
    public static final SoundEvent BLADE_PHANTOM = reg("blade.phantom");
    public static final SoundEvent BLADE_MARK = reg("blade.mark");

    private static SoundEvent reg(String path) {
        Identifier id = AbilityWar.id(path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }

    private AWSounds() {
    }
}

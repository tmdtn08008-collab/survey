package com.abilitywar.client.fx;

import com.abilitywar.AbilityWar;
import net.minecraft.util.Identifier;

public final class Tex {
    public static final Identifier GLOW = fx("glow");
    public static final Identifier RING = fx("ring");
    public static final Identifier RING_SOFT = fx("ring_soft");
    public static final Identifier SHOCKWAVE = fx("shockwave");
    public static final Identifier SPARK = fx("spark");
    public static final Identifier FLARE = fx("flare");
    public static final Identifier SMOKE = fx("smoke");
    public static final Identifier STREAK = fx("streak");
    public static final Identifier IMPACT = fx("impact");
    public static final Identifier WISP = fx("wisp");
    public static final Identifier SHADOW_POOL = fx("shadow_pool");
    public static final Identifier TENDRIL = fx("tendril");
    public static final Identifier SWIRL = fx("swirl");
    public static final Identifier RIFT = fx("rift");
    public static final Identifier CRACK = fx("crack");
    public static final Identifier MAGIC_CIRCLE = fx("magic_circle");
    public static final Identifier MAGIC_CIRCLE2 = fx("magic_circle2");
    public static final Identifier RUNE_SHIELD = fx("rune_shield");
    public static final Identifier VIGNETTE = AbilityWar.id("textures/gui/vignette.png");

    public static Identifier fx(String name) {
        return AbilityWar.id("textures/fx/" + name + ".png");
    }

    public static Identifier icon(String ability, String slot) {
        return AbilityWar.id("textures/gui/" + ability + "_" + slot + ".png");
    }

    private Tex() {
    }
}

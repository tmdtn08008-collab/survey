package com.abilitywar.ability.dragon;

import com.abilitywar.ability.AbilityFx;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

final class AbilityFxCompat {
    static void crater(ServerWorld w, Vec3d pos, float r) {
        AbilityFx.crater(w, pos, r, DragonAbility.COLOR);
    }

    private AbilityFxCompat() {
    }
}

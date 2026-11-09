package com.abilitywar.ability;

import com.abilitywar.net.AWNet;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

/** 능력들이 같이 쓰는 서버측 연출 도우미. */
public final class AbilityFx {
    public static void sound(Entity at, SoundEvent s, float vol, float pitch) {
        at.getWorld().playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundCategory.PLAYERS, vol, pitch);
    }

    public static void sound(ServerWorld w, Vec3d at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, vol, pitch);
    }

    public static Vec3d chest(Entity e) {
        return e.getPos().add(0, e.getHeight() * 0.62, 0);
    }

    public static NbtCompound nbt() {
        return new NbtCompound();
    }

    public static void impact(ServerWorld w, Vec3d pos, String kind, float scale, int color) {
        NbtCompound n = nbt();
        n.putString("k", kind);
        n.putFloat("s", scale);
        n.putInt("col", color);
        AWNet.fx(w, pos, "impact", n);
    }

    public static void crater(ServerWorld w, Vec3d pos, float radius, int color) {
        NbtCompound n = nbt();
        n.putFloat("r", radius);
        n.putInt("col", color);
        AWNet.fx(w, pos, "crater", n);
    }

    private AbilityFx() {
    }
}

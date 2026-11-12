package com.abilitywar.mob;

import com.abilitywar.net.AWNet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** 몹 공격 연출 도우미 (클라이언트 VfxManager 의 basic_bolt · boss_slam · basic_ring). */
public final class MobFx {
    /** 시전자 → 대상 마력탄 (t 틱 동안 날아감). */
    public static void bolt(ServerWorld w, Entity from, LivingEntity to, int col, float size, int ticks) {
        NbtCompound n = new NbtCompound();
        n.putInt("c", from.getId());
        n.putInt("e", to.getId());
        Vec3d tp = to.getPos().add(0, to.getHeight() * 0.5, 0);
        n.putDouble("tx", tp.x);
        n.putDouble("ty", tp.y);
        n.putDouble("tz", tp.z);
        n.putInt("col", col);
        n.putFloat("s", size);
        n.putInt("t", ticks);
        AWNet.fx(w, from.getPos(), "basic_bolt", n);
    }

    public static void slam(ServerWorld w, Vec3d at, float r, int col) {
        NbtCompound n = new NbtCompound();
        n.putFloat("r", r);
        n.putInt("col", col);
        AWNet.fx(w, at, "boss_slam", n);
    }

    public static void ring(ServerWorld w, Entity c, float r, int col) {
        NbtCompound n = new NbtCompound();
        n.putInt("c", c.getId());
        n.putFloat("r", r);
        n.putInt("col", col);
        n.putInt("k", 0);
        AWNet.fx(w, c.getPos(), "basic_ring", n);
    }

    private MobFx() {
    }
}

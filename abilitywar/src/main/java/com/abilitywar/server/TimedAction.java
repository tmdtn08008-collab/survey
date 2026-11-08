package com.abilitywar.server;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

/** 여러 틱에 걸쳐 진행되는 서버측 스킬 동작. */
public abstract class TimedAction {
    protected final Entity owner;
    protected int t;
    private boolean finished;

    protected TimedAction(Entity owner) {
        this.owner = owner;
    }

    /** @return true 면 종료. */
    protected abstract boolean step(int t);

    /** 종료/취소 시 반드시 호출됨 (중력 복구 등). */
    protected void cleanup() {
    }

    public final boolean tick() {
        if (finished) return true;
        if (owner.isRemoved() || (owner instanceof net.minecraft.entity.LivingEntity le && !le.isAlive())) {
            finish();
            return true;
        }
        boolean done;
        try {
            done = step(t);
        } catch (Throwable th) {
            com.abilitywar.AbilityWar.LOG.error("Ability action failed", th);
            done = true;
        }
        t++;
        if (done) finish();
        return done;
    }

    public final void finish() {
        if (!finished) {
            finished = true;
            try {
                cleanup();
            } catch (Throwable th) {
                com.abilitywar.AbilityWar.LOG.error("Ability cleanup failed", th);
            }
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public Entity owner() {
        return owner;
    }

    protected ServerWorld world() {
        return (ServerWorld) owner.getWorld();
    }
}

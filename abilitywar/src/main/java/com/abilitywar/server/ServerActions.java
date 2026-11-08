package com.abilitywar.server;

import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ServerActions {
    private static final List<TimedAction> ACTIVE = new ArrayList<>();
    private static final List<TimedAction> PENDING = new ArrayList<>();
    private static boolean ticking;

    public static <T extends TimedAction> T start(T action) {
        if (ticking) PENDING.add(action);
        else if (!action.tick()) ACTIVE.add(action);
        return action;
    }

    public static void tick() {
        ticking = true;
        try {
            Iterator<TimedAction> it = ACTIVE.iterator();
            while (it.hasNext()) {
                if (it.next().tick()) it.remove();
            }
        } finally {
            ticking = false;
        }
        if (!PENDING.isEmpty()) {
            List<TimedAction> copy = new ArrayList<>(PENDING);
            PENDING.clear();
            for (TimedAction a : copy) {
                // 시작 틱에 바로 한 번 실행
                if (!a.tick()) ACTIVE.add(a);
            }
        }
    }

    public static void cancelFor(Entity owner) {
        for (TimedAction a : ACTIVE) if (a.owner() == owner) a.finish();
        for (TimedAction a : PENDING) if (a.owner() == owner) a.finish();
    }

    public static boolean hasAction(Entity owner, Class<? extends TimedAction> type) {
        for (TimedAction a : ACTIVE) if (a.owner() == owner && type.isInstance(a) && !a.isFinished()) return true;
        for (TimedAction a : PENDING) if (a.owner() == owner && type.isInstance(a) && !a.isFinished()) return true;
        return false;
    }

    public static void clear() {
        for (TimedAction a : ACTIVE) a.finish();
        for (TimedAction a : PENDING) a.finish();
        ACTIVE.clear();
        PENDING.clear();
    }

    private ServerActions() {
    }
}

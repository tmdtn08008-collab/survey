package com.abilitywar.ability.warlock;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 흑마법사의 흑마법서 (플레이어에 저장, 죽어도/재접속해도 유지).
 * <ul>
 *   <li>해골 장비 6칸: 머리, 몸통, 다리, 발, 오른손, 왼손</li>
 *   <li>영혼 목록: 죽여서 얻은 영혼(소환한 몹이 죽으면 사라짐) / 궁극기로 포획한 영혼(죽으면 30분 쿨타임)</li>
 * </ul>
 */
public final class Grimoire {
    public static final int GEAR_SLOTS = 6;
    public static final int MAX_SOULS = 24;
    public static final long CAPTURED_COOLDOWN = 20L * 60 * 30;

    public final SimpleInventory gear = new SimpleInventory(GEAR_SLOTS);
    public final List<Soul> souls = new ArrayList<>();
    public int selected = -1;
    public int nextId = 1;
    /** 지금 나와 있는 해골 (런타임 전용). */
    @Nullable
    public UUID skeleton;

    public static final class Soul {
        public int id;
        public String type;
        public NbtCompound data;
        public boolean captured;
        /** 월드 시간 기준: 이 시각까지 소환 불가 (포획 영혼이 죽었을 때). */
        public long cooldownUntil;
        /** 지금 소환되어 있는 엔티티 (런타임 전용). */
        @Nullable
        public UUID summoned;

        public boolean ready(long worldTime) {
            return summoned == null && worldTime >= cooldownUntil;
        }

        NbtCompound write() {
            NbtCompound n = new NbtCompound();
            n.putInt("Id", id);
            n.putString("Type", type);
            n.put("Data", data);
            n.putBoolean("Captured", captured);
            n.putLong("Cooldown", cooldownUntil);
            return n;
        }

        static Soul read(NbtCompound n) {
            Soul s = new Soul();
            s.id = n.getInt("Id");
            s.type = n.getString("Type");
            s.data = n.getCompound("Data");
            s.captured = n.getBoolean("Captured");
            s.cooldownUntil = n.getLong("Cooldown");
            return s;
        }
    }

    @Nullable
    public Soul soul(int id) {
        for (Soul s : souls) if (s.id == id) return s;
        return null;
    }

    public Soul add(String type, NbtCompound data, boolean captured) {
        Soul s = new Soul();
        s.id = nextId++;
        s.type = type;
        s.data = data;
        s.captured = captured;
        souls.add(s);
        return s;
    }

    public int summonedCount() {
        int n = 0;
        for (Soul s : souls) if (s.summoned != null) n++;
        return n;
    }

    public NbtCompound write() {
        NbtCompound n = new NbtCompound();
        NbtList g = new NbtList();
        for (int i = 0; i < GEAR_SLOTS; i++) {
            ItemStack st = gear.getStack(i);
            NbtCompound e = new NbtCompound();
            e.putInt("Slot", i);
            if (!st.isEmpty()) st.writeNbt(e);
            g.add(e);
        }
        n.put("Gear", g);
        NbtList l = new NbtList();
        for (Soul s : souls) l.add(s.write());
        n.put("Souls", l);
        n.putInt("Selected", selected);
        n.putInt("NextId", nextId);
        return n;
    }

    public void read(NbtCompound n) {
        NbtList g = n.getList("Gear", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < GEAR_SLOTS; i++) gear.setStack(i, ItemStack.EMPTY);
        for (int i = 0; i < g.size(); i++) {
            NbtCompound e = g.getCompound(i);
            int slot = e.getInt("Slot");
            if (slot >= 0 && slot < GEAR_SLOTS && e.contains("id")) gear.setStack(slot, ItemStack.fromNbt(e));
        }
        souls.clear();
        NbtList l = n.getList("Souls", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < l.size(); i++) souls.add(Soul.read(l.getCompound(i)));
        selected = n.getInt("Selected");
        nextId = Math.max(1, n.getInt("NextId"));
    }
}

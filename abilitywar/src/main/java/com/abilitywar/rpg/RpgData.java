package com.abilitywar.rpg;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashSet;
import java.util.Set;

/** 플레이어 RPG 정보: 레벨, 경험치, 스탯, 1차 직업, 진행 중인 전직 퀘스트. */
public class RpgData {
    public static final int STATS = 5; // 힘, 민첩, 지능, 체력, 지혜
    public int level = 1;
    public long xp;
    public int points;
    public final int[] stats = new int[STATS];
    /** 1차 직업 (warrior / mage / tank / healer). */
    public String baseClass = "";
    /** 진행 중인 퀘스트 id ("" = 없음). */
    public String quest = "";
    public final int[] progress = new int[8];
    public final Set<String> completed = new HashSet<>();
    public boolean dirty = true;

    public NbtCompound write() {
        NbtCompound t = new NbtCompound();
        t.putInt("Level", level);
        t.putLong("Xp", xp);
        t.putInt("Points", points);
        t.putIntArray("Stats", stats);
        t.putString("Base", baseClass);
        t.putString("Quest", quest);
        t.putIntArray("Progress", progress);
        NbtList l = new NbtList();
        for (String s : completed) l.add(NbtString.of(s));
        t.put("Completed", l);
        return t;
    }

    public void read(NbtCompound t) {
        level = Math.max(1, t.getInt("Level"));
        xp = t.getLong("Xp");
        points = t.getInt("Points");
        int[] s = t.getIntArray("Stats");
        for (int i = 0; i < STATS && i < s.length; i++) stats[i] = s[i];
        baseClass = t.getString("Base");
        quest = t.getString("Quest");
        int[] p = t.getIntArray("Progress");
        for (int i = 0; i < progress.length; i++) progress[i] = i < p.length ? p[i] : 0;
        completed.clear();
        for (NbtElement e : t.getList("Completed", NbtElement.STRING_TYPE)) completed.add(e.asString());
        dirty = true;
    }

    public void copyFrom(RpgData o) {
        read(o.write());
    }

    public int spent() {
        int n = 0;
        for (int v : stats) n += v;
        return n;
    }
}

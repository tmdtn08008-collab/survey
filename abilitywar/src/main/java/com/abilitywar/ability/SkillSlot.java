package com.abilitywar.ability;

/** 능력 슬롯. 순서는 네트워크 동기화에 쓰이므로 바꾸지 말 것. */
public enum SkillSlot {
    SKILL1("1"),
    SKILL2("2"),
    SKILL3("3"),
    SKILL4("4"),
    ULTIMATE("R"),
    PASSIVE("T");

    public static final SkillSlot[] VALUES = values();
    public final String keyLabel;

    SkillSlot(String keyLabel) {
        this.keyLabel = keyLabel;
    }

    public static SkillSlot byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : null;
    }
}

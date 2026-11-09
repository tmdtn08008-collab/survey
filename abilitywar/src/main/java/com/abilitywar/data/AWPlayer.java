package com.abilitywar.data;

/** PlayerEntity 에 mixin 으로 붙는 인터페이스. */
public interface AWPlayer {
    byte FLAG_BLOCKING = 1;
    byte FLAG_COUNTER = 2;
    byte FLAG_SHADOW = 4;
    byte FLAG_CINEMATIC = 8;
    byte FLAG_ULT_READY = 16;
    /** 용기사 용화 상태. */
    byte FLAG_TRANSFORMED = 32;
    /** 혈귀 박쥐 산개 (몸이 박쥐 떼로 흩어짐: 모델을 그리지 않음). */
    byte FLAG_SWARM = 64;

    String aw$getAbilityKey();

    void aw$setAbilityKey(String key);

    byte aw$getFlags();

    void aw$setFlags(byte flags);

    AbilityData aw$data();

    default boolean aw$hasFlag(byte flag) {
        return (aw$getFlags() & flag) != 0;
    }

    default void aw$setFlag(byte flag, boolean on) {
        byte f = aw$getFlags();
        byte n = on ? (byte) (f | flag) : (byte) (f & ~flag);
        if (n != f) aw$setFlags(n);
    }
}

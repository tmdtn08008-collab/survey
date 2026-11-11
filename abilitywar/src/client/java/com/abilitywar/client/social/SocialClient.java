package com.abilitywar.client.social;

import com.abilitywar.social.Social;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/** 플레이어끼리: 받은 요청 (카드), 파티원 (HUD), 서버로 보내기. */
public final class SocialClient {
    /** 받은 요청: 종류 (0 파티, 1 거래), 보낸 사람, 남은 틱. */
    public static final class Req {
        public final int type;
        public final String from;
        public int ticks;
        public final int total;

        Req(int type, String from, int ticks) {
            this.type = type;
            this.from = from;
            this.ticks = ticks;
            this.total = ticks;
        }
    }

    public record Member(String name, boolean online, float hp, float maxHp, int level, boolean near) {
    }

    public static final List<Req> requests = new ArrayList<>();
    public static final List<Member> party = new ArrayList<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Social.S2C_REQUEST, (client, handler, buf, sender) -> {
            int op = buf.readByte(), type = buf.readByte();
            String from = buf.readString();
            int sec = buf.readVarInt();
            client.execute(() -> {
                requests.removeIf(r -> r.type == type && r.from.equals(from));
                if (op == 0) requests.add(0, new Req(type, from, sec * 20));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Social.S2C_PARTY, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            List<Member> l = new ArrayList<>();
            for (int i = 0; i < n; i++)
                l.add(new Member(buf.readString(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
            client.execute(() -> {
                party.clear();
                party.addAll(l);
            });
        });
    }

    public static void send(int action, int target, long value) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeVarInt(target);
        buf.writeVarLong(value);
        ClientPlayNetworking.send(Social.C2S, buf);
    }

    /** Y / N: 가장 최근 요청에 답함. */
    public static void answer(boolean accept) {
        if (requests.isEmpty()) return;
        Req r = requests.remove(0);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.networkHandler.sendChatCommand("aw request " + (accept ? "accept " : "decline ") + (r.type == 0 ? "party " : "trade ") + r.from);
    }

    public static void tick() {
        requests.removeIf(r -> --r.ticks <= 0);
    }

    public static void reset() {
        requests.clear();
        party.clear();
    }

    private SocialClient() {
    }
}

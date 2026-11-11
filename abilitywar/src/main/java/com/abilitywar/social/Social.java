package com.abilitywar.social;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** 플레이어끼리: 파티 · 거래 · 요청 (패킷 · 틱). */
public final class Social {
    public static final Identifier C2S = com.abilitywar.AbilityWar.id("social");
    public static final Identifier S2C_REQUEST = com.abilitywar.AbilityWar.id("social_request");
    public static final Identifier S2C_PARTY = com.abilitywar.AbilityWar.id("social_party");

    public static void init() {
        Object type = TradeScreenHandler.TYPE; // 등록
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Requests.tick(s);
            Party.tick(s);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            Trade.Session t = Trade.ACTIVE.get(h.player.getUuid());
            if (t != null) Trade.cancel(t, h.player, "social.abilitywar.trade_cancelled");
        });
        // 0 거래 요청 (대상 엔티티), 1 파티 초대 (대상), 2 답하기 (종류 + 수락), 3 파티 나가기, 4 거래 G 올리기
        ServerPlayNetworking.registerGlobalReceiver(C2S, (server, player, handler, buf, sender) -> {
            int action = buf.readByte();
            int target = buf.readVarInt();
            long value = buf.readVarLong();
            server.execute(() -> {
                switch (action) {
                    case 0, 1 -> {
                        Entity e = player.getWorld().getEntityById(target);
                        if (!(e instanceof ServerPlayerEntity to)) return;
                        String err = Requests.send(player, to, action == 0 ? Requests.Type.TRADE : Requests.Type.PARTY);
                        if (err != null) player.sendMessage(Text.translatable(err).formatted(Formatting.RED), true);
                    }
                    case 2 -> Requests.respond(player, target < 0 ? null : Requests.Type.values()[Math.min(target, 1)], value != 0, null);
                    case 3 -> Party.leave(player);
                    case 4 -> Trade.setGold(player, value);
                    default -> {
                    }
                }
            });
        });
    }

    private Social() {
    }
}

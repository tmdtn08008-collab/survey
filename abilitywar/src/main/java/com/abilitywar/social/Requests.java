package com.abilitywar.social;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 플레이어끼리 요청 (거래 · 파티 초대): 받은 쪽 화면에 카드 (Y 수락 · N 거절) + 채팅의 [수락] [거절]. 30 초 뒤 사라짐. */
public final class Requests {
    public enum Type {PARTY, TRADE}

    record Req(Type type, UUID from, String fromName, long expire) {
    }

    static final Map<UUID, List<Req>> PENDING = new HashMap<>();
    public static final int SECONDS = 30;

    @Nullable
    public static String send(ServerPlayerEntity from, ServerPlayerEntity to, Type type) {
        if (from == to) return "social.abilitywar.err.self";
        if (from.getWorld() != to.getWorld() || from.squaredDistanceTo(to) > (type == Type.TRADE ? 12 * 12 : 64 * 64)) return "social.abilitywar.err.far";
        if (type == Type.PARTY) {
            if (Party.team(to) != null) return "social.abilitywar.err.target_in_party";
            var t = Party.team(from);
            if (t != null && t.getPlayerList().size() >= Party.MAX) return "social.abilitywar.err.party_full";
        } else if (Trade.trading(from) || Trade.trading(to)) return "social.abilitywar.err.busy";
        List<Req> l = PENDING.computeIfAbsent(to.getUuid(), k -> new ArrayList<>());
        for (Req r : l) if (r.type == type && r.from.equals(from.getUuid())) return "social.abilitywar.err.already_sent";
        long now = from.getServer().getTicks();
        l.add(new Req(type, from.getUuid(), from.getEntityName(), now + SECONDS * 20L));
        packet(to, 0, type, from.getEntityName(), SECONDS);
        String k = type == Type.PARTY ? "party" : "trade";
        MutableText msg = Text.translatable("social.abilitywar.req." + k, from.getDisplayName()).formatted(Formatting.YELLOW);
        msg.append(" ").append(button("social.abilitywar.btn.accept", Formatting.GREEN, "/aw request accept " + k + " " + from.getEntityName()));
        msg.append(" ").append(button("social.abilitywar.btn.decline", Formatting.RED, "/aw request decline " + k + " " + from.getEntityName()));
        to.sendMessage(msg, false);
        to.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.4f);
        from.sendMessage(Text.translatable("social.abilitywar.req_sent." + k, to.getDisplayName()).formatted(Formatting.GRAY), true);
        return null;
    }

    static Text button(String key, Formatting color, String cmd) {
        return Text.literal("[").append(Text.translatable(key)).append("]").styled(s -> s.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(cmd))));
    }

    /** 받은 요청에 답함 (fromName 이 없으면 그 종류의 가장 최근 요청). */
    public static void respond(ServerPlayerEntity to, @Nullable Type type, boolean accept, @Nullable String fromName) {
        List<Req> l = PENDING.get(to.getUuid());
        Req found = null;
        if (l != null)
            for (int i = l.size() - 1; i >= 0; i--) {
                Req r = l.get(i);
                if ((type == null || r.type == type) && (fromName == null || r.fromName.equalsIgnoreCase(fromName))) {
                    found = r;
                    break;
                }
            }
        if (found == null) {
            to.sendMessage(Text.translatable("social.abilitywar.err.no_request").formatted(Formatting.RED), true);
            return;
        }
        l.remove(found);
        packet(to, 1, found.type, found.fromName, 0);
        ServerPlayerEntity from = to.getServer().getPlayerManager().getPlayer(found.from);
        if (from == null) {
            to.sendMessage(Text.translatable("social.abilitywar.err.offline").formatted(Formatting.RED), true);
            return;
        }
        String k = found.type == Type.PARTY ? "party" : "trade";
        if (!accept) {
            from.sendMessage(Text.translatable("social.abilitywar.declined." + k, to.getDisplayName()).formatted(Formatting.GRAY), false);
            to.sendMessage(Text.translatable("social.abilitywar.you_declined").formatted(Formatting.GRAY), true);
            return;
        }
        String err = found.type == Type.PARTY ? Party.join(from, to) : Trade.start(from, to);
        if (err != null) {
            to.sendMessage(Text.translatable(err).formatted(Formatting.RED), false);
            from.sendMessage(Text.translatable(err).formatted(Formatting.RED), false);
        }
    }

    /** 카드 표시: op 0 = 추가, 1 = 지움. */
    static void packet(ServerPlayerEntity p, int op, Type type, String from, int seconds) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(op);
        buf.writeByte(type.ordinal());
        buf.writeString(from);
        buf.writeVarInt(seconds);
        if (ServerPlayNetworking.canSend(p, Social.S2C_REQUEST)) ServerPlayNetworking.send(p, Social.S2C_REQUEST, buf);
    }

    static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 0) return;
        long now = s.getTicks();
        PENDING.entrySet().removeIf(en -> {
            ServerPlayerEntity to = s.getPlayerManager().getPlayer(en.getKey());
            en.getValue().removeIf(r -> {
                if (r.expire > now && to != null) return false;
                if (to != null) packet(to, 1, r.type, r.fromName, 0);
                return true;
            });
            return en.getValue().isEmpty();
        });
    }

    private Requests() {
    }
}

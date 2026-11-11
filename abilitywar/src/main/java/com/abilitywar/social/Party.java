package com.abilitywar.social;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 파티 = 점수판 팀 (이름 awp_…): 같은 팀이라 서로 못 때리고, 능력 버프 · 치유 · 경험치 나누기가 그대로 적용됨.
 * 최대 5 명. 둘보다 적어지면 해산.
 */
public final class Party {
    public static final String PREFIX = "awp_";
    public static final int MAX = 5;

    @Nullable
    public static Team team(ServerPlayerEntity p) {
        AbstractTeam t = p.getScoreboardTeam();
        return t instanceof Team tm && tm.getName().startsWith(PREFIX) ? tm : null;
    }

    public static List<ServerPlayerEntity> online(MinecraftServer s, Team t) {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (String n : t.getPlayerList()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(n);
            if (p != null) l.add(p);
        }
        return l;
    }

    /** joiner 가 inviter 의 파티에 들어감 (inviter 가 파티가 없으면 새로 만듦). @return 오류 번역 키 */
    @Nullable
    public static String join(ServerPlayerEntity inviter, ServerPlayerEntity joiner) {
        if (team(joiner) != null) return "social.abilitywar.err.already_party";
        ServerScoreboard sb = inviter.getServer().getScoreboard();
        Team t = team(inviter);
        if (t == null) {
            String name;
            do name = PREFIX + Integer.toHexString(inviter.getRandom().nextInt(0xFFFFFF)); while (sb.getTeam(name) != null);
            t = sb.addTeam(name);
            t.setFriendlyFireAllowed(false);
            t.setShowFriendlyInvisibles(true);
            t.setColor(Formatting.AQUA);
            t.setDisplayName(Text.translatable("social.abilitywar.party"));
            sb.addPlayerToTeam(inviter.getEntityName(), t);
        }
        if (t.getPlayerList().size() >= MAX) return "social.abilitywar.err.party_full";
        sb.addPlayerToTeam(joiner.getEntityName(), t);
        for (ServerPlayerEntity m : online(inviter.getServer(), t))
            m.sendMessage(Text.translatable("social.abilitywar.party_joined", joiner.getDisplayName()).formatted(Formatting.AQUA), false);
        return null;
    }

    public static void leave(ServerPlayerEntity p) {
        Team t = team(p);
        if (t == null) {
            p.sendMessage(Text.translatable("social.abilitywar.err.no_party").formatted(Formatting.RED), true);
            return;
        }
        ServerScoreboard sb = p.getServer().getScoreboard();
        sb.removePlayerFromTeam(p.getEntityName(), t);
        p.sendMessage(Text.translatable("social.abilitywar.party_left_you").formatted(Formatting.GRAY), false);
        sendHud(p);
        List<ServerPlayerEntity> rest = online(p.getServer(), t);
        for (ServerPlayerEntity m : rest) m.sendMessage(Text.translatable("social.abilitywar.party_left", p.getDisplayName()).formatted(Formatting.GRAY), false);
        if (t.getPlayerList().size() < 2) {
            for (ServerPlayerEntity m : rest) m.sendMessage(Text.translatable("social.abilitywar.party_disbanded").formatted(Formatting.GRAY), false);
            sb.removeTeam(t);
            for (ServerPlayerEntity m : rest) sendHud(m);
        }
    }

    public static void list(ServerPlayerEntity p) {
        Team t = team(p);
        if (t == null) {
            p.sendMessage(Text.translatable("social.abilitywar.err.no_party").formatted(Formatting.RED), false);
            return;
        }
        p.sendMessage(Text.translatable("social.abilitywar.party_list", t.getPlayerList().size(), MAX, String.join(", ", t.getPlayerList())).formatted(Formatting.AQUA), false);
    }

    /** 파티 HUD: 나를 뺀 파티원 (이름 · HP · 최대 HP · 레벨 · 같은 세계인지 · 온라인인지). */
    public static void sendHud(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        Team t = team(p);
        List<String> names = t == null ? new ArrayList<>() : new ArrayList<>(t.getPlayerList());
        names.remove(p.getEntityName());
        buf.writeVarInt(names.size());
        for (String n : names) {
            ServerPlayerEntity m = p.getServer().getPlayerManager().getPlayer(n);
            buf.writeString(n);
            buf.writeBoolean(m != null);
            buf.writeFloat(m == null ? 0 : m.getHealth());
            buf.writeFloat(m == null ? 20 : m.getMaxHealth());
            buf.writeVarInt(m == null ? 0 : com.abilitywar.rpg.RpgManager.rpg(m).level);
            buf.writeBoolean(m != null && m.getWorld() == p.getWorld());
        }
        if (ServerPlayNetworking.canSend(p, Social.S2C_PARTY)) ServerPlayNetworking.send(p, Social.S2C_PARTY, buf);
    }

    static void tick(MinecraftServer s) {
        if (s.getTicks() % 10 != 3) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) if (team(p) != null) sendHud(p);
    }

    private Party() {
    }
}

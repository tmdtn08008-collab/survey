package com.abilitywar.client.net;

import com.abilitywar.client.ClientState;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.VfxManager;
import com.abilitywar.client.hud.DamageNumbers;
import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

public final class ClientNet {
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(AWNet.S2C_OPEN_SELECT, (client, handler, buf, sender) -> {
            boolean forced = buf.readBoolean();
            client.execute(() -> com.abilitywar.client.screen.AbilitySelectScreen.request(forced));
        });
        ClientPlayNetworking.registerGlobalReceiver(com.abilitywar.rpg.RpgManager.S2C_RPG, (client, handler, buf, sender) -> {
            net.minecraft.network.PacketByteBuf copy = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.copy(buf);
            client.execute(() -> {
                com.abilitywar.client.rpg.RpgClient.read(copy);
                copy.release();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(com.abilitywar.rpg.RpgManager.S2C_OPEN_NPC, (client, handler, buf, sender) -> {
            int npc = buf.readVarInt();
            client.execute(() -> {
                var e = client.world == null ? null : client.world.getEntityById(npc);
                if (e instanceof com.abilitywar.rpg.GuildClerkEntity) com.abilitywar.client.screen.GuildScreen.open(npc);
                else if (e instanceof com.abilitywar.rpg.ShopNpcEntity shop) {
                    if (com.abilitywar.rpg.ShopNpcEntity.BLACKSMITH.equals(shop.kind())) com.abilitywar.client.screen.EnhanceScreen.open(npc);
                    else if (com.abilitywar.rpg.ShopNpcEntity.TOWER_KEEPER.equals(shop.kind())) com.abilitywar.client.screen.TowerScreen.open(npc);
                    else if (com.abilitywar.rpg.ShopNpcEntity.DUNGEON_GUIDE.equals(shop.kind())) com.abilitywar.client.screen.DungeonScreen.open(npc);
                    else com.abilitywar.client.screen.ShopScreen.open(npc, shop.kind());
                } else com.abilitywar.client.screen.JobMasterScreen.open(npc);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(com.abilitywar.rpg.Shops.S2C_ENHANCE, (client, handler, buf, sender) -> {
            int code = buf.readByte(), level = buf.readVarInt(), slot = buf.readVarInt();
            client.execute(() -> com.abilitywar.client.screen.EnhanceScreen.onResult(code, level, slot));
        });
        ClientPlayNetworking.registerGlobalReceiver(AWNet.S2C_STATE, (client, handler, buf, sender) -> {
            net.minecraft.network.PacketByteBuf copy = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.copy(buf);
            client.execute(() -> {
                ClientState.read(copy);
                copy.release();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(AWNet.S2C_SOULS, (client, handler, buf, sender) -> {
            net.minecraft.network.PacketByteBuf copy = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.copy(buf);
            client.execute(() -> {
                com.abilitywar.client.screen.GrimoireScreen.read(copy);
                copy.release();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(AWNet.S2C_ANIM, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            String anim = buf.readString();
            float speed = buf.readFloat();
            client.execute(() -> PlayerAnimator.play(id, anim, speed));
        });
        ClientPlayNetworking.registerGlobalReceiver(AWNet.S2C_FX, (client, handler, buf, sender) -> {
            String type = buf.readString();
            NbtCompound n = buf.readNbt();
            client.execute(() -> VfxManager.spawn(type, n == null ? new NbtCompound() : n));
        });
        ClientPlayNetworking.registerGlobalReceiver(AWNet.S2C_DAMAGE, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            float amount = buf.readFloat();
            byte kind = buf.readByte();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            client.execute(() -> DamageNumbers.add(id, amount, kind, x, y, z));
        });
        ClientPlayNetworking.registerGlobalReceiver(AWNet.S2C_CINEMATIC, (client, handler, buf, sender) -> {
            int caster = buf.readVarInt();
            String type = buf.readString();
            int dur = buf.readVarInt();
            Vec3d target = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> CameraController.startCinematic(caster, type, dur, target));
        });
    }

    private ClientNet() {
    }
}

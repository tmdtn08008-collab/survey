package com.abilitywar.social;

import com.abilitywar.rpg.RpgManager;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 플레이어끼리 거래: 서로 물건 9 칸 + G 를 올리고 → [준비] → 둘 다 준비면 [거래 확정] → 둘 다 확정하면 한꺼번에 바뀜.
 * 올린 것이 바뀌면 준비 · 확정이 풀림 (속임수 방지). 창을 닫거나 멀어지면 취소 (올린 물건은 돌려줌).
 */
public final class Trade {
    static final Map<UUID, Session> ACTIVE = new HashMap<>();

    public static boolean trading(ServerPlayerEntity p) {
        return ACTIVE.containsKey(p.getUuid());
    }

    public static final class Session {
        final ServerPlayerEntity a, b;
        final SimpleInventory offerA = new SimpleInventory(9), offerB = new SimpleInventory(9);
        int goldA, goldB;
        boolean readyA, readyB, confirmA, confirmB, done;

        Session(ServerPlayerEntity a, ServerPlayerEntity b) {
            this.a = a;
            this.b = b;
            offerA.addListener(i -> changed());
            offerB.addListener(i -> changed());
        }

        void changed() {
            if (done) return;
            readyA = readyB = confirmA = confirmB = false;
        }

        int side(PlayerEntity p) {
            return p == a || p.getUuid().equals(a.getUuid()) ? 0 : 1;
        }

        ServerPlayerEntity other(PlayerEntity p) {
            return side(p) == 0 ? b : a;
        }

        public SimpleInventory mine(int side) {
            return side == 0 ? offerA : offerB;
        }

        public SimpleInventory theirs(int side) {
            return side == 0 ? offerB : offerA;
        }

        /**
         * 화면 숫자 (화면 숫자는 16 비트로 보내져서 G 는 15 비트씩 둘로 나눔):
         * 0 · 1 내 G (아래 · 위), 2 · 3 상대 G, 4 내 준비, 5 상대 준비, 6 내 확정, 7 상대 확정.
         */
        public int prop(int side, int i) {
            boolean me = side == 0;
            int my = me ? goldA : goldB, their = me ? goldB : goldA;
            return switch (i) {
                case 0 -> my & 0x7FFF;
                case 1 -> my >>> 15;
                case 2 -> their & 0x7FFF;
                case 3 -> their >>> 15;
                case 4 -> (me ? readyA : readyB) ? 1 : 0;
                case 5 -> (me ? readyB : readyA) ? 1 : 0;
                case 6 -> (me ? confirmA : confirmB) ? 1 : 0;
                default -> (me ? confirmB : confirmA) ? 1 : 0;
            };
        }

        public boolean valid(PlayerEntity p) {
            ServerPlayerEntity o = other(p);
            return !done && !o.isRemoved() && o.getWorld() == p.getWorld() && o.squaredDistanceTo(p) < 16 * 16;
        }
    }

    @Nullable
    public static String start(ServerPlayerEntity a, ServerPlayerEntity b) {
        if (trading(a) || trading(b)) return "social.abilitywar.err.busy";
        if (a.getWorld() != b.getWorld() || a.squaredDistanceTo(b) > 12 * 12) return "social.abilitywar.err.far";
        Session s = new Session(a, b);
        ACTIVE.put(a.getUuid(), s);
        ACTIVE.put(b.getUuid(), s);
        open(a, s, 0);
        open(b, s, 1);
        return null;
    }

    static void open(ServerPlayerEntity p, Session s, int side) {
        ServerPlayerEntity other = side == 0 ? s.b : s.a;
        p.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
                buf.writeString(other.getEntityName());
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("trade.abilitywar.title", other.getDisplayName());
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                return new TradeScreenHandler(syncId, inv, s, side);
            }
        });
    }

    /** 화면 버튼: 0 준비 (켜고 끔), 1 거래 확정, 2 취소. */
    static void button(Session s, ServerPlayerEntity p, int id) {
        if (s.done) return;
        int side = s.side(p);
        switch (id) {
            case 0 -> {
                if (side == 0) s.readyA = !s.readyA;
                else s.readyB = !s.readyB;
                s.confirmA = s.confirmB = false;
            }
            case 1 -> {
                if (!s.readyA || !s.readyB) return;
                if (side == 0) s.confirmA = true;
                else s.confirmB = true;
                if (s.confirmA && s.confirmB) complete(s);
            }
            default -> p.closeHandledScreen();
        }
    }

    public static void setGold(ServerPlayerEntity p, long amount) {
        Session s = ACTIVE.get(p.getUuid());
        if (s == null || s.done) return;
        int g = (int) Math.max(0, Math.min(amount, Math.min(999_999_999L, RpgManager.gold(p))));
        if (s.side(p) == 0) s.goldA = g;
        else s.goldB = g;
        s.changed();
    }

    static void complete(Session s) {
        if (RpgManager.gold(s.a) < s.goldA || RpgManager.gold(s.b) < s.goldB) {
            cancel(s, null, "social.abilitywar.err.trade_gold");
            return;
        }
        s.done = true;
        RpgManager.setGold(s.a, RpgManager.gold(s.a) - s.goldA + s.goldB);
        RpgManager.setGold(s.b, RpgManager.gold(s.b) - s.goldB + s.goldA);
        moveAll(s.offerA, s.b);
        moveAll(s.offerB, s.a);
        end(s);
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{s.a, s.b}) {
            p.sendMessage(Text.translatable("social.abilitywar.trade_done", s.other(p).getDisplayName()).formatted(Formatting.GREEN), false);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.7f, 1.2f);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.6f);
        }
    }

    static void moveAll(SimpleInventory inv, ServerPlayerEntity to) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.removeStack(i);
            if (st.isEmpty()) continue;
            if (!to.getInventory().insertStack(st)) to.dropItem(st, false);
        }
    }

    /** 취소: 올린 물건을 주인에게 돌려줌. by = 취소한 사람 (없으면 null). */
    static void cancel(Session s, @Nullable ServerPlayerEntity by, String reason) {
        if (s.done) return;
        s.done = true;
        moveAll(s.offerA, s.a);
        moveAll(s.offerB, s.b);
        end(s);
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{s.a, s.b})
            p.sendMessage(Text.translatable(reason, by == null ? Text.literal("-") : by.getDisplayName()).formatted(Formatting.GRAY), false);
    }

    static void end(Session s) {
        ACTIVE.remove(s.a.getUuid());
        ACTIVE.remove(s.b.getUuid());
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{s.a, s.b})
            if (p.currentScreenHandler instanceof TradeScreenHandler h && h.session == s) p.closeHandledScreen();
    }

    /** 거래 창이 닫힘 (닫기 · 멀어짐 · 접속 끊김). */
    static void closed(Session s, PlayerEntity p) {
        if (!s.done && p instanceof ServerPlayerEntity sp) cancel(s, sp, "social.abilitywar.trade_cancelled");
    }

    private Trade() {
    }
}

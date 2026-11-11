package com.abilitywar.social;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/** 거래 창: 내 물건 9 칸 (넣고 뺄 수 있음) · 상대 물건 9 칸 (보기만) · 내 인벤토리. */
public class TradeScreenHandler extends ScreenHandler {
    public static final ScreenHandlerType<TradeScreenHandler> TYPE = Registry.register(Registries.SCREEN_HANDLER, com.abilitywar.AbilityWar.id("trade"),
            new ExtendedScreenHandlerType<>(TradeScreenHandler::new));
    public static final int MINE_X = 14, THEIRS_X = 232, OFFER_Y = 30, INV_X = 69, INV_Y = 154;

    @Nullable
    final Trade.Session session;
    final int side;
    public final String partner;
    public final Inventory mine, theirs;
    public final PropertyDelegate props;

    /** 클라이언트. */
    public TradeScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        this(syncId, inv, null, 0, buf.readString(), new SimpleInventory(9), new SimpleInventory(9), new ArrayPropertyDelegate(8));
    }

    /** 서버. */
    TradeScreenHandler(int syncId, PlayerInventory inv, Trade.Session s, int side) {
        this(syncId, inv, s, side, (side == 0 ? s.b : s.a).getEntityName(), s.mine(side), s.theirs(side), new PropertyDelegate() {
            @Override
            public int get(int i) {
                return s.prop(side, i);
            }

            @Override
            public void set(int i, int v) {
            }

            @Override
            public int size() {
                return 8;
            }
        });
    }

    TradeScreenHandler(int syncId, PlayerInventory inv, @Nullable Trade.Session s, int side, String partner, Inventory mine, Inventory theirs, PropertyDelegate props) {
        super(TYPE, syncId);
        this.session = s;
        this.side = side;
        this.partner = partner;
        this.mine = mine;
        this.theirs = theirs;
        this.props = props;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) addSlot(new Slot(mine, r * 3 + c, MINE_X + c * 18, OFFER_Y + r * 18));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                addSlot(new Slot(theirs, r * 3 + c, THEIRS_X + c * 18, OFFER_Y + r * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean canTakeItems(PlayerEntity player) {
                        return false;
                    }
                });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
        addProperties(props);
    }

    public int prop(int i) {
        return props.get(i);
    }

    public long myGold() {
        return ((long) (prop(1) & 0x7FFF) << 15) | (prop(0) & 0x7FFF);
    }

    public long theirGold() {
        return ((long) (prop(3) & 0x7FFF) << 15) | (prop(2) & 0x7FFF);
    }

    public boolean myReady() {
        return prop(4) != 0;
    }

    public boolean theirReady() {
        return prop(5) != 0;
    }

    public boolean myConfirm() {
        return prop(6) != 0;
    }

    public boolean theirConfirm() {
        return prop(7) != 0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack() || (index >= 9 && index < 18)) return ItemStack.EMPTY;
        ItemStack st = slot.getStack(), copy = st.copy();
        if (index < 9) {
            if (!insertItem(st, 18, 54, true)) return ItemStack.EMPTY;
        } else if (!insertItem(st, 0, 9, false)) return ItemStack.EMPTY;
        if (st.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return copy;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return session == null || session.valid(player);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (session != null && player instanceof ServerPlayerEntity sp) Trade.button(session, sp, id);
        return true;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (session != null) Trade.closed(session, player);
    }
}

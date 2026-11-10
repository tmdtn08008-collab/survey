package com.abilitywar.ability.warlock;

import com.abilitywar.AbilityWar;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** 흑마법서 화면 (G): 해골 장비 6칸 + 내 인벤토리. 영혼 목록은 화면이 따로 그림 (S2C souls). */
public class GrimoireScreenHandler extends ScreenHandler {
    public static final ScreenHandlerType<GrimoireScreenHandler> TYPE = Registry.register(Registries.SCREEN_HANDLER, AbilityWar.id("grimoire"),
            new ScreenHandlerType<>(GrimoireScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final int GEAR_X = 8, GEAR_Y = 18;
    public static final int INV_X = 8, INV_Y = 110;
    static final Identifier[] EMPTY = {PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE, null, PlayerScreenHandler.EMPTY_OFFHAND_ARMOR_SLOT};

    private final Inventory gear;
    @Nullable
    private final InventoryChangedListener listener;

    public static void init() {
    }

    /** 클라이언트 쪽. */
    public GrimoireScreenHandler(int syncId, PlayerInventory inv) {
        this(syncId, inv, new SimpleInventory(Grimoire.GEAR_SLOTS), null);
    }

    /** 서버 쪽: 해골이 나와 있으면 바꾼 장비를 바로 입힘. */
    public GrimoireScreenHandler(int syncId, PlayerInventory inv, Inventory gear, @Nullable Runnable onChange) {
        super(TYPE, syncId);
        this.gear = gear;
        gear.onOpen(inv.player);
        if (onChange != null && gear instanceof SimpleInventory si) {
            listener = i -> onChange.run();
            si.addListener(listener);
        } else listener = null;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addSlot(new Slot(gear, i, GEAR_X, GEAR_Y + i * 18) {
                @Override
                public int getMaxItemCount() {
                    return 1;
                }

                @Override
                public boolean canInsert(ItemStack stack) {
                    // 머리 칸은 아무거나 (호박 등), 나머지는 맞는 갑옷만
                    if (idx == 0) return true;
                    return MobEntity.getPreferredEquipmentSlot(stack) == Necromancy.GEAR[idx];
                }

                @Override
                public Pair<Identifier, Identifier> getBackgroundSprite() {
                    return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, EMPTY[idx]);
                }
            });
        }
        addSlot(new Slot(gear, 4, GEAR_X + 72, GEAR_Y + 54));
        addSlot(new Slot(gear, 5, GEAR_X + 90, GEAR_Y + 54) {
            @Override
            public Pair<Identifier, Identifier> getBackgroundSprite() {
                return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, EMPTY[5]);
            }
        });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;
        ItemStack st = slot.getStack();
        ItemStack copy = st.copy();
        int gearEnd = Grimoire.GEAR_SLOTS;
        if (index < gearEnd) {
            if (!insertItem(st, gearEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            EquipmentSlot pref = MobEntity.getPreferredEquipmentSlot(st);
            int target = switch (pref) {
                case HEAD -> 0;
                case CHEST -> 1;
                case LEGS -> 2;
                case FEET -> 3;
                case OFFHAND -> 5;
                default -> 4;
            };
            if (!insertItem(st, target, target + 1, false) && !insertItem(st, 4, 6, false)) return ItemStack.EMPTY;
        }
        if (st.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return copy;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.isAlive();
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        gear.onClose(player);
        if (listener != null && gear instanceof SimpleInventory si) si.removeListener(listener);
    }
}

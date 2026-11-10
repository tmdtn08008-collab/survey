package com.abilitywar.client.gear;

import com.abilitywar.AbilityWar;
import com.abilitywar.gear.GearArmorItem;
import com.abilitywar.gear.GearGrade;
import com.abilitywar.gear.GearItem;
import com.abilitywar.gear.GearItems;
import com.abilitywar.gear.GearWeaponItem;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** RPG 장비 (클라이언트): 아이템 모델, 착용 갑옷, 이름 색, 칸 배경, 툴팁 테두리. */
public final class GearClient {
    /** 지금 그리는 툴팁의 장비 등급 (HandledScreenMixin 이 설정). */
    @Nullable
    public static GearGrade tooltipGrade;
    static BipedEntityModel<LivingEntity> inner, outer;

    public static void init() {
        GearItem.CLIENT_PLAYER[0] = () -> MinecraftClient.getInstance().player;
        GearItem.NAMER[0] = GearClient::fancyName;
        // 아이템 모델: 파일 2350개 대신 코드로 만듦 (무기 = 손에 든 모양, 방어구 = 평면)
        ModelLoadingPlugin.register(ctx -> ctx.resolveModel().register(rc -> {
            Identifier id = rc.id();
            if (!id.getNamespace().equals(AbilityWar.MOD_ID) || !id.getPath().startsWith("item/")) return null;
            String name = id.getPath().substring(5);
            Item item = GearItems.BY_ID.get(name);
            if (item == null) return null;
            String parent = item instanceof GearWeaponItem ? "minecraft:item/handheld" : "minecraft:item/generated";
            return JsonUnbakedModel.deserialize("{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"abilitywar:item/gear/" + name + "\"}}");
        }));
        List<Item> armor = new ArrayList<>();
        for (Item i : GearItems.BY_ID.values()) if (i instanceof GearArmorItem) armor.add(i);
        ArmorRenderer.register(GearClient::renderArmor, armor.toArray(new Item[0]));
    }

    // ------------------------------------------------------------------ 착용 갑옷
    static void renderArmor(net.minecraft.client.util.math.MatrixStack ms, net.minecraft.client.render.VertexConsumerProvider vcp, ItemStack stack,
                            LivingEntity entity, EquipmentSlot slot, int light, BipedEntityModel<LivingEntity> ctx) {
        if (!(stack.getItem() instanceof GearArmorItem item)) return;
        if (outer == null) {
            var loader = MinecraftClient.getInstance().getEntityModelLoader();
            inner = new BipedEntityModel<>(loader.getModelPart(EntityModelLayers.PLAYER_INNER_ARMOR));
            outer = new BipedEntityModel<>(loader.getModelPart(EntityModelLayers.PLAYER_OUTER_ARMOR));
        }
        boolean legs = slot == EquipmentSlot.LEGS;
        BipedEntityModel<LivingEntity> m = legs ? inner : outer;
        ctx.copyBipedStateTo(m);
        m.setVisible(false);
        switch (slot) {
            case HEAD -> {
                m.head.visible = true;
                m.hat.visible = true;
            }
            case CHEST -> {
                m.body.visible = true;
                m.rightArm.visible = true;
                m.leftArm.visible = true;
            }
            case LEGS -> {
                m.body.visible = true;
                m.rightLeg.visible = true;
                m.leftLeg.visible = true;
            }
            case FEET -> {
                m.rightLeg.visible = true;
                m.leftLeg.visible = true;
            }
            default -> {
            }
        }
        String base = "textures/models/armor/gear/" + item.set.id() + (legs ? "_2" : "_1");
        ArmorRenderer.renderPart(ms, vcp, light, stack, m, AbilityWar.id(base + ".png"));
        if (item.set.glow()) {
            float t = (Util.getMeasuringTimeMs() % 2400) / 2400f;
            float k = 0.75f + 0.25f * (float) Math.sin(t * Math.PI * 2);
            m.render(ms, vcp.getBuffer(RenderLayer.getEyes(AbilityWar.id(base + "e.png"))), light, OverlayTexture.DEFAULT_UV, k, k, k, 1f);
        }
    }

    // ------------------------------------------------------------------ 이름 (신화 이상: 글자마다 색이 흐름, ? 는 깨진 글자)
    static Text fancyName(GearGrade g, String key) {
        String s = Language.getInstance().get(key);
        long t = Util.getMeasuringTimeMs();
        MutableText out = Text.empty();
        int[] cps = s.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            Style st = Style.EMPTY.withColor(TextColor.fromRgb(g.colorAt(i, t)));
            if (g == GearGrade.UNKNOWN && cps[i] != ' ' && ((t / 110 + i * 5L) % 9 == 0)) st = st.withObfuscated(true);
            if (g == GearGrade.SECRET) st = st.withBold(false);
            out.append(Text.literal(new String(Character.toChars(cps[i]))).setStyle(st));
        }
        return out;
    }

    // ------------------------------------------------------------------ 칸 배경 (등급 색)
    public static void slotBackground(DrawContext ctx, int x, int y, ItemStack stack) {
        if (!(stack.getItem() instanceof GearItem gi)) return;
        GearGrade g = gi.grade();
        int col = g.colorAt(0, Util.getMeasuringTimeMs());
        int top = (0x18 << 24) | col, bot = ((g.tier() >= 4 ? 0x70 : 0x50) << 24) | col;
        ctx.fillGradient(x, y, x + 16, y + 16, top, bot);
        if (g.tier() >= GearGrade.LEGENDARY.tier()) {
            int edge = (0x90 << 24) | col;
            ctx.fill(x, y + 15, x + 16, y + 16, edge);
        }
    }

    // ------------------------------------------------------------------ 툴팁 테두리 (등급 색)
    public static void tooltipBorder(DrawContext ctx, int x, int y, int w, int h, int z) {
        GearGrade g = tooltipGrade;
        if (g == null) return;
        long t = Util.getMeasuringTimeMs();
        int c0 = g.colorAt(0, t), c1 = g.colorAt(6, t);
        int top = 0xFF000000 | c0, bot = 0x90000000 | c1;
        int x0 = x - 3, y0 = y - 3, x1 = x + w + 3, y1 = y + h + 3;
        ctx.fillGradient(x0, y0 + 1, x0 + 1, y1 - 1, z, top, bot);
        ctx.fillGradient(x1 - 1, y0 + 1, x1, y1 - 1, z, top, bot);
        ctx.fill(net.minecraft.client.render.RenderLayer.getGuiOverlay(), x0, y0, x1, y0 + 1, z, top);
        ctx.fill(net.minecraft.client.render.RenderLayer.getGuiOverlay(), x0, y1 - 1, x1, y1, z, bot);
        if (g.tier() >= GearGrade.LEGENDARY.tier()) {
            // 바깥쪽 빛 한 줄
            int glow = 0x50000000 | c0;
            ctx.fill(net.minecraft.client.render.RenderLayer.getGuiOverlay(), x0 - 1, y0 - 1, x1 + 1, y0, z, glow);
            ctx.fill(net.minecraft.client.render.RenderLayer.getGuiOverlay(), x0 - 1, y1, x1 + 1, y1 + 1, z, glow);
            ctx.fill(net.minecraft.client.render.RenderLayer.getGuiOverlay(), x0 - 1, y0, x0, y1, z, glow);
            ctx.fill(net.minecraft.client.render.RenderLayer.getGuiOverlay(), x1, y0, x1 + 1, y1, z, glow);
        }
    }

    private GearClient() {
    }
}

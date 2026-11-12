package com.abilitywar.client.render;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.JobMasterEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/** 전직 교관: 사람 모양 + 머리 위 퀘스트 표시 (! 받을 수 있음, ? 완료 가능). */
public class JobMasterRenderer extends MobEntityRenderer<JobMasterEntity, PlayerEntityModel<JobMasterEntity>> {
    static final Identifier SKIN = AbilityWar.id("textures/entity/job_master.png");
    static final Identifier KING_SKIN = AbilityWar.id("textures/entity/king.png");
    static final Identifier CLERK_SKIN = AbilityWar.id("textures/entity/guild_clerk.png");
    static final Identifier APOTHECARY_SKIN = AbilityWar.id("textures/entity/apothecary.png");
    static final Identifier BLACKSMITH_SKIN = AbilityWar.id("textures/entity/blacksmith.png");
    static final Identifier TOWER_KEEPER_SKIN = AbilityWar.id("textures/entity/tower_keeper.png");

    public static JobMasterRenderer apothecary(EntityRendererFactory.Context ctx) {
        return new JobMasterRenderer(ctx, APOTHECARY_SKIN, false);
    }

    public static JobMasterRenderer blacksmith(EntityRendererFactory.Context ctx) {
        return new JobMasterRenderer(ctx, BLACKSMITH_SKIN, false);
    }

    public static JobMasterRenderer towerKeeper(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx) {
        return new JobMasterRenderer(ctx, TOWER_KEEPER_SKIN, false);
    }
    final Identifier skin;

    public JobMasterRenderer(EntityRendererFactory.Context ctx) {
        this(ctx, SKIN, false);
    }

    JobMasterRenderer(EntityRendererFactory.Context ctx, Identifier skin, boolean crown) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.skin = skin;
        if (crown) addFeature(new Crown(this));
    }

    /** 왕: 붉은 왕의 옷 + 금관. */
    public static JobMasterRenderer clerk(EntityRendererFactory.Context ctx) {
        return new JobMasterRenderer(ctx, CLERK_SKIN, false);
    }

    public static JobMasterRenderer king(EntityRendererFactory.Context ctx) {
        return new JobMasterRenderer(ctx, KING_SKIN, true);
    }

    @Override
    public Identifier getTexture(JobMasterEntity e) {
        return skin;
    }

    /** 머리 위 금관 (뾰족한 끝 + 보석). */
    static final class Crown extends net.minecraft.client.render.entity.feature.FeatureRenderer<JobMasterEntity, PlayerEntityModel<JobMasterEntity>> {
        Crown(net.minecraft.client.render.entity.feature.FeatureRendererContext<JobMasterEntity, PlayerEntityModel<JobMasterEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, JobMasterEntity e, float limbAngle, float limbDistance, float td, float anim, float headYaw, float headPitch) {
            ms.push();
            getContextModel().head.rotate(ms);
            Matrix4f m = ms.peek().getPositionMatrix();
            net.minecraft.client.render.VertexConsumer vc = vcp.getBuffer(com.abilitywar.client.fx.AWRenderLayers.SOLID_COLOR);
            float y0 = -0.5f - 0.02f, h = 0.12f, r = 0.27f;
            // 둘레 띠 (8각)
            for (int i = 0; i < 8; i++) {
                double a0 = i * Math.PI / 4 + Math.PI / 8, a1 = (i + 1) * Math.PI / 4 + Math.PI / 8;
                float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
                float k = 0.75f + 0.25f * (float) Math.cos(a0 + 0.7);
                vc.vertex(m, x0, y0, z0).color(0.95f * k, 0.76f * k, 0.3f * k, 1).next();
                vc.vertex(m, x1, y0, z1).color(0.95f * k, 0.76f * k, 0.3f * k, 1).next();
                vc.vertex(m, x1, y0 - h, z1).color(1f * k, 0.86f * k, 0.42f * k, 1).next();
                vc.vertex(m, x0, y0 - h, z0).color(1f * k, 0.86f * k, 0.42f * k, 1).next();
                // 뾰족한 끝
                float xm = (x0 + x1) / 2, zm = (z0 + z1) / 2;
                vc.vertex(m, x0 * 0.98f, y0 - h, z0 * 0.98f).color(1f * k, 0.86f * k, 0.42f * k, 1).next();
                vc.vertex(m, x1 * 0.98f, y0 - h, z1 * 0.98f).color(1f * k, 0.86f * k, 0.42f * k, 1).next();
                vc.vertex(m, xm, y0 - h - 0.13f, zm).color(1f, 0.93f, 0.6f, 1).next();
                vc.vertex(m, xm, y0 - h - 0.13f, zm).color(1f, 0.93f, 0.6f, 1).next();
            }
            // 앞쪽 붉은 보석
            float gz = -r - 0.005f;
            vc.vertex(m, -0.05f, y0 - 0.02f, gz).color(0.85f, 0.08f, 0.12f, 1).next();
            vc.vertex(m, 0.05f, y0 - 0.02f, gz).color(0.85f, 0.08f, 0.12f, 1).next();
            vc.vertex(m, 0.05f, y0 - 0.1f, gz).color(1f, 0.3f, 0.3f, 1).next();
            vc.vertex(m, -0.05f, y0 - 0.1f, gz).color(1f, 0.3f, 0.3f, 1).next();
            ms.pop();
        }
    }

    @Override
    protected boolean hasLabel(JobMasterEntity e) {
        return net.minecraft.client.MinecraftClient.getInstance().currentScreen == null && super.hasLabel(e);
    }

    @Override
    public void render(JobMasterEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        super.render(e, yaw, td, ms, vcp, light);
        int mark = RpgClient.marker(e.giver());
        if (mark == 0 || net.minecraft.client.MinecraftClient.getInstance().currentScreen != null) return;
        float t = e.age + td;
        String s = mark == 1 ? "!" : "?";
        int col = mark == 2 ? 0xB8B8B8 : 0xFFD84A;
        ms.push();
        ms.translate(0, e.getHeight() + 0.95f + 0.08f * MathHelper.sin(t * 0.12f), 0);
        ms.multiply(dispatcher.getRotation());
        float sc = 0.05f * (1 + 0.06f * MathHelper.sin(t * 0.25f));
        ms.scale(-sc, -sc, sc);
        Matrix4f m = ms.peek().getPositionMatrix();
        TextRenderer tr = getTextRenderer();
        float x = -tr.getWidth(s) / 2f;
        tr.drawWithOutline(net.minecraft.text.Text.literal(s).asOrderedText(), x, 0, 0xFF000000 | col, 0xFF2A1A00, m, vcp, 0xF000F0);
        ms.pop();
    }
}

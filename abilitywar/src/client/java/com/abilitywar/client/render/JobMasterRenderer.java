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

    public JobMasterRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(JobMasterEntity e) {
        return SKIN;
    }

    @Override
    protected boolean hasLabel(JobMasterEntity e) {
        return net.minecraft.client.MinecraftClient.getInstance().currentScreen == null && super.hasLabel(e);
    }

    @Override
    public void render(JobMasterEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        super.render(e, yaw, td, ms, vcp, light);
        int mark = RpgClient.marker();
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

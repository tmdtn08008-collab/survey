package com.abilitywar.client.render.mob;

import com.abilitywar.AbilityWar;
import com.abilitywar.mob.SpiderQueenEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.SpiderEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** 거미 여왕: 거미 모델을 1.6배로, 검보라 몸에 붉은 모래시계 무늬. */
public class SpiderQueenRenderer extends SpiderEntityRenderer<SpiderQueenEntity> {
    static final Identifier TEX = AbilityWar.id("textures/entity/spider_queen.png");

    public SpiderQueenRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4f;
    }

    @Override
    protected void scale(SpiderQueenEntity e, MatrixStack m, float tickDelta) {
        m.scale(1.6f, 1.6f, 1.6f);
    }

    @Override
    public Identifier getTexture(SpiderQueenEntity e) {
        return TEX;
    }
}

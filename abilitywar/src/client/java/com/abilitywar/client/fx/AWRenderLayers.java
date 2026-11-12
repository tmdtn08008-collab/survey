package com.abilitywar.client.fx;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/** 이펙트용 렌더 레이어 (가산 혼합/반투명, 깊이 쓰기 없음). */
public abstract class AWRenderLayers extends RenderLayer {
    private AWRenderLayers(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    /**
     * 위치 → 텍스처 → 색 순서 (position_tex_color). 셰이더 모드 (Iris) 는 position_color_tex 프로그램을 이 순서의 프로그램으로 바꿔 끼워서,
     * 위치 → 색 → 텍스처 순서로 보내면 텍스처 좌표가 색으로 읽혀 빨강 · 초록 삼각형이 됨.
     */
    private static final RenderPhase.ShaderProgram POSITION_TEXTURE_COLOR_PROGRAM = new RenderPhase.ShaderProgram(GameRenderer::getPositionTexColorProgram);

    private static final Function<Identifier, RenderLayer> ADDITIVE = Util.memoize(tex -> RenderLayer.of("abilitywar_additive",
            VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS, 4096, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(POSITION_TEXTURE_COLOR_PROGRAM)
                    .texture(new RenderPhase.Texture(tex, false, false))
                    .transparency(RenderPhase.LIGHTNING_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)));

    private static final Function<Identifier, RenderLayer> ADDITIVE_NODEPTH = Util.memoize(tex -> RenderLayer.of("abilitywar_additive_nodepth",
            VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS, 4096, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(POSITION_TEXTURE_COLOR_PROGRAM)
                    .texture(new RenderPhase.Texture(tex, false, false))
                    .transparency(RenderPhase.LIGHTNING_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)));

    private static final Function<Identifier, RenderLayer> TRANSLUCENT = Util.memoize(tex -> RenderLayer.of("abilitywar_translucent",
            VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS, 4096, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(POSITION_TEXTURE_COLOR_PROGRAM)
                    .texture(new RenderPhase.Texture(tex, false, false))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false)));

    public static final RenderLayer ADDITIVE_COLOR = RenderLayer.of("abilitywar_additive_color",
            VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 2048, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.COLOR_PROGRAM)
                    .transparency(RenderPhase.LIGHTNING_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false));

    /** 불투명 단색 (깊이 쓰기). 블랙홀 중심 등. */
    public static final RenderLayer SOLID_COLOR = RenderLayer.of("abilitywar_solid_color",
            VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 2048, false, false,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.COLOR_PROGRAM)
                    .transparency(RenderPhase.NO_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderPhase.ALL_MASK)
                    .build(false));

    /** 반투명 단색 (어둠). */
    public static final RenderLayer DARK_COLOR = RenderLayer.of("abilitywar_dark_color",
            VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 2048, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.COLOR_PROGRAM)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderPhase.COLOR_MASK)
                    .build(false));

    public static RenderLayer additive(Identifier tex) {
        return ADDITIVE.apply(tex);
    }

    public static RenderLayer additiveNoDepth(Identifier tex) {
        return ADDITIVE_NODEPTH.apply(tex);
    }

    public static RenderLayer translucent(Identifier tex) {
        return TRANSLUCENT.apply(tex);
    }
}

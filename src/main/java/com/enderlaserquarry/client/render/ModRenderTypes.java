package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.api.distmarker.Dist;

import java.util.function.Function;

/**
 * Our own render types: entity cutout pushed towards the camera, for overlays lying right on block faces, and an
 * additive glow (vanilla eyes blends translucent now).
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID, value = Dist.CLIENT)
public final class ModRenderTypes {

    private static RenderPipeline offsetCutout(String name, float scale, float constant) {
        return RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(EnderLaserQuarry.rl("pipeline/" + name))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, scale, constant))
            .build();
    }

    private static final RenderPipeline CASING_PIPELINE = offsetCutout("casing", -1.0f, -10.0f);
    // twice the offset, to go on top of a casing layer
    private static final RenderPipeline CASING_OVERLAY_PIPELINE = offsetCutout("casing_overlay", -2.0f, -20.0f);

    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(EnderLaserQuarry.rl("pipeline/glow"))
        .withVertexShader("core/entity")
        .withFragmentShader("core/entity")
        .withShaderDefine("EMISSIVE")
        .withShaderDefine("NO_OVERLAY")
        .withShaderDefine("NO_CARDINAL_LIGHTING")
        .withSampler("Sampler0")
        .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
        .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
        .build();

    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CASING_PIPELINE);
        event.registerPipeline(CASING_OVERLAY_PIPELINE);
        event.registerPipeline(GLOW_PIPELINE);
    }

    private static Function<Identifier, RenderType> type(String name, RenderPipeline pipeline) {
        return Util.memoize(texture -> RenderType.create("ender_laser_quarry_" + name, RenderSetup.builder(pipeline)
            .withTexture("Sampler0", texture)
            .useLightmap()
            .useOverlay()
            .createRenderSetup()));
    }

    private static final Function<Identifier, RenderType> CASING = type("casing", CASING_PIPELINE);
    private static final Function<Identifier, RenderType> CASING_OVERLAY = type("casing_overlay", CASING_OVERLAY_PIPELINE);

    public static RenderType casing(Identifier texture) {
        return CASING.apply(texture);
    }

    // dish base light strips
    public static RenderType casingOverlay(Identifier texture) {
        return CASING_OVERLAY.apply(texture);
    }

    private static final Function<Identifier, RenderType> GLOW = Util.memoize(texture -> RenderType.create("ender_laser_quarry_glow",
        RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));

    // additive, no depth write
    public static RenderType glow(Identifier texture) {
        return GLOW.apply(texture);
    }

    private ModRenderTypes() {
    }
}

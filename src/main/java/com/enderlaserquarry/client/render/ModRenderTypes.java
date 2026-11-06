package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Our own render types.
 */
public final class ModRenderTypes {

    // entityCutout with polygon offset, for overlays lying right on block faces
    private static final Function<ResourceLocation, RenderType> CASING = Util.memoize(texture -> RenderType.create(
        "ender_laser_quarry_casing", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
            .createCompositeState(true)));

    public static RenderType casing(ResourceLocation texture) {
        return CASING.apply(texture);
    }

    // twice the offset, to go on top of a casing layer
    private static final RenderStateShard.LayeringStateShard CASING_OVERLAY_LAYERING = new RenderStateShard.LayeringStateShard(
        "ender_laser_quarry_casing_overlay_layering", () -> {
            RenderSystem.polygonOffset(-2.0f, -20.0f);
            RenderSystem.enablePolygonOffset();
        }, () -> {
            RenderSystem.polygonOffset(0.0f, 0.0f);
            RenderSystem.disablePolygonOffset();
        });

    private static final Function<ResourceLocation, RenderType> CASING_OVERLAY = Util.memoize(texture -> RenderType.create(
        "ender_laser_quarry_casing_overlay", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setLayeringState(CASING_OVERLAY_LAYERING)
            .createCompositeState(true)));

    // dish base light strips
    public static RenderType casingOverlay(ResourceLocation texture) {
        return CASING_OVERLAY.apply(texture);
    }

    private ModRenderTypes() {
    }
}

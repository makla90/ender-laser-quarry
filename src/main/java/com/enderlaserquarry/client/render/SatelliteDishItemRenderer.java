package com.enderlaserquarry.client.render;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.dish.SatelliteDishBlock;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 3D item for the satellite dishes, block with the dish on top.
 */
public class SatelliteDishItemRenderer {
    // block + dish are about 2.8 blocks tall
    private static final float FIT = 0.36f;
    private static final float PITCH = 35;

    private final SatelliteDishModel model;

    public SatelliteDishItemRenderer(EntityModelSet models) {
        model = new SatelliteDishModel(models.bakeLayer(SatelliteDishModel.LAYER));
    }

    public void render(SatelliteDishBlock block, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        QuarryTier tier = block.getTier();

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(FIT, FIT, FIT);
        poseStack.translate(-0.5, 0, -0.5);
        ModelQuads.renderBlock(block.defaultBlockState(), poseStack.last(), buffers, light, overlay);

        // dish facing north, tilted up
        poseStack.translate(0.5, 1.0, 0.5);
        float scale = SatelliteDishBlockEntity.MODEL_SCALE;
        poseStack.scale(scale, scale, scale);
        model.yoke.yRot = Mth.PI;
        model.bowl.xRot = -PITCH * Mth.DEG_TO_RAD;
        model.emitter.zRot = 0;
        model.root.render(poseStack, buffers.getBuffer(RenderTypes.entityCutout(SatelliteDishRenderer.texture(tier))), light,
            OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}

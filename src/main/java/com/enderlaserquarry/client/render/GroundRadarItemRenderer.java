package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.function.Supplier;

/**
 * 3D item for blocks with a radar antenna on top (ground radar, launch pad controller).
 */
public class GroundRadarItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final Supplier<? extends Block> block;
    private final float antennaScale;
    // scale so block + antenna fit in one block
    private final float fit;
    private static final float PITCH = 25;

    private GroundRadarModel model;

    public GroundRadarItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models, Supplier<? extends Block> block,
                                   float antennaScale, float fit) {
        super(dispatcher, models);
        this.block = block;
        this.antennaScale = antennaScale;
        this.fit = fit;
    }

    @Override
    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
        model = null;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (model == null) {
            model = new GroundRadarModel(minecraft.getEntityModels().bakeLayer(GroundRadarModel.LAYER));
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(fit, fit, fit);
        poseStack.translate(-0.5, 0, -0.5);
        minecraft.getBlockRenderer().renderSingleBlock(block.get().defaultBlockState(), poseStack, buffers,
            light, overlay, ModelData.EMPTY, null);

        // antenna facing north like the dish item
        poseStack.translate(0.5, 1.0, 0.5);
        poseStack.scale(antennaScale, antennaScale, antennaScale);
        model.turret.yRot = Mth.PI;
        model.antenna.xRot = -PITCH * Mth.DEG_TO_RAD;
        model.horn.visible = false;
        model.root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(GroundRadarRenderer.TEXTURE)), light,
            OverlayTexture.NO_OVERLAY);
        model.horn.visible = true;

        model.root.translateAndRotate(poseStack);
        model.turret.translateAndRotate(poseStack);
        model.antenna.translateAndRotate(poseStack);
        model.horn.render(poseStack, buffers.getBuffer(RenderType.entityTranslucentEmissive(GroundRadarRenderer.TEXTURE)),
            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}

package com.enderlaserquarry.client.render;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.dish.SatelliteDishBlock;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 3D item for the satellite dishes, block with the dish on top.
 */
public class SatelliteDishItemRenderer extends BlockEntityWithoutLevelRenderer {
    // block + dish are about 2.8 blocks tall
    private static final float FIT = 0.36f;
    private static final float PITCH = 35;

    private SatelliteDishModel model;

    public SatelliteDishItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
        model = null;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof SatelliteDishBlock block)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (model == null) {
            model = new SatelliteDishModel(minecraft.getEntityModels().bakeLayer(SatelliteDishModel.LAYER));
        }
        QuarryTier tier = block.getTier();

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(FIT, FIT, FIT);
        poseStack.translate(-0.5, 0, -0.5);
        minecraft.getBlockRenderer().renderSingleBlock(block.defaultBlockState(), poseStack, buffers, light, overlay,
            ModelData.EMPTY, null);

        // dish facing north, tilted up
        poseStack.translate(0.5, 1.0, 0.5);
        float scale = SatelliteDishBlockEntity.MODEL_SCALE;
        poseStack.scale(scale, scale, scale);
        model.yoke.yRot = Mth.PI;
        model.bowl.xRot = -PITCH * Mth.DEG_TO_RAD;
        model.emitter.zRot = 0;
        model.root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(SatelliteDishRenderer.texture(tier))), light,
            OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}

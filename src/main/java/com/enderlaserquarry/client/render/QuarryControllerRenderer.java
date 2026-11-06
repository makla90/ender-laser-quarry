package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Floor overlay plus the antenna on top. The antenna is drawn here and not in the block model, which would light it
 * with the solid block's own (zero) light.
 */
public class QuarryControllerRenderer implements BlockEntityRenderer<QuarryControllerBlockEntity> {
    private static final ModelResourceLocation BASIC = model("quarry_controller_mast");
    private static final ModelResourceLocation ENHANCED = model("enhanced_quarry_controller_mast");
    private static final ModelResourceLocation ENDER = model("ender_quarry_controller_mast");
    private static final ModelResourceLocation TIP = model("quarry_controller_mast_tip");
    public static final List<ModelResourceLocation> MODELS = List.of(BASIC, ENHANCED, ENDER, TIP);

    public QuarryControllerRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(EnderLaserQuarry.MOD_ID, "block/" + name));
    }

    @Override
    public void render(QuarryControllerBlockEntity controller, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (controller.getLevel() == null) {
            return;
        }
        QuarryFloorOverlay.render(controller.getLevel(), controller.getBlockPos(), QuarryFloorOverlay.Emblem.CONTROLLER, poseStack, buffers);

        BlockState state = controller.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        QuarryTier tier = controller.getTier();
        ModelResourceLocation mast = tier == QuarryTier.ENDER ? ENDER : tier == QuarryTier.ENHANCED ? ENHANCED : BASIC;
        int light = LevelRenderer.getLightColor(controller.getLevel(), controller.getBlockPos().above());
        // the LED is lit while a launch pad is linked
        int tipLight = controller.getPad() != null ? LightTexture.FULL_BRIGHT : light;

        poseStack.pushPose();
        // same turn as the blockstate's y rotation
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180));
        poseStack.translate(-0.5, 0, -0.5);
        renderModel(mast, state, poseStack, buffers, light);
        renderModel(TIP, state, poseStack, buffers, tipLight);
        poseStack.popPose();
    }

    private static void renderModel(ModelResourceLocation id, BlockState state, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(id);
        mc.getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), state, model,
            1, 1, 1, light, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public AABB getRenderBoundingBox(QuarryControllerBlockEntity controller) {
        return new AABB(controller.getBlockPos()).expandTowards(0, 1, 0);
    }
}

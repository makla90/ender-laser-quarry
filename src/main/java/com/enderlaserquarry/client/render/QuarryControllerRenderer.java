package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Floor overlay plus the antenna on top. The antenna is drawn here and not in the block model, which would light it
 * with the solid block's own (zero) light.
 */
public class QuarryControllerRenderer extends LiveRenderer<QuarryControllerBlockEntity> {
    private static final StandaloneModelKey<BlockStateModelPart> BASIC = model("quarry_controller_mast");
    private static final StandaloneModelKey<BlockStateModelPart> ENHANCED = model("enhanced_quarry_controller_mast");
    private static final StandaloneModelKey<BlockStateModelPart> ENDER = model("ender_quarry_controller_mast");
    private static final StandaloneModelKey<BlockStateModelPart> TIP = model("quarry_controller_mast_tip");
    // the Launch Pad Controller carries the same mast, in the pad's colours
    public static final StandaloneModelKey<BlockStateModelPart> PAD_MAST = model("launch_pad_controller_mast");
    public static final List<StandaloneModelKey<BlockStateModelPart>> MODELS = List.of(BASIC, ENHANCED, ENDER, TIP, PAD_MAST);

    public QuarryControllerRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static Identifier modelId(StandaloneModelKey<BlockStateModelPart> key) {
        return Identifier.fromNamespaceAndPath(EnderLaserQuarry.MOD_ID, "block/" + key.getName());
    }

    private static StandaloneModelKey<BlockStateModelPart> model(String name) {
        return new StandaloneModelKey<>(() -> name);
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
        StandaloneModelKey<BlockStateModelPart> mast = tier == QuarryTier.ENDER ? ENDER : tier == QuarryTier.ENHANCED ? ENHANCED : BASIC;
        int light = LevelRenderer.getLightCoords(controller.getLevel(), controller.getBlockPos().above());
        // the LED is lit while a launch pad is linked
        int tipLight = controller.getPad() != null ? LightCoordsUtil.FULL_BRIGHT : light;

        renderMast(mast, state, facing, poseStack, buffers, light, tipLight);
    }

    // mast + LED tip on top of a block, turned like its blockstate
    public static void renderMast(StandaloneModelKey<BlockStateModelPart> mast, BlockState state, Direction facing, PoseStack poseStack,
                                  MultiBufferSource buffers, int light, int tipLight) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180));
        poseStack.translate(-0.5, 0, -0.5);
        renderModel(mast, state, poseStack, buffers, light);
        renderModel(TIP, state, poseStack, buffers, tipLight);
        poseStack.popPose();
    }

    private static void renderModel(StandaloneModelKey<BlockStateModelPart> id, BlockState state, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        ModelQuads.render(mc.getModelManager().getStandaloneModel(id), poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), light,
            OverlayTexture.NO_OVERLAY);
    }

    @Override
    public AABB getRenderBoundingBox(QuarryControllerBlockEntity controller) {
        return new AABB(controller.getBlockPos()).expandTowards(0, 1, 0);
    }
}

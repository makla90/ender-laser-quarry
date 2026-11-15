package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.enderlaserquarry.content.port.StructurePortBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The 3D dish on top of the block and its beam to the crystal.
 */
public class SatelliteDishRenderer implements BlockEntityRenderer<SatelliteDishBlockEntity> {
    private static final int COLOR = 0xF4F8FF;
    private static final float BEAM_WIDTH = 0.05f;
    private static final ResourceLocation[] TEXTURES = {
        EnderLaserQuarry.rl("textures/entity/satellite_dish.png"),
        EnderLaserQuarry.rl("textures/entity/enhanced_satellite_dish.png"),
        EnderLaserQuarry.rl("textures/entity/ender_satellite_dish.png")};
    private static final ResourceLocation BASE_LIGHT_TEXTURE = EnderLaserQuarry.rl("textures/entity/satellite_dish_base_light.png");
    // off, then the capacitor item colours: basic, double layer, octadic, then Endergy's grainy, vivid, crystalline,
    // melodic, stellar, totemic (index from SatelliteDishBlockEntity.getCapacitorTier)
    private static final int[] TIER_COLORS = { 0xFF2A2C30, 0xFFD6DAE0, 0xFFE48F68, 0xFFD3E87D,
        0xFF979797, 0xFF47E0FF, 0xFF74C7C7, 0xFFF78AF7, 0xFFFFFFF4, 0xFFD8BC66 };
    private static final int WRONG_CAPACITOR = 0xFFE03030;

    private final SatelliteDishModel model;

    public SatelliteDishRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new SatelliteDishModel(context.bakeLayer(SatelliteDishModel.LAYER));
    }

    @Override
    public void render(SatelliteDishBlockEntity dish, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (dish.getLevel() == null) {
            return;
        }

        float time = dish.getLevel().getGameTime() + partialTick;
        renderDish(dish, partialTick, time, poseStack, buffers);

        float power = dish.getBeamPower(partialTick);
        Vec3 aim = power > 0 ? dish.getAimPoint(partialTick) : null;
        if (aim == null) {
            return;
        }
        // flickers while powering up
        if (power < 1 && flicker(time, dish.getBlockPos().hashCode()) > power * 1.4f) {
            return;
        }

        Vec3 from = dish.getFocus(partialTick);
        Vec3 to = aim.subtract(Vec3.atLowerCornerOf(dish.getBlockPos()));
        to = from.lerp(to, Math.min(1, power * 2));
        LaserBeamRenderer.render(poseStack, buffers, from, to, COLOR, BEAM_WIDTH * (0.3f + 0.7f * power), time);
    }

    private static float flicker(float time, int seed) {
        float t = time * 0.9f + (seed & 0xFF);
        return 0.5f + 0.5f * Mth.sin(t * 2.3f) * Mth.sin(t * 5.7f + 1.3f);
    }

    private void renderDish(SatelliteDishBlockEntity dish, float partialTick, float time, PoseStack poseStack,
                            MultiBufferSource buffers) {
        // block is opaque, use the light above
        int light = LevelRenderer.getLightColor(dish.getLevel(), dish.getBlockPos().above());
        float spool = dish.getSpool(partialTick);

        if (dish.isFormed()) {
            // light strips in the capacitor colour, drawn on the block faces (polygon offset), not over ports
            poseStack.pushPose();
            poseStack.translate(0.5, 0, 0.5);
            boolean[] ports = findPorts(dish);
            int tier = dish.isPowered() ? Mth.clamp(dish.getCapacitorTier(), 0, TIER_COLORS.length - 1) : 0;
            int color = TIER_COLORS[tier];
            boolean lit = tier > 0;
            if (dish.hasWrongCapacitor()) {
                // capacitor that doesn't work in this dish: blink red, powered or not
                lit = (time % 20) < 10;
                color = lit ? WRONG_CAPACITOR : TIER_COLORS[0];
            } else if (lit) {
                // dimmer while idle
                color =FastColor.ARGB32.lerp(0.3f * (1 - spool), color, 0xFF000000);
            }
            DishBaseModel.render(poseStack, buffers.getBuffer(ModRenderTypes.casingOverlay(BASE_LIGHT_TEXTURE)),
                lit ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, color, ports);
            poseStack.popPose();
        }

        model.yoke.yRot = dish.getYaw(partialTick) * Mth.DEG_TO_RAD;
        model.bowl.xRot = -dish.getPitch(partialTick) * Mth.DEG_TO_RAD;
        model.emitter.zRot = dish.getSpin(partialTick) * Mth.DEG_TO_RAD;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.0, 0.5);
        float scale = SatelliteDishBlockEntity.MODEL_SCALE;
        poseStack.scale(scale, scale, scale);

        ResourceLocation texture = texture(dish.getTier());
        model.emitter.visible = false;
        model.root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        model.emitter.visible = true;

        int emitterColor;
        if (spool > 0) {
            int grey = (int) Mth.lerp(spool, 150, 255);
            int alpha = (int) Mth.lerp(spool, 255, 200 + 55 * Mth.sin(time * 0.5f));
            emitterColor = FastColor.ARGB32.color(alpha, grey, grey, grey);
        } else {
            emitterColor = FastColor.ARGB32.color(255, dish.isFormed() ? 150 : 70, dish.isFormed() ? 150 : 70, dish.isFormed() ? 150 : 70);
        }
        model.root.translateAndRotate(poseStack);
        model.yoke.translateAndRotate(poseStack);
        model.bowl.translateAndRotate(poseStack);
        model.emitter.render(poseStack, buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)),
            spool > 0.3f ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, emitterColor);

        poseStack.popPose();
    }

    public static ResourceLocation texture(QuarryTier tier) {
        return TEXTURES[Math.min(tier.ordinal(), TEXTURES.length - 1)];
    }

    // index (dx + 1) + (dz + 1) * 3
    private static boolean[] findPorts(SatelliteDishBlockEntity dish) {
        boolean[] ports = new boolean[9];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ports[(dx + 1) + (dz + 1) * 3] = dish.getLevel().getBlockState(dish.getBlockPos().offset(dx, 0, dz)).getBlock()
                    instanceof StructurePortBlock;
            }
        }
        return ports;
    }

    @Override
    public boolean shouldRenderOffScreen(SatelliteDishBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(SatelliteDishBlockEntity dish) {
        // the 3x3 frame and the dish on it, plus the beam to the crystal: dishes out of view are skipped
        BlockPos pos = dish.getBlockPos();
        AABB box = new AABB(pos.getX() - 1.5, pos.getY(), pos.getZ() - 1.5, pos.getX() + 2.5, pos.getY() + 4, pos.getZ() + 2.5);
        BlockPos obelisk = dish.getObeliskPos();
        return obelisk == null ? box : box.minmax(new AABB(obelisk).inflate(1, 2, 1));
    }
}

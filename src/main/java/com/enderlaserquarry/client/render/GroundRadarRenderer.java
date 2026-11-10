package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.launchpad.UplinkStatus;
import com.enderlaserquarry.content.radar.GroundRadarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Radar antenna, the uplink to the satellite and the hop down to the launch pad controller.
 */
public class GroundRadarRenderer implements BlockEntityRenderer<GroundRadarBlockEntity> {
    static final ResourceLocation TEXTURE = EnderLaserQuarry.rl("textures/entity/ground_radar.png");

    private static final int CARRIER = 0x103848;
    private static final int UPLINK = 0x4CE0FF;
    private static final int DOWNLINK = 0x5CFF8A;
    private static final int PACKETS = 5;

    private final GroundRadarModel model;

    public GroundRadarRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new GroundRadarModel(context.bakeLayer(GroundRadarModel.LAYER));
    }

    @Override
    public void render(GroundRadarBlockEntity radar, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (radar.getLevel() == null) {
            return;
        }
        float time = radar.getLevel().getGameTime() + partialTick;

        int light = LaunchPadRenderer.lightAt(radar.getLevel(), radar.getBlockPos().above());

        model.turret.yRot = radar.getYaw(partialTick) * Mth.DEG_TO_RAD;
        model.antenna.xRot = -radar.getPitch(partialTick) * Mth.DEG_TO_RAD;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.0, 0.5);

        model.horn.visible = false;
        model.root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        model.horn.visible = true;

        model.root.translateAndRotate(poseStack);
        model.turret.translateAndRotate(poseStack);
        model.antenna.translateAndRotate(poseStack);
        boolean powered = radar.isPowered();
        model.horn.render(poseStack, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)),
            powered ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, powered ? 0xFFFFFFFF : 0xFF505050);
        poseStack.popPose();

        LaunchPadBlockEntity pad = radar.getSatellitePad();
        if (pad == null || pad.getUplink() != UplinkStatus.ONLINE) {
            return;
        }
        Vec3 satellite = pad.getSatellitePos(partialTick);
        if (satellite == null) {
            return;
        }

        Vec3 from = radar.getHornPos(partialTick);
        Vec3 to = Vec3.atLowerCornerOf(pad.getPadCenter().subtract(radar.getBlockPos())).add(satellite);
        // full beam only while scanning
        if (radar.isScanning()) {
            LaserBeamRenderer.render(poseStack, buffers, from, to, CARRIER, 0.02f, time);
        }

        // packets up and down
        for (int i = 0; i < PACKETS; i++) {
            float up = frac(time * 0.012f + i / (float) PACKETS);
            LaserBeamRenderer.render(poseStack, buffers, from.lerp(to, up), from.lerp(to, Math.min(1, up + 0.02f)), UPLINK, 0.06f, time);
            float down = 1 - frac(time * 0.009f + (i + 0.5f) / PACKETS);
            LaserBeamRenderer.render(poseStack, buffers, from.lerp(to, down), from.lerp(to, Math.min(1, down + 0.02f)), DOWNLINK, 0.06f, time);
        }

        // data on to the controller's mini radar
        Vec3 relay = pad.getMiniRadarHorn(partialTick).subtract(Vec3.atLowerCornerOf(radar.getBlockPos()));
        for (int i = 0; i < 2; i++) {
            float t = frac(time * 0.03f + i / 2f);
            LaserBeamRenderer.render(poseStack, buffers, from.lerp(relay, t), from.lerp(relay, Math.min(1, t + 0.08f)), DOWNLINK, 0.04f, time);
        }
    }

    private static float frac(float value) {
        return value - Mth.floor(value);
    }

    @Override
    public boolean shouldRenderOffScreen(GroundRadarBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(GroundRadarBlockEntity blockEntity) {
        return AABB.INFINITE;
    }
}

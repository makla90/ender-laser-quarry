package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.obelisk.CrystalGlow;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The floating crystal, its halo and the beam up to the satellite.
 */
public class CrystalObeliskRenderer extends LiveRenderer<CrystalObeliskBlockEntity> {
    private static final Identifier TEXTURE = EnderLaserQuarry.rl("textures/entity/laser_crystal.png");

    // nether star like tint
    private static final int[] SHIMMER = { 0xFFF6D0, 0xE2F6FF, 0xEEE2FF, 0xFFFFFF };
    private static final int[] VOID_SHIMMER = { 0x123A44, 0x1F7A86, 0x0E2A32, 0x46D2E0 };
    private static final int[] PULSATING_SHIMMER = { 0x9AFFDE, 0x6DF8DA, 0xCFFFF5, 0x5ADEC2 };
    private static final int[] HOT_SHIMMER = { 0xFF7A2E, 0xFFB347, 0xFF5A1F, 0xFFD27A };
    private static final int CHARGED_COLOR = 0xFFFFFF;
    private static final int PULSES = 6;

    private final CrystalModel model;

    public CrystalObeliskRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new CrystalModel(context.bakeLayer(CrystalModel.LAYER));
    }

    @Override
    public void render(CrystalObeliskBlockEntity obelisk, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (obelisk.getLevel() == null || !obelisk.hasCrystal()) {
            return;
        }

        float time = obelisk.getLevel().getGameTime() + partialTick;
        float charge = obelisk.getCharge(partialTick);
        float shake = obelisk.getShake(partialTick);
        float flash = obelisk.getFlash(partialTick);
        Vec3 center = CrystalObeliskBlockEntity.CRYSTAL_CENTER;
        float bob = Mth.sin(time * 0.08f) * 0.06f;

        // whiter while charging, flash when it fires
        int color = lerpColor(Math.min(1, charge * 0.5f + flash), shimmer(palette(obelisk), time * (0.02f + 0.04f * charge)), CHARGED_COLOR);
        // running hot: a flickering warm glow
        float heat = obelisk.getHeat(partialTick);
        if (heat > 0) {
            color = lerpColor(heat * (0.55f + 0.1f * Mth.sin(time * 0.7f)), color, shimmer(HOT_SHIMMER, time * 0.05f));
        }

        poseStack.pushPose();
        poseStack.translate(center.x, center.y + bob, center.z);
        if (shake > 0) {
            // shake before firing
            float amount = 0.035f * shake;
            poseStack.translate(amount * Mth.sin(time * 7.3f), amount * Mth.sin(time * 9.1f + 1), amount * Mth.sin(time * 8.2f + 2));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(obelisk.getSpin(partialTick)));
        poseStack.mulPose(Axis.ZP.rotation(0.08f * Mth.sin(time * 0.05f)));

        // opaque body, translucent let clouds and held items show through
        model.shards.yRot = -time * 0.05f * (1 + 3 * charge);
        model.root.render(poseStack, buffers.getBuffer(RenderTypes.entityCutout(TEXTURE)), LightCoordsUtil.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 0xFF000000 | color);

        // halo: additive without depth write, otherwise it hides the crystal
        float halo = 1.35f + 0.15f * charge + 0.4f * shake + 1.2f * flash + 0.05f * Mth.sin(time * 0.2f);
        poseStack.scale(halo, halo, halo);
        float strength = Math.min(1, (40 + 50 * charge + 140 * flash + 30 * heat) / 255f);
        model.gem.render(poseStack, buffers.getBuffer(ModRenderTypes.glow(TEXTURE)), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            ARGB.color(255, (int) (ARGB.red(color) * strength), (int) (ARGB.green(color) * strength),
                (int) (ARGB.blue(color) * strength)));
        poseStack.popPose();

        // beam up to the satellite
        float uplink = obelisk.getUplink(partialTick);
        if (uplink > 0) {
            Vec3 target = obelisk.getUplinkTarget(partialTick);
            if (target != null) {
                Vec3 from = center.add(0, bob + 0.55, 0);
                Vec3 to = from.lerp(target.subtract(Vec3.atLowerCornerOf(obelisk.getBlockPos())), uplink);
                CrystalGlow glow = beamGlow(obelisk);
                int pulse = TierBeamRenderer.pulse(glow);
                TierBeamRenderer.renderUplink(poseStack, buffers, from, to, obelisk.getBeamTier(), glow, 0.12f * (1 + 1.5f * flash), time);
                if (uplink < 1) {
                    LaserBeamRenderer.render(poseStack, buffers, from.lerp(to, 0.9), to, pulse, 0.3f, time);
                } else {
                    for (int i = 0; i < PULSES; i++) {
                        float up = frac(time * 0.015f + i / (float) PULSES);
                        LaserBeamRenderer.render(poseStack, buffers, from.lerp(to, up), from.lerp(to, Math.min(1, up + 0.03f)),
                            pulse, 0.2f, time);
                    }
                }
            }
        }
    }

    static int shimmer(int[] colors, float phase) {
        int i = Mth.floor(phase);
        float t = phase - i;
        t = t * t * (3 - 2 * t);
        return lerpColor(t, colors[Math.floorMod(i, colors.length)], colors[Math.floorMod(i + 1, colors.length)]);
    }

    private static float frac(float value) {
        return value - Mth.floor(value);
    }

    private static CrystalGlow beamGlow(CrystalObeliskBlockEntity obelisk) {
        if (obelisk.hasVoidCrystal()) {
            return CrystalGlow.VOID;
        }
        BlockState state = obelisk.getBlockState();
        return state.hasProperty(CrystalGlow.PROPERTY) && state.getValue(CrystalGlow.PROPERTY) == CrystalGlow.PULSATING ? CrystalGlow.PULSATING : CrystalGlow.LASER;
    }

    private static int[] palette(CrystalObeliskBlockEntity obelisk) {
        if (obelisk.hasVoidCrystal()) {
            return VOID_SHIMMER;
        }
        BlockState state = obelisk.getBlockState();
        return state.hasProperty(CrystalGlow.PROPERTY) && state.getValue(CrystalGlow.PROPERTY) == CrystalGlow.PULSATING ? PULSATING_SHIMMER : SHIMMER;
    }

    private static int lerpColor(float t, int a, int b) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(CrystalObeliskBlockEntity blockEntity) {
        return AABB.INFINITE;
    }
}

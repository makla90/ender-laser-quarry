package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.obelisk.CrystalGlow;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import com.enderlaserquarry.content.obelisk.QuarryPlatingBlock;
import com.enderlaserquarry.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Draws the plating top with an emblem over the controller and radar when they sit in a quarry floor, so the floor
 * looks connected. Tier and glow come from the plating next to them.
 */
public final class QuarryFloorOverlay {
    public enum Emblem {
        CONTROLLER("quarry_floor_controller"),
        RADAR("quarry_floor_radar");

        private final ResourceLocation[][] sheets = new ResourceLocation[QuarryTier.values().length][CrystalGlow.values().length];

        Emblem(String name) {
            for (QuarryTier tier : QuarryTier.values()) {
                String prefix = tier == QuarryTier.BASIC ? "" : tier.id() + "_";
                for (CrystalGlow glow : CrystalGlow.values()) {
                    String suffix = glow == CrystalGlow.NONE ? "" : "_" + glow.getSerializedName();
                    sheets[tier.ordinal()][glow.ordinal()] = EnderLaserQuarry.rl("textures/entity/" + prefix + name + suffix + ".png");
                }
            }
        }
    }

    // mask bit order, sheet cell = mask % 4, mask / 4
    private static final Direction[] SIDES = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    public static void render(Level level, BlockPos pos, Emblem emblem, PoseStack poseStack, MultiBufferSource buffers) {
        int mask = 0;
        boolean inFloor = false;
        QuarryTier tier = null;
        CrystalGlow glow = CrystalGlow.NONE;
        for (int i = 0; i < SIDES.length; i++) {
            var neighbour = level.getBlockState(pos.relative(SIDES[i]));
            if (QuarryPlatingBlock.isQuarryFloor(neighbour)) {
                mask |= 1 << i;
                if (neighbour.is(CrystalObeliskBlockEntity.FRAME_BLOCKS)) {
                    inFloor = true;
                    if (tier == null) {
                        tier = ModBlocks.quarryFloorLook(neighbour);
                    }
                    if (glow == CrystalGlow.NONE && neighbour.hasProperty(CrystalGlow.PROPERTY)) {
                        glow = neighbour.getValue(CrystalGlow.PROPERTY);
                    }
                }
            }
        }
        if (!inFloor) {
            return;
        }
        if (tier == null) {
            // datapack frame blocks have no tier
            tier = QuarryTier.BASIC;
        }

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer top = buffers.getBuffer(ModRenderTypes.casing(emblem.sheets[tier.ordinal()][glow.ordinal()]));
        float u0 = (mask % 4) / 4f;
        float v0 = (mask / 4) / 4f;
        float u1 = u0 + 0.25f;
        float v1 = v0 + 0.25f;
        int light = LevelRenderer.getLightColor(level, pos.above());
        // counter clockwise from above
        vertex(top, pose, 0, 1, 0, u0, v0, light);
        vertex(top, pose, 0, 1, 1, u0, v1, light);
        vertex(top, pose, 1, 1, 1, u1, v1, light);
        vertex(top, pose, 1, 1, 0, u1, v0, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        consumer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(pose, 0, 1, 0);
    }

    private QuarryFloorOverlay() {
    }
}

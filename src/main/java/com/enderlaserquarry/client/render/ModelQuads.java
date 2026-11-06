package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Draws the quads of a baked block model part into a vertex consumer, all with one light value. */
public final class ModelQuads {
    private static final Direction[] SIDES = Direction.values();

    public static void render(@Nullable BlockStateModelPart part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
        if (part == null) {
            return;
        }
        QuadInstance instance = new QuadInstance();
        instance.setColor(-1);
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        for (Direction side : SIDES) {
            put(part, side, pose, buffer, instance);
        }
        put(part, null, pose, buffer, instance);
    }

    // a block's own model, like a placed block (fixed random)
    public static void renderBlock(BlockState state, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42), parts);
        VertexConsumer buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        for (BlockStateModelPart part : parts) {
            render(part, pose, buffer, light, overlay);
        }
    }

    private static void put(BlockStateModelPart part, @Nullable Direction side, PoseStack.Pose pose, VertexConsumer buffer, QuadInstance instance) {
        for (BakedQuad quad : part.getQuads(side)) {
            buffer.putBakedQuad(pose, quad, instance);
        }
    }

    private ModelQuads() {
    }
}

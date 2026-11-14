package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * PORT: block entity renderer for the 26.1 render states that draws straight from the block entity: the render state only carries it and the partial
 * tick, {@link #render} draws into recorded buffers that go to the submit pipeline afterwards.
 */
public abstract class LiveRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, LiveRenderer.State<T>> {

    public static class State<T> extends BlockEntityRenderState {
        @Nullable
        public T blockEntity;
        public float partialTick;
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T blockEntity, State<T> state, float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State<T> state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        T blockEntity = state.blockEntity;
        if (blockEntity == null || blockEntity.isRemoved()) {
            return;
        }
        DeferredBuffers buffers = new DeferredBuffers();
        render(blockEntity, state.partialTick, poseStack, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY);
        buffers.submit(collector);
        // don't keep the block entity alive
        state.blockEntity = null;
    }

    public abstract void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                int packedOverlay);
}

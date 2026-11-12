package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Arrays;

/**
 * Beams are collected here and drawn last in the frame, after water, clouds and weather. They glow over everything
 * behind them without hiding it, and stay hidden behind what's in front.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID, value = Dist.CLIENT)
public final class LateBeams {
    private static final Recorder RECORDER = new Recorder();

    // vertices go in already transformed by the block entity's camera-relative pose
    public static VertexConsumer buffer() {
        return RECORDER;
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        RECORDER.vertices = 0;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || RECORDER.vertices == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Fabulous draws weather into its own layer and merges the layers by their depth, so there the beam writes it
        RenderType type = minecraft.levelRenderer.getWeatherTarget() != null ? RenderType.lightning() : ModRenderTypes.beam();
        MultiBufferSource.BufferSource source = minecraft.renderBuffers().bufferSource();
        RECORDER.replay(source.getBuffer(type));
        source.endBatch(type);
        RECORDER.vertices = 0;
    }

    // position + colour only, like the lightning format
    private static final class Recorder implements VertexConsumer {
        private float[] positions = new float[3 * 1024];
        private int[] colors = new int[1024];
        private int vertices;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (vertices == colors.length) {
                positions = Arrays.copyOf(positions, positions.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            positions[vertices * 3] = x;
            positions[vertices * 3 + 1] = y;
            positions[vertices * 3 + 2] = z;
            colors[vertices] = -1;
            vertices++;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
        }

        @Override
        public VertexConsumer setColor(int color) {
            colors[vertices - 1] = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        void replay(VertexConsumer out) {
            for (int i = 0; i < vertices; i++) {
                out.addVertex(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]).setColor(colors[i]);
            }
        }
    }

    private LateBeams() {
    }
}

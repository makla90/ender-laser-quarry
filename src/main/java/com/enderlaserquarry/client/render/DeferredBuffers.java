package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PORT: lets immediate-style drawing code feed the 26.1 submit pipeline. Vertices are recorded (already transformed by the pose
 * they were added with) and handed to the submit pipeline once per render type by {@link #submit}.
 */
public final class DeferredBuffers implements MultiBufferSource {
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    // Recorders go back here once replayed (vanilla draws each submit once), so their arrays live on between frames.
    // Render thread only.
    private static final ArrayDeque<Recorder> POOL = new ArrayDeque<>();
    private static final int POOL_LIMIT = 64;
    private static final PoseStack IDENTITY = new PoseStack();

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return recorders.computeIfAbsent(renderType, type -> {
            Recorder pooled = POOL.poll();
            return pooled != null ? pooled : new Recorder();
        });
    }

    // the recorded positions are final, so they're replayed with an identity pose
    public void submit(SubmitNodeCollector collector) {
        for (var entry : recorders.entrySet()) {
            Recorder recorder = entry.getValue();
            if (recorder.vertices > 0) {
                collector.submitCustomGeometry(IDENTITY, entry.getKey(), (pose, buffer) -> {
                    recorder.replay(buffer);
                    release(recorder);
                });
            } else {
                release(recorder);
            }
        }
        recorders.clear();
    }

    private static void release(Recorder recorder) {
        recorder.vertices = 0;
        if (POOL.size() < POOL_LIMIT) {
            POOL.push(recorder);
        }
    }

    // one vertex = 14 ints: x y z, color, u v, uv1 (2), uv2 (2), normal (3) and a mask of what was set
    private static final class Recorder implements VertexConsumer {
        private static final int STRIDE = 14;
        private static final int COLOR = 1, UV = 2, UV1 = 4, UV2 = 8, NORMAL = 16;
        private int[] data = new int[STRIDE * 64];
        private int vertices;

        private int base() {
            return (vertices - 1) * STRIDE;
        }

        private void set(int bit) {
            data[base() + 13] |= bit;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices++;
            if (vertices * STRIDE > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            int b = base();
            Arrays.fill(data, b, b + STRIDE, 0);
            data[b] = Float.floatToRawIntBits(x);
            data[b + 1] = Float.floatToRawIntBits(y);
            data[b + 2] = Float.floatToRawIntBits(z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
        }

        @Override
        public VertexConsumer setColor(int color) {
            data[base() + 3] = color;
            set(COLOR);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            data[base() + 4] = Float.floatToRawIntBits(u);
            data[base() + 5] = Float.floatToRawIntBits(v);
            set(UV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            data[base() + 6] = u;
            data[base() + 7] = v;
            set(UV1);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            data[base() + 8] = u;
            data[base() + 9] = v;
            set(UV2);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            data[base() + 10] = Float.floatToRawIntBits(x);
            data[base() + 11] = Float.floatToRawIntBits(y);
            data[base() + 12] = Float.floatToRawIntBits(z);
            set(NORMAL);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        private float f(int index) {
            return Float.intBitsToFloat(data[index]);
        }

        void replay(VertexConsumer out) {
            for (int i = 0; i < vertices; i++) {
                int b = i * STRIDE;
                int mask = data[b + 13];
                out.addVertex(f(b), f(b + 1), f(b + 2));
                if ((mask & COLOR) != 0) {
                    out.setColor(data[b + 3]);
                }
                if ((mask & UV) != 0) {
                    out.setUv(f(b + 4), f(b + 5));
                }
                if ((mask & UV1) != 0) {
                    out.setUv1(data[b + 6], data[b + 7]);
                }
                if ((mask & UV2) != 0) {
                    out.setUv2(data[b + 8], data[b + 9]);
                }
                if ((mask & NORMAL) != 0) {
                    out.setNormal(f(b + 10), f(b + 11), f(b + 12));
                }
            }
        }
    }
}

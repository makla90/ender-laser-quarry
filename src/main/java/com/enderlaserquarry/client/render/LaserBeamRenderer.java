package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a glowing laser between two arbitrary points as two nested square prisms (a bright core and a soft glow).
 * Uses the lightning render type: untextured, additive, fullbright.
 */
public final class LaserBeamRenderer {

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to, int rgb, float width, float time) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }

        // Two unit vectors perpendicular to the beam
        Vec3 axis = dir.normalize();
        Vec3 ref = Math.abs(axis.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(ref).normalize();
        Vec3 v = axis.cross(u).normalize();

        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float pulse = 1f + 0.15f * (float) Math.sin(time * 0.4f);

        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poseStack.last().pose();

        // Outer glow, then white-hot core
        prism(consumer, pose, from, to, u, v, width * pulse, r, g, b, 0.35f);
        prism(consumer, pose, from, to, u, v, width * 0.35f, Math.min(1, r + 0.6f), Math.min(1, g + 0.6f), Math.min(1, b + 0.6f), 0.9f);
    }

    private static void prism(VertexConsumer consumer, Matrix4f pose, Vec3 from, Vec3 to, Vec3 u, Vec3 v, float halfWidth,
                              float r, float g, float b, float a) {
        Vec3[] offsets = {
            u.add(v).scale(halfWidth),
            u.subtract(v).scale(halfWidth),
            u.reverse().subtract(v).scale(halfWidth),
            v.subtract(u).scale(halfWidth)
        };

        for (int i = 0; i < 4; i++) {
            Vec3 o1 = offsets[i];
            Vec3 o2 = offsets[(i + 1) % 4];
            Vec3 p1 = from.add(o1);
            Vec3 p2 = from.add(o2);
            Vec3 p3 = to.add(o2);
            Vec3 p4 = to.add(o1);

            // Both windings, since the render type culls back faces
            quad(consumer, pose, p1, p2, p3, p4, r, g, b, a);
            quad(consumer, pose, p4, p3, p2, p1, r, g, b, a);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4,
                             float r, float g, float b, float a) {
        vertex(consumer, pose, p1, r, g, b, a);
        vertex(consumer, pose, p2, r, g, b, a);
        vertex(consumer, pose, p3, r, g, b, a);
        vertex(consumer, pose, p4, r, g, b, a);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec3 p, float r, float g, float b, float a) {
        consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    private LaserBeamRenderer() {
    }
}

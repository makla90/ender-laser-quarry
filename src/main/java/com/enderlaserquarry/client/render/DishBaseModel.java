package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The formed 3x3 dish base as a 48x16x48 box for the light overlay, drawn per cell so ports can be left out. Same UVs
 * as a ModelPart box (192x64 texture, top at 96,0).
 */
public final class DishBaseModel {
    private static final float TEX_WIDTH = 192;
    private static final float TEX_HEIGHT = 64;
    private static final float MIN = -24;
    private static final float HEIGHT = 16;

    // portCells index (dx + 1) + (dz + 1) * 3
    public static void render(PoseStack poseStack, VertexConsumer out, int light, int overlay, int color, boolean[] portCells) {
        PoseStack.Pose pose = poseStack.last();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                float x0 = dx * 16 - 8, x1 = x0 + 16;
                float z0 = dz * 16 - 8, z1 = z0 + 16;
                quad(pose, out, light, overlay, color, Direction.UP,
                    x1, HEIGHT, z0, x0, HEIGHT, z0, x0, HEIGHT, z1, x1, HEIGHT, z1);
                quad(pose, out, light, overlay, color, Direction.DOWN,
                    x1, 0, z1, x0, 0, z1, x0, 0, z0, x1, 0, z0);

                if (portCells[(dx + 1) + (dz + 1) * 3]) {
                    continue;
                }
                if (dx == -1) {
                    quad(pose, out, light, overlay, color, Direction.WEST,
                        x0, 0, z0, x0, 0, z1, x0, HEIGHT, z1, x0, HEIGHT, z0);
                }
                if (dx == 1) {
                    quad(pose, out, light, overlay, color, Direction.EAST,
                        x1, 0, z1, x1, 0, z0, x1, HEIGHT, z0, x1, HEIGHT, z1);
                }
                if (dz == -1) {
                    quad(pose, out, light, overlay, color, Direction.NORTH,
                        x1, 0, z0, x0, 0, z0, x0, HEIGHT, z0, x1, HEIGHT, z0);
                }
                if (dz == 1) {
                    quad(pose, out, light, overlay, color, Direction.SOUTH,
                        x0, 0, z1, x1, 0, z1, x1, HEIGHT, z1, x0, HEIGHT, z1);
                }
            }
        }
    }

    // same vertex order as ModelPart.Cube
    private static void quad(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int color, Direction face,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        Vector3f normal = pose.transformNormal(face.step(), new Vector3f());
        Matrix4f matrix = pose.pose();
        vertex(matrix, out, light, overlay, color, face, normal, ax, ay, az);
        vertex(matrix, out, light, overlay, color, face, normal, bx, by, bz);
        vertex(matrix, out, light, overlay, color, face, normal, cx, cy, cz);
        vertex(matrix, out, light, overlay, color, face, normal, dx, dy, dz);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer out, int light, int overlay, int color, Direction face,
                               Vector3f normal, float x, float y, float z) {
        float u, v;
        switch (face) {
            case UP -> { u = 96 + (x - MIN); v = 48 - (z - MIN); }
            case DOWN -> { u = 48 + (x - MIN); v = 48 - (z - MIN); }
            case WEST -> { u = 48 - (z - MIN); v = 48 + y; }
            case EAST -> { u = 96 + (z - MIN); v = 48 + y; }
            case NORTH -> { u = 48 + (x - MIN); v = 48 + y; }
            default -> { u = 192 - (x - MIN); v = 48 + y; } // SOUTH
        }
        Vector3f pos = matrix.transformPosition(x / 16, y / 16, z / 16, new Vector3f());
        out.addVertex(pos.x(), pos.y(), pos.z(), color, u / TEX_WIDTH, v / TEX_HEIGHT, overlay, light,
            normal.x(), normal.y(), normal.z());
    }

    private DishBaseModel() {
    }
}

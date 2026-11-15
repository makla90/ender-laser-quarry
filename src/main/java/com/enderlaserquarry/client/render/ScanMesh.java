package com.enderlaserquarry.client.render;

import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** The satellite's scan: a glowing wireframe draped over the chunk it maps, a sweep line with a beam fan, fading trail. */
final class ScanMesh {
    private static final float LIFE = 30;
    private static final int STEP = 2;
    private static final int POINTS = 16 / STEP + 1;
    // fast satellites map more than a chunk per tick: keep the trail short
    private static final int MAX_TRAIL = 12;
    private static final float R = 0x3C / 255f, G = 1, B = 0x6E / 255f;
    // lines widen with the camera distance so a mesh far below a high pad still shows
    private static float widthScale = 1;

    // heights relative to the pad centre, [z * POINTS + x]
    private record Mesh(ChunkPos chunk, float start, float[] heights) {
    }

    // render thread only; keyed by pad, cleared when a pad's trail runs out
    private static final Map<BlockPos, ArrayDeque<Mesh>> TRAILS = new HashMap<>();

    private ScanMesh() {
    }

    /**
     * Draws the trail of the pad at {@code origin} (the pose is at the pad centre) and, while it scans, the beam from the
     * lens to the sweep line. Returns false when there was nothing to draw the beam onto (chunk not loaded here).
     */
    static boolean render(LaunchPadBlockEntity pad, @Nullable ChunkPos scanning, PoseStack poseStack, BlockPos origin,
                          @Nullable Vec3 lens, float time) {
        Level level = pad.getLevel();
        if (level == null) {
            return false;
        }
        ArrayDeque<Mesh> trail = TRAILS.computeIfAbsent(origin.immutable(), pos -> new ArrayDeque<>());
        if (scanning != null && (trail.isEmpty() || !trail.peekFirst().chunk().equals(scanning))) {
            float[] heights = sample(level, scanning, origin);
            if (heights != null) {
                trail.addFirst(new Mesh(scanning, time, heights));
                while (trail.size() > MAX_TRAIL) {
                    trail.removeLast();
                }
            }
        }
        for (Iterator<Mesh> it = trail.iterator(); it.hasNext(); ) {
            if (time - it.next().start() > LIFE) {
                it.remove();
            }
        }
        if (trail.isEmpty()) {
            TRAILS.remove(origin);
            return false;
        }

        VertexConsumer out = LateBeams.buffer();
        Matrix4f pose = poseStack.last().pose();
        boolean beamDrawn = false;
        // PORT: Camera.getPosition()
        Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (Mesh mesh : trail) {
            Vec3 centre = Vec3.atLowerCornerOf(origin).add(point(mesh, origin, POINTS / 2, POINTS / 2));
            widthScale = (float) Math.max(1, camera.distanceTo(centre) / 16);
            float age = (time - mesh.start()) / LIFE;
            boolean current = scanning != null && mesh.chunk().equals(scanning);
            float alpha = current ? 0.55f : 0.45f * (1 - age) * (1 - age);
            drawGrid(out, pose, mesh, origin, alpha);
            if (current && lens != null) {
                // the sweep runs north to south and starts over
                float sweep = (time * 0.08f) % 1;
                drawSweep(out, pose, mesh, origin, sweep, lens);
                beamDrawn = true;
            }
        }
        return beamDrawn;
    }

    @Nullable
    private static float[] sample(Level level, ChunkPos chunk, BlockPos origin) {
        if (!level.hasChunk(chunk.x, chunk.z)) {
            return null;
        }
        float[] heights = new float[POINTS * POINTS];
        for (int z = 0; z < POINTS; z++) {
            for (int x = 0; x < POINTS; x++) {
                int bx = chunk.getMinBlockX() + Math.min(x * STEP, 15);
                int bz = chunk.getMinBlockZ() + Math.min(z * STEP, 15);
                heights[z * POINTS + x] = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - origin.getY() + 0.06f;
            }
        }
        return heights;
    }

    private static Vec3 point(Mesh mesh, BlockPos origin, int x, int z) {
        return new Vec3(mesh.chunk().getMinBlockX() + x * STEP - origin.getX(), mesh.heights()[z * POINTS + x],
            mesh.chunk().getMinBlockZ() + z * STEP - origin.getZ());
    }

    private static void drawGrid(VertexConsumer out, Matrix4f pose, Mesh mesh, BlockPos origin, float alpha) {
        for (int i = 0; i < POINTS; i++) {
            boolean edge = i == 0 || i == POINTS - 1;
            float a = edge ? alpha * 1.6f : alpha;
            float w = edge ? 0.08f : 0.04f;
            for (int j = 0; j + 1 < POINTS; j++) {
                ribbon(out, pose, point(mesh, origin, j, i), point(mesh, origin, j + 1, i), w, a);
                ribbon(out, pose, point(mesh, origin, i, j), point(mesh, origin, i, j + 1), w, a);
            }
        }
    }

    // a bright line across the chunk at sweep (0..1 north to south) and a faint fan from the lens down to it
    private static void drawSweep(VertexConsumer out, Matrix4f pose, Mesh mesh, BlockPos origin, float sweep, Vec3 lens) {
        float zf = sweep * (POINTS - 1);
        int z0 = Math.min((int) zf, POINTS - 2);
        float t = zf - z0;
        Vec3 west = null;
        Vec3 east = null;
        for (int x = 0; x < POINTS; x++) {
            Vec3 p = point(mesh, origin, x, z0).lerp(point(mesh, origin, x, z0 + 1), t).add(0, 0.05, 0);
            if (x == 0) {
                west = p;
            } else {
                ribbon(out, pose, east, p, 0.12f, 0.9f);
            }
            east = p;
        }
        Vec3 middle = west.lerp(east, 0.5);
        LaserBeamRenderer.quad(out, pose, lens, lens, east, west, R, G, B, 0.12f);
        LaserBeamRenderer.quad(out, pose, west, east, lens, lens, R, G, B, 0.12f);
        ribbon(out, pose, lens, middle, 0.05f, 0.7f);
    }

    // a flat strip between two points, both windings so it shows from above and below
    private static void ribbon(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, float width, float alpha) {
        Vec3 dir = to.subtract(from);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 1.0E-6) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize().scale(width * widthScale / 2);
        Vec3 p1 = from.add(side), p2 = to.add(side), p3 = to.subtract(side), p4 = from.subtract(side);
        float a = Math.min(1, alpha);
        LaserBeamRenderer.quad(out, pose, p1, p2, p3, p4, R, G, B, a);
        LaserBeamRenderer.quad(out, pose, p4, p3, p2, p1, R, G, B, a);
    }
}

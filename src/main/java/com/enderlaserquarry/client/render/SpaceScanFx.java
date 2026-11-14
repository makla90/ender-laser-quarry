package com.enderlaserquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Void Satellite scanning space: a radar sweep turning around it in the sky with a fading trail, sonar rings going
 * out and blips that light up where the sweep passes. Cyan like the space map.
 */
final class SpaceScanFx {
    private static final int CYAN = 0x46D2E0;
    private static final float R = 0x46 / 255f, G = 0xD2 / 255f, B = 0xE0 / 255f;
    private static final float RADIUS = 100;
    // ticks per turn of the sweep, and between two pings
    private static final float TURN = 80;
    private static final float PING = 40;
    private static final int TRAIL_SLICES = 14;
    private static final float TRAIL_ANGLE = 0.9f;
    private static final int BLIPS = 14;

    private SpaceScanFx() {
    }

    /** {@code at} = the satellite, in the pose's space; {@code seed} keeps the blips in place for one pad. */
    static void render(PoseStack poseStack, Vec3 at, float time, long seed) {
        VertexConsumer out = LateBeams.buffer();
        Matrix4f pose = poseStack.last().pose();
        float sweep = (time / TURN) * Mth.TWO_PI;

        // the wedge: bright leading edge, trail fading behind it
        for (int i = 0; i < TRAIL_SLICES; i++) {
            float a0 = sweep - TRAIL_ANGLE * i / TRAIL_SLICES;
            float a1 = sweep - TRAIL_ANGLE * (i + 1) / TRAIL_SLICES;
            float fade = 1 - (float) i / TRAIL_SLICES;
            fan(out, pose, at, a0, a1, 0.22f * fade * fade);
        }
        Vec3 edge = at.add(Math.cos(sweep) * RADIUS, 0, Math.sin(sweep) * RADIUS);
        LaserBeamRenderer.quad(out, pose, at.add(0, 0.15, 0), edge.add(0, 0.15, 0), edge.add(0, -0.15, 0), at.add(0, -0.15, 0), R, G, B, 0.8f);
        LaserBeamRenderer.quad(out, pose, at.add(0, -0.15, 0), edge.add(0, -0.15, 0), edge.add(0, 0.15, 0), at.add(0, 0.15, 0), R, G, B, 0.8f);

        // sonar rings, two out at a time
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 south = new Vec3(0, 0, 1);
        for (int k = 0; k < 2; k++) {
            float age = ((time + k * PING) % (2 * PING)) / (2 * PING);
            TierBeamRenderer.ring(out, pose, at, east, south, RADIUS * age, 0.6f + 1.5f * age, CYAN, 0.6f * (1 - age) * (1 - age));
        }
        // the edge of the scanned field
        TierBeamRenderer.ring(out, pose, at, east, south, RADIUS, 0.4f, CYAN, 0.25f + 0.1f * Mth.sin(time * 0.15f));

        // blips: bright as the sweep passes them, fading until it comes round again
        RandomSource random = RandomSource.create(seed);
        for (int i = 0; i < BLIPS; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float distance = RADIUS * (0.25f + 0.7f * random.nextFloat());
            float since = Mth.positiveModulo(sweep - angle, Mth.TWO_PI) / Mth.TWO_PI;
            float glow = (1 - since) * (1 - since) * (1 - since);
            if (glow > 0.02f) {
                blip(out, pose, at.add(Math.cos(angle) * distance, (random.nextFloat() - 0.5f) * 6, Math.sin(angle) * distance), 1.2f + 1.5f * glow, glow);
            }
        }
    }

    // a flat slice of the disc between two angles, both windings
    private static void fan(VertexConsumer out, Matrix4f pose, Vec3 at, float a0, float a1, float alpha) {
        Vec3 p0 = at.add(Math.cos(a0) * RADIUS, 0, Math.sin(a0) * RADIUS);
        Vec3 p1 = at.add(Math.cos(a1) * RADIUS, 0, Math.sin(a1) * RADIUS);
        LaserBeamRenderer.quad(out, pose, at, at, p1, p0, R, G, B, alpha);
        LaserBeamRenderer.quad(out, pose, p0, p1, at, at, R, G, B, alpha);
    }

    // a small four-pointed star, flat and upright so it reads from below and from the side
    private static void blip(VertexConsumer out, Matrix4f pose, Vec3 at, float size, float alpha) {
        Vec3[] axes = {new Vec3(size, 0, 0), new Vec3(0, size, 0), new Vec3(0, 0, size)};
        for (int i = 0; i < 3; i++) {
            Vec3 u = axes[i];
            Vec3 v = axes[(i + 1) % 3].scale(0.25);
            LaserBeamRenderer.quad(out, pose, at.add(u), at.add(v), at.subtract(u), at.subtract(v), R, G, B, alpha);
            LaserBeamRenderer.quad(out, pose, at.subtract(v), at.subtract(u), at.add(v), at.add(u), R, G, B, alpha);
        }
    }
}

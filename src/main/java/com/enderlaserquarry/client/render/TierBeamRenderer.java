package com.enderlaserquarry.client.render;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.obelisk.CrystalGlow;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The mining beams. Uplink (crystal -> satellite): even width, rings rising around it from Enhanced on.
 * Downlink (satellite -> ground): a cone widening to the ground, corona and rings from Enhanced, impacts on the ground.
 * Colours follow the crystal.
 */
public final class TierBeamRenderer {
    private static final int RING_SIDES = 20;

    // nether star, pulsating greens, void blues
    private static final int[] LASER = { 0xB88CFF, 0x8CD8FF, 0xFFE08C, 0xE6B4FF };
    private static final int[] PULSATING = { 0x44D58A, 0x8CFFB0, 0x2DBF6A, 0x6DF8C0 };
    private static final int[] VOID = { 0x3A7BFF, 0x46C8F0, 0x2A4FD7, 0x8FC8FF };
    private static final int LASER_PULSE = 0xFFF8EC;
    private static final int PULSATING_PULSE = 0xE6FFEE;
    private static final int VOID_PULSE = 0xE6F2FF;

    public static int color(CrystalGlow glow, float time) {
        return CrystalObeliskRenderer.shimmer(palette(glow), time * 0.03f);
    }

    public static int pulse(CrystalGlow glow) {
        return switch (glow) {
            case PULSATING -> PULSATING_PULSE;
            case VOID -> VOID_PULSE;
            default -> LASER_PULSE;
        };
    }

    private static int[] palette(CrystalGlow glow) {
        return switch (glow) {
            case PULSATING -> PULSATING;
            case VOID -> VOID;
            default -> LASER;
        };
    }

    /** Crystal -> satellite: the classic beam plus rings rising around it from Enhanced on, more and bolder on Ender. */
    public static void renderUplink(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to, QuarryTier tier, CrystalGlow glow,
                                    float width, float time) {
        LaserBeamRenderer.render(poseStack, buffers, from, to, color(glow, time), width, time);
        int level = tier.ordinal();
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (level == 0 || length < 2) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.lightning());
        Matrix4f pose = poseStack.last().pose();
        Vec3 axis = dir.scale(1 / length);
        Vec3[] uv = basis(axis);
        boolean ender = level >= 2;
        float spacing = CrystalObeliskBlockEntity.ringSpacing(tier);
        float speed = CrystalObeliskBlockEntity.ringSpeed(tier);
        float base = ender ? 0.34f : 0.27f;
        int count = Mth.ceil(length / spacing);
        double loop = count * spacing;
        int hot = pulse(glow);
        for (int i = 0; i < count; i++) {
            double along = (i * spacing + time * speed) % loop;
            if (along > length) {
                continue;
            }
            // which ring this is since the start, so the big ones match the crystal's thump
            long index = Math.round((time * speed - along) / spacing);
            boolean big = CrystalObeliskBlockEntity.isBigRing(index);
            // grows out of the crystal, fades in and out at both ends
            float grow = (float) Math.min(1, along / (big ? 3 : 2));
            float fade = (float) Math.min(1, Math.min(along, length - along) / 4);
            float radius = base * (big ? 1.8f : 1) * (0.4f + 0.6f * grow) * (1 + 0.08f * Mth.sin(time * 0.5f + i));
            Vec3 at = from.add(axis.scale(along));
            int ringColor = color(glow, time + i * 20);
            ring(consumer, pose, at, uv[0], uv[1], radius, big ? 0.07f : 0.045f, hot, (big ? 1 : 0.85f) * fade);
            ring(consumer, pose, at, uv[0], uv[1], radius * 0.9f, big ? 0.2f : 0.12f, ringColor, (big ? 0.65f : 0.5f) * fade);
            if (big) {
                ring(consumer, pose, at, uv[0], uv[1], radius * 1.5f, 0.25f, ringColor, 0.15f * fade);
            } else if (ender) {
                ring(consumer, pose, at, uv[0], uv[1], radius * 1.6f, 0.05f, color(glow, time + i * 20 + 40), 0.18f * fade);
            }
        }
    }

    /** Satellite -> ground: a cone, wider per tier, impacts where it hits. */
    public static void renderDownlink(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to, QuarryTier tier, CrystalGlow glow,
                                      float time, boolean impact) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.05) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.lightning());
        Matrix4f pose = poseStack.last().pose();
        Vec3 axis = dir.scale(1 / length);
        Vec3[] uv = basis(axis);
        Vec3 u = uv[0], v = uv[1];

        int level = tier.ordinal();
        float pulse = 1 + 0.12f * Mth.sin(time * 0.4f);
        float start = 0.04f + 0.01f * level;
        float end = (0.16f + 0.07f * level) * pulse;
        int color = color(glow, time);

        cone(consumer, pose, from, to, u, v, start, end, color, 0.35f);
        cone(consumer, pose, from, to, u, v, start * 0.4f, end * 0.35f, brighten(color), 0.9f);

        if (level >= 1) {
            float breathe = 1 + 0.15f * Mth.sin(time * 0.9f) + 0.05f * Mth.sin(time * 3.1f);
            cone(consumer, pose, from, to, u, v, start * 1.5f, end * (level >= 2 ? 3.2f : 2.4f) * breathe, color(glow, time + 25), 0.12f);
            renderRings(consumer, pose, from, axis, u, v, length, start, end, level, glow, time);
        }
        if (level >= 2) {
            cone(consumer, pose, from, to, u, v, start * 2, end * 5 * (1 + 0.1f * Mth.sin(time * 0.6f)), color(glow, time + 50), 0.06f);
        }
        if (impact) {
            renderImpact(consumer, pose, to, level, glow, time);
        }
    }

    // energy rings running down the cone, fading in and out
    private static void renderRings(VertexConsumer consumer, Matrix4f pose, Vec3 from, Vec3 axis, Vec3 u, Vec3 v, double length,
                                    float start, float end, int level, CrystalGlow glow, float time) {
        int count = level >= 2 ? 5 : 3;
        float speed = level >= 2 ? 0.03f : 0.02f;
        for (int i = 0; i < count; i++) {
            float s = frac(time * speed + (float) i / count);
            float fade = Math.min(1, Math.min(s, 1 - s) * 6);
            float radius = Mth.lerp(s, start, end) * 2.2f;
            ring(consumer, pose, from.add(axis.scale(s * length)), u, v, radius, radius * 0.25f, pulse(glow), 0.55f * fade);
        }
    }

    // glow at the hit, plus small impacts: a pop, sparks and a ring rolling out fast
    private static void renderImpact(VertexConsumer consumer, Matrix4f pose, Vec3 at, int level, CrystalGlow glow, float time) {
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 south = new Vec3(0, 0, 1);
        int hot = pulse(glow);
        float flicker = 1 + 0.15f * Mth.sin(time * 1.9f) + 0.08f * Mth.sin(time * 5.3f);
        float ball = (0.25f + 0.15f * level) * flicker;
        box(consumer, pose, at, ball, color(glow, time + 10), 0.25f);
        box(consumer, pose, at, ball * 0.5f, hot, 0.5f);

        // one impact every this many ticks, staggered
        int interval = level >= 2 ? 10 : level == 1 ? 16 : 24;
        float reach = 1.4f + 0.9f * level;
        for (int i = 0; i < 2; i++) {
            float cycle = (time + i * interval * 0.5f) / interval;
            int seed = Mth.floor(cycle) * 2 + i;
            float age = frac(cycle);
            float out = 1 - (1 - age) * (1 - age);
            float fade = (1 - age) * (1 - age);
            Vec3 ground = at.add(0, -0.45, 0);
            // pop
            if (age < 0.3f) {
                float pop = 1 - age / 0.3f;
                box(consumer, pose, at, ball * (1 + 1.2f * pop), hot, 0.45f * pop);
            }
            // ring: white edge over a coloured band
            float radius = 0.3f + reach * out;
            ring(consumer, pose, ground, east, south, radius, 0.06f + 0.1f * fade, hot, 0.9f * fade);
            ring(consumer, pose, ground, east, south, radius * 0.92f, 0.18f + 0.3f * fade, color(glow, time + i * 30), 0.55f * fade);
            // sparks
            if (age < 0.4f && level >= 1) {
                float spark = 1 - age / 0.4f;
                int rays = 4 + 2 * level;
                for (int r = 0; r < rays; r++) {
                    int h = seed * 7919 + r * 104729;
                    float yaw = hash(h) * Mth.TWO_PI;
                    float rise = 0.2f + 0.8f * hash(h + 1);
                    float len = (0.6f + 0.8f * hash(h + 2)) * (0.6f + 0.4f * level);
                    Vec3 ray = new Vec3(Mth.cos(yaw), rise, Mth.sin(yaw)).normalize();
                    Vec3 tipStart = at.add(ray.scale(len * out * 0.6));
                    Vec3 tip = at.add(ray.scale(len * (0.4 + out)));
                    line(consumer, pose, tipStart, tip, 0.025f, hot, 0.9f * spark);
                }
            }
        }
    }

    private static void box(VertexConsumer consumer, Matrix4f pose, Vec3 at, float half, int rgb, float alpha) {
        cone(consumer, pose, at.subtract(0, half, 0), at.add(0, half, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), half, half, rgb, alpha);
    }

    // flat ring in the plane of a and b
    private static void ring(VertexConsumer consumer, Matrix4f pose, Vec3 center, Vec3 a, Vec3 b, float radius, float thickness,
                             int rgb, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float r = red(rgb), g = green(rgb), bl = blue(rgb);
        float inner = Math.max(0, radius - thickness);
        for (int i = 0; i < RING_SIDES; i++) {
            float a1 = i * Mth.TWO_PI / RING_SIDES;
            float a2 = (i + 1) * Mth.TWO_PI / RING_SIDES;
            Vec3 d1 = a.scale(Mth.cos(a1)).add(b.scale(Mth.sin(a1)));
            Vec3 d2 = a.scale(Mth.cos(a2)).add(b.scale(Mth.sin(a2)));
            Vec3 p1 = center.add(d1.scale(inner));
            Vec3 p2 = center.add(d1.scale(radius));
            Vec3 p3 = center.add(d2.scale(radius));
            Vec3 p4 = center.add(d2.scale(inner));
            LaserBeamRenderer.quad(consumer, pose, p1, p2, p3, p4, r, g, bl, alpha);
            LaserBeamRenderer.quad(consumer, pose, p4, p3, p2, p1, r, g, bl, alpha);
        }
    }

    private static void line(VertexConsumer consumer, Matrix4f pose, Vec3 from, Vec3 to, float halfWidth, int rgb, float alpha) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-6 || alpha <= 0.01f) {
            return;
        }
        Vec3[] uv = basis(dir.normalize());
        cone(consumer, pose, from, to, uv[0], uv[1], halfWidth, halfWidth, rgb, alpha);
    }

    private static Vec3[] basis(Vec3 axis) {
        Vec3 ref = Math.abs(axis.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(ref).normalize();
        return new Vec3[] { u, axis.cross(u).normalize() };
    }

    private static void cone(VertexConsumer consumer, Matrix4f pose, Vec3 from, Vec3 to, Vec3 u, Vec3 v, float halfFrom, float halfTo,
                             int rgb, float alpha) {
        LaserBeamRenderer.cone(consumer, pose, from, to, u, v, halfFrom, halfTo, red(rgb), green(rgb), blue(rgb), alpha);
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 153);
        int g = Math.min(255, ((rgb >> 8) & 0xFF) + 153);
        int b = Math.min(255, (rgb & 0xFF) + 153);
        return (r << 16) | (g << 8) | b;
    }

    private static float hash(int x) {
        x = (x ^ 61) ^ (x >>> 16);
        x *= 9;
        x ^= x >>> 4;
        x *= 0x27d4eb2d;
        x ^= x >>> 15;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255f;
    }

    private static float frac(float value) {
        return value - Mth.floor(value);
    }

    private TierBeamRenderer() {
    }
}

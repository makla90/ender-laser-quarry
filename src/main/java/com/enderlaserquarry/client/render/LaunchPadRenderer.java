package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.client.RemoteSatellites;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.launchpad.FlightPhase;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Rocket, satellite, beams and the controller's antenna mast. Flight positions are relative to the pad center, not the
 * controller.
 */
public class LaunchPadRenderer implements BlockEntityRenderer<LaunchPadBlockEntity> {
    private static final ResourceLocation ROCKET_TEXTURE = EnderLaserQuarry.rl("textures/entity/rocket.png");
    private static final ResourceLocation[] SATELLITE_TEXTURES = {
        EnderLaserQuarry.rl("textures/entity/satellite.png"),
        EnderLaserQuarry.rl("textures/entity/enhanced_satellite.png"),
        EnderLaserQuarry.rl("textures/entity/ender_satellite.png")};
    private static final ResourceLocation VOID_SATELLITE_TEXTURE = EnderLaserQuarry.rl("textures/entity/void_satellite.png");

    private static final int FLAME_OUTER = 0xFF6A10;
    private static final int FLAME_CORE = 0xFFE08A;
    private static final int SCAN_BEAM = 0x3CFF6E;
    private static final int PUFF = 0xDDEEFF;
    private static final int FUEL_LINE = 0x8A5A10;
    private static final int FUEL_PULSE = 0xFFC23A;
    // resupply: Dew of the Void goes up
    private static final int DEW_LINE = 0x3A1E6C;
    private static final int DEW_PULSE = 0xB088F8;
    private static final int BURN_OUTER = 0x6FB8FF;
    private static final int BURN_CORE = 0xF0FAFF;
    private static final float PUFF_INTERVAL = 67;
    private static final float PUFF_TICKS = 5;
    public static final int VIEW_DISTANCE = 256;

    // controller antenna -> quarry controller antenna
    private static final int LINK_PULSE = 0x4CE0FF;
    private static final int LINK_BEAM = 0x1C6C80;
    private static final float PULSE_INTERVAL = 80;
    private static final float PULSE_TRAVEL = 24;
    // quarry controller -> pad: power over the antenna link
    private static final int POWER_PULSE = 0xFFD84A;
    private static final int POWER_PACKETS = 3;
    private static final float POWER_TRAVEL = 30;

    private final RocketModel rocket;
    private final SatelliteModel satellite;

    public LaunchPadRenderer(BlockEntityRendererProvider.Context context) {
        this.rocket = new RocketModel(context.bakeLayer(RocketModel.LAYER));
        this.satellite = new SatelliteModel(context.bakeLayer(SatelliteModel.LAYER));
    }

    @Override
    public void render(LaunchPadBlockEntity pad, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = pad.getLevel();
        if (level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;

        renderAntenna(pad, level, partialTick, time, poseStack, buffers);
        // far from the pad RemoteSatellites draws the flight, this renderer gets culled out there
        if (!RemoteSatellites.drawsFlight(pad)) {
            renderFlight(pad, level, partialTick, time, poseStack, buffers, false);
        }
    }

    // Flight only, for a pad that is far away (RemoteSatellites); the pose is at the pad block
    public void renderRemote(LaunchPadBlockEntity pad, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        Level level = pad.getLevel();
        if (level != null) {
            renderFlight(pad, level, partialTick, level.getGameTime() + partialTick, poseStack, buffers, true);
        }
    }

    private void renderFlight(LaunchPadBlockEntity pad, Level level, float partialTick, float time, PoseStack poseStack,
                              MultiBufferSource buffers, boolean remote) {
        // move to the pad center
        BlockPos offset = pad.getPadCenter().subtract(pad.getBlockPos());
        poseStack.pushPose();
        poseStack.translate(offset.getX(), offset.getY(), offset.getZ());

        // from afar only a rocket out at the satellite, not one standing on the pad
        Vec3 rocketPos = !remote || pad.isRocketAway() ? pad.getRocketPos(partialTick) : null;
        if (rocketPos != null) {
            renderRocket(pad, level, rocketPos, partialTick, time, poseStack, buffers);
        }

        Vec3 satellitePos = pad.getSatellitePos(partialTick);
        if (satellitePos != null) {
            renderSatellite(pad, level, satellitePos, partialTick, time, poseStack, buffers);
        }
        if (rocketPos != null && satellitePos != null && pad.getPhase() == FlightPhase.ORBIT_OPS && pad.getMission().refuelsInOrbit()) {
            renderFuelLine(pad, rocketPos, satellitePos, partialTick, time, poseStack, buffers);
        }
        poseStack.popPose();
    }

    // orbit transfer: fuel goes up from the rocket's nose to the satellite, little amber pulses
    private static void renderFuelLine(LaunchPadBlockEntity pad, Vec3 rocketPos, Vec3 satellitePos, float partialTick, float time,
                                       PoseStack poseStack, MultiBufferSource buffers) {
        float progress = pad.getPhaseProgress(partialTick);
        // connects after the fairing opens, lets go before it closes
        float strength = Mth.clamp(Math.min((progress - 0.2f) / 0.1f, (0.85f - progress) / 0.1f), 0, 1);
        if (strength <= 0) {
            return;
        }
        Vec3 nose = rocketPos.add(0, LaunchPadBlockEntity.PAYLOAD_DOCK_HEIGHT + 0.6, 0);
        Vec3 to = satellitePos.add(0, -0.4, 0);
        boolean dew = pad.getMission() == FlightPhase.Mission.RESUPPLY;
        int lineColor = dew ? DEW_LINE : FUEL_LINE;
        int pulseColor = dew ? DEW_PULSE : FUEL_PULSE;
        LaserBeamRenderer.render(poseStack, buffers, nose, to, lineColor, 0.07f * strength, time);
        LaserBeamRenderer.render(poseStack, buffers, nose, to, pulseColor, 0.025f * strength, time);
        for (int i = 0; i < 4; i++) {
            float at = frac(time * 0.035f + i / 4f);
            LaserBeamRenderer.render(poseStack, buffers, nose.lerp(to, at), nose.lerp(to, Math.min(1, at + 0.08f)), pulseColor, 0.16f * strength, time);
        }
    }

    private void renderAntenna(LaunchPadBlockEntity pad, Level level, float partialTick, float time, PoseStack poseStack,
                               MultiBufferSource buffers) {
        int light = lightAt(level, pad.getBlockPos().above());
        BlockState state = pad.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        // the LED is lit while a quarry controller is linked
        boolean linked = pad.getStation() != null;
        QuarryControllerRenderer.renderMast(QuarryControllerRenderer.PAD_MAST, state, facing, poseStack, buffers, light,
            linked ? LightTexture.FULL_BRIGHT : light);

        Vec3 target = pad.getStationTarget();
        if (target == null) {
            return;
        }
        Vec3 origin = Vec3.atLowerCornerOf(pad.getBlockPos());
        Vec3 from = pad.getAntennaTip().subtract(origin);
        Vec3 to = target.subtract(origin);

        // just linked: show the whole link for a moment
        float flash = pad.getLinkFlash(partialTick);
        if (flash >= 0) {
            float fade = 1 - flash;
            LaserBeamRenderer.render(poseStack, buffers, from, to, LINK_BEAM, 0.015f + 0.035f * fade, time);
            for (int i = 0; i < 3; i++) {
                float at = frac(flash * 2.5f + i / 3f);
                LaserBeamRenderer.render(poseStack, buffers, from.lerp(to, at), from.lerp(to, Math.min(1, at + 0.05f)), LINK_PULSE, 0.07f, time);
            }
            return;
        }

        // a packet now and then
        if (linked) {
            float age = (time % PULSE_INTERVAL) / PULSE_TRAVEL;
            if (age < 1) {
                LaserBeamRenderer.render(poseStack, buffers, from.lerp(to, age), from.lerp(to, Math.min(1, age + 0.04f)), LINK_PULSE, 0.05f, time);
            }
        }

        // power from the quarry controller's antenna: yellow packets the other way
        if (linked && pad.isAntennaFeeding()) {
            for (int i = 0; i < POWER_PACKETS; i++) {
                float at = frac(time / POWER_TRAVEL + i / (float) POWER_PACKETS);
                LaserBeamRenderer.render(poseStack, buffers, to.lerp(from, at), to.lerp(from, Math.min(1, at + 0.03f)), POWER_PULSE, 0.04f, time);
            }
        } else if (linked && pad.isAntennaPowered()) {
            // just the uplink trickle: one packet now and then, between the cyan ones
            float at = ((time + PULSE_INTERVAL / 2) % PULSE_INTERVAL) / POWER_TRAVEL;
            if (at < 1) {
                LaserBeamRenderer.render(poseStack, buffers, to.lerp(from, at), to.lerp(from, Math.min(1, at + 0.03f)), POWER_PULSE, 0.04f, time);
            }
        }
    }

    private static float frac(float value) {
        return value - Mth.floor(value);
    }

    private void renderRocket(LaunchPadBlockEntity pad, Level level, Vec3 pos, float partialTick, float time,
                              PoseStack poseStack, MultiBufferSource buffers) {
        int light = lightAt(level, pad.getPadCenter().offset(BlockPos.containing(pos.x, pos.y + 2, pos.z)));
        float scale = LaunchPadBlockEntity.ROCKET_SCALE;

        Vec3 up = pad.getRocketUp(partialTick);
        poseStack.pushPose();
        poseStack.translate(pos.x, pos.y, pos.z);
        poseStack.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) up.x, (float) up.y, (float) up.z));
        poseStack.scale(scale, scale, scale);
        rocket.setFairingOpen(pad.getFairingOpen(partialTick));
        rocket.root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(ROCKET_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // exhaust
        float throttle = pad.getThrottle(partialTick);
        if (throttle > 0) {
            float flicker = 0.85f + 0.1f * Mth.sin(time * 3.1f) + 0.05f * Mth.sin(time * 7.3f);
            double length = (1.5 + 3.5 * throttle) * flicker;
            LaserBeamRenderer.render(poseStack, buffers, pos, pos.subtract(up.scale(length)), FLAME_OUTER, 0.1f + 0.2f * throttle, time);
            LaserBeamRenderer.render(poseStack, buffers, pos, pos.subtract(up.scale(length * 0.45)), FLAME_CORE, 0.12f, time);
        }
    }

    private void renderSatellite(LaunchPadBlockEntity pad, Level level, Vec3 pos, float partialTick, float time,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        boolean voidSatellite = pad.isDrawnVoid();
        ResourceLocation texture = voidSatellite ? VOID_SATELLITE_TEXTURE
            : SATELLITE_TEXTURES[Math.min(pad.getDrawnSatelliteTier().ordinal(), SATELLITE_TEXTURES.length - 1)];
        float transfer = pad.getSatelliteTransfer(partialTick);
        float scale = Mth.lerp(transfer, LaunchPadBlockEntity.SATELLITE_DOCKED_SCALE, LaunchPadBlockEntity.SATELLITE_ORBIT_SCALE);
        float wings = Mth.clamp((transfer - 0.2f) / 0.6f, 0, 1);
        wings = wings * wings * (3 - 2 * wings);

        double angle = pad.getOrbitAngle(partialTick);
        float yaw = (float) Math.atan2(-Math.sin(angle), Math.cos(angle)) * transfer;
        float roll = 0.05f * Mth.sin(time * 0.05f);
        int light = lightAt(level, pad.getPadCenter().offset(BlockPos.containing(pos)));

        poseStack.pushPose();
        poseStack.translate(pos.x, pos.y, pos.z);
        poseStack.mulPose(Axis.YP.rotation(yaw));
        poseStack.mulPose(Axis.ZP.rotation(roll));
        poseStack.scale(scale, scale, scale);

        QuarryTier tier = pad.getDrawnSatelliteTier();
        satellite.setWings(wings, 0.35f * Mth.sin(time * 0.01f));
        satellite.animate(time);
        satellite.antenna2.visible = tier != QuarryTier.BASIC;
        satellite.beacon.visible = false;
        satellite.scanner.visible = false;
        satellite.mirror.visible = false;
        satellite.navLeft.visible = false;
        satellite.navRight.visible = false;
        satellite.ring.visible = false;
        satellite.endRods.visible = false;
        satellite.root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        satellite.beacon.visible = true;
        satellite.scanner.visible = true;
        satellite.mirror.visible = true;
        satellite.navLeft.visible = true;
        satellite.navRight.visible = true;
        satellite.ring.visible = true;
        satellite.endRods.visible = true;

        var glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        boolean blink = (level.getGameTime() % 30) < 4;
        satellite.beacon.render(poseStack, glow, blink ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY,
            blink ? 0xFFFFFFFF : 0xFF606060);
        // lens lights up while scanning
        ChunkPos scanning = pad.getScanningChunk();
        satellite.scanner.render(poseStack, glow, scanning != null ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY,
            scanning != null ? 0xFFFFFFFF : 0xFF707070);
        // lights: beacon beam type, no directional shading, so they glow from below too
        var lights = buffers.getBuffer(RenderType.beaconBeam(texture, false));
        if (wings > 0.5f) {
            renderNavLights(poseStack, lights, level.getGameTime());
        }
        if (tier == QuarryTier.ENDER && wings > 0.33f) {
            renderRing(poseStack, lights, time, voidSatellite);
        }
        if (voidSatellite) {
            // end rod white with a slow violet breath
            float breath = 0.5f + 0.5f * Mth.sin(time * 0.08f);
            int rod = FastColor.ARGB32.color(255, (int) Mth.lerp(breath, 0xE8, 0xF8), (int) Mth.lerp(breath, 0xD8, 0xF4), 0xFF);
            satellite.endRods.render(poseStack, lights, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, rod);
        }
        if (transfer >= 1 && !pad.isArriving()) {
            renderThrusterPuff(poseStack, buffers, time);
        }
        poseStack.popPose();

        // orbit transfer: a steady burn pointing away from where it flies
        Vec3 heading = pad.getRelocationDirection();
        if (heading.lengthSqr() > 0) {
            float p = pad.getRelocationProgress(partialTick);
            // full burn to get going, short brake burn at the end
            float burn = p < 0.75f ? Mth.clamp(p / 0.08f, 0, 1) : Mth.clamp((p - 0.85f) / 0.05f, 0, 1) * Mth.clamp((1 - p) / 0.05f, 0, 1);
            if (burn > 0) {
                Vec3 dir = p < 0.75f ? heading.scale(-1) : heading;
                float flicker = 0.85f + 0.1f * Mth.sin(time * 3.7f) + 0.05f * Mth.sin(time * 9.1f);
                double length = (1.2 + 2.2 * burn) * flicker * scale / LaunchPadBlockEntity.SATELLITE_ORBIT_SCALE * 2;
                Vec3 nozzle = pos.add(dir.scale(0.5 * scale / 4));
                LaserBeamRenderer.render(poseStack, buffers, nozzle, nozzle.add(dir.scale(length)), BURN_OUTER, 0.12f * burn, time);
                LaserBeamRenderer.render(poseStack, buffers, nozzle, nozzle.add(dir.scale(length * 0.45)), BURN_CORE, 0.07f * burn, time);
            }
        }

        // mirror turns towards the target
        Vec3 mirrorPos = pos.add(0, -SatelliteModel.MIRROR_DROP / 16.0 * scale, 0);
        BlockPos target = pad.getMirrorPos(partialTick) != null ? pad.getBeamTarget() : null;
        float mirrorYaw = yaw;
        float mirrorTilt = 0;
        Vec3 targetCentre = null;
        if (target != null) {
            targetCentre = Vec3.atCenterOf(target.subtract(pad.getPadCenter()));
            Vec3 dir = targetCentre.subtract(mirrorPos).normalize();
            double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            mirrorYaw = (float) Math.atan2(dir.x, dir.z);
            mirrorTilt = (float) Math.atan2(horizontal, -dir.y);
        }

        poseStack.pushPose();
        poseStack.translate(mirrorPos.x, mirrorPos.y, mirrorPos.z);
        poseStack.mulPose(Axis.YP.rotation(mirrorYaw));
        // face is -Y, target is along +Z after the yaw
        poseStack.mulPose(Axis.XP.rotation(-mirrorTilt));
        poseStack.scale(scale, scale, scale);

        satellite.mirrorSurface.visible = false;
        satellite.mirror.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        satellite.mirrorSurface.visible = true;

        boolean reflecting = targetCentre != null;
        int surfaceColor = reflecting ? FastColor.ARGB32.color((int) (215 + 40 * Mth.sin(time * 0.6f)), 255, 255, 255) : 0xFFB8C0CC;
        // WORKAROUND: get it again, the cutout buffer may have flushed it
        glow =buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        satellite.mirrorSurface.render(poseStack, glow, reflecting ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, surfaceColor);
        poseStack.popPose();

        if (reflecting) {
            if (targetCentre.y > mirrorPos.y) {
                TierBeamRenderer.renderSpaceBeam(poseStack, buffers, mirrorPos, targetCentre, pad.getBeamTier(), pad.getBeamGlow(), time);
            } else {
                TierBeamRenderer.renderDownlink(poseStack, buffers, mirrorPos, targetCentre, pad.getBeamTier(), pad.getBeamGlow(), time, true);
            }
        }

        // scan: wireframe over the chunk with a sweep line and a beam fan, the mapped ones fade out behind it
        BlockPos origin = pad.getPadCenter();
        Vec3 lens = null;
        if (scanning != null) {
            double lensZ = SatelliteModel.LENS_Z / 16.0 * scale;
            lens = pos.add(Math.sin(yaw) * lensZ, SatelliteModel.LENS_Y / 16.0 * scale, Math.cos(yaw) * lensZ);
        }
        // the Void Satellite scanning space: radar sweep, sonar rings and blips around it
        if (isSpaceScanning(pad)) {
            SpaceScanFx.render(poseStack, pos, time, pad.getBlockPos().asLong());
        }
        if (!ScanMesh.render(pad, scanning, poseStack, origin, lens, time) && scanning != null) {
            // chunk not loaded on this client: just the beam, down to the pad's height
            Vec3 ground = new Vec3(scanning.getMiddleBlockX() + 0.5 - origin.getX(), 1.0, scanning.getMiddleBlockZ() + 0.5 - origin.getZ());
            LaserBeamRenderer.render(poseStack, buffers, lens, ground, SCAN_BEAM, 0.05f, time);
        }
    }

    private static boolean isSpaceScanning(LaunchPadBlockEntity pad) {
        var radar = pad.getRadar();
        return radar != null && radar.isSpaceScanning() && pad.isVoidSatelliteUp();
    }

    // wing tips: steady red left, green right, white double strobe like an aircraft
    private void renderNavLights(PoseStack poseStack, VertexConsumer glow, long gameTime) {
        long cycle = gameTime % 40;
        boolean strobe = cycle < 2 || (cycle >= 5 && cycle < 7);
        navLight(poseStack, glow, satellite.wingLeft, satellite.panelLeft, satellite.outerLeft, satellite.navLeft,
            strobe ? 0xFFFFFFFF : 0xFFFF2A2A);
        navLight(poseStack, glow, satellite.wingRight, satellite.panelRight, satellite.outerRight, satellite.navRight,
            strobe ? 0xFFFFFFFF : 0xFF2AFF5A);
    }

    private static void navLight(PoseStack poseStack, VertexConsumer glow, ModelPart wing, ModelPart panel, ModelPart outer,
                                 ModelPart light, int color) {
        poseStack.pushPose();
        wing.translateAndRotate(poseStack);
        panel.translateAndRotate(poseStack);
        outer.translateAndRotate(poseStack);
        light.render(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        poseStack.popPose();
    }

    // Ender ring: teal segments with a bright pulse running around (Void: violet)
    private void renderRing(PoseStack poseStack, VertexConsumer glow, float time, boolean voidSatellite) {
        poseStack.pushPose();
        satellite.ring.translateAndRotate(poseStack);
        float head = (time * 0.15f) % SatelliteModel.RING_SEGMENTS;
        for (int i = 0; i < SatelliteModel.RING_SEGMENTS; i++) {
            float behind = Math.floorMod((int) Math.floor(head) - i, SatelliteModel.RING_SEGMENTS) + (head - (float) Math.floor(head));
            float bright = Math.max(0, 1 - behind / 3f);
            int color = voidSatellite
                ? FastColor.ARGB32.color(255, (int) Mth.lerp(bright, 0x7A, 0xEC), (int) Mth.lerp(bright, 0x3C, 0xD0), (int) Mth.lerp(bright, 0xC8, 0xFF))
                : FastColor.ARGB32.color(255, (int) Mth.lerp(bright, 0x30, 0xE0), (int) Mth.lerp(bright, 0xE8, 0xFF), (int) Mth.lerp(bright, 0xC8, 0xF8));
            satellite.ringSegments[i].render(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        }
        poseStack.popPose();
    }

    // station keeping: every few seconds a short puff from one of the four nozzles
    private static void renderThrusterPuff(PoseStack poseStack, MultiBufferSource buffers, float time) {
        int window = Mth.floor(time / PUFF_INTERVAL);
        float age = time - window * PUFF_INTERVAL;
        if (age > PUFF_TICKS) {
            return;
        }
        int hash = window * 0x9E3779B1;
        float[] nozzle = SatelliteModel.NOZZLES[(hash >>> 28) & 3];
        float grow = Mth.sin(age / PUFF_TICKS * Mth.PI);
        Vec3 from = new Vec3(nozzle[0] / 16.0, -5.2 / 16.0, nozzle[1] / 16.0);
        Vec3 out = new Vec3(nozzle[0] * 0.35, -3, nozzle[1] * 0.35).normalize().scale(3.5 / 16.0 * grow);
        LaserBeamRenderer.render(poseStack, buffers, from, from.add(out), PUFF, 0.04f * grow, time);
    }

    // no light data above the build limit
    static int lightAt(Level level, BlockPos pos) {
        if (pos.getY() >= level.getMaxBuildHeight()) {
            return LightTexture.pack(0, 15);
        }
        return LevelRenderer.getLightColor(level, pos);
    }

    @Override
    public boolean shouldRenderOffScreen(LaunchPadBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    // Infinite only while the rocket flies or the beam is on. Otherwise: the pad, the link to the quarry controller, the
    // orbit circle up there and the scan beam's spot on the ground.
    @Override
    public AABB getRenderBoundingBox(LaunchPadBlockEntity pad) {
        if (pad.isFlying() || pad.getBeamTarget() != null) {
            return AABB.INFINITE;
        }
        BlockPos center = pad.getPadCenter();
        BlockPos controller = pad.getBlockPos();
        int radius = LaunchPadBlockEntity.RADIUS + 1;
        double minX = Math.min(center.getX() - radius, controller.getX() - 1);
        double minY = Math.min(center.getY(), controller.getY()) - 1;
        double minZ = Math.min(center.getZ() - radius, controller.getZ() - 1);
        double maxX = Math.max(center.getX() + radius + 1, controller.getX() + 2);
        // rocket standing on the pad, antenna on the controller
        double maxY = Math.max(center.getY(), controller.getY()) + 10;
        double maxZ = Math.max(center.getZ() + radius + 1, controller.getZ() + 2);

        Vec3 station = pad.getStationTarget();
        if (station != null) {
            minX = Math.min(minX, station.x - 1);
            minY = Math.min(minY, station.y - 1);
            minZ = Math.min(minZ, station.z - 1);
            maxX = Math.max(maxX, station.x + 1);
            maxY = Math.max(maxY, station.y + 1);
            maxZ = Math.max(maxZ, station.z + 1);
        }
        if (pad.hasSatelliteInOrbit()) {
            Vec3 orbit = Vec3.atLowerCornerOf(center).add(LaunchPadBlockEntity.LAUNCH_POINT).add(pad.getOrbitAnchor(0));
            // the space scan's sweep reaches far out around the satellite
            double reach = LaunchPadBlockEntity.ORBIT_RADIUS + LaunchPadBlockEntity.SATELLITE_ORBIT_SCALE * 2 + (isSpaceScanning(pad) ? 105 : 0);
            minX = Math.min(minX, orbit.x - reach);
            minZ = Math.min(minZ, orbit.z - reach);
            maxX = Math.max(maxX, orbit.x + reach);
            maxY = Math.max(maxY, orbit.y + pad.getOrbitAltitude() + reach);
            maxZ = Math.max(maxZ, orbit.z + reach);
            ChunkPos scanning = pad.getScanningChunk();
            if (scanning != null) {
                // the scan mesh lies on the terrain far below a high pad
                minY = Math.min(minY, pad.getLevel() != null ? pad.getLevel().getMinBuildHeight() : minY);
                minX = Math.min(minX, scanning.getMinBlockX());
                minZ = Math.min(minZ, scanning.getMinBlockZ());
                maxX = Math.max(maxX, scanning.getMaxBlockX() + 1);
                maxZ = Math.max(maxZ, scanning.getMaxBlockZ() + 1);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

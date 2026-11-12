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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Rocket, satellite, beams and the controller's mini radar. Flight positions are relative to the pad center, not the
 * controller.
 */
public class LaunchPadRenderer extends LiveRenderer<LaunchPadBlockEntity> {
    private static final Identifier ROCKET_TEXTURE = EnderLaserQuarry.rl("textures/entity/rocket.png");
    private static final Identifier[] SATELLITE_TEXTURES = {
        EnderLaserQuarry.rl("textures/entity/satellite.png"),
        EnderLaserQuarry.rl("textures/entity/enhanced_satellite.png"),
        EnderLaserQuarry.rl("textures/entity/ender_satellite.png")};
    private static final Identifier VOID_SATELLITE_TEXTURE = EnderLaserQuarry.rl("textures/entity/void_satellite.png");

    private static final int FLAME_OUTER = 0xFF6A10;
    private static final int FLAME_CORE = 0xFFE08A;
    private static final int SCAN_BEAM = 0x3CFF6E;
    private static final int PUFF = 0xDDEEFF;
    private static final int FUEL_LINE = 0x8A5A10;
    private static final int FUEL_PULSE = 0xFFC23A;
    private static final int BURN_OUTER = 0x6FB8FF;
    private static final int BURN_CORE = 0xF0FAFF;
    private static final float PUFF_INTERVAL = 67;
    private static final float PUFF_TICKS = 5;
    public static final int VIEW_DISTANCE = 256;

    // mini radar -> obelisk
    private static final int LINK_PULSE = 0x4CE0FF;
    private static final int LINK_BEAM = 0x1C6C80;
    private static final float PULSE_INTERVAL = 80;
    private static final float PULSE_TRAVEL = 24;

    private final RocketModel rocket;
    private final SatelliteModel satellite;
    private final GroundRadarModel miniRadar;

    public LaunchPadRenderer(BlockEntityRendererProvider.Context context) {
        this.rocket = new RocketModel(context.bakeLayer(RocketModel.LAYER));
        this.satellite = new SatelliteModel(context.bakeLayer(SatelliteModel.LAYER));
        this.miniRadar = new GroundRadarModel(context.bakeLayer(GroundRadarModel.LAYER));
    }

    @Override
    public void render(LaunchPadBlockEntity pad, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = pad.getLevel();
        if (level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;

        renderMiniRadar(pad, level, partialTick, time, poseStack, buffers);
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
        if (rocketPos != null && satellitePos != null && pad.getPhase() == FlightPhase.ORBIT_OPS && pad.getMission() == FlightPhase.Mission.TRANSFER) {
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
        LaserBeamRenderer.render(poseStack, buffers, nose, to, FUEL_LINE, 0.07f * strength, time);
        LaserBeamRenderer.render(poseStack, buffers, nose, to, FUEL_PULSE, 0.025f * strength, time);
        for (int i = 0; i < 4; i++) {
            float at = frac(time * 0.035f + i / 4f);
            LaserBeamRenderer.render(poseStack, buffers, nose.lerp(to, at), nose.lerp(to, Math.min(1, at + 0.08f)), FUEL_PULSE, 0.16f * strength, time);
        }
    }

    private void renderMiniRadar(LaunchPadBlockEntity pad, Level level, float partialTick, float time, PoseStack poseStack,
                                 MultiBufferSource buffers) {
        int light = lightAt(level, pad.getBlockPos().above());
        float scale = LaunchPadBlockEntity.MINI_RADAR_SCALE;
        miniRadar.turret.yRot = pad.getMiniRadarYaw(partialTick) * Mth.DEG_TO_RAD;
        miniRadar.antenna.xRot = -pad.getMiniRadarPitch(partialTick) * Mth.DEG_TO_RAD;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.0, 0.5);
        poseStack.scale(scale, scale, scale);
        miniRadar.horn.visible = false;
        miniRadar.root.render(poseStack, buffers.getBuffer(RenderTypes.entityCutout(GroundRadarRenderer.TEXTURE)), light,
            OverlayTexture.NO_OVERLAY);
        miniRadar.horn.visible = true;
        miniRadar.root.translateAndRotate(poseStack);
        miniRadar.turret.translateAndRotate(poseStack);
        miniRadar.antenna.translateAndRotate(poseStack);
        boolean linked = pad.getStation() != null;
        miniRadar.horn.render(poseStack, buffers.getBuffer(RenderTypes.entityTranslucentEmissive(GroundRadarRenderer.TEXTURE)),
            linked ? LightCoordsUtil.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, linked ? 0xFFFFFFFF : 0xFF505050);
        poseStack.popPose();

        Vec3 target = pad.getStationTarget();
        if (target == null) {
            return;
        }
        Vec3 origin = Vec3.atLowerCornerOf(pad.getBlockPos());
        Vec3 from = pad.getMiniRadarHorn(partialTick).subtract(origin);
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
        rocket.root.render(poseStack, buffers.getBuffer(RenderTypes.entityCutout(ROCKET_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
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
        Identifier texture = voidSatellite ? VOID_SATELLITE_TEXTURE
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
        satellite.root.render(poseStack, buffers.getBuffer(RenderTypes.entityCutout(texture)), light, OverlayTexture.NO_OVERLAY);
        satellite.beacon.visible = true;
        satellite.scanner.visible = true;
        satellite.mirror.visible = true;
        satellite.navLeft.visible = true;
        satellite.navRight.visible = true;
        satellite.ring.visible = true;
        satellite.endRods.visible = true;

        var glow = buffers.getBuffer(RenderTypes.entityTranslucentEmissive(texture));
        boolean blink = (level.getGameTime() % 30) < 4;
        satellite.beacon.render(poseStack, glow, blink ? LightCoordsUtil.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY,
            blink ? 0xFFFFFFFF : 0xFF606060);
        // lens lights up while scanning
        ChunkPos scanning = pad.getScanningChunk();
        satellite.scanner.render(poseStack, glow, scanning != null ? LightCoordsUtil.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY,
            scanning != null ? 0xFFFFFFFF : 0xFF707070);
        // lights: beacon beam type, no directional shading, so they glow from below too
        var lights = buffers.getBuffer(RenderTypes.beaconBeam(texture, false));
        if (wings > 0.5f) {
            renderNavLights(poseStack, lights, level.getGameTime());
        }
        if (tier == QuarryTier.ENDER && wings > 0.33f) {
            renderRing(poseStack, lights, time, voidSatellite);
        }
        if (voidSatellite) {
            // end rod white with a slow violet breath
            float breath = 0.5f + 0.5f * Mth.sin(time * 0.08f);
            int rod = ARGB.color(255, (int) Mth.lerp(breath, 0xE8, 0xF8), (int) Mth.lerp(breath, 0xD8, 0xF4), 0xFF);
            satellite.endRods.render(poseStack, lights, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, rod);
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
        satellite.mirror.render(poseStack, buffers.getBuffer(RenderTypes.entityCutout(texture)), light, OverlayTexture.NO_OVERLAY);
        satellite.mirrorSurface.visible = true;

        boolean reflecting = targetCentre != null;
        int surfaceColor = reflecting ? ARGB.color((int) (215 + 40 * Mth.sin(time * 0.6f)), 255, 255, 255) : 0xFFB8C0CC;
        // get it again, the cutout buffer may have flushed it
        glow =buffers.getBuffer(RenderTypes.entityTranslucentEmissive(texture));
        satellite.mirrorSurface.render(poseStack, glow, reflecting ? LightCoordsUtil.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, surfaceColor);
        poseStack.popPose();

        if (reflecting) {
            TierBeamRenderer.renderDownlink(poseStack, buffers, mirrorPos, targetCentre, pad.getBeamTier(), pad.getBeamGlow(), time,
                targetCentre.y < mirrorPos.y);
        }

        if (scanning != null) {
            // scan beam
            BlockPos origin = pad.getPadCenter();
            double lensZ = SatelliteModel.LENS_Z / 16.0 * scale;
            Vec3 lens = pos.add(Math.sin(yaw) * lensZ, SatelliteModel.LENS_Y / 16.0 * scale, Math.cos(yaw) * lensZ);
            Vec3 ground = new Vec3(scanning.getMiddleBlockX() + 0.5 - origin.getX(), 1.0, scanning.getMiddleBlockZ() + 0.5 - origin.getZ());
            LaserBeamRenderer.render(poseStack, buffers, lens, ground, SCAN_BEAM, 0.05f, time);
        }
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
        light.render(poseStack, glow, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
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
                ? ARGB.color(255, (int) Mth.lerp(bright, 0x7A, 0xEC), (int) Mth.lerp(bright, 0x3C, 0xD0), (int) Mth.lerp(bright, 0xC8, 0xFF))
                : ARGB.color(255, (int) Mth.lerp(bright, 0x30, 0xE0), (int) Mth.lerp(bright, 0xE8, 0xFF), (int) Mth.lerp(bright, 0xC8, 0xF8));
            satellite.ringSegments[i].render(poseStack, glow, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
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
        if (pos.getY() >= (level.getMaxY() + 1)) {
            return LightCoordsUtil.pack(0, 15);
        }
        return LevelRenderer.getLightCoords(level, pos);
    }

    @Override
    public boolean shouldRenderOffScreen() {
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
        // rocket standing on the pad, mini radar on the controller
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
            double reach = LaunchPadBlockEntity.ORBIT_RADIUS + LaunchPadBlockEntity.SATELLITE_ORBIT_SCALE * 2;
            minX = Math.min(minX, orbit.x - reach);
            minZ = Math.min(minZ, orbit.z - reach);
            maxX = Math.max(maxX, orbit.x + reach);
            maxY = Math.max(maxY, orbit.y + pad.getOrbitAltitude() + reach);
            maxZ = Math.max(maxZ, orbit.z + reach);
            ChunkPos scanning = pad.getScanningChunk();
            if (scanning != null) {
                minX = Math.min(minX, scanning.getMinBlockX());
                minZ = Math.min(minZ, scanning.getMinBlockZ());
                maxX = Math.max(maxX, scanning.getMaxBlockX() + 1);
                maxZ = Math.max(maxZ, scanning.getMaxBlockZ() + 1);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

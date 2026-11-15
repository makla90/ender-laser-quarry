package com.enderlaserquarry.content.radar;

import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.ConnectedFrameBlock;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.launchpad.UplinkStatus;
import com.enderlaserquarry.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Ground radar on the launch pad ring. Keeps the uplink to the satellite, paid from the pad's energy.
 */
public class GroundRadarBlockEntity extends BlockEntity {
    public static final Vec3 ANTENNA_PIVOT = new Vec3(0.5, 1 + 11 / 16.0, 0.5);
    public static final double HORN_DISTANCE = 11 / 16.0;

    private static final String STATION = "Station";
    private static final String POWERED = "Powered";
    private static final String SCANNING = "Scanning";
    private static final String SPACE_SCAN = "SpaceScan";
    private static final int SYNC_INTERVAL = 5;
    private static final int VALIDATE_INTERVAL = 20;

    private static final float MAX_YAW_STEP = 6;
    private static final float MAX_PITCH_STEP = 4;
    private static final float SWEEP_SPEED = 1.2f;
    private static final float SWEEP_PITCH = 12;
    // space scan: fast spin, horn up at the sky
    private static final float SPACE_SWEEP_SPEED = 14;
    private static final float SPACE_PITCH = 55;

    @Nullable
    private BlockPos stationPos;
    private boolean powered;
    private boolean scanning;
    private boolean spaceScanning;

    // client animation
    private float yaw, prevYaw;
    private float pitch = SWEEP_PITCH, prevPitch = SWEEP_PITCH;
    private float spinSpeed = SWEEP_SPEED;
    private boolean wasSpaceScanning;

    public GroundRadarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GROUND_RADAR.get(), pos, state);
    }

    // region Station link & power

    @Nullable
    public BlockPos getStationPos() {
        return stationPos;
    }

    public void setStation(@Nullable BlockPos pos) {
        if (Objects.equals(stationPos, pos)) {
            return;
        }
        stationPos = pos == null ? null : pos.immutable();
        setChanged();
        syncToClients();
    }

    @Nullable
    public LaunchPadBlockEntity getStation() {
        if (level == null || stationPos == null || !level.isLoaded(stationPos)) {
            return null;
        }
        return level.getBlockEntity(stationPos) instanceof LaunchPadBlockEntity pad ? pad : null;
    }

    // only while the pad still has us on its ring
    @Nullable
    public LaunchPadBlockEntity getSatellitePad() {
        LaunchPadBlockEntity pad = getStation();
        return pad != null && worldPosition.equals(pad.getRadarPos()) ? pad : null;
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean consumeUplinkEnergy() {
        return consumeScanEnergy(QuarryConfig.RADAR_UPLINK_USAGE.get());
    }

    public boolean consumeScanEnergy(int amount) {
        LaunchPadBlockEntity pad = getSatellitePad();
        return pad != null && pad.getEnergy().consume(amount);
    }

    // endregion

    // region Ticking

    public static void tick(Level level, BlockPos pos, BlockState state, GroundRadarBlockEntity radar) {
        if (level.isClientSide()) {
            radar.clientTick();
        } else {
            radar.serverTick();
        }
    }

    // joined edges to the pad plating, for blocks placed without neighbour updates (/elq demo, structure templates)
    public void updateConnections() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ConnectedFrameBlock.NORTH)) {
            return;
        }
        BlockState joined = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            joined = joined.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side),
                GroundRadarBlock.joinsPad(level.getBlockState(worldPosition.relative(side))));
        }
        if (joined != state) {
            level.setBlock(worldPosition, joined, Block.UPDATE_CLIENTS);
        }
    }

    private void serverTick() {
        if (level == null || level.getGameTime() % SYNC_INTERVAL != 0) {
            return;
        }
        boolean nowSpaceScanning = computeSpaceScanning();
        if (nowSpaceScanning != spaceScanning) {
            spaceScanning = nowSpaceScanning;
            syncToClients();
        }
        if (level.getGameTime() % VALIDATE_INTERVAL != 0) {
            return;
        }

        if (stationPos != null && level.isLoaded(stationPos)) {
            if (getSatellitePad() == null) {
                setStation(null);
            }
        }
        updateConnections();

        LaunchPadBlockEntity pad = getSatellitePad();
        boolean nowPowered = pad != null && pad.getEnergy().getEnergyStored() >= Math.max(1, QuarryConfig.RADAR_UPLINK_USAGE.get());
        boolean nowScanning = computeScanning();
        if (nowPowered != powered || nowScanning != scanning) {
            powered = nowPowered;
            scanning = nowScanning;
            syncToClients();
        }
    }

    // mapping chunks, running a space scan or analyzing an asteroid (the Void Satellite maps no ground)
    private boolean computeScanning() {
        LaunchPadBlockEntity pad = getSatellitePad();
        if (pad == null || pad.getUplink() != UplinkStatus.ONLINE) {
            return false;
        }
        if (!pad.isVoidSatelliteUp() && !pad.isScanComplete()) {
            return true;
        }
        QuarryControllerBlockEntity controller = pad.getStation();
        if (controller == null) {
            return false;
        }
        return (controller.isScanningSpace() && !controller.isScanStalled())
            || (controller.getAnalyzeId() >= 0 && !controller.isAnalyzeStalled());
    }

    private boolean computeSpaceScanning() {
        LaunchPadBlockEntity pad = getSatellitePad();
        QuarryControllerBlockEntity controller = pad != null ? pad.getStation() : null;
        return powered && controller != null && controller.isScanningSpace() && !controller.isScanStalled();
    }

    public boolean isSpaceScanning() {
        return spaceScanning;
    }

    // The full beam only shows while scanning, otherwise just the packets
    public boolean isScanning() {
        return scanning;
    }

    private void clientTick() {
        prevYaw = yaw;
        prevPitch = pitch;

        if (!powered) {
            return;
        }

        spaceScanEffects();
        if (spaceScanning || spinSpeed > SWEEP_SPEED + 0.01f) {
            // spins up and winds down smoothly
            spinSpeed += Mth.clamp((spaceScanning ? SPACE_SWEEP_SPEED : SWEEP_SPEED) - spinSpeed, -0.25f, 0.4f);
            yaw += spinSpeed;
            float targetPitch = spaceScanning ? SPACE_PITCH + 10 * Mth.sin(level.getGameTime() * 0.08f) : SWEEP_PITCH;
            pitch += Mth.clamp(targetPitch - pitch, -MAX_PITCH_STEP, MAX_PITCH_STEP);
            wrapYaw();
            return;
        }

        LaunchPadBlockEntity pad = getSatellitePad();
        Vec3 target = pad != null ? pad.getTrackingTarget(0) : null;
        Vec3 dir = target == null ? null
            : Vec3.atLowerCornerOf(pad.getPadCenter()).add(target).subtract(Vec3.atLowerCornerOf(worldPosition).add(ANTENNA_PIVOT));
        double horizontal = dir == null ? 0 : Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float targetPitch = dir == null ? SWEEP_PITCH : Mth.clamp((float) Math.toDegrees(Math.atan2(dir.y, horizontal)), -10, 90);
        if (dir != null && pad.isFlying()) {
            // follow the rocket
            float targetYaw = horizontal > 1.0E-3 ? (float) Math.toDegrees(Math.atan2(dir.x, dir.z)) : yaw;
            yaw += Mth.clamp(Mth.wrapDegrees(targetYaw - yaw), -MAX_YAW_STEP, MAX_YAW_STEP);
        } else {
            yaw += SWEEP_SPEED;
        }
        pitch += Mth.clamp(targetPitch - pitch, -MAX_PITCH_STEP, MAX_PITCH_STEP);

        wrapYaw();
    }

    // wrap, prev too so the lerp doesn't jump
    private void wrapYaw() {
        if (yaw > 180 || yaw < -180) {
            float wrapped = Mth.wrapDegrees(yaw);
            prevYaw += wrapped - yaw;
            yaw = wrapped;
        }
    }

    // space scan: start chirp, sonar pings, a quiet hum and sparks off the horn, wind-down at the end
    private void spaceScanEffects() {
        Vec3 horn = Vec3.atLowerCornerOf(worldPosition).add(getHornPos(0));
        if (spaceScanning && !wasSpaceScanning) {
            level.playLocalSound(horn.x, horn.y, horn.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.8f, false);
        } else if (!spaceScanning && wasSpaceScanning) {
            level.playLocalSound(horn.x, horn.y, horn.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.4f, 1.6f, false);
        }
        wasSpaceScanning = spaceScanning;
        if (!spaceScanning) {
            return;
        }
        long time = level.getGameTime();
        if (time % 30 == 0) {
            // sonar ping with a softer echo
            level.playLocalSound(horn.x, horn.y, horn.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5f, 1.7f, false);
        } else if (time % 30 == 6) {
            level.playLocalSound(horn.x, horn.y, horn.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.2f, 1.7f, false);
        }
        if (time % 60 == 15) {
            level.playLocalSound(horn.x, horn.y, horn.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.35f, 1.9f, false);
        }
        if (time % 3 == 0) {
            RandomSource random = level.getRandom();
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, horn.x + random.nextGaussian() * 0.08, horn.y + random.nextGaussian() * 0.08,
                horn.z + random.nextGaussian() * 0.08, random.nextGaussian() * 0.03, 0.05, random.nextGaussian() * 0.03);
        }
    }

    public float getYaw(float partialTick) {
        return Mth.lerp(partialTick, prevYaw, yaw);
    }

    public float getPitch(float partialTick) {
        return Mth.lerp(partialTick, prevPitch, pitch);
    }

    public Vec3 getHornPos(float partialTick) {
        float yawRad = getYaw(partialTick) * Mth.DEG_TO_RAD;
        float pitchRad = getPitch(partialTick) * Mth.DEG_TO_RAD;
        Vec3 axis = new Vec3(Mth.sin(yawRad) * Mth.cos(pitchRad), Mth.sin(pitchRad), Mth.cos(yawRad) * Mth.cos(pitchRad));
        return ANTENNA_PIVOT.add(axis.scale(HORN_DISTANCE));
    }

    // endregion

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // region Serialization

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stationPos != null) {
            tag.put(STATION, NbtUtils.writeBlockPos(stationPos));
        }
        tag.putBoolean(POWERED, powered);
        tag.putBoolean(SCANNING, scanning);
        tag.putBoolean(SPACE_SCAN, spaceScanning);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stationPos = NbtUtils.readBlockPos(tag, STATION).orElse(null);
        powered = tag.getBoolean(POWERED);
        scanning = tag.getBoolean(SCANNING);
        spaceScanning = tag.getBoolean(SPACE_SCAN);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // always send all keys, so a cleared station gets synced too
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // endregion
}

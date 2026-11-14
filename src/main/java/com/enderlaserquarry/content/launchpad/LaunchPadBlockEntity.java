package com.enderlaserquarry.content.launchpad;

import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.MultiConfigurable;
import com.enderio.core.common.storage.ExternalResourceStorageView;
import com.enderio.core.common.storage.FluidStorage;
import com.enderio.core.common.storage.layout.FluidStorageLayout;
import com.enderio.core.common.storage.layout.ItemStorageLayout;
import com.enderio.core.common.storage.layout.SlotTemplates;
import com.enderio.core.common.storage.slot.SingleResourceSlotKey;
import com.enderlaserquarry.content.Tank;
import com.enderlaserquarry.init.ModDataComponents;
import com.enderlaserquarry.init.ModTickets;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import com.enderlaserquarry.content.Transfers;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.fluid.FluidItemInteractive;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIODataComponents;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.QuarryStates;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.asteroid.AsteroidField;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.controller.MinimapSnapshot;
import com.enderlaserquarry.content.launchpad.FlightPhase.Mission;
import com.enderlaserquarry.content.obelisk.CrystalGlow;
import com.enderlaserquarry.content.port.StructurePortBlockEntity;
import com.enderlaserquarry.content.radar.GroundRadarBlockEntity;
import com.enderlaserquarry.content.satellite.RocketItem;
import com.enderlaserquarry.content.satellite.SatelliteItem;
import com.enderlaserquarry.init.ModTriggers;
import com.enderlaserquarry.network.RemoteSatellitePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import com.enderlaserquarry.content.port.PortedMultiblock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Launch pad controller. Sits on the ring of the 5x5 pad, launches and recovers the satellite and runs the uplink
 * through the ground radar on the same ring, which it powers.
 * No entities for the flight: phase and start tick are synced and the client works out all positions from them.
 */
public class LaunchPadBlockEntity extends MachineBlockEntity implements FluidItemInteractive, MultiConfigurable, PortedMultiblock {
    public static final SingleResourceSlotKey<ItemResource> ROCKET = new SingleResourceSlotKey<>();
    public static final SingleResourceSlotKey<ItemResource> SATELLITE = new SingleResourceSlotKey<>();
    public static final SingleResourceSlotKey<FluidResource> FUEL = new SingleResourceSlotKey<>();
    public static final SingleResourceSlotKey<FluidResource> DEW = new SingleResourceSlotKey<>();

    public static final TagKey<Block> FRAME_BLOCKS = TagKey.create(Registries.BLOCK, EnderLaserQuarry.rl("launch_pad_frame"));
    public static final int RADIUS = 2;
    public static final TagKey<Fluid> ROCKET_FUEL = TagKey.create(Registries.FLUID, EnderLaserQuarry.rl("rocket_fuel"));
    // what the Void Satellite needs for a space scan (Ender IO's Dew of the Void)
    public static final TagKey<Fluid> SPACE_SCAN_FLUID = TagKey.create(Registries.FLUID, EnderLaserQuarry.rl("space_scan_fluid"));

    // Flight geometry, relative to the pad center
    public static final Vec3 LAUNCH_POINT = new Vec3(0.5, 1.0, 0.5);
    public static final float ROCKET_SCALE = 1.25f;
    // fairing center above the nozzle (67px in the model)
    public static final double PAYLOAD_DOCK_HEIGHT = 67 / 16.0 * ROCKET_SCALE;
    public static final double ORBIT_RADIUS = 6;
    // one lap every 30 s
    public static final double ORBIT_SPEED = Mth.TWO_PI / 600.0;
    // drawn bigger in orbit so you can still see it
    public static final float SATELLITE_DOCKED_SCALE = 0.75f;
    public static final float SATELLITE_ORBIT_SCALE = 4.0f;
    public static final double REFLECTOR_MIRROR_DROP = 9 / 16.0 * SATELLITE_ORBIT_SCALE;
    private static final double HOVER_BELOW_ORBIT = 6;

    private static final int STRUCTURE_CHECK_INTERVAL = 20;

    private static final int SCAN_LOAD_WAIT = 1200;
    private static final int KNOWN_PER_STEP = 32;

    public static final int LINK_FLASH_TICKS = 50;

    private static final String PHASE = "Phase";
    private static final String MISSION = "Mission";
    private static final String PHASE_START = "PhaseStart";
    private static final String FLIGHT_ROCKET = "FlightRocket";
    private static final String FULL_SAVE = "FullSave";
    private static final String FLUIDS = "Fluids";
    private static final String PAYLOAD = "Payload";
    private static final String CARGO_DEW = "CargoDew";
    private static final String SATELLITE_DEW = "SatelliteDew";
    private static final String ORBITING = "Orbiting";
    private static final String HAS_PAYLOAD = "HasPayload";
    private static final String IN_ORBIT = "InOrbit";
    private static final String ROCKET_ON_PAD = "RocketOnPad";
    private static final String ALTITUDE = "Altitude";
    private static final String PLATING_OK = "PlatingOk";
    private static final String SKY_OK = "SkyOk";
    private static final String STATION_POS = "StationPos";
    private static final String UPLINK = "Uplink";
    private static final String SCAN_INDEX = "ScanIndex";
    private static final String SCAN_RADIUS = "ScanRadius";
    private static final String BEAM_TARGET = "BeamTarget";
    private static final String BEAM_TIER = "BeamTier";
    private static final String BEAM_GLOW = "BeamGlow";
    private static final String DRAWN_TIER = "DrawnSatelliteTier";
    private static final String DRAWN_VOID = "DrawnVoid";
    private static final String VOID_UP = "VoidUp";
    private static final String SCAN_IMAGES = "ScanImages";
    private static final String ASTEROIDS = "Asteroids";
    private static final String PAD_CENTER = "PadCenter";
    private static final String MARKED_CENTER = "MarkedCenter";
    private static final String LINK_FLASH = "LinkFlash";
    private static final String ORBIT_DX = "OrbitDx";
    private static final String ORBIT_DZ = "OrbitDz";
    private static final String MOVE_FROM_DX = "MoveFromDx";
    private static final String MOVE_FROM_DZ = "MoveFromDz";
    private static final String MOVE_START = "MoveStart";
    private static final String MOVE_TICKS = "MoveTicks";
    private static final String ARRIVING = "Arriving";
    private static final String TRANSFER_DX = "TransferDx";
    private static final String TRANSFER_DZ = "TransferDz";
    private static final String TRANSFER_RANGE = "TransferRange";
    private static final String FLIGHT_DX = "FlightDx";
    private static final String FLIGHT_DZ = "FlightDz";
    private static final String RADAR_POS = "RadarPos";
    private static final String ENERGY = "Energy";
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double MAX_ROCKET_TILT = Math.toRadians(55);

    private final FluidStorage fluidStorage;
    private final ResourceHandler<FluidResource> externalFluids;
    private final Tank fuelTank;
    private final Tank dewTank;
    private final PadEnergy energy = new PadEnergy();

    private boolean platingOk;
    private boolean skyOk;

    private FlightPhase phase = FlightPhase.IDLE;
    private Mission mission = Mission.DEPLOY;
    private long phaseStart;
    private int orbitAltitude = 150;
    // Server only: rocket in flight, satellite on board, satellite in orbit
    private ItemStack flightRocket = ItemStack.EMPTY;
    private ItemStack payload = ItemStack.EMPTY;
    // Dew of the Void on its way up (resupply)
    private int cargoDew;
    // synced: Dew in the Void Satellite up there
    private int satelliteDew;
    private ItemStack orbiting = ItemStack.EMPTY;
    // synced for rendering
    private boolean hasPayload;
    private boolean inOrbit;
    private QuarryTier drawnSatelliteTier = QuarryTier.BASIC;
    private boolean drawnVoid;
    // a Void Satellite in orbit (synced: the controller GUI shows the space map then)
    private boolean voidUp;
    private boolean rocketOnPad;

    // The linked quarry controller
    @Nullable
    private BlockPos stationPos;
    // the ground radar on our ring, synced
    @Nullable
    private BlockPos radarPos;
    private UplinkStatus uplink = UplinkStatus.NO_SATELLITE;
    private int scanIndex;
    // from the satellite tier, kept after recovery
    private int scanRadius = QuarryTier.BASIC.defaultChunkRadius();
    private int scanTicks;
    private final ScanImageStore scanImages = new ScanImageStore();
    private final AsteroidField asteroids = new AsteroidField(this::setChanged);
    private int refreshIndex;
    // scan step waiting for an unloaded chunk to come in
    private int scanWaitTicks;

    // Orbit transfer: the centre of the satellite's area in chunks from the pad's chunk, and the move under way
    private int orbitDx;
    private int orbitDz;
    private int moveFromDx;
    private int moveFromDz;
    private long moveStart;
    private int moveTicks;
    private boolean arriving;
    // where the running TRANSFER flight sends it
    private int transferDx;
    private int transferDz;
    // synced from the satellite tier (COMMON config isn't synced)
    private int transferRange;
    // where this flight's rocket meets the satellite, in chunks from the pad's chunk (the area it was over at launch)
    private int flightDx;
    private int flightDz;
    // not saved: the tickets are checked again on world load
    private boolean selfLoaded;

    // block being lasered via our satellite
    @Nullable
    private BlockPos beamTarget;
    private QuarryTier beamTier = QuarryTier.BASIC;
    private CrystalGlow beamGlow = CrystalGlow.LASER;

    private boolean structureChecked;

    @Nullable
    private BlockPos padCenter;
    @Nullable
    private BlockPos markedCenter;

    // MIN_VALUE / 2 so now - NEVER can't overflow
    private static final long NEVER = Long.MIN_VALUE / 2;
    private long linkFlashStart = NEVER;
    // power over the controller's antenna
    private static final String ANTENNA_FEEDING = "AntennaFeeding";
    private static final String ANTENNA_POWERED = "AntennaPowered";
    private static final int USAGE_WINDOW = 20;
    private static final int FEED_LINGER = 20;
    private static final int FEED_SHOW_MIN = 100;
    private static final int TRICKLE_LINGER = 40;
    private long antennaFedTick = NEVER;
    private boolean antennaFeeding;
    // any antenna power lately, also the uplink trickle: a yellow packet now and then
    private long antennaTrickleTick = NEVER;
    private boolean antennaPowered;
    private int radarUsedSum;
    private int radarUsage;
    private int antennaInSum;
    private int antennaInput;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAUNCH_PAD.get(), pos, state, true);
        fluidStorage = new FluidStorage(FluidStorageLayout.builder()
            // fill only from outside
            .add(FUEL, SlotTemplates.input(QuarryConfig.PAD_TANK_CAPACITY.get()), slot -> slot.filter((i, fluid) -> fluid.is(ROCKET_FUEL)))
            .add(DEW, SlotTemplates.input(QuarryConfig.PAD_DEW_TANK.get()), slot -> slot.filter((i, fluid) -> fluid.is(SPACE_SCAN_FLUID)))
            .build()) {
            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                super.onContentsChanged(index, previousContents);
                setChanged();
            }
        };
        externalFluids = new ExternalResourceStorageView<>(fluidStorage);
        fuelTank = new Tank(fluidStorage, FUEL);
        dewTank = new Tank(fluidStorage, DEW);
    }

    // region Inventory & tank

    @Override
    protected ItemStorageLayout createInventoryLayout() {
        // locked during a flight so the returning items always fit
        return ItemStorageLayout.builder()
            .add(ROCKET, SlotTemplates.input(1), slot -> slot.filter((i, item) -> item.getItem() instanceof RocketItem && phase == FlightPhase.IDLE))
            .add(SATELLITE, SlotTemplates.input(1), slot -> slot.filter((i, item) -> item.getItem() instanceof SatelliteItem && phase == FlightPhase.IDLE))
            .build();
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        if (level != null && !level.isClientSide() && ROCKET.index(getInventory()) == slot) {
            boolean onPad = !getInventory().getStack(ROCKET).isEmpty();
            if (onPad != rocketOnPad) {
                rocketOnPad = onPad;
                syncToClients();
            }
        }
    }

    public FluidStorage getFluidStorage() {
        return fluidStorage;
    }

    public Tank getFuelTank() {
        return fuelTank;
    }

    public int getFuel() {
        return fuelTank.getFluidAmount();
    }

    public Tank getDewTank() {
        return dewTank;
    }

    // the tank a fluid goes into (buckets), null if none takes it
    @Nullable
    public SingleResourceSlotKey<FluidResource> tankFor(FluidStack fluid) {
        return fluid.is(ROCKET_FUEL) ? FUEL : fluid.is(SPACE_SCAN_FLUID) ? DEW : null;
    }

    // endregion

    // region Structure

    public void updateStructure() {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos oldCenter = padCenter;
        boolean nowPlating = updatePadCenter();
        BlockPos center = getPadCenter();
        // WORKAROUND: heightmap instead of sky light, light updates lag behind
        boolean nowSky = level.getHeight(Heightmap.Types.WORLD_SURFACE, center.getX(), center.getZ()) <= center.getY() + 1;
        BlockPos nowRadar = findRadar(center);
        // claimed even while the pad is incomplete
        if (nowRadar != null && level.getBlockEntity(nowRadar) instanceof GroundRadarBlockEntity radar) {
            radar.setStation(worldPosition);
        }

        boolean changed = nowPlating != platingOk || nowSky != skyOk || !Objects.equals(oldCenter, padCenter)
            || !Objects.equals(nowRadar, radarPos);
        platingOk = nowPlating;
        skyOk = nowSky;
        radarPos = nowRadar;
        markCenter(platingOk ? center : null);

        // WORKAROUND: items restored from the dropped block don't fire a slot change
        boolean nowRocketOnPad = phase == FlightPhase.IDLE && !getInventory().getStack(ROCKET).isEmpty();
        if (nowRocketOnPad != rocketOnPad) {
            rocketOnPad = nowRocketOnPad;
            changed = true;
        }

        // common config isn't synced, so send the altitude. Don't move it while something is up there
        if (phase == FlightPhase.IDLE && orbiting.isEmpty() && orbitAltitude != QuarryConfig.ORBIT_ALTITUDE.get()) {
            orbitAltitude = QuarryConfig.ORBIT_ALTITUDE.get();
            changed = true;
        }
        changed |= updateScanRadius();

        setState(QuarryStates.PAD_INCOMPLETE, !platingOk);
        setState(QuarryStates.NO_SKY, !skyOk);
        setState(QuarryStates.NO_RADAR, platingOk && radarPos == null);
        int cost = getLaunchCost();
        setState(MachineState.EMPTY_TANK, phase == FlightPhase.IDLE && cost > 0 && getFuel() < cost);

        if (changed) {
            setChanged();
            syncToClients();
        }
    }

    // Picks the best matching 5x5 that has us on its ring. Returns true if it's complete.
    private boolean updatePadCenter() {
        if (phase != FlightPhase.IDLE && padCenter != null) {
            return padScore(padCenter) == 25;
        }
        List<BlockPos> candidates = new ArrayList<>();
        if (padCenter != null) {
            candidates.add(padCenter);
        }
        candidates.add(defaultCenter());
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS) {
                    candidates.add(worldPosition.offset(-dx, 0, -dz));
                }
            }
        }
        BlockPos best = null;
        int bestScore = -1;
        for (BlockPos candidate : candidates) {
            int score = padScore(candidate);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        padCenter = best.immutable();
        return bestScore == 25;
    }

    // 25 = complete. One ground radar anywhere on the ring counts as frame
    private int padScore(BlockPos center) {
        int score = 0;
        boolean radar = false;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (pos.equals(worldPosition)) {
                    score++;
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                boolean outer = Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS;
                if (outer && !radar && state.is(ModBlocks.GROUND_RADAR.get())) {
                    radar = true;
                    score++;
                } else if (outer ? state.is(FRAME_BLOCKS) : state.is(ModBlocks.LAUNCH_PAD_PLATING.get())) {
                    score++;
                }
            }
        }
        return score;
    }

    // the radar on the ring around center, the one we had first
    @Nullable
    private BlockPos findRadar(BlockPos center) {
        BlockPos found = null;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS && level.getBlockEntity(pos) instanceof GroundRadarBlockEntity) {
                    if (pos.equals(radarPos)) {
                        return radarPos;
                    }
                    if (found == null) {
                        found = pos.immutable();
                    }
                }
            }
        }
        return found;
    }

    // behind the front, until there's plating
    private BlockPos defaultCenter() {
        Direction front = getBlockState().getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        return worldPosition.relative(front.getOpposite(), RADIUS);
    }

    // Where the rocket stands, everything flight related is relative to this
    public BlockPos getPadCenter() {
        return padCenter != null ? padCenter : worldPosition;
    }

    // Orange pad texture on the center plating, null to clear
    public void markCenter(@Nullable BlockPos center) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!Objects.equals(center, markedCenter)) {
            setCenterFlag(markedCenter, false);
            markedCenter = center;
            setChanged();
        }
        // also for the same spot, a replaced plating lost the flag
        setCenterFlag(center, true);
    }

    private void setCenterFlag(@Nullable BlockPos pos, boolean center) {
        if (pos == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(LaunchPadPlatingBlock.CENTER) && state.getValue(LaunchPadPlatingBlock.CENTER) != center) {
            level.setBlock(pos, state.setValue(LaunchPadPlatingBlock.CENTER, center), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    public boolean isPlatingComplete() {
        return platingOk;
    }

    public boolean hasSky() {
        return skyOk;
    }

    public boolean isFormed() {
        return platingOk;
    }

    // IO config screen edits the ports
    @Override
    public List<BlockPos> getConfigurables() {
        List<BlockPos> ports = findPorts();
        return ports.isEmpty() ? List.of(worldPosition) : ports;
    }

    // No IO of its own, only through the ports
    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.DISABLED);
    }

    @Override
    public @Nullable ResourceHandler<ItemResource> getPortItemHandler() {
        return MachineBlockEntity.ITEM_HANDLER_PROVIDER.getCapability(this, null);
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getPortFluidHandler() {
        return externalFluids;
    }

    // powers the radar
    @Override
    public @Nullable EnergyHandler getPortEnergyStorage() {
        return energy;
    }

    public PadEnergy getEnergy() {
        return energy;
    }

    public int getEnergyPercent() {
        return energy.getMaxEnergyStored() <= 0 ? 0 : (int) (100L * energy.getEnergyStored() / energy.getMaxEnergyStored());
    }

    // the linked quarry controller tops the buffer up over its antenna link, returns what fit
    public int receiveAntennaPower(int max) {
        int taken = max <= 0 ? 0 : energy.receiveAntenna(max);
        if (taken > 0 && level != null) {
            antennaInSum += taken;
            antennaTrickleTick = level.getGameTime();
            // the uplink trickle alone shows no packets, a scan, Analyze or a refill does
            if (taken >= FEED_SHOW_MIN) {
                antennaFedTick = level.getGameTime();
            }
        }
        return taken;
    }

    // 20-tick averages for the GUIs
    public int getRadarUsage() {
        return radarUsage;
    }

    public int getAntennaInput() {
        return antennaInput;
    }

    // synced: the controller is sending power right now (yellow packets on the link)
    public boolean isAntennaFeeding() {
        return antennaFeeding;
    }

    // synced: the pad lives on the antenna (any power from it lately)
    public boolean isAntennaPowered() {
        return antennaPowered;
    }

    private void updatePowerStats(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        if (now % USAGE_WINDOW == 0) {
            radarUsage = radarUsedSum / USAGE_WINDOW;
            antennaInput = antennaInSum / USAGE_WINDOW;
            radarUsedSum = 0;
            antennaInSum = 0;
        }
        boolean feeding = now - antennaFedTick <= FEED_LINGER;
        boolean powered = now - antennaTrickleTick <= TRICKLE_LINGER;
        if (feeding != antennaFeeding || powered != antennaPowered) {
            antennaFeeding = feeding;
            antennaPowered = powered;
            syncToClients();
        }
    }

    // Read from the world, works client side too
    @Override
    public List<BlockPos> findPorts() {
        List<BlockPos> ports = new ArrayList<>();
        if (level != null) {
            BlockPos center = getPadCenter();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    if (level.getBlockEntity(pos) instanceof StructurePortBlockEntity) {
                        ports.add(pos);
                    }
                }
            }
        }
        return ports;
    }

    // endregion

    // region Ground station

    @Nullable
    public BlockPos getStationPos() {
        return stationPos;
    }

    public void setStation(@Nullable BlockPos pos) {
        if (pos != null && level != null) {
            // show the link animation, also when relinking the same one
            linkFlashStart = level.getGameTime();
        } else if (Objects.equals(stationPos, pos)) {
            return;
        }
        stationPos = pos == null ? null : pos.immutable();
        setChanged();
        syncToClients();
    }

    // Only if it links back to us
    @Nullable
    public QuarryControllerBlockEntity getStation() {
        if (level == null || stationPos == null || !level.isLoaded(stationPos)) {
            return null;
        }
        return level.getBlockEntity(stationPos) instanceof QuarryControllerBlockEntity controller
            && worldPosition.equals(controller.getPadPos()) ? controller : null;
    }

    @Nullable
    public BlockPos getRadarPos() {
        return radarPos;
    }

    // The radar on our ring
    @Nullable
    public GroundRadarBlockEntity getRadar() {
        if (level == null || radarPos == null || !level.isLoaded(radarPos)) {
            return null;
        }
        return level.getBlockEntity(radarPos) instanceof GroundRadarBlockEntity radar ? radar : null;
    }

    // endregion

    // region Flight control

    // Deploys, or recovers if a satellite is up. Returns the reason if it can't fly.
    @Nullable
    public Component tryLaunch() {
        if (level == null || level.isClientSide()) {
            return launchError("busy");
        }
        if (phase != FlightPhase.IDLE) {
            return launchError("busy");
        }

        updateStructure();
        if (!platingOk) {
            return launchError("incomplete");
        }
        if (!skyOk) {
            return launchError("no_sky");
        }

        ItemStack rocket = getInventory().getStack(ROCKET);
        if (rocket.isEmpty()) {
            return launchError("no_rocket");
        }

        Mission newMission = orbiting.isEmpty() ? Mission.DEPLOY : Mission.RECOVER;
        ItemStack satellite = getInventory().getStack(SATELLITE);
        if (newMission == Mission.DEPLOY && satellite.isEmpty()) {
            return launchError("no_satellite");
        }
        if (newMission == Mission.RECOVER && !satellite.isEmpty()) {
            return launchError("satellite_slot_full");
        }

        int cost = getLaunchCost();
        if (getFuel() < cost) {
            return Component.translatable("message.ender_laser_quarry.launch.no_fuel", getFuel(), cost);
        }

        if (cost > 0) {
            fuelTank.drain(cost, false);
        }
        flightRocket = rocket.copy();
        getInventory().setStack(ROCKET, ItemStack.EMPTY);
        if (newMission == Mission.DEPLOY) {
            payload = satellite.copy();
            getInventory().setStack(SATELLITE, ItemStack.EMPTY);
            loadDew(payload);
        }
        mission = newMission;
        flightDx = orbitDx;
        flightDz = orbitDz;
        refreshSyncedItems();
        setPhase(FlightPhase.COUNTDOWN, level.getGameTime());
        return null;
    }

    private static Component launchError(String key) {
        return Component.translatable("message.ender_laser_quarry.launch." + key);
    }

    /**
     * Orbit transfer: the rocket fuels the satellite up and it flies over the area centred dx / dz chunks from the pad's
     * chunk. Returns the reason if it can't go.
     */
    @Nullable
    public Component tryTransfer(int dx, int dz) {
        if (level == null || level.isClientSide() || phase != FlightPhase.IDLE || arriving) {
            return launchError("busy");
        }
        updateStructure();
        if (!platingOk) {
            return launchError("incomplete");
        }
        if (!skyOk) {
            return launchError("no_sky");
        }
        if (getInventory().getStack(ROCKET).isEmpty()) {
            return launchError("no_rocket");
        }
        QuarryTier tier = getSatelliteTier();
        if (tier == null) {
            return launchError("transfer_no_satellite");
        }
        if (isVoidSatelliteUp()) {
            return launchError("transfer_void");
        }
        int range = ((SatelliteItem) orbiting.getItem()).transferRange();
        if (range <= 0) {
            return Component.translatable("message.ender_laser_quarry.launch.transfer_tier", tier.displayName());
        }
        if (Math.max(Math.abs(dx), Math.abs(dz)) > range) {
            return Component.translatable("message.ender_laser_quarry.launch.transfer_too_far", range);
        }
        if (dx == orbitDx && dz == orbitDz) {
            return launchError("transfer_same");
        }
        QuarryControllerBlockEntity station = getStation();
        if (station != null && station.isRunning()) {
            return launchError("transfer_stop_quarry");
        }
        int cost = getTransferCost(dx, dz);
        if (getFuel() < cost) {
            return Component.translatable("message.ender_laser_quarry.launch.no_fuel", getFuel(), cost);
        }

        if (cost > 0) {
            fuelTank.drain(cost, false);
        }
        flightRocket = getInventory().getStack(ROCKET).copy();
        getInventory().setStack(ROCKET, ItemStack.EMPTY);
        transferDx = dx;
        transferDz = dz;
        mission = Mission.TRANSFER;
        flightDx = orbitDx;
        flightDz = orbitDz;
        refreshSyncedItems();
        setPhase(FlightPhase.COUNTDOWN, level.getGameTime());
        return null;
    }

    // chunks between the current area centre and that one
    public double getTransferDistance(int dx, int dz) {
        return Math.sqrt((double) (dx - orbitDx) * (dx - orbitDx) + (double) (dz - orbitDz) * (dz - orbitDz));
    }

    public int getTransferCost(int dx, int dz) {
        return getTransferBaseCost() + (int) Math.ceil(getTransferDistance(dx, dz) * QuarryConfig.FUEL_PER_TRANSFER_CHUNK.get());
    }

    // a transfer flies up lightly loaded, like a recovery
    public int getTransferBaseCost() {
        return orbiting.getItem() instanceof SatelliteItem satellite ? satellite.returnFuel() : QuarryTier.BASIC.returnFuel();
    }

    /**
     * Rocket fuel the next Launch press burns: the satellite in the slot goes up (its launchFuel), or the one in orbit
     * comes back (its returnFuel, the rocket goes up empty). -1 = nothing to fly.
     */
    public int getLaunchCost() {
        if (!orbiting.isEmpty()) {
            return getTransferBaseCost();
        }
        return getInventory().getStack(SATELLITE).getItem() instanceof SatelliteItem satellite ? satellite.launchFuel() : -1;
    }

    // region Dew of the Void

    // a Void Satellite going up takes what fits from the pad's Dew tank
    private void loadDew(ItemStack satellite) {
        if (!SatelliteItem.isVoid(satellite)) {
            return;
        }
        int have = SatelliteItem.getDew(satellite);
        int take = Math.min(dewTank.getFluidAmount(), SatelliteItem.dewCapacity() - have);
        if (take > 0) {
            dewTank.drain(take, false);
            SatelliteItem.setDew(satellite, have + take);
        }
    }

    /**
     * Resupply: the rocket flies the pad's Dew of the Void up to the Void Satellite and comes back; the satellite stays up
     * and the quarry keeps mining. Recovery price. Returns the reason if it can't go.
     */
    @Nullable
    public Component tryResupply() {
        if (level == null || level.isClientSide() || phase != FlightPhase.IDLE || arriving) {
            return launchError("busy");
        }
        updateStructure();
        if (!platingOk) {
            return launchError("incomplete");
        }
        if (!skyOk) {
            return launchError("no_sky");
        }
        if (getInventory().getStack(ROCKET).isEmpty()) {
            return launchError("no_rocket");
        }
        if (!SatelliteItem.isVoid(orbiting)) {
            return launchError("resupply_no_void");
        }
        int space = SatelliteItem.dewCapacity() - SatelliteItem.getDew(orbiting);
        if (space <= 0) {
            return launchError("resupply_full");
        }
        if (dewTank.getFluidAmount() <= 0) {
            return launchError("resupply_no_dew");
        }
        int cost = getTransferBaseCost();
        if (getFuel() < cost) {
            return Component.translatable("message.ender_laser_quarry.launch.no_fuel", getFuel(), cost);
        }

        if (cost > 0) {
            fuelTank.drain(cost, false);
        }
        cargoDew = Math.min(space, dewTank.getFluidAmount());
        dewTank.drain(cargoDew, false);
        flightRocket = getInventory().getStack(ROCKET).copy();
        getInventory().setStack(ROCKET, ItemStack.EMPTY);
        mission = Mission.RESUPPLY;
        flightDx = orbitDx;
        flightDz = orbitDz;
        refreshSyncedItems();
        setPhase(FlightPhase.COUNTDOWN, level.getGameTime());
        return null;
    }

    // Client and server: Dew in the satellite up there (0 for any other satellite)
    public int getSatelliteDew() {
        return level != null && !level.isClientSide() ? SatelliteItem.getDew(orbiting) : satelliteDew;
    }

    // Server: takes a space scan's Dew from the satellite, false if it has too little
    public boolean consumeScanDew() {
        int need = QuarryConfig.VOID_SCAN_DEW.get();
        int have = SatelliteItem.getDew(orbiting);
        if (!SatelliteItem.isVoid(orbiting) || have < need) {
            return false;
        }
        SatelliteItem.setDew(orbiting, have - need);
        setChanged();
        syncToClients();
        return true;
    }

    public boolean isOutOfDew() {
        return isVoidSatelliteUp() && getSatelliteDew() < QuarryConfig.VOID_SCAN_DEW.get();
    }

    // endregion

    // ticks the satellite needs to fly over
    public static int relocationTicks(double chunks) {
        return (int) Mth.clamp(60 + chunks * 6, 60, 300);
    }

    private void startRelocation(ServerLevel serverLevel, long start) {
        moveFromDx = orbitDx;
        moveFromDz = orbitDz;
        orbitDx = transferDx;
        orbitDz = transferDz;
        moveStart = start;
        moveTicks = relocationTicks(Math.sqrt((double) (orbitDx - moveFromDx) * (orbitDx - moveFromDx)
            + (double) (orbitDz - moveFromDz) * (orbitDz - moveFromDz)));
        arriving = true;
        // map the new area from its middle outwards, known chunks fly by
        scanIndex = 0;
        refreshIndex = 0;
        scanWaitTicks = 0;
        setChanged();
        syncToClients();
        serverLevel.playSound(null, getPadCenter(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, 1.3f);
    }

    private void tickArrival(ServerLevel serverLevel) {
        if (!arriving || serverLevel.getGameTime() < moveStart + moveTicks) {
            return;
        }
        arriving = false;
        setChanged();
        syncToClients();
        serverLevel.playSound(null, getPadCenter(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2f, 1.2f);
        serverLevel.playSound(null, getPadCenter(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 1.5f);

        QuarryControllerBlockEntity station = getStation();
        if (station != null) {
            station.onSatelliteMoved(this);
        }
        // the owner and whoever stands at the pad hear where it is now
        ChunkPos area = getOrbitChunk();
        int across = scanRadius * 2 + 1;
        Component message = Component.translatable("message.ender_laser_quarry.transfer.arrived", area.getMiddleBlockX(), area.getMiddleBlockZ(),
            across, across).withStyle(net.minecraft.ChatFormatting.AQUA);
        java.util.Set<ServerPlayer> told = new java.util.HashSet<>();
        for (ServerPlayer player : serverLevel.players()) {
            if (player.blockPosition().closerThan(getPadCenter(), 48)) {
                told.add(player);
            }
        }
        if (station != null && station.getMachineOwner() != null) {
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(station.getMachineOwner());
            if (owner != null) {
                told.add(owner);
            }
        }
        told.forEach(player -> player.sendSystemMessage(message));
    }

    // The chunk in the middle of the satellite's area
    public ChunkPos getOrbitChunk() {
        ChunkPos pad = ChunkPos.containing(getPadCenter());
        return new ChunkPos(pad.x() + orbitDx, pad.z() + orbitDz);
    }

    public int getOrbitDx() {
        return orbitDx;
    }

    public int getOrbitDz() {
        return orbitDz;
    }

    // flying over to its new area (also true while the rocket still brings it fuel)
    public boolean isRelocating() {
        return arriving || (phase != FlightPhase.IDLE && mission == Mission.TRANSFER);
    }

    // 0..1 of the fly-over, 0 before it starts
    public float getRelocationProgress(float partialTick) {
        if (!arriving || level == null || moveTicks <= 0) {
            return 0;
        }
        return Mth.clamp((level.getGameTime() - moveStart + partialTick) / moveTicks, 0f, 1f);
    }

    // only the fly-over part
    public boolean isArriving() {
        return arriving;
    }

    // which way it flies over, horizontal unit vector (zero when not moving)
    public Vec3 getRelocationDirection() {
        Vec3 dir = new Vec3(orbitDx - moveFromDx, 0, orbitDz - moveFromDz);
        return !arriving || dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize();
    }

    // 0..1 through countdown, ascent and fuelling of a transfer flight
    public float getTransferFlightProgress() {
        if (level == null || mission != Mission.TRANSFER || phase == FlightPhase.IDLE || phase == FlightPhase.DESCENT) {
            return 0;
        }
        int total = FlightPhase.COUNTDOWN.duration() + FlightPhase.ASCENT.duration() + FlightPhase.ORBIT_OPS.duration();
        int before = switch (phase) {
            case ASCENT -> FlightPhase.COUNTDOWN.duration();
            case ORBIT_OPS -> FlightPhase.COUNTDOWN.duration() + FlightPhase.ASCENT.duration();
            default -> 0;
        };
        return Mth.clamp((before + getPhaseProgress(0) * phase.duration()) / total, 0f, 1f);
    }

    public boolean isRocketReady() {
        return phase == FlightPhase.IDLE && !getInventory().getStack(ROCKET).isEmpty();
    }

    // where a running transfer flight goes, or the current area
    public int getTransferTargetDx() {
        return phase != FlightPhase.IDLE && mission == Mission.TRANSFER ? transferDx : orbitDx;
    }

    public int getTransferTargetDz() {
        return phase != FlightPhase.IDLE && mission == Mission.TRANSFER ? transferDz : orbitDz;
    }

    // synced: chunks this satellite may go from its pad (0 = none)
    public int getTransferRange() {
        return transferRange;
    }

    // Where the satellite circles, in blocks from the pad centre (glides along during the fly-over)
    public Vec3 getOrbitAnchor(float partialTick) {
        double dx = orbitDx;
        double dz = orbitDz;
        if (arriving) {
            float t = smoothstep(getRelocationProgress(partialTick));
            dx = Mth.lerp(t, moveFromDx, orbitDx);
            dz = Mth.lerp(t, moveFromDz, orbitDz);
        }
        return new Vec3(dx * 16, 0, dz * 16);
    }

    private void setPhase(FlightPhase newPhase, long start) {
        phase = newPhase;
        phaseStart = start;
        setState(MachineState.EMPTY_TANK, false);
        setChanged();
        syncToClients();
    }

    private void tickFlight(ServerLevel serverLevel) {
        if (phase == FlightPhase.IDLE) {
            return;
        }

        long now = serverLevel.getGameTime();
        long elapsed = now - phaseStart;

        if (phase == FlightPhase.COUNTDOWN && elapsed % 20 == 0 && elapsed < phase.duration()) {
            serverLevel.playSound(null, getPadCenter(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5f, 1.4f);
        }
        if (phase == FlightPhase.ASCENT && elapsed < 30 && elapsed % 5 == 0) {
            scorchLaunchArea(serverLevel);
        }

        if (elapsed < phase.duration()) {
            return;
        }

        // from the scheduled end so an unloaded pad catches up
        long next = phaseStart + phase.duration();
        switch (phase) {
            case COUNTDOWN -> {
                serverLevel.playSound(null, getPadCenter(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3f, 0.5f);
                serverLevel.playSound(null, getPadCenter(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 4f, 0.5f);
                setPhase(FlightPhase.ASCENT, next);
            }
            case ASCENT -> setPhase(FlightPhase.ORBIT_OPS, next);
            case ORBIT_OPS -> {
                if (mission == Mission.DEPLOY) {
                    orbiting = payload;
                    payload = ItemStack.EMPTY;
                } else if (mission == Mission.RECOVER) {
                    payload = orbiting;
                    orbiting = ItemStack.EMPTY;
                    // the next one goes up over the pad again
                    if (orbitDx != 0 || orbitDz != 0) {
                        orbitDx = 0;
                        orbitDz = 0;
                        scanIndex = 0;
                        refreshIndex = 0;
                    }
                } else if (mission == Mission.RESUPPLY) {
                    if (SatelliteItem.isVoid(orbiting)) {
                        SatelliteItem.setDew(orbiting, SatelliteItem.getDew(orbiting) + cargoDew);
                    }
                    cargoDew = 0;
                } else {
                    startRelocation(serverLevel, next);
                }
                refreshSyncedItems();
                setPhase(FlightPhase.DESCENT, next);
            }
            case DESCENT -> land(serverLevel);
            default -> setPhase(FlightPhase.IDLE, now);
        }
    }

    private void land(ServerLevel serverLevel) {
        returnToSlot(ROCKET, flightRocket);
        returnToSlot(SATELLITE, payload);
        flightRocket = ItemStack.EMPTY;
        payload = ItemStack.EMPTY;
        rocketOnPad = !getInventory().getStack(ROCKET).isEmpty();
        refreshSyncedItems();

        serverLevel.playSound(null, getPadCenter(), SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.5f, 0.6f);
        serverLevel.playSound(null, getPadCenter(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.8f);
        setPhase(FlightPhase.IDLE, serverLevel.getGameTime());
    }

    private void returnToSlot(SingleResourceSlotKey<ItemResource> slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (getInventory().getStack(slot).isEmpty()) {
            getInventory().setStack(slot, stack);
        } else {
            // shouldn't happen, but don't lose items
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, stack);
        }
    }

    // Don't stand on the pad at liftoff
    private void scorchLaunchArea(ServerLevel serverLevel) {
        BlockPos center = getPadCenter();
        AABB area = new AABB(center.offset(-1, 1, -1).getCenter(), center.offset(1, 3, 1).getCenter()).inflate(0.5);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.igniteForSeconds(3);
        }
    }

    // block broken or replaced: what's on board drops, the item only keeps the fuel
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropFlightContents();
        markCenter(null);
        releaseSelfLoading();
    }

    public void dropFlightContents() {
        if (level == null) {
            return;
        }
        ItemStack rocketSlot = getInventory().getStack(ROCKET).copy();
        ItemStack satelliteSlot = getInventory().getStack(SATELLITE).copy();
        getInventory().setStack(ROCKET, ItemStack.EMPTY);
        getInventory().setStack(SATELLITE, ItemStack.EMPTY);
        for (ItemStack stack : new ItemStack[] { rocketSlot, satelliteSlot, flightRocket, payload, orbiting }) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, stack.copy());
            }
        }
        flightRocket = ItemStack.EMPTY;
        payload = ItemStack.EMPTY;
        orbiting = ItemStack.EMPTY;
    }

    private void refreshSyncedItems() {
        hasPayload = !payload.isEmpty();
        inOrbit = !orbiting.isEmpty();
        updateScanRadius();
        setChanged();
        syncToClients();
    }

    public FlightPhase getPhase() {
        return phase;
    }

    public Mission getMission() {
        return mission;
    }

    public boolean isFlying() {
        return phase != FlightPhase.IDLE;
    }

    public boolean hasSatelliteInOrbit() {
        return inOrbit;
    }

    public int getOrbitAltitude() {
        return orbitAltitude;
    }

    // endregion

    // region Uplink & scanning

    private void tickUplink() {
        UplinkStatus status;
        if (orbiting.isEmpty()) {
            status = UplinkStatus.NO_SATELLITE;
        } else {
            GroundRadarBlockEntity radar = getRadar();
            if (radar == null) {
                status = UplinkStatus.NO_RADAR;
            } else if (!radar.consumeUplinkEnergy()) {
                status = UplinkStatus.NO_POWER;
            } else {
                status = UplinkStatus.ONLINE;
            }
        }

        if (status != uplink) {
            uplink = status;
            syncToClients();
        }
        setState(QuarryStates.RADAR_NO_POWER, status == UplinkStatus.NO_POWER);
        setState(QuarryStates.SATELLITE_NO_DEW, isOutOfDew());

        // in case the controller vanished mid beam
        if (beamTarget != null && getReflectorProblem() != null) {
            setBeamTarget(null);
        }

        // no mapping while it flies over, and the Void Satellite maps no ground
        if (status == UplinkStatus.ONLINE && !arriving && !isVoidSatelliteUp() && ++scanTicks >= QuarryConfig.SCAN_TICKS_PER_CHUNK.get()) {
            scanTicks = 0;
            if (!isScanComplete()) {
                scanStep();
            } else {
                // all mapped, keep refreshing so the map stays current
                refreshIndex = (refreshIndex + 1) % getScanTotal();
                int[] offset = ScanCoverage.of(scanRadius).offset(refreshIndex);
                ChunkPos center = getOrbitChunk();
                recordScanImage(new ChunkPos(center.x() + offset[0], center.z() + offset[1]));
            }
        }
    }

    // Maps the next chunk. One fresh picture per step; chunks with a picture from an earlier visit fly by, new unloaded
    // ones are loaded for a moment (like a player walking past), given up after SCAN_LOAD_WAIT
    private void scanStep() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean advanced = false;
        for (int i = 0; i < KNOWN_PER_STEP && !isScanComplete(); i++) {
            ChunkPos chunk = getScanningChunk();
            if (chunk == null) {
                break;
            }
            LevelChunk loaded = serverLevel.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            if (loaded == null && scanImages.get(chunk) == null && scanWaitTicks < SCAN_LOAD_WAIT) {
                serverLevel.getChunkSource().addTicketWithRadius(ModTickets.SCAN.get(), chunk, 0);
                scanWaitTicks += QuarryConfig.SCAN_TICKS_PER_CHUNK.get();
                break;
            }
            if (loaded != null) {
                scanImages.put(chunk, MinimapSnapshot.captureChunk(serverLevel, loaded));
            }
            scanWaitTicks = 0;
            scanIndex++;
            advanced = true;
            // a known chunk (not loaded, old picture) costs nothing, go on
            if (loaded != null || scanImages.get(chunk) == null) {
                break;
            }
        }
        if (advanced) {
            setChanged();
            syncToClients();
        }
    }

    // Unloaded chunks are skipped (never load chunks for this), the refresh picks them up later
    private void recordScanImage(@Nullable ChunkPos chunk) {
        if (chunk != null && level instanceof ServerLevel serverLevel) {
            LevelChunk loaded = serverLevel.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            if (loaded != null && scanImages.put(chunk, MinimapSnapshot.captureChunk(serverLevel, loaded))) {
                setChanged();
            }
        }
    }

    public void updateScanImage(ChunkPos chunk) {
        if (isChunkScanned(chunk)) {
            recordScanImage(chunk);
        }
    }

    public byte @Nullable [] getScanImage(ChunkPos chunk) {
        return isChunkScanned(chunk) ? scanImages.get(chunk) : null;
    }

    public ScanImageStore getScanImages() {
        return scanImages;
    }

    public AsteroidField getAsteroids() {
        return asteroids;
    }

    // Debug: satellite straight into orbit
    public void debugPlaceInOrbit(ItemStack satellite) {
        orbiting = satellite.copy();
        refreshSyncedItems();
    }

    // Debug: satellite area moved without a flight
    public void debugSetOrbit(int dx, int dz) {
        orbitDx = dx;
        orbitDz = dz;
        arriving = false;
        setChanged();
        syncToClients();
    }

    // Debug: whole area mapped (no pictures)
    public void debugCompleteScan() {
        scanIndex = getScanTotal();
        setChanged();
        syncToClients();
    }

    public UplinkStatus getUplink() {
        return uplink;
    }

    public int getScannedChunks() {
        return scanIndex;
    }

    public int getScanTotal() {
        return ScanCoverage.of(scanRadius).total();
    }

    public boolean isScanComplete() {
        return scanIndex >= getScanTotal();
    }

    public boolean isChunkScanned(ChunkPos chunk) {
        ChunkPos center = getOrbitChunk();
        int index = ScanCoverage.of(scanRadius).indexOf(chunk.x() - center.x(), chunk.z() - center.z());
        // pictures from an earlier visit count right away
        return index >= 0 && (index < scanIndex || scanImages.get(chunk) != null);
    }

    @Nullable
    public ChunkPos getScanningChunk() {
        if (uplink != UplinkStatus.ONLINE || !inOrbit || arriving || isVoidSatelliteUp() || isScanComplete()) {
            return null;
        }
        int[] offset = ScanCoverage.of(scanRadius).offset(scanIndex);
        ChunkPos center = getOrbitChunk();
        return new ChunkPos(center.x() + offset[0], center.z() + offset[1]);
    }

    // endregion

    // region Reflecting (the obelisk's beam bounces off the satellite down onto the chunk)

    // Horizontal only, range = the controller tier's linkRange
    public boolean isInLinkRange(BlockPos pos, long range) {
        long dx = pos.getX() - getPadCenter().getX();
        long dz = pos.getZ() - getPadCenter().getZ();
        return dx * dx + dz * dz <= range * range;
    }

    public int getScanRadius() {
        return scanRadius;
    }

    // Server only
    @Nullable
    public QuarryTier getSatelliteTier() {
        return SatelliteItem.tierOf(orbiting);
    }

    public QuarryTier getDrawnSatelliteTier() {
        return drawnSatelliteTier;
    }

    // the satellite on the rocket, else the one in orbit, is the Void Satellite
    public boolean isDrawnVoid() {
        return drawnVoid;
    }

    // Client and server
    public boolean isVoidSatelliteUp() {
        return level != null && !level.isClientSide() ? SatelliteItem.isVoid(orbiting) : voidUp;
    }

    // A bigger satellite keeps mapping outwards, a smaller one keeps the inner rings
    private boolean updateScanRadius() {
        QuarryTier tier = getSatelliteTier();
        int range = orbiting.getItem() instanceof SatelliteItem satellite ? satellite.transferRange() : transferRange;
        boolean rangeChanged = range != transferRange;
        transferRange = range;
        int radius = tier != null ? tier.chunkRadius() : scanRadius;
        if (radius == scanRadius) {
            return rangeChanged;
        }
        scanRadius = radius;
        scanIndex = Math.min(scanIndex, ScanCoverage.of(scanRadius).total());
        return true;
    }

    public @Nullable MachineState getReflectorProblem() {
        // busy docking while the rocket catches it
        if (!inOrbit || (phase == FlightPhase.ORBIT_OPS && mission == Mission.RECOVER)) {
            return QuarryStates.NO_SATELLITE;
        }
        if (isRelocating()) {
            return QuarryStates.SATELLITE_MOVING;
        }
        return switch (uplink) {
            case ONLINE -> null;
            case NO_RADAR -> QuarryStates.NO_RADAR;
            case NO_POWER -> QuarryStates.RADAR_NO_POWER;
            default -> QuarryStates.SATELLITE_OFFLINE;
        };
    }

    @Nullable
    public BlockPos getBeamTarget() {
        return beamTarget;
    }

    public QuarryTier getBeamTier() {
        return beamTier;
    }

    // the crystal on the firing obelisk, picks the beam colours
    public CrystalGlow getBeamGlow() {
        return beamGlow;
    }

    public void setBeamTarget(@Nullable BlockPos target) {
        setBeamTarget(target, beamTier, beamGlow);
    }

    // tier = the firing controller's, picks the beam style
    public void setBeamTarget(@Nullable BlockPos target, QuarryTier tier, CrystalGlow glow) {
        if (Objects.equals(beamTarget, target) && beamTier == tier && beamGlow == glow) {
            return;
        }
        beamTarget = target == null ? null : target.immutable();
        beamTier = tier;
        beamGlow = glow;
        syncToClients();
    }

    public @Nullable Vec3 getMirrorWorldPos(float partialTick) {
        Vec3 mirror = getMirrorPos(partialTick);
        return mirror == null ? null : Vec3.atLowerCornerOf(getPadCenter()).add(mirror);
    }

    @Nullable
    public Vec3 getMirrorPos(float partialTick) {
        if (!inOrbit || phase == FlightPhase.ORBIT_OPS) {
            return null;
        }
        return getOrbitPos(partialTick).add(0, -REFLECTOR_MIRROR_DROP, 0);
    }

    // endregion

    // region Ticking

    @Override
    public void serverTick() {
        super.serverTick();

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!structureChecked || serverLevel.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            structureChecked = true;
            updateStructure();
        }

        tickFlight(serverLevel);
        tickArrival(serverLevel);
        tickUplink();
        updatePowerStats(serverLevel);
        if (serverLevel.getGameTime() % 10 == 0) {
            updateRegistry(serverLevel);
            tickSelfLoading(serverLevel);
        }
        if (serverLevel.getGameTime() % 10 == 0) {
            checkSpyglasses(serverLevel);
        }
    }

    // advancement: someone looks at the satellite in orbit through a spyglass
    private void checkSpyglasses(ServerLevel serverLevel) {
        Vec3 satellite = getSatellitePos(0);
        if (satellite == null || getSatelliteTransfer(0) < 1 || serverLevel.players().isEmpty()) {
            return;
        }
        ModTriggers.SPYGLASS_AT_SATELLITE.get().checkPlayers(serverLevel, Vec3.atLowerCornerOf(getPadCenter()).add(satellite));
    }

    @Override
    public boolean isActive() {
        return phase != FlightPhase.IDLE || uplink == UplinkStatus.ONLINE;
    }

    // Exhaust, smoke and sound
    @Override
    public void clientTick() {
        super.clientTick();
        relocationTrail();
        rocketEffects();
    }

    private void rocketEffects() {
        if (level == null || phase == FlightPhase.IDLE) {
            return;
        }

        Vec3 rocketRel = getRocketPos(0);
        if (rocketRel == null) {
            return;
        }

        RandomSource random = level.getRandom();
        BlockPos center = getPadCenter();
        Vec3 nozzle = Vec3.atLowerCornerOf(center).add(rocketRel);
        float progress = getPhaseProgress(0);
        boolean onGround = rocketRel.y - LAUNCH_POINT.y < 8;
        Vec3 down = getRocketUp(0).scale(-1);

        if (isEngineBurning(0)) {
            for (int i = 0; i < 3; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, nozzle.x + down.x * 0.2 + random.nextGaussian() * 0.15,
                    nozzle.y + down.y * 0.2, nozzle.z + down.z * 0.2 + random.nextGaussian() * 0.15,
                    down.x * 0.6 + random.nextGaussian() * 0.03, down.y * 0.6, down.z * 0.6 + random.nextGaussian() * 0.03);
            }
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, nozzle.x + down.x * 0.6, nozzle.y + down.y * 0.6, nozzle.z + down.z * 0.6,
                down.x * 0.25 + random.nextGaussian() * 0.05, down.y * 0.25, down.z * 0.25 + random.nextGaussian() * 0.05);

            if (phase == FlightPhase.ASCENT && level.getGameTime() % 2 == 0) {
                // contrail
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, nozzle.x + down.x, nozzle.y + down.y, nozzle.z + down.z,
                    random.nextGaussian() * 0.01, 0.01, random.nextGaussian() * 0.01);
            }

            if (onGround) {
                // exhaust spreading over the pad
                for (int i = 0; i < 4; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    double speed = 0.25 + random.nextDouble() * 0.25;
                    level.addAlwaysVisibleParticle(random.nextBoolean() ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE, true,
                        center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5,
                        Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed);
                }
            }

            if (level.getGameTime() % 4 == 0) {
                level.playLocalSound(nozzle.x, nozzle.y, nozzle.z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS,
                    onGround ? 2.5f : 4f, 0.45f + random.nextFloat() * 0.1f, false);
            }
        } else if (phase == FlightPhase.COUNTDOWN && progress > 0.4f) {
            // venting before ignition
            level.addParticle(ParticleTypes.CLOUD, nozzle.x + random.nextGaussian() * 0.4, nozzle.y + 0.3,
                nozzle.z + random.nextGaussian() * 0.4, 0, 0.05, 0);
        }
    }

    // client copy of a far pad (RemoteSatellites): only the effects up at the satellite and a rocket out there
    public void remoteEffects() {
        relocationTrail();
        if (isRocketAway()) {
            rocketEffects();
        }
    }

    // Players around the satellite may not have this pad's chunk: SatelliteRegistry sends them its data (RemoteSatellites)
    private void updateRegistry(ServerLevel serverLevel) {
        Vec3 satellite = inOrbit || arriving || isRocketAway() ? getTrackingTarget(0) : null;
        if (satellite == null) {
            SatelliteRegistry.remove(serverLevel, worldPosition);
            return;
        }
        SatelliteRegistry.update(serverLevel, this, Vec3.atLowerCornerOf(getPadCenter()).add(satellite));
    }

    // Keeps its own chunk loaded while the satellite moves or maps a far area, nobody may be around the base
    private void tickSelfLoading(ServerLevel serverLevel) {
        boolean wanted = wantsSelfLoad();
        if (wanted != selfLoaded) {
            ChunkPos chunk = ChunkPos.containing(worldPosition);
            EnderLaserQuarry.CHUNK_TICKETS.forceChunk(serverLevel, worldPosition, chunk.x(), chunk.z(), wanted, false);
            selfLoaded = wanted;
        }
    }

    // also asked on world load (EnderLaserQuarry.CHUNK_TICKETS)
    public boolean wantsSelfLoad() {
        return isRelocating() || isRocketAway() || (inOrbit && (orbitDx != 0 || orbitDz != 0) && !isScanComplete());
    }

    // when the block goes
    public void releaseSelfLoading() {
        if (selfLoaded && level instanceof ServerLevel serverLevel) {
            ChunkPos chunk = ChunkPos.containing(worldPosition);
            EnderLaserQuarry.CHUNK_TICKETS.forceChunk(serverLevel, worldPosition, chunk.x(), chunk.z(), false, false);
            selfLoaded = false;
        }
        if (level != null) {
            SatelliteRegistry.remove(level, worldPosition);
        }
    }

    // orbit transfer: sparkles behind the satellite, a soft burn sound
    private void relocationTrail() {
        if (level == null || !arriving) {
            return;
        }
        Vec3 satellite = getSatellitePos(0);
        if (satellite == null) {
            return;
        }
        Vec3 world = Vec3.atLowerCornerOf(getPadCenter()).add(satellite);
        Vec3 back = getRelocationDirection().scale(-2.5);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 2; i++) {
            level.addAlwaysVisibleParticle(random.nextInt(3) == 0 ? ParticleTypes.FIREWORK : ParticleTypes.END_ROD, true,
                world.x + back.x + random.nextGaussian() * 0.3, world.y + random.nextGaussian() * 0.3, world.z + back.z + random.nextGaussian() * 0.3,
                back.x * 0.02, -0.01, back.z * 0.02);
        }
        if (level.getGameTime() % 12 == 0) {
            level.playLocalSound(world.x, world.y, world.z, SoundEvents.BLAZE_BURN, SoundSource.BLOCKS, 3f, 1.6f, false);
        }
    }

    @Nullable
    public Vec3 getStationTarget() {
        if (stationPos == null || level == null) {
            return null;
        }
        return QuarryControllerBlockEntity.antennaTip(stationPos, level.getBlockState(stationPos));
    }

    // tip of the antenna mast on top of the controller, where the link packets start and land (same mast as the quarry controller's)
    public Vec3 getAntennaTip() {
        return QuarryControllerBlockEntity.antennaTip(worldPosition, getBlockState());
    }

    // 0..1 during the link animation, -1 otherwise
    public float getLinkFlash(float partialTick) {
        if (level == null) {
            return -1;
        }
        float age = level.getGameTime() - linkFlashStart + partialTick;
        return age >= 0 && age < LINK_FLASH_TICKS ? age / LINK_FLASH_TICKS : -1;
    }

    // endregion

    // region Flight path (shared by server, renderers and radar)

    public float getPhaseProgress(float partialTick) {
        if (level == null || phase == FlightPhase.IDLE) {
            return 0;
        }
        return Mth.clamp((level.getGameTime() - phaseStart + partialTick) / phase.duration(), 0f, 1f);
    }

    public boolean isEngineBurning(float partialTick) {
        float progress = getPhaseProgress(partialTick);
        return switch (phase) {
            case COUNTDOWN -> progress > 0.8f;
            case ASCENT -> true;
            case DESCENT -> progress > 0.55f;
            default -> false;
        };
    }

    public float getThrottle(float partialTick) {
        float progress = getPhaseProgress(partialTick);
        return switch (phase) {
            case COUNTDOWN -> progress > 0.8f ? (progress - 0.8f) / 0.2f * 0.7f : 0;
            case ASCENT -> 1f - progress * 0.4f;
            case DESCENT -> progress > 0.55f ? 0.8f : 0;
            default -> 0;
        };
    }

    public double getHoverAltitude() {
        return orbitAltitude - HOVER_BELOW_ORBIT;
    }

    // Nozzle position, null = nothing to draw
    @Nullable
    public Vec3 getRocketPos(float partialTick) {
        float progress = getPhaseProgress(partialTick);
        double hover = getHoverAltitude();
        return switch (phase) {
            case IDLE -> rocketOnPad ? LAUNCH_POINT : null;
            case COUNTDOWN -> {
                float shake = progress > 0.8f ? 0.03f : 0;
                float time = level == null ? 0 : level.getGameTime() + partialTick;
                yield LAUNCH_POINT.add(Mth.sin(time * 2.1f) * shake, 0, Mth.cos(time * 2.7f) * shake);
            }
            case ASCENT, ORBIT_OPS, DESCENT -> rocketPath(phase, progress);
        };
    }

    // In flight: straight up, then over to the satellite's area (flightDx / flightDz) and back down at the end
    private Vec3 rocketPath(FlightPhase flight, float progress) {
        double hover = getHoverAltitude();
        Vec3 away = new Vec3(flightDx * 16, 0, flightDz * 16);
        return switch (flight) {
            case ASCENT -> LAUNCH_POINT.add(away.scale(smoothstep(Mth.clamp((progress - 0.2f) / 0.8f, 0, 1))))
                .add(0, hover * smoothstep(progress), 0);
            case DESCENT -> LAUNCH_POINT.add(away.scale(1 - smoothstep(Mth.clamp(progress / 0.75f, 0, 1))))
                .add(0, hover * (1 - smoothstep(progress)), 0);
            default -> LAUNCH_POINT.add(away).add(0, hover + 0.15 * Mth.sin(progress * Mth.TWO_PI), 0);
        };
    }

    // Which way the rocket's nose points: leans into the climb on the way out, engine first on the way back
    public Vec3 getRocketUp(float partialTick) {
        if ((phase != FlightPhase.ASCENT && phase != FlightPhase.DESCENT) || (flightDx == 0 && flightDz == 0)) {
            return UP;
        }
        float progress = getPhaseProgress(partialTick);
        Vec3 velocity = rocketPath(phase, Math.min(1, progress + 0.01f)).subtract(rocketPath(phase, Math.max(0, progress - 0.01f)));
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal < 1.0E-4) {
            return UP;
        }
        // upright again before docking with the satellite
        float settle = phase == FlightPhase.ASCENT ? Mth.clamp((1 - progress) / 0.15f, 0, 1) : Mth.clamp(progress / 0.15f, 0, 1);
        double tilt = Math.min(Math.atan2(horizontal, Math.abs(velocity.y)), MAX_ROCKET_TILT) * smoothstep(settle);
        double side = Math.sin(tilt) / horizontal * (phase == FlightPhase.ASCENT ? 1 : -1);
        return new Vec3(velocity.x * side, Math.cos(tilt), velocity.z * side);
    }

    // in flight away from the pad (orbit transfer, or recovering a moved satellite)
    public boolean isRocketAway() {
        return phase != FlightPhase.IDLE && phase != FlightPhase.COUNTDOWN && (flightDx != 0 || flightDz != 0);
    }

    public Vec3 getOrbitPos(float partialTick) {
        double angle = getOrbitAngle(partialTick);
        return LAUNCH_POINT.add(getOrbitAnchor(partialTick)).add(Math.cos(angle) * ORBIT_RADIUS, orbitAltitude, Math.sin(angle) * ORBIT_RADIUS);
    }

    public double getOrbitAngle(float partialTick) {
        return level == null ? 0 : (level.getGameTime() + partialTick) * ORBIT_SPEED;
    }

    @Nullable
    public Vec3 getSatellitePos(float partialTick) {
        // a transfer or resupply doesn't dock: the rocket only brings it fuel / Dew
        if (phase == FlightPhase.ORBIT_OPS && !mission.refuelsInOrbit()) {
            Vec3 rocket = getRocketPos(partialTick);
            Vec3 dock = rocket.add(0, PAYLOAD_DOCK_HEIGHT, 0);
            return dock.lerp(getOrbitPos(partialTick), getSatelliteTransfer(partialTick));
        }
        return inOrbit ? getOrbitPos(partialTick) : null;
    }

    // 0 = docked, 1 = in orbit
    public float getSatelliteTransfer(float partialTick) {
        if (phase != FlightPhase.ORBIT_OPS || mission.refuelsInOrbit()) {
            return inOrbit ? 1 : 0;
        }
        float move = smoothstep(Mth.clamp((getPhaseProgress(partialTick) - 0.25f) / 0.5f, 0, 1));
        return mission == Mission.DEPLOY ? move : 1 - move;
    }

    public float getFairingOpen(float partialTick) {
        if (phase != FlightPhase.ORBIT_OPS) {
            return 0;
        }
        float progress = getPhaseProgress(partialTick);
        if (progress < 0.2f) {
            return smoothstep(progress / 0.2f);
        }
        return progress > 0.8f ? smoothstep((1 - progress) / 0.2f) : 1;
    }

    // For the radar: the satellite, else the rocket
    @Nullable
    public Vec3 getTrackingTarget(float partialTick) {
        Vec3 satellite = getSatellitePos(partialTick);
        if (satellite != null) {
            return satellite;
        }
        return phase != FlightPhase.IDLE ? getRocketPos(partialTick) : null;
    }

    private static float smoothstep(float t) {
        return t * t * (3 - 2 * t);
    }

    // endregion

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LaunchPadMenu(containerId, playerInventory, this);
    }

    private void setState(MachineState state, boolean wanted) {
        // every update sends a block update, so only on change
        if (wanted != getMachineStates().contains(state)) {
            updateMachineState(state, wanted);
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // region Serialization

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putChild(FLUIDS, fluidStorage);
        tag.putChild(ENERGY, energy);
        tag.putBoolean(FULL_SAVE, true);
        if (!flightRocket.isEmpty()) {
            tag.store(FLIGHT_ROCKET, ItemStack.CODEC, flightRocket);
        }
        if (!payload.isEmpty()) {
            tag.store(PAYLOAD, ItemStack.CODEC, payload);
        }
        tag.putInt(CARGO_DEW, cargoDew);
        if (!orbiting.isEmpty()) {
            tag.store(ORBITING, ItemStack.CODEC, orbiting);
        }
        if (!scanImages.isEmpty()) {
            tag.store(SCAN_IMAGES, CompoundTag.CODEC, scanImages.save());
        }
        // always saved so asteroid ids never repeat
        tag.store(ASTEROIDS, CompoundTag.CODEC, asteroids.save());
        tag.storeNullable(MARKED_CENTER, BlockPos.CODEC, markedCenter);
    }

    @Override
    protected void saveAdditionalSynced(ValueOutput tag) {
        super.saveAdditionalSynced(tag);
        tag.putInt(PHASE, phase.ordinal());
        tag.putInt(MISSION, mission.ordinal());
        tag.putLong(PHASE_START, phaseStart);
        tag.putBoolean(HAS_PAYLOAD, hasPayload);
        tag.putBoolean(IN_ORBIT, inOrbit);
        tag.putBoolean(ROCKET_ON_PAD, rocketOnPad);
        tag.putInt(ALTITUDE, orbitAltitude);
        tag.putBoolean(PLATING_OK, platingOk);
        tag.putBoolean(SKY_OK, skyOk);
        tag.storeNullable(STATION_POS, BlockPos.CODEC, stationPos);
        tag.storeNullable(RADAR_POS, BlockPos.CODEC, radarPos);
        tag.putInt(UPLINK, uplink.ordinal());
        tag.putInt(SCAN_INDEX, scanIndex);
        tag.putInt(SCAN_RADIUS, scanRadius);
        QuarryTier drawn = SatelliteItem.tierOf(!payload.isEmpty() ? payload : orbiting);
        tag.putInt(DRAWN_TIER, drawn == null ? 0 : drawn.ordinal());
        tag.putBoolean(DRAWN_VOID, SatelliteItem.isVoid(!payload.isEmpty() ? payload : orbiting));
        tag.putBoolean(VOID_UP, SatelliteItem.isVoid(orbiting));
        tag.putInt(SATELLITE_DEW, SatelliteItem.getDew(orbiting));
        tag.storeNullable(BEAM_TARGET, BlockPos.CODEC, beamTarget);
        tag.putInt(BEAM_TIER, beamTier.ordinal());
        tag.putInt(BEAM_GLOW, beamGlow.ordinal());
        tag.storeNullable(PAD_CENTER, BlockPos.CODEC, padCenter);
        tag.putLong(LINK_FLASH, linkFlashStart);
        tag.putBoolean(ANTENNA_FEEDING, antennaFeeding);
        tag.putBoolean(ANTENNA_POWERED, antennaPowered);
        tag.putInt(ORBIT_DX, orbitDx);
        tag.putInt(ORBIT_DZ, orbitDz);
        tag.putInt(MOVE_FROM_DX, moveFromDx);
        tag.putInt(MOVE_FROM_DZ, moveFromDz);
        tag.putLong(MOVE_START, moveStart);
        tag.putInt(MOVE_TICKS, moveTicks);
        tag.putBoolean(ARRIVING, arriving);
        tag.putInt(TRANSFER_DX, transferDx);
        tag.putInt(TRANSFER_DZ, transferDz);
        tag.putInt(TRANSFER_RANGE, transferRange);
        tag.putInt(FLIGHT_DX, flightDx);
        tag.putInt(FLIGHT_DZ, flightDz);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        tag.readChild(FLUIDS, fluidStorage);
        tag.readChild(ENERGY, energy);

        phase = FlightPhase.byOrdinal(tag.getIntOr(PHASE, 0));
        mission = Mission.byOrdinal(tag.getIntOr(MISSION, 0));
        phaseStart = tag.getLongOr(PHASE_START, 0L);
        hasPayload = tag.getBooleanOr(HAS_PAYLOAD, false);
        inOrbit = tag.getBooleanOr(IN_ORBIT, false);
        rocketOnPad = tag.getBooleanOr(ROCKET_ON_PAD, false);
        orbitAltitude = tag.getIntOr(ALTITUDE, orbitAltitude);
        platingOk = tag.getBooleanOr(PLATING_OK, false);
        skyOk = tag.getBooleanOr(SKY_OK, false);
        stationPos = tag.read(STATION_POS, BlockPos.CODEC).orElse(null);
        radarPos = tag.read(RADAR_POS, BlockPos.CODEC).orElse(null);
        uplink = UplinkStatus.byOrdinal(tag.getIntOr(UPLINK, 0));
        scanIndex = tag.getIntOr(SCAN_INDEX, 0);
        scanRadius = tag.getIntOr(SCAN_RADIUS, scanRadius);
        drawnSatelliteTier = QuarryTier.values()[Math.floorMod(tag.getIntOr(DRAWN_TIER, 0), QuarryTier.values().length)];
        drawnVoid = tag.getBooleanOr(DRAWN_VOID, false);
        voidUp = tag.getBooleanOr(VOID_UP, false);
        satelliteDew = tag.getIntOr(SATELLITE_DEW, 0);
        beamTarget = tag.read(BEAM_TARGET, BlockPos.CODEC).orElse(null);
        beamTier = QuarryTier.values()[Math.floorMod(tag.getIntOr(BEAM_TIER, 0), QuarryTier.values().length)];
        beamGlow = CrystalGlow.values()[Math.floorMod(tag.getIntOr(BEAM_GLOW, 1), CrystalGlow.values().length)];
        padCenter = tag.read(PAD_CENTER, BlockPos.CODEC).orElse(null);
        linkFlashStart = tag.getLongOr(LINK_FLASH, NEVER);
        antennaFeeding = tag.getBooleanOr(ANTENNA_FEEDING, false);
        antennaPowered = tag.getBooleanOr(ANTENNA_POWERED, false);
        orbitDx = tag.getIntOr(ORBIT_DX, 0);
        orbitDz = tag.getIntOr(ORBIT_DZ, 0);
        moveFromDx = tag.getIntOr(MOVE_FROM_DX, 0);
        moveFromDz = tag.getIntOr(MOVE_FROM_DZ, 0);
        moveStart = tag.getLongOr(MOVE_START, 0L);
        moveTicks = tag.getIntOr(MOVE_TICKS, 0);
        arriving = tag.getBooleanOr(ARRIVING, false);
        transferDx = tag.getIntOr(TRANSFER_DX, 0);
        transferDz = tag.getIntOr(TRANSFER_DZ, 0);
        transferRange = tag.getIntOr(TRANSFER_RANGE, 0);
        flightDx = tag.getIntOr(FLIGHT_DX, 0);
        flightDz = tag.getIntOr(FLIGHT_DZ, 0);
        tag.read(MARKED_CENTER, BlockPos.CODEC).ifPresent(pos -> markedCenter = pos);

        tag.read(SCAN_IMAGES, CompoundTag.CODEC).ifPresent(scanImages::load);
        tag.read(ASTEROIDS, CompoundTag.CODEC).ifPresent(asteroids::load);

        // full save only
        if (tag.getBooleanOr(FULL_SAVE, false)) {
            flightRocket = tag.read(FLIGHT_ROCKET, ItemStack.CODEC).orElse(ItemStack.EMPTY);
            payload = tag.read(PAYLOAD, ItemStack.CODEC).orElse(ItemStack.EMPTY);
            cargoDew = tag.getIntOr(CARGO_DEW, 0);
            orbiting = tag.read(ORBITING, ItemStack.CODEC).orElse(ItemStack.EMPTY);
            hasPayload = !payload.isEmpty();
            inOrbit = !orbiting.isEmpty();
        }
    }

    // Keep the fuel in the item

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent stored = components.get(EIODataComponents.ITEM_FLUID_CONTENT);
        if (stored != null) {
            fuelTank.setFluid(stored.copy());
        }
        SimpleFluidContent dew = components.get(ModDataComponents.PAD_DEW.get());
        if (dew != null) {
            dewTank.setFluid(dew.copy());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        // rocket and satellite drop on the ground instead
        components.set(DataComponents.CONTAINER, null);
        var tank = fuelTank;
        if (!tank.isEmpty()) {
            components.set(EIODataComponents.ITEM_FLUID_CONTENT, SimpleFluidContent.copyOf(tank.getFluid()));
        }
        if (!dewTank.isEmpty()) {
            components.set(ModDataComponents.PAD_DEW.get(), SimpleFluidContent.copyOf(dewTank.getFluid()));
        }
    }

    // endregion

    // receive only, the radar spends it
    public class PadEnergy extends SimpleEnergyHandler {
        PadEnergy() {
            super(QuarryConfig.RADAR_CAPACITY.get(), QuarryConfig.RADAR_MAX_INPUT.get(), 0);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }

        public int getEnergyStored() {
            return energy;
        }

        public int getMaxEnergyStored() {
            return capacity;
        }

        public int receiveEnergy(int amount, boolean simulate) {
            return Transfers.insert(this, amount, simulate);
        }

        public boolean consume(int amount) {
            if (energy < amount) {
                return false;
            }
            if (amount > 0) {
                set(energy - amount);
                radarUsedSum += amount;
            }
            return true;
        }

        // past the port input limit, the antenna has its own
        int receiveAntenna(int amount) {
            int taken = Math.min(amount, capacity - energy);
            if (taken > 0) {
                set(energy + taken);
            }
            return taken;
        }
    }
}

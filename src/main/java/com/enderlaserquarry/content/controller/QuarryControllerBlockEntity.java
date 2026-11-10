package com.enderlaserquarry.content.controller;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.LinearScalable;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.block.entity.MultiConfigurable;
import com.enderio.core.common.storage.ExternalResourceStorageView;
import com.enderio.core.common.storage.FluidStorage;
import com.enderio.core.common.storage.ItemStorage;
import com.enderio.core.common.storage.layout.FluidStorageLayout;
import com.enderio.core.common.storage.layout.ItemStorageLayout;
import com.enderio.core.common.storage.layout.SimpleSlotAccessRules;
import com.enderio.core.common.storage.layout.SlotTemplates;
import com.enderio.core.common.storage.slot.MultiResourceSlotKey;
import com.enderio.core.common.storage.slot.SingleResourceSlotKey;
import com.enderio.enderio.foundation.inventory.MachineSlotTemplates;
import com.enderlaserquarry.content.Tank;
import com.enderlaserquarry.content.Transfers;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.fluid.FluidItemInteractive;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.common.Tags;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.QuarryStates;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.content.asteroid.Asteroid;
import com.enderlaserquarry.content.asteroid.AsteroidComposition;
import com.enderlaserquarry.content.asteroid.AsteroidField;
import com.enderlaserquarry.content.asteroid.sampling.BiomeSampling;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.obelisk.CrystalGlow;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import com.enderlaserquarry.content.radar.GroundRadarBlockEntity;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The quarry controller. Sits on the quarry floor's ring, fires through the obelisk up to the satellite and mines the
 * queued chunks.
 */
public class QuarryControllerBlockEntity extends PoweredMachineBlockEntity implements MultiConfigurable, FluidItemInteractive {
    // Any capacitor works, values come from the config
    // buffer = bufferTicks of intake, both times level squared; own use times level
    private static final QuadraticScalable ENERGY_CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
        () -> (int) Math.min((long) QuarryConfig.CONTROLLER_ENERGY_INTAKE.get() * QuarryConfig.CONTROLLER_BUFFER_TICKS.get(), Integer.MAX_VALUE / 64));
    private static final LinearScalable ENERGY_USAGE = new LinearScalable(CapacitorModifier.ENERGY_USE, QuarryConfig.CONTROLLER_USAGE::get);
    // a port or the intake hit its cap within this many ticks
    private static final int LIMIT_LINGER = 10;
    private static final int USAGE_WINDOW = 20;

    public static final int OUTPUT_SLOTS = 12;
    public static final MultiResourceSlotKey<ItemResource> OUTPUT = new MultiResourceSlotKey<>(OUTPUT_SLOTS);
    public static final SingleResourceSlotKey<ItemResource> FILTER = new SingleResourceSlotKey<>();
    public static final SingleResourceSlotKey<ItemResource> CAPACITOR = new SingleResourceSlotKey<>();
    public static final SingleResourceSlotKey<FluidResource> COOLANT = new SingleResourceSlotKey<>();
    public static final SingleResourceSlotKey<FluidResource> XP = new SingleResourceSlotKey<>();
    public static final TagKey<Fluid> QUARRY_COOLANT = TagKey.create(Registries.FLUID, EnderLaserQuarry.rl("quarry_coolant"));

    // Per tick limits
    private static final int SCAN_BUDGET = 512;
    private static final int WALL_SCAN_BUDGET = 2048;
    // box select guard against odd packets (the map is 33x33 at most)
    private static final int MAX_BOX_CHUNKS = 4096;
    private static final int LIQUID_BUDGET = 256;
    private static final int BEAM_UPDATE_INTERVAL = 4;
    private static final int PARTICLES_PER_TICK = 2;
    // How far the void mining beam goes past the satellite
    private static final int SPACE_BEAM_LENGTH = 400;

    // findNextTarget ran out of budget
    private static final BlockPos SCANNING = new BlockPos(0, Integer.MIN_VALUE, 0);
    private static final BlockPos CLAIMED = new BlockPos(0, Integer.MIN_VALUE + 1, 0);

    private static final List<MachineState> PROBLEM_STATES = Stream.concat(Stream.of(QuarryStates.NOT_IN_QUARRY, QuarryStates.NO_LAUNCH_PAD,
        QuarryStates.PAD_OUT_OF_RANGE, QuarryStates.NO_SATELLITE, QuarryStates.SATELLITE_OFFLINE, QuarryStates.SATELLITE_MOVING, QuarryStates.OBELISK_NO_SKY,
        QuarryStates.OBELISK_INCOMPLETE, QuarryStates.NO_CRYSTAL, QuarryStates.CRYSTAL_BROKEN, QuarryStates.CRYSTAL_TIER_TOO_LOW, QuarryStates.NO_DISHES, QuarryStates.NO_RADAR, QuarryStates.RADAR_NO_POWER, QuarryStates.NO_CONTROLLER,
        QuarryStates.TARGET_OUT_OF_RANGE, QuarryStates.NO_TARGET, QuarryStates.OUTPUT_LIMITED, QuarryStates.INTAKE_LIMITED, QuarryStates.FINISHED, QuarryStates.POWERING_UP,
        QuarryStates.VOID_NEEDS_ENDER, QuarryStates.ASTEROID_UNMEASURED,
        MachineState.NO_CAPACITOR, MachineState.NO_POWER, MachineState.FULL_OUTPUT),
        Stream.concat(QuarryStates.allNeedsSatellite().stream(), QuarryStates.allQuarryNeedsPlating().stream())).toList();

    private static final String OBELISK_POS = "ObeliskPos";
    private static final String PAD_POS = "PadPos";
    private static final String FLUIDS = "Fluids";
    private static final long NEVER_LIMITED = Long.MIN_VALUE / 2;
    private static final int OUTPUT_RETRY_TICKS = 20;
    // antenna in the middle of the top, tip LED centre
    private static final double ANTENNA_TIP_Y = 24.75 / 16;
    private static final String RUNNING = "Running";
    private static final String CURSOR_Y = "CursorY";
    private static final String CURSOR_INDEX = "CursorIndex";
    private static final String TOP_RESOLVED = "TopResolved";
    private static final String WALL_DONE = "WallDone";
    private static final String WALL_CHUNK_INDEX = "WallChunkIndex";
    private static final String ENERGY_PROGRESS = "EnergyProgress";
    private static final String BLOCKS_MINED = "BlocksMined";
    private static final String VOID_WEAR = "VoidWear";
    private static final String HEAT = "Heat";
    private static final String DEPTH_MODE = "DepthMode";
    private static final String FLOOR_Y = "FloorY";
    private static final String ASTEROID_QUEUE = "AsteroidQueue";
    private static final String SPACE_SCAN = "SpaceScan";
    private static final String ANALYZE_ID = "AnalyzeId";
    private static final String ANALYZE_TICKS = "AnalyzeTicks";
    private static final String TICKET_CHUNK = "TicketChunk";
    private static final String BASE_TICKETS = "BaseTickets";

    @Nullable
    private BlockPos obeliskPos;
    @Nullable
    private BlockPos padPos;
    private final ChunkQueue queue = new ChunkQueue();
    private boolean running;

    // Cursor: layer + index in the layer (x + z * 16, or the wall column while walling)
    private int cursorY;
    private int cursorIndex;
    private boolean topResolved;
    // First the liquid walls around all queued chunks, then mining
    private boolean wallDone;
    private int wallChunkIndex;
    private int energyProgress;
    private int blocksMined;
    private int voidWearBlocks;
    // Laser energy not yet cooled away
    private int heat;
    private DepthMode depthMode = DepthMode.BEDROCK;
    // Ender only, null = config default
    @Nullable
    private Integer floorY;
    // Void crystal: queued asteroid ids and the running space scan
    private final List<Integer> asteroidQueue = new ArrayList<>();
    private int spaceScanTicks;
    // asteroid being analyzed, -1 = none
    private int analyzeId = -1;
    private int analyzeTicks;

    private final FluidStorage fluidStorage;
    private final ResourceHandler<FluidResource> externalFluids;
    private final Tank coolantTank;
    private final Tank xpTank;

    // Not saved
    @Nullable
    private MiningFloor floor;
    // Current asteroid vein
    @Nullable
    private BlockState pendingVoidBlock;
    private int voidVeinLeft;
    private boolean scanStalled;
    private boolean analyzeStalled;
    private float analyzeSampleProgress;
    @Nullable
    private ChunkPos ticketedChunk;
    // Chunks kept loaded for the base, saved so they survive a restart
    private final Set<ChunkPos> baseTickets = new HashSet<>();
    private int problemTicks;
    @Nullable
    private MachineState currentProblem;
    // Set when our chunk unloads, see setRemoved
    private boolean chunkUnloading;
    @Nullable
    private BlockPos beamingPad;
    private long lastBeamUpdate;
    private long particleTick;
    private long lastTickNanos;
    private int particlesThisTick;
    // Usage for the GUI, averaged over USAGE_WINDOW
    private int ownUsedSum;
    private int ownUsage;
    private long liquidsAndBlocksWorn;
    private long liquidsDeleted;
    private long intakeTick = NEVER_LIMITED;
    private int intakeLeft;
    private long outputLimitedTick = NEVER_LIMITED;
    private long intakeLimitedTick = NEVER_LIMITED;
    private final IntakeStorage portEnergy = new IntakeStorage();
    @Nullable
    private FakePlayer claimPlayer;
    @Nullable
    private UUID claimPlayerOwner;
    private int claimSkipped;
    private final ClaimCache breakClaims = new ClaimCache();
    private final ClaimCache wallClaims = new ClaimCache();
    @Nullable
    private Boolean debugPerBlockClaims;
    // output full: wait for a slot to change (or OUTPUT_RETRY_TICKS) before rolling drops again
    private long outputFullTick = NEVER_LIMITED;
    private long outputFullSignature;
    private boolean outputBlocked;
    private int outputRetries;

    public QuarryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY_CONTROLLER.get(), pos, state, false, CapacitorSupport.REQUIRED, CAPACITOR, EnergyIOMode.Input,
            ENERGY_CAPACITY, ENERGY_USAGE);
        fluidStorage = new FluidStorage(tankLayout(getTier())) {
            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                super.onContentsChanged(index, previousContents);
                setChanged();
            }
        };
        externalFluids = new ExternalResourceStorageView<>(fluidStorage);
        coolantTank = new Tank(fluidStorage, COOLANT);
        xpTank = new Tank(fluidStorage, XP);
    }

    // region Power

    public int getOwnUsage() {
        return ownUsage;
    }

    private boolean payOwnUsage() {
        var buffer = getEnergyStorage();
        int usage = getMaxEnergyUse();
        if (buffer.getAmountAsInt() < usage) {
            return false;
        }
        buffer.set(buffer.getAmountAsInt() - usage);
        ownUsedSum += usage;
        return true;
    }

    // region Capacitor limits

    // Ender IO capacitor level (Basic 1, Double-Layer 2, Octadic 3, Endergy up to 6), 0 without one
    public float getCapacitorLevel() {
        return isCapacitorInstalled() ? getCapacitorData().getModifier(CapacitorModifier.ENERGY_USE) : 0;
    }

    private int squared(int base) {
        float level = getCapacitorLevel();
        return (int) Math.min(Integer.MAX_VALUE / 8, Math.round((double) base * level * level));
    }

    // items per tick one quarry port may take out
    public int getPortItemRate() {
        return squared(QuarryConfig.CONTROLLER_OUTPUT_PER_PORT.get());
    }

    // mB per tick one quarry port may fill in
    public int getPortFluidRate() {
        return squared(QuarryConfig.CONTROLLER_FLUID_PER_PORT.get());
    }

    // through all ports together
    public int getEnergyIntake() {
        return squared(QuarryConfig.CONTROLLER_ENERGY_INTAKE.get());
    }

    public void onPortOutputLimited() {
        if (level != null) {
            outputLimitedTick = level.getGameTime();
        }
    }

    public boolean isOutputLimited() {
        return recently(outputLimitedTick);
    }

    private boolean recently(long tick) {
        return level != null && level.getGameTime() - tick <= LIMIT_LINGER;
    }

    // what the ports hand on: the buffer, capped at the intake per tick
    public EnergyHandler getPortEnergyStorage() {
        return portEnergy;
    }

    private class IntakeStorage extends SnapshotJournal<Integer> implements EnergyHandler {
        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (level == null) {
                return 0;
            }
            long now = level.getGameTime();
            if (intakeTick != now) {
                intakeTick = now;
                intakeLeft = getEnergyIntake();
            }
            int offered = Math.min(amount, intakeLeft);
            var buffer = getEnergyStorage();
            int taken = offered <= 0 ? 0 : buffer.insert(offered, transaction);
            if (taken > 0) {
                updateSnapshots(transaction);
                intakeLeft -= taken;
            }
            // more was offered than the capacitor lets in, while the buffer still had room
            if (amount > offered && taken == offered && buffer.getAmountAsLong() < buffer.getCapacityAsLong()) {
                intakeLimitedTick = now;
            }
            return taken;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long getAmountAsLong() {
            return getEnergyStorage().getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return getEnergyStorage().getCapacityAsLong();
        }

        @Override
        protected Integer createSnapshot() {
            return intakeLeft;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            intakeLeft = snapshot;
        }
    }

    // endregion

    private void updateUsage() {
        if (level.getGameTime() % USAGE_WINDOW == 0) {
            ownUsage = ownUsedSum / USAGE_WINDOW;
            ownUsedSum = 0;
        }
    }

    // endregion

    // region Coolant

    // Fill only from outside: a fill goes to the first tank that takes the fluid (water -> coolant, liquid XP -> XP)
    private static FluidStorageLayout tankLayout(QuarryTier tier) {
        return FluidStorageLayout.builder()
            .add(COOLANT, SlotTemplates.input(tier.coolantTank()), slot -> slot.filter((i, fluid) -> fluid.is(QUARRY_COOLANT)))
            .add(XP, SlotTemplates.input(tier.xpTank()), slot -> slot.filter((i, fluid) -> fluid.is(Tags.Fluids.EXPERIENCE)))
            .build();
    }

    public FluidStorage getFluidStorage() {
        return fluidStorage;
    }

    // what quarry ports see
    public ResourceHandler<FluidResource> getPortFluidHandler() {
        return externalFluids;
    }

    public Tank getCoolantTank() {
        return coolantTank;
    }

    public Tank getXpTank() {
        return xpTank;
    }

    // 1 mB of coolant per coolant.energyPerMb of laser energy
    private void coolCrystal(int laserEnergy) {
        int perMb = QuarryConfig.COOLANT_ENERGY_PER_MB.get();
        // capped so an empty tank doesn't build up heat forever
        heat = (int) Math.min((long) heat + laserEnergy, (long) perMb * 100);
        int needed = heat / perMb;
        if (needed > 0) {
            int drained = coolantTank.drain(needed, false).getAmount();
            heat -= drained * perMb;
        }
    }

    // Keep the coolant in the item
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent stored = components.get(EIODataComponents.ITEM_FLUID_CONTENT);
        if (stored != null) {
            coolantTank.setFluid(stored.copy());
        }
        SimpleFluidContent xp = components.get(ModDataComponents.XP_FLUID);
        if (xp != null) {
            xpTank.setFluid(xp.copy());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        var tank = coolantTank;
        if (!tank.isEmpty()) {
            components.set(EIODataComponents.ITEM_FLUID_CONTENT, SimpleFluidContent.copyOf(tank.getFluid()));
        }
        var xp = xpTank;
        if (!xp.isEmpty()) {
            components.set(ModDataComponents.XP_FLUID, SimpleFluidContent.copyOf(xp.getFluid()));
        }
    }

    // endregion

    // region Linking & selection

    public void setObeliskPos(BlockPos pos) {
        if (!pos.equals(obeliskPos)) {
            obeliskPos = pos.immutable();
            setChanged();
        }
    }

    // Only if the obelisk still claims us
    @Nullable
    public CrystalObeliskBlockEntity getObelisk() {
        if (level == null || obeliskPos == null || !level.isLoaded(obeliskPos)) {
            return null;
        }
        return level.getBlockEntity(obeliskPos) instanceof CrystalObeliskBlockEntity obelisk
            && worldPosition.equals(obelisk.getControllerPos()) ? obelisk : null;
    }

    private ItemStack miningTool() {
        CrystalObeliskBlockEntity obelisk = getObelisk();
        return MiningRules.miningTool(obelisk != null ? obelisk.getCrystalEnchantments() : ItemEnchantments.EMPTY);
    }

    // Replaces any old link on both sides
    @Nullable
    public Component linkPad(LaunchPadBlockEntity pad) {
        if (!pad.isInLinkRange(worldPosition, getLinkRange())) {
            return Component.translatable("message.ender_laser_quarry.linker.too_far", getLinkRange());
        }
        padPos = pad.getBlockPos().immutable();
        pad.setStation(worldPosition);
        setChanged();
        syncToClients();
        return null;
    }

    @Nullable
    public BlockPos getPadPos() {
        return padPos;
    }

    // Only if it links back to us
    @Nullable
    public LaunchPadBlockEntity getPad() {
        if (level == null || padPos == null || !level.isLoaded(padPos)) {
            return null;
        }
        return level.getBlockEntity(padPos) instanceof LaunchPadBlockEntity pad
            && worldPosition.equals(pad.getStationPos()) ? pad : null;
    }

    @Nullable
    public LaunchPadBlockEntity getLinkedPad() {
        var pad = getPad();
        return pad != null && pad.isInLinkRange(worldPosition, getLinkRange()) ? pad : null;
    }

    // Where the launch pad's packets land, also used client side
    public static Vec3 antennaTip(BlockPos pos, BlockState state) {
        // the antenna stands in the middle of the top
        return Vec3.atLowerCornerOf(pos).add(0.5, ANTENNA_TIP_Y, 0.5);
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // All tiers share this BE type, so ask the block
    // how far the linked launch pad may be
    public int getLinkRange() {
        return getTier().linkRange();
    }

    public QuarryTier getTier() {
        return getBlockState().getBlock() instanceof Tiered tiered ? tiered.getTier() : QuarryTier.BASIC;
    }

    public int getChunkRadius(LaunchPadBlockEntity pad) {
        return Math.min(getTier().chunkRadius(), pad.getScanRadius());
    }

    public boolean isChunkInRange(LaunchPadBlockEntity pad, ChunkPos chunk) {
        return pad.getOrbitChunk().getChessboardDistance(chunk) <= getChunkRadius(pad);
    }

    // Orbit transfer from the controller GUI: moves the linked pad's satellite to the area dx / dz chunks from the pad
    @Nullable
    public Component tryTransferSatellite(int dx, int dz) {
        LaunchPadBlockEntity pad = getLinkedPad();
        if (pad == null) {
            return Component.translatable("message.ender_laser_quarry.controller.no_scanner");
        }
        if (running) {
            return Component.translatable("message.ender_laser_quarry.launch.transfer_stop_quarry");
        }
        return pad.tryTransfer(dx, dz);
    }

    // For the GUI, see QuarryControllerMenu: transfer range, orbit dx/dz, target dx/dz, state, progress, fuel, rocket,
    // fuel per flight / per chunk, satellite in orbit
    public int getOrbitInfo(int index) {
        LaunchPadBlockEntity pad = getLinkedPad();
        if (pad == null) {
            return 0;
        }
        return switch (index) {
            case 0 -> pad.getTransferRange();
            case 1 -> pad.getOrbitDx();
            case 2 -> pad.getOrbitDz();
            case 3 -> pad.getTransferTargetDx();
            case 4 -> pad.getTransferTargetDz();
            case 5 -> pad.isArriving() ? 2 : pad.isRelocating() ? 1 : 0;
            case 6 -> Math.round((pad.isArriving() ? pad.getRelocationProgress(0) : pad.getTransferFlightProgress()) * 1000);
            case 7 -> pad.getFuel();
            case 8 -> pad.isRocketReady() ? 1 : 0;
            case 9 -> pad.getTransferBaseCost();
            case 10 -> QuarryConfig.FUEL_PER_TRANSFER_CHUNK.get();
            case 11 -> pad.hasSatelliteInOrbit() ? 1 : 0;
            default -> 0;
        };
    }

    // the satellite now looks at another area: queued chunks it can't reach go
    public void onSatelliteMoved(LaunchPadBlockEntity pad) {
        if (running || queue.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (ChunkPos chunk : List.copyOf(queue.chunks())) {
            if (!isChunkInRange(pad, chunk)) {
                changed |= queue.remove(chunk);
            }
        }
        if (changed) {
            restartWall();
            setChanged();
        }
    }

    @Nullable
    private MachineState getSatelliteTierProblem(LaunchPadBlockEntity pad) {
        QuarryTier satellite = pad.getSatelliteTier();
        return satellite == null || satellite.atLeast(getTier()) ? null : QuarryStates.needsSatellite(getTier());
    }

    @Nullable
    public ChunkPos getTargetChunk() {
        return queue.current();
    }

    public List<ChunkPos> getTargetChunks() {
        return queue.chunks();
    }

    // the queue as the menu syncs it
    public String getTargetChunksEncoded() {
        return queue.encoded();
    }

    public int getChunkIndex() {
        return queue.index();
    }

    public boolean isRunning() {
        return running;
    }

    public int getBlocksMined() {
        return blocksMined;
    }

    public int getCursorY() {
        return cursorY;
    }

    public long getLastTickNanos() {
        return lastTickNanos;
    }

    public boolean isBuildingWall() {
        return running && !wallDone;
    }

    public void skipWall() {
        if (!wallDone) {
            wallDone = true;
            resetCursor();
            setChanged();
        }
    }

    // While walling the chunk whose wall goes up, otherwise the one being mined
    @Nullable
    public ChunkPos getWorkChunk() {
        if (!wallDone && wallChunkIndex < queue.size()) {
            return queue.get(wallChunkIndex);
        }
        return queue.current();
    }

    // Toggles the chunk. Returns an error message or null.
    @Nullable
    public Component trySelectChunk(ChunkPos chunk) {
        // after a finished queue the next click starts a new one
        return trySetQueued(chunk, queue.isFinished() || !queue.contains(chunk));
    }

    @Nullable
    public Component trySetQueued(ChunkPos chunk, boolean queued) {
        if (running) {
            return Component.translatable("message.ender_laser_quarry.controller.stop_first");
        }

        if (!queued) {
            if (!queue.isFinished() && queue.remove(chunk)) {
                // the neighbours need their wall on that side now
                restartWall();
                setChanged();
            }
            return null;
        }

        if (queue.isFinished()) {
            clearSelection();
        }

        if (queue.contains(chunk)) {
            return null;
        }

        int maxChunks = getTier().maxChunks();
        if (queue.size() >= maxChunks) {
            return Component.translatable("message.ender_laser_quarry.controller.queue_full", maxChunks);
        }

        var pad = getLinkedPad();
        if (pad == null) {
            return Component.translatable("message.ender_laser_quarry.controller.no_scanner");
        }
        MachineState satelliteProblem = getSatelliteTierProblem(pad);
        if (satelliteProblem != null) {
            return satelliteProblem.component();
        }
        if (!isChunkInRange(pad, chunk)) {
            return QuarryStates.TARGET_OUT_OF_RANGE.component();
        }
        if (!pad.isChunkScanned(chunk)) {
            return Component.translatable("message.ender_laser_quarry.controller.not_scanned");
        }
        if (!mayQueue(chunk)) {
            return Component.translatable("message.ender_laser_quarry.controller.chunk_claimed");
        }

        queue.add(chunk);
        // outline changed, redo the walls (existing ones are skipped fast)
        restartWall();
        setChanged();
        return null;
    }

    // Box select: adds every scanned chunk in range inside the box (all or nothing), or removes the queued ones
    @Nullable
    public Component trySetBox(ChunkPos from, ChunkPos to, boolean queued) {
        if (running) {
            return Component.translatable("message.ender_laser_quarry.controller.stop_first");
        }
        int minX = Math.min(from.x(), to.x());
        int maxX = Math.max(from.x(), to.x());
        int minZ = Math.min(from.z(), to.z());
        int maxZ = Math.max(from.z(), to.z());
        int maxChunks = getTier().maxChunks();
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_BOX_CHUNKS) {
            return Component.translatable("message.ender_laser_quarry.controller.box_too_big", maxChunks);
        }

        if (!queued) {
            boolean changed = false;
            if (!queue.isFinished()) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        changed |= queue.remove(new ChunkPos(x, z));
                    }
                }
            }
            if (changed) {
                restartWall();
                setChanged();
            }
            return null;
        }

        var pad = getLinkedPad();
        if (pad == null) {
            return Component.translatable("message.ender_laser_quarry.controller.no_scanner");
        }
        MachineState satelliteProblem = getSatelliteTierProblem(pad);
        if (satelliteProblem != null) {
            return satelliteProblem.component();
        }

        boolean fresh = queue.isFinished();
        List<ChunkPos> added = new ArrayList<>();
        boolean skipped = false;
        int claimed = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!fresh && queue.contains(chunk)) {
                    continue;
                }
                if (!isChunkInRange(pad, chunk) || !pad.isChunkScanned(chunk)) {
                    skipped = true;
                } else if (!mayQueue(chunk)) {
                    claimed++;
                } else {
                    added.add(chunk);
                }
            }
        }
        if (added.isEmpty()) {
            if (claimed > 0) {
                return Component.translatable("message.ender_laser_quarry.controller.chunk_claimed");
            }
            return skipped ? Component.translatable("message.ender_laser_quarry.controller.not_scanned") : null;
        }
        int total = (fresh ? 0 : queue.size()) + added.size();
        if (total > maxChunks) {
            return Component.translatable("message.ender_laser_quarry.controller.box_too_big", maxChunks);
        }

        if (fresh) {
            clearSelection();
        }
        added.forEach(queue::add);
        restartWall();
        setChanged();
        return claimed > 0 ? Component.translatable("message.ender_laser_quarry.controller.box_claimed", claimed) : null;
    }

    public boolean isQueueFinished() {
        return queue.isFinished();
    }

    /** How deep the laser goes. Basic controllers always mine down to bedrock. */
    public DepthMode getDepthMode() {
        return getTier() == QuarryTier.BASIC ? DepthMode.BEDROCK : depthMode;
    }

    /**
     * Picks the depth mode (Enhanced and Ender controllers, while stopped).
     * @return null on success, otherwise the reason it was rejected
     */
    @Nullable
    public Component trySetDepthMode(DepthMode mode) {
        if (getTier() == QuarryTier.BASIC) {
            return Component.translatable("message.ender_laser_quarry.controller.depth_needs_tier");
        }
        if (running) {
            return Component.translatable("message.ender_laser_quarry.controller.depth_stop_first");
        }
        if (mode != depthMode) {
            depthMode = mode;
            restartWall();
            setChanged();
        }
        return null;
    }

    // Only Ender controllers pick their own Y
    @Nullable
    private Integer getPlayerFloorY() {
        if (getTier() != QuarryTier.ENDER) {
            return null;
        }
        return floorY != null ? floorY : QuarryConfig.DEPTH_FLOOR_Y.get();
    }

    private int getFixedFloor(Level level) {
        return getDepthMode().fixedFloor(level, getPlayerFloorY());
    }

    public int getFloorY() {
        return level != null ? DepthMode.Y_LEVEL.fixedFloor(level, getPlayerFloorY()) : QuarryConfig.DEPTH_FLOOR_Y.get();
    }

    @Nullable
    public Component trySetFloorY(int y) {
        if (getTier() != QuarryTier.ENDER) {
            return Component.translatable("message.ender_laser_quarry.controller.floor_needs_ender");
        }
        if (running) {
            return Component.translatable("message.ender_laser_quarry.controller.depth_stop_first");
        }
        if (level != null) {
            y = DepthMode.clampY(level, y);
        }
        if (floorY == null || y != floorY) {
            floorY = y;
            if (getDepthMode() == DepthMode.Y_LEVEL) {
                // wall goes down to the floor
                restartWall();
            }
            setChanged();
        }
        return null;
    }

    public void clearSelection() {
        if (running) {
            return;
        }
        if (isVoidMode()) {
            asteroidQueue.clear();
            setChanged();
            return;
        }
        queue.clear();
        blocksMined = 0;
        restartWall();
        setChanged();
    }

    public void start() {
        if (running) {
            return;
        }
        if (isVoidMode()) {
            if (!asteroidQueue.isEmpty()) {
                running = true;
                setChanged();
            }
            return;
        }
        if (queue.isEmpty()) {
            return;
        }

        if (queue.isFinished()) {
            queue.rewind();
            blocksMined = 0;
            restartWall();
        }

        running = true;
        outputFullTick = NEVER_LIMITED;
        breakClaims.clear();
        wallClaims.clear();
        setChanged();
    }

    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        setChanged();
        pauseLaser();
    }

    private void resetCursor() {
        // clamped to the top block once the chunk is loaded
        cursorY = Integer.MAX_VALUE;
        cursorIndex = 0;
        topResolved = false;
        energyProgress = 0;
    }

    private void restartWall() {
        wallDone = false;
        wallChunkIndex = queue.index();
        skipEnclosedRings();
        resetCursor();
    }

    private boolean nextWallChunk() {
        wallChunkIndex++;
        skipEnclosedRings();
        return wallChunkIndex < queue.size();
    }

    // a ring with queued chunks on all 8 sides lies inside the hole, no wall needed
    private void skipEnclosedRings() {
        while (wallChunkIndex < queue.size() && isEnclosed(queue.get(wallChunkIndex))) {
            wallChunkIndex++;
        }
    }

    public boolean isEnclosed(ChunkPos chunk) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !queue.contains(new ChunkPos(chunk.x() + dx, chunk.z() + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    // endregion

    // region Mining

    @Override
    public boolean isActive() {
        return running && canAct() && getMachineStates().stream().noneMatch(PROBLEM_STATES::contains);
    }

    @Override
    public void serverTick() {
        updateWornState();
        super.serverTick();

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        updateUsage();
        tickSpaceScan(serverLevel);
        tickAnalyze(serverLevel);
        tickChunkLoading(serverLevel);

        if (!running) {
            CrystalObeliskBlockEntity obelisk = getObelisk();
            if (obelisk != null && obelisk.isCrystalTierTooLow()) {
                // say it before Start is pressed
                setProblem(QuarryStates.CRYSTAL_TIER_TOO_LOW);
            } else if (isVoidMode()) {
                setProblem(asteroidQueue.isEmpty() ? QuarryStates.NO_TARGET : null);
            } else if (queue.isEmpty()) {
                setProblem(QuarryStates.NO_TARGET);
            } else {
                setProblem(queue.isFinished() ? QuarryStates.FINISHED : null);
            }
            pauseLaser();
            return;
        }

        if (!canAct()) {
            pauseLaser();
            return;
        }

        long started = System.nanoTime();
        tickMining(serverLevel);
        lastTickNanos = System.nanoTime() - started;
    }

    // yellow warning, mining goes on
    private void updateWornState() {
        CrystalObeliskBlockEntity obelisk = getObelisk();
        boolean worn = obelisk != null && obelisk.isCrystalWorn() && !obelisk.isCrystalBroken();
        if (worn != getMachineStates().contains(QuarryStates.CRYSTAL_WORN)) {
            updateMachineState(QuarryStates.CRYSTAL_WORN, worn);
        }
        boolean hot = running && obelisk != null && obelisk.isRunningHot();
        if (hot != getMachineStates().contains(QuarryStates.RUNNING_HOT)) {
            updateMachineState(QuarryStates.RUNNING_HOT, hot);
        }
    }

    private void tickMining(ServerLevel serverLevel) {
        if (!isCapacitorInstalled()) {
            fail(MachineState.NO_CAPACITOR);
            return;
        }

        var obelisk = getObelisk();
        if (obelisk == null) {
            fail(QuarryStates.NOT_IN_QUARRY);
            return;
        }

        MachineState obeliskProblem = obelisk.getProblem();
        if (obeliskProblem != null) {
            fail(obeliskProblem);
            return;
        }

        var pad = getPad();
        if (pad == null) {
            fail(QuarryStates.NO_LAUNCH_PAD);
            return;
        }

        if (!pad.isInLinkRange(worldPosition, getLinkRange())) {
            fail(QuarryStates.PAD_OUT_OF_RANGE);
            return;
        }

        if (!obelisk.hasSky()) {
            fail(QuarryStates.OBELISK_NO_SKY);
            return;
        }

        MachineState reflectorProblem = pad.getReflectorProblem();
        if (reflectorProblem != null) {
            fail(reflectorProblem);
            return;
        }

        MachineState satelliteProblem = getSatelliteTierProblem(pad);
        if (satelliteProblem != null) {
            fail(satelliteProblem);
            return;
        }

        if (obelisk.hasVoidCrystal()) {
            tickVoidMining(serverLevel, obelisk, pad);
            return;
        }

        if (queue.current() == null) {
            fail(QuarryStates.NO_TARGET);
            return;
        }

        ChunkPos workChunk = getWorkChunk();
        if (!isChunkInRange(pad, workChunk)) {
            fail(QuarryStates.TARGET_OUT_OF_RANGE);
            return;
        }

        if (!payOwnUsage()) {
            fail(recently(intakeLimitedTick) ? QuarryStates.INTAKE_LIMITED : MachineState.NO_POWER);
            return;
        }

        // ~10 s power up before the first block, the chunk can load meanwhile
        MachineState powering = obelisk.powerUp();
        if (powering == QuarryStates.POWERING_UP) {
            clearBeam();
            setProblem(powering);
            acquireTicket(serverLevel, workChunk);
            return;
        } else if (powering != null) {
            fail(powering);
            return;
        }

        // chunk loading is async, wait for it
        acquireTicket(serverLevel, workChunk);
        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(workChunk.x(), workChunk.z());
        if (chunk == null) {
            return;
        }

        DepthMode mode = getDepthMode();
        if (!wallDone && !mode.drains()) {
            // surface mode leaves liquids alone, no wall needed
            skipWall();
        }
        if (wallDone) {
            int fixedFloor = getFixedFloor(serverLevel);
            if (floor == null || !floor.matches(workChunk, mode, fixedFloor)) {
                floor = new MiningFloor(serverLevel, workChunk, mode, fixedFloor);
            }
            if (!floor.compute(serverLevel)) {
                return;
            }
        }

        if (!topResolved) {
            // the wall ring needs the neighbour chunks loaded too
            int top = wallDone ? MiningRules.highestBlock(chunk) : LiquidWall.top(serverLevel, workChunk, queue);
            if (top == Integer.MIN_VALUE) {
                return;
            }
            cursorY = Math.min(cursorY, top);
            topResolved = true;
        }

        if (waitingForOutput()) {
            return;
        }
        int speed = Math.max(1, obelisk.getBlocksPerTick());
        if (withOutputBuffer(() -> fireLaser(serverLevel, chunk, obelisk, pad, speed)) > 0) {
            setChanged();
        }
    }

    // Returns how many blocks got mined
    private int fireLaser(ServerLevel serverLevel, LevelChunk chunk, CrystalObeliskBlockEntity obelisk, LaunchPadBlockEntity pad, int speed) {
        double costScale = MiningRules.enchantCostScale(obelisk.getCrystalEnchantments());
        boolean drewEnergy = false;
        int mined = 0;
        while (mined < speed) {
            BlockPos target = wallDone ? findNextTarget(serverLevel, chunk) : findNextWallTarget(serverLevel);
            if (target == null) {
                if (wallDone) {
                    nextChunk();
                } else if (nextWallChunk()) {
                    resetCursor();
                    setChanged();
                } else {
                    // all walls done, start mining
                    skipWall();
                }
                return mined;
            }

            if (target == SCANNING) {
                return mined;
            }

            BlockState state = target == CLAIMED ? null : serverLevel.getBlockState(target);
            // wall targets were checked when found
            if (state == null || (wallDone && !mayBreak(serverLevel, target, state))) {
                skipClaimedChunk();
                return mined;
            }
            int cost = MiningRules.energyCost(serverLevel, target, state, costScale);

            if (energyProgress < cost) {
                if (drewEnergy) {
                    return mined;
                }
                drewEnergy = true;
                if (!drawLaserEnergy(obelisk, cost, speed - mined)) {
                    if (mined == 0) {
                        fail(MachineState.NO_POWER);
                    }
                    return mined;
                }
            }

            setProblem(null);
            updateBeam(pad, target);

            if (energyProgress < cost) {
                // expensive block, keep charging next tick
                return mined;
            }

            if (!mineBlock(serverLevel, target, state, !wallDone)) {
                outputFull();
                return mined;
            }
            energyProgress -= cost;
            blocksMined++;
            mined++;
            advanceCursor();
        }
        return mined;
    }

    // One draw per tick (Ender IO caps each draw), 1.5x the block cost per block left since the next
    // ones are often harder. Leftovers stay in energyProgress.
    private boolean drawLaserEnergy(CrystalObeliskBlockEntity obelisk, int cost, int blocksLeft) {
        long wanted = (long) cost * blocksLeft * 3 / 2 - energyProgress;
        int drawn = obelisk.drawLaserEnergy((int) Math.min(Integer.MAX_VALUE, wanted));
        if (drawn <= 0) {
            return false;
        }
        energyProgress += drawn;
        coolCrystal(drawn);
        return true;
    }

    private void nextChunk() {
        CrystalObeliskBlockEntity obelisk = getObelisk();
        if (obelisk != null) {
            obelisk.wearDishCapacitors();
        }
        queue.advance();
        resetCursor();
        setChanged();
        if (queue.current() == null) {
            finish();
        }
    }

    // null = chunk done, SCANNING = out of budget for this tick
    @Nullable
    private BlockPos findNextTarget(ServerLevel serverLevel, LevelChunk chunk) {
        ChunkPos mining = chunk.getPos();
        boolean drains = getDepthMode().drains();
        var pos = new BlockPos.MutableBlockPos();
        int deleted = 0;
        for (int i = 0; i < SCAN_BUDGET; i++) {
            if (cursorY < floor.lowest()) {
                return null;
            }

            // skip empty sections
            if (cursorIndex == 0 && chunk.getSection(chunk.getSectionIndex(cursorY)).hasOnlyAir()) {
                cursorY = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(cursorY)) - 1;
                continue;
            }

            if (cursorY < floor.at(cursorIndex)) {
                advanceCursor();
                continue;
            }

            pos.set(mining.getBlockX(cursorIndex & 15), cursorY, mining.getBlockZ(cursorIndex >> 4));
            BlockState state = chunk.getBlockState(pos);
            if (!drains && MiningRules.hasLiquid(state)) {
                advanceCursor();
                continue;
            }
            if (MiningRules.isLiquid(state)) {
                if (!mayBreak(serverLevel, pos, state)) {
                    return CLAIMED;
                }
                // delete silently so nothing starts flowing
                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), MiningRules.SILENT);
                LiquidWall.sealEdge(serverLevel, queue, mining, pos, wallPlacer(serverLevel));
                int drained = 1 + LiquidWall.drainNeighbours(serverLevel, queue, mining, pos, floor.lowest(), wallPlacer(serverLevel));
                deleted += drained;
                liquidsDeleted += drained;
                wear(drained);
                advanceCursor();
                if (deleted >= LIQUID_BUDGET) {
                    return SCANNING;
                }
                continue;
            }

            // surface mode: don't break pond rims
            if (MiningRules.canMine(serverLevel, pos, state) && (drains || !MiningRules.holdsBackLiquid(serverLevel, pos))) {
                return pos.immutable();
            }
            advanceCursor();
        }

        return SCANNING;
    }

    // Next spot on the ring around the chunk where liquid could get in, null when the ring is done
    @Nullable
    private BlockPos findNextWallTarget(ServerLevel serverLevel) {
        ChunkPos ring = getWorkChunk();
        int bottom = getFixedFloor(serverLevel);
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < WALL_SCAN_BUDGET; i++) {
            if (cursorY < bottom) {
                return null;
            }

            LiquidWall.setPos(pos, ring, cursorIndex, cursorY);
            // no wall between two queued chunks
            if (queue.containsBlock(pos)) {
                advanceCursor();
                continue;
            }
            LevelChunk chunk = LiquidWall.loadedChunk(serverLevel, pos);
            if (chunk == null) {
                return SCANNING;
            }

            if (cursorY <= LiquidWall.columnTop(chunk, pos)) {
                BlockState state = chunk.getBlockState(pos);
                // no wall in someone else's claim
                if (LiquidWall.needsWall(serverLevel, queue, pos, state) && mayWall(serverLevel, pos, state)) {
                    return pos.immutable();
                }
            }
            advanceCursor();
        }

        return SCANNING;
    }

    private void advanceCursor() {
        cursorIndex++;
        if (cursorIndex >= (wallDone ? 256 : LiquidWall.COLUMNS)) {
            cursorIndex = 0;
            cursorY--;
        }
    }

    private LiquidWall.Placer wallPlacer(ServerLevel serverLevel) {
        return new LiquidWall.Placer() {
            @Override
            public void placeWall(BlockPos pos, BlockState replaced) {
                if (mayWall(serverLevel, pos, replaced)) {
                    mineBlock(serverLevel, pos, replaced, true);
                }
            }

            @Override
            public boolean mayDelete(BlockPos pos, BlockState state) {
                return mayBreak(serverLevel, pos, state);
            }
        };
    }

    // region Claims

    // the owner's fake player, so claim mods decide as if the owner did it by hand
    private FakePlayer claimPlayer(ServerLevel serverLevel) {
        UUID owner = getMachineOwner();
        if (claimPlayer == null || claimPlayer.level() != serverLevel || !Objects.equals(claimPlayerOwner, owner)) {
            claimPlayer = ClaimGuard.player(serverLevel, owner);
            claimPlayerOwner = owner;
            breakClaims.clear();
            wallClaims.clear();
        }
        return claimPlayer;
    }

    private boolean perBlockClaims() {
        return debugPerBlockClaims != null ? debugPerBlockClaims : "per_block".equals(QuarryConfig.CLAIM_CHECK_MODE.get());
    }

    // tests: per_block (true) or per_section (false), null = the config
    public void debugSetPerBlockClaims(@Nullable Boolean perBlock) {
        debugPerBlockClaims = perBlock;
        breakClaims.clear();
        wallClaims.clear();
    }

    private boolean mayBreak(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (perBlockClaims()) {
            return ClaimGuard.mayBreak(serverLevel, pos, state, claimPlayer(serverLevel));
        }
        long now = serverLevel.getGameTime();
        int known = breakClaims.lookup(pos, now);
        if (known >= 0) {
            return known == 1;
        }
        boolean allowed = ClaimGuard.mayBreak(serverLevel, pos, state, claimPlayer(serverLevel));
        breakClaims.put(pos, now, allowed);
        return allowed;
    }

    private boolean mayWall(ServerLevel serverLevel, BlockPos pos, BlockState replaced) {
        if (perBlockClaims()) {
            return checkWall(serverLevel, pos, replaced);
        }
        long now = serverLevel.getGameTime();
        int known = wallClaims.lookup(pos, now);
        if (known >= 0) {
            return known == 1;
        }
        boolean allowed = checkWall(serverLevel, pos, replaced);
        wallClaims.put(pos, now, allowed);
        return allowed;
    }

    private boolean checkWall(ServerLevel serverLevel, BlockPos pos, BlockState replaced) {
        FakePlayer player = claimPlayer(serverLevel);
        return (replaced.isAir() || ClaimGuard.mayBreak(serverLevel, pos, replaced, player)) && ClaimGuard.mayPlace(serverLevel, pos, player);
    }

    private boolean mayQueue(ChunkPos chunk) {
        return !(level instanceof ServerLevel serverLevel) || ClaimGuard.mayQueue(serverLevel, chunk, claimPlayer(serverLevel));
    }

    // claimed after it was queued: leave it, go on with the next one
    private void skipClaimedChunk() {
        ChunkPos skipped = queue.current();
        claimSkipped++;
        if (skipped != null) {
            notifyOwner(Component.translatable("message.ender_laser_quarry.controller.chunk_claimed_skipped", skipped.x(), skipped.z()));
        }
        queue.advance();
        resetCursor();
        setChanged();
        if (queue.current() == null) {
            finish();
        }
    }

    private void notifyOwner(Component message) {
        UUID owner = getMachineOwner();
        if (owner != null && level != null && level.getServer() != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    // tests: chunks skipped because of a claim
    public int getClaimSkipped() {
        return claimSkipped;
    }

    // endregion

    // Returns false if the drops don't fit (nothing changed then)
    private boolean mineBlock(ServerLevel serverLevel, BlockPos pos, BlockState state, boolean wall) {
        int worn = state.isAir() ? 0 : 1;
        if (!state.isAir() && !MiningRules.isLiquid(state)) {
            BlockEntity blockEntity = state.hasBlockEntity() ? serverLevel.getBlockEntity(pos) : null;
            if (!storeDrops(Block.getDrops(state, serverLevel, pos, blockEntity, null, miningTool()))) {
                return false;
            }
            // only a couple of particles per tick, fast quarries would lag clients
            if (level.getGameTime() != particleTick) {
                particleTick = level.getGameTime();
                particlesThisTick = 0;
            }
            if (particlesThisTick++ < PARTICLES_PER_TICK) {
                serverLevel.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            }
        }

        // silent, otherwise water next to it starts flowing into the hole
        if (wall) {
            serverLevel.setBlock(pos, LiquidWall.WALL_BLOCK.defaultBlockState(), MiningRules.SILENT);
        } else {
            ChunkPos mining = queue.current();
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), MiningRules.SILENT);
            if (getDepthMode().drains()) {
                LiquidWall.sealEdge(serverLevel, queue, mining, pos, wallPlacer(serverLevel));
                int bottom = floor != null ? floor.lowest() : serverLevel.getMinY();
                int drained = LiquidWall.drainNeighbours(serverLevel, queue, mining, pos, bottom, wallPlacer(serverLevel));
                liquidsDeleted += drained;
                worn += drained;
            }
        }
        wear(worn);
        return true;
    }

    // crystal wear: solid blocks (voided too) and deleted liquids, never air
    private void wear(int blocks) {
        if (blocks <= 0) {
            return;
        }
        liquidsAndBlocksWorn += blocks;
        CrystalObeliskBlockEntity obelisk = getObelisk();
        if (obelisk != null) {
            // no coolant: keeps running, wears faster
            obelisk.wearCrystal(blocks, coolantTank.isEmpty() ? QuarryConfig.UNCOOLED_WEAR_MULTIPLIER.get() : 1);
        }
    }

    // tests: everything that wore the crystal (mined blocks + deleted liquids)
    public long getWornCount() {
        return liquidsAndBlocksWorn;
    }

    public long getLiquidsDeleted() {
        return liquidsDeleted;
    }

    private boolean storeDrops(List<ItemStack> drops) {
        drops = QuarryOutput.applyVoidFilter(drops, getInventory().getStack(FILTER));
        return tickOutput != null ? tickOutput.add(drops) : QuarryOutput.insertAll(getInventory(), OUTPUT, drops);
    }

    // While the laser fires, drops collect in a buffer that is written to the output slots once at the end
    @Nullable
    private QuarryOutput.Buffer tickOutput;

    private int withOutputBuffer(java.util.function.IntSupplier laser) {
        tickOutput = new QuarryOutput.Buffer(getInventory(), OUTPUT);
        try {
            return laser.getAsInt();
        } finally {
            QuarryOutput.Buffer buffer = tickOutput;
            tickOutput = null;
            buffer.flush();
            if (outputBlocked) {
                // after the flush, so this tick's own drops don't count as a change
                outputBlocked = false;
                outputFullTick = level.getGameTime();
                outputFullSignature = outputSignature();
                outputRetries++;
            }
        }
    }

    private void outputFull() {
        outputBlocked = true;
        setProblem(isOutputLimited() ? QuarryStates.OUTPUT_LIMITED : MachineState.FULL_OUTPUT);
    }

    // still full and nothing taken out since: skip the claim check and drop roll this tick
    private boolean waitingForOutput() {
        if (outputFullTick == NEVER_LIMITED) {
            return false;
        }
        if (level.getGameTime() - outputFullTick < OUTPUT_RETRY_TICKS && outputSignature() == outputFullSignature) {
            setProblem(isOutputLimited() ? QuarryStates.OUTPUT_LIMITED : MachineState.FULL_OUTPUT);
            return true;
        }
        outputFullTick = NEVER_LIMITED;
        return false;
    }

    private long outputSignature() {
        var inventory = getInventory();
        long signature = 0;
        for (int i = 0; i < OUTPUT.count(); i++) {
            var slot = OUTPUT.slot(i);
            signature = signature * 31 + inventory.getAmountAsInt(slot) * 7L + inventory.getResource(slot).hashCode();
        }
        return signature;
    }

    // tests: how often a full output was retried
    public int getOutputRetries() {
        return outputRetries;
    }

    private void finish() {
        running = false;
        setChanged();
        pauseLaser();
        setProblem(QuarryStates.FINISHED);
    }

    private void fail(MachineState problem) {
        setProblem(problem);
        pauseLaser();
    }

    private void setProblem(@Nullable MachineState problem) {
        currentProblem = problem;
        var current = getMachineStates();
        for (MachineState state : PROBLEM_STATES) {
            // only touch changed states, every update sends a block update. NO_CAPACITOR is Ender IO's own
            boolean wanted = state == problem || (state == MachineState.NO_CAPACITOR && !isCapacitorInstalled());
            if (wanted != current.contains(state)) {
                updateMachineState(state, wanted);
            }
        }
    }

    // endregion

    // region Void mining (asteroids)

    // 0 = output empty, 15 = every slot full
    public int getComparatorSignal() {
        var inventory = getInventory();
        float fill = 0;
        boolean any = false;
        for (int i = 0; i < OUTPUT.count(); i++) {
            var slot = OUTPUT.slot(i);
            int amount = inventory.getAmountAsInt(slot);
            if (amount > 0) {
                fill += amount / (float) Math.max(1, inventory.getCapacityAsInt(slot, inventory.getResource(slot)));
                any = true;
            }
        }
        return any ? Mth.floor(fill / OUTPUT.count() * 14) + 1 : 0;
    }

    public boolean isVoidMode() {
        CrystalObeliskBlockEntity obelisk = getObelisk();
        return obelisk != null && obelisk.hasVoidCrystal();
    }

    public List<Integer> getAsteroidQueue() {
        return Collections.unmodifiableList(asteroidQueue);
    }

    public boolean isScanningSpace() {
        return spaceScanTicks > 0;
    }

    public int getSpaceScanTicks() {
        return spaceScanTicks;
    }

    public boolean isScanStalled() {
        return isScanningSpace() && scanStalled;
    }

    @Nullable
    private Component getVoidProblem() {
        if (!isVoidMode()) {
            return Component.translatable("message.ender_laser_quarry.controller.no_void_crystal");
        }
        if (getTier() != QuarryTier.ENDER) {
            return QuarryStates.VOID_NEEDS_ENDER.component();
        }
        LaunchPadBlockEntity pad = getLinkedPad();
        if (pad == null) {
            return Component.translatable("message.ender_laser_quarry.controller.no_scanner");
        }
        MachineState satelliteProblem = getSatelliteTierProblem(pad);
        if (satelliteProblem != null) {
            return satelliteProblem.component();
        }
        MachineState reflectorProblem = pad.getReflectorProblem();
        return reflectorProblem != null ? reflectorProblem.component() : null;
    }

    @Nullable
    public Component tryScanSpace() {
        // mining goes on while it scans
        if (analyzeId >= 0) {
            return Component.translatable("message.ender_laser_quarry.controller.scan_analyze_running");
        }
        Component problem = getVoidProblem();
        if (problem != null) {
            return problem;
        }
        if (!isScanningSpace()) {
            spaceScanTicks = QuarryConfig.VOID_SCAN_TICKS.get();
            scanStalled = false;
            setChanged();
        }
        return null;
    }

    @Nullable
    public Component trySetAsteroidQueued(int id, boolean queued) {
        if (running) {
            return Component.translatable("message.ender_laser_quarry.controller.stop_first");
        }
        if (!queued) {
            if (asteroidQueue.remove(Integer.valueOf(id))) {
                setChanged();
            }
            return null;
        }
        Component problem = getVoidProblem();
        if (problem != null) {
            return problem;
        }
        if (asteroidQueue.contains(id)) {
            return null;
        }
        Asteroid wanted = getLinkedPad().getAsteroids().get(id);
        if (wanted != null && level instanceof ServerLevel serverLevel && !BiomeSampling.knowsRock(serverLevel.getServer(), wanted)) {
            return Component.translatable(wanted.analyzed() ? "message.ender_laser_quarry.controller.analyze_again"
                : "message.ender_laser_quarry.controller.analyze_first");
        }
        int max = QuarryConfig.VOID_MAX_QUEUED.get();
        if (asteroidQueue.size() >= max) {
            return Component.translatable("message.ender_laser_quarry.controller.asteroid_queue_full", max);
        }
        if (getLinkedPad().getAsteroids().get(id) == null) {
            return Component.translatable("message.ender_laser_quarry.controller.asteroid_gone");
        }
        asteroidQueue.add(id);
        setChanged();
        return null;
    }

    // The radar on the pad pays the scan bit by bit, without power it waits
    private void tickSpaceScan(ServerLevel serverLevel) {
        if (spaceScanTicks <= 0) {
            return;
        }
        LaunchPadBlockEntity scanPad = getLinkedPad();
        GroundRadarBlockEntity radar = scanPad != null ? scanPad.getRadar() : null;
        int perTick = Mth.ceil((double) QuarryConfig.VOID_SCAN_ENERGY.get() / QuarryConfig.VOID_SCAN_TICKS.get());
        if (radar == null || getVoidProblem() != null || !radar.consumeScanEnergy(perTick)) {
            scanStalled = true;
            return;
        }
        scanStalled = false;
        if (--spaceScanTicks == 0) {
            getLinkedPad().getAsteroids().refresh(serverLevel, serverLevel.getRandom(), asteroidQueue::contains);
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        setChanged();
    }

    public int getAnalyzeId() {
        return analyzeId;
    }

    /** 0..1, or -1 when nothing is analyzed. Slower of the two: the minimum time and measuring the biome. */
    public float getAnalyzeProgress() {
        if (analyzeId < 0) {
            return -1;
        }
        float time = Math.min(1, analyzeTicks / (float) QuarryConfig.VOID_ANALYZE_TICKS.get());
        return Math.min(time, analyzeSampleProgress);
    }

    public boolean isAnalyzeStalled() {
        return analyzeId >= 0 && analyzeStalled;
    }

    @Nullable
    public Component tryAnalyze(int id) {
        Component problem = getVoidProblem();
        if (problem != null) {
            return problem;
        }
        if (isScanningSpace()) {
            return Component.translatable("message.ender_laser_quarry.controller.analyze_scan_running");
        }
        Asteroid asteroid = getLinkedPad().getAsteroids().get(id);
        if (asteroid == null) {
            return Component.translatable("message.ender_laser_quarry.controller.asteroid_gone");
        }
        if (analyzeId == id || (level instanceof ServerLevel known && BiomeSampling.knowsRock(known.getServer(), asteroid))) {
            return null;
        }
        if (analyzeId >= 0) {
            return Component.translatable("message.ender_laser_quarry.controller.analyze_busy");
        }
        analyzeId = id;
        analyzeTicks = 0;
        analyzeStalled = false;
        analyzeSampleProgress = 0;
        if (level instanceof ServerLevel serverLevel) {
            BiomeSampling.request(serverLevel.getServer(), asteroid.biome());
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.8f);
        }
        setChanged();
        return null;
    }

    // Like the space scan: the radar pays bit by bit, the biome is measured meanwhile
    private void tickAnalyze(ServerLevel serverLevel) {
        if (analyzeId < 0) {
            return;
        }
        LaunchPadBlockEntity pad = getLinkedPad();
        Asteroid asteroid = pad != null ? pad.getAsteroids().get(analyzeId) : null;
        MinecraftServer server = serverLevel.getServer();
        if (asteroid == null || (asteroid.analyzed() && analyzeTicks >= QuarryConfig.VOID_ANALYZE_TICKS.get()
            && BiomeSampling.knowsRock(server, asteroid))) {
            analyzeId = -1;
            setChanged();
            return;
        }
        BiomeSampling.request(server, asteroid.biome());
        BiomeSampling.Status status = BiomeSampling.status(server, asteroid.biome());
        if (status.state() == BiomeSampling.State.FAILED) {
            pad.getAsteroids().remove(analyzeId);
            analyzeId = -1;
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 1.2f);
            setChanged();
            return;
        }
        analyzeSampleProgress = status.state() == BiomeSampling.State.READY ? 1 : status.progress();

        int total = QuarryConfig.VOID_ANALYZE_TICKS.get();
        if (analyzeTicks < total) {
            GroundRadarBlockEntity radar = pad.getRadar();
            int perTick = Mth.ceil((double) QuarryConfig.VOID_ANALYZE_ENERGY.get() / total);
            if (radar == null || getVoidProblem() != null || !radar.consumeScanEnergy(perTick)) {
                analyzeStalled = true;
                return;
            }
            analyzeTicks++;
            setChanged();
        }
        analyzeStalled = false;
        if (analyzeTicks >= total && status.state() == BiomeSampling.State.READY) {
            pad.getAsteroids().setAnalyzed(analyzeId);
            analyzeId = -1;
            serverLevel.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.5f);
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5f, 1.9f);
            setChanged();
        }
    }

    // Asteroid mining: no chunk, no wall, every "block" is picked from the asteroid's composition
    private void tickVoidMining(ServerLevel serverLevel, CrystalObeliskBlockEntity obelisk, LaunchPadBlockEntity pad) {
        if (getTier() != QuarryTier.ENDER) {
            fail(QuarryStates.VOID_NEEDS_ENDER);
            return;
        }

        // drop asteroids that are gone (mined out elsewhere or replaced by a scan)
        AsteroidField field = pad.getAsteroids();
        if (asteroidQueue.removeIf(id -> field.get(id) == null)) {
            setChanged();
        }
        if (asteroidQueue.isEmpty()) {
            finish();
            return;
        }

        if (!payOwnUsage()) {
            fail(recently(intakeLimitedTick) ? QuarryStates.INTAKE_LIMITED : MachineState.NO_POWER);
            return;
        }        MachineState powering = obelisk.powerUp();
        if (powering == QuarryStates.POWERING_UP) {
            clearBeam();
            setProblem(powering);
            return;
        } else if (powering != null) {
            fail(powering);
            return;
        }

        if (waitingForOutput()) {
            return;
        }
        int speed = Math.max(1, obelisk.getBlocksPerTick());
        if (withOutputBuffer(() -> fireVoidLaser(serverLevel, obelisk, pad, field, speed)) > 0) {
            setChanged();
        }
    }

    private int fireVoidLaser(ServerLevel serverLevel, CrystalObeliskBlockEntity obelisk, LaunchPadBlockEntity pad,
                              AsteroidField field, int speed) {
        int id = asteroidQueue.get(0);
        Asteroid asteroid = field.get(id);
        AsteroidComposition rock = AsteroidComposition.of(serverLevel.getServer(), asteroid.biome());
        if (rock == null || !BiomeSampling.knowsRock(serverLevel.getServer(), asteroid)) {
            // queued before analyzing existed, or the mods changed: the player analyzes it (again)
            fail(QuarryStates.ASTEROID_UNMEASURED);
            return 0;
        }
        int limit = (int) Math.min(speed, asteroid.remaining());
        double costScale = MiningRules.enchantCostScale(obelisk.getCrystalEnchantments());
        BlockPos space = pad.getPadCenter().above(pad.getOrbitAltitude() + SPACE_BEAM_LENGTH);

        boolean drewEnergy = false;
        int mined = 0;
        while (mined < limit) {
            if (pendingVoidBlock == null || voidVeinLeft <= 0) {
                // veins, so the output doesn't fill up with 15 different items
                AsteroidComposition.Vein vein = rock.nextVein(serverLevel.getRandom());
                pendingVoidBlock = vein.state();
                voidVeinLeft = vein.length();
            }
            BlockState state = pendingVoidBlock;
            int cost = MiningRules.energyCost(serverLevel, worldPosition, state, costScale);
            if (energyProgress < cost) {
                if (drewEnergy) {
                    break;
                }
                drewEnergy = true;
                if (!drawLaserEnergy(obelisk, cost, limit - mined)) {
                    if (mined == 0) {
                        fail(MachineState.NO_POWER);
                    }
                    break;
                }
            }

            setProblem(null);
            updateBeam(pad, space);
            if (energyProgress < cost) {
                break;
            }
            if (!storeDrops(Block.getDrops(state, serverLevel, worldPosition, null, null, miningTool()))) {
                outputFull();
                break;
            }
            energyProgress -= cost;
            voidVeinLeft--;
            blocksMined++;
            wear(1);
            mined++;
        }

        if (mined > 0) {
            // asteroids wear capacitors per chunk of blocks
            voidWearBlocks += mined;
            while (voidWearBlocks >= QuarryConfig.VOID_BLOCKS_PER_CHUNK.get()) {
                voidWearBlocks -= QuarryConfig.VOID_BLOCKS_PER_CHUNK.get();
                obelisk.wearDishCapacitors();
            }
            field.take(id, mined);
            if (field.get(id) == null) {
                asteroidQueue.remove(0);
                pendingVoidBlock = null;
                if (asteroidQueue.isEmpty()) {
                    finish();
                }
            }
        }
        return mined;
    }

    // endregion

    // region Laser visuals & chunk loading

    private void updateBeam(LaunchPadBlockEntity pad, BlockPos target) {
        long now = level.getGameTime();
        if (!pad.getBlockPos().equals(beamingPad)) {
            clearBeam();
            beamingPad = pad.getBlockPos();
        } else if (now - lastBeamUpdate < BEAM_UPDATE_INTERVAL) {
            return;
        }

        lastBeamUpdate = now;
        CrystalObeliskBlockEntity obelisk = getObelisk();
        BlockState obeliskState = obelisk != null ? obelisk.getBlockState() : null;
        CrystalGlow glow = obeliskState != null && obeliskState.hasProperty(CrystalGlow.PROPERTY) ? obeliskState.getValue(CrystalGlow.PROPERTY) : CrystalGlow.LASER;
        pad.setBeamTarget(target, getTier(), glow);
    }

    private void clearBeam() {
        if (beamingPad != null && level != null && level.isLoaded(beamingPad)
            && level.getBlockEntity(beamingPad) instanceof LaunchPadBlockEntity pad) {
            pad.setBeamTarget(null);
        }
        beamingPad = null;
    }

    private void pauseLaser() {
        clearBeam();
        if (level instanceof ServerLevel serverLevel) {
            releaseTicket(serverLevel);
        }
    }

    private void acquireTicket(ServerLevel serverLevel, ChunkPos chunk) {
        if (chunk.equals(ticketedChunk)) {
            return;
        }

        releaseTicket(serverLevel);
        EnderLaserQuarry.CHUNK_TICKETS.forceChunk(serverLevel, worldPosition, chunk.x(), chunk.z(), true, false);
        ticketedChunk = chunk;
    }

    private void releaseTicket(ServerLevel serverLevel) {
        if (ticketedChunk != null) {
            EnderLaserQuarry.CHUNK_TICKETS.forceChunk(serverLevel, worldPosition, ticketedChunk.x(), ticketedChunk.z(), false, false);
            ticketedChunk = null;
        }
    }

    @Override
    public void onChunkUnloaded() {
        chunkUnloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        // With base tickets our chunk only unloads on shutdown, keep the tickets for the next start
        if (chunkUnloading && !baseTickets.isEmpty()) {
            super.setRemoved();
            return;
        }
        pauseLaser();
        if (level instanceof ServerLevel serverLevel) {
            setBaseTickets(serverLevel, Set.of());
        }
        super.setRemoved();
    }

    // region Chunk loading

    // Keeps the base loaded while running, lets go after problemTimeout seconds stuck on a problem
    private void tickChunkLoading(ServerLevel serverLevel) {
        boolean stuck = running && currentProblem != null && currentProblem != QuarryStates.POWERING_UP;
        problemTicks = stuck ? problemTicks + 1 : 0;
        boolean keep = running && (debugLoadBase != null ? debugLoadBase : QuarryConfig.LOAD_BASE.get()) && problemTicks <= QuarryConfig.PROBLEM_TIMEOUT.get() * 20;
        if (!keep) {
            if (!baseTickets.isEmpty()) {
                setBaseTickets(serverLevel, Set.of());
            }
            if (stuck) {
                releaseTicket(serverLevel);
            }
            return;
        }
        if (baseTickets.isEmpty() || serverLevel.getGameTime() % 20 == 0) {
            setBaseTickets(serverLevel, wantedBaseChunks());
        }
    }

    // A ticket keeps the 8 neighbours ticking too, so neighbours of a taken chunk are skipped
    private Set<ChunkPos> wantedBaseChunks() {
        List<BlockPos> parts = new ArrayList<>();
        parts.add(worldPosition);
        CrystalObeliskBlockEntity obelisk = getObelisk();
        if (obelisk != null) {
            parts.add(obelisk.getBlockPos());
            parts.addAll(obelisk.getDishes());
        }
        if (padPos != null) {
            parts.add(padPos);
        }
        Set<ChunkPos> centres = new LinkedHashSet<>();
        for (BlockPos part : parts) {
            ChunkPos chunk = ChunkPos.containing(part);
            if (centres.stream().noneMatch(c -> Math.abs(c.x() - chunk.x()) <= 1 && Math.abs(c.z() - chunk.z()) <= 1)) {
                centres.add(chunk);
            }
        }
        return centres;
    }

    private void setBaseTickets(ServerLevel serverLevel, Set<ChunkPos> wanted) {
        if (wanted.equals(baseTickets)) {
            return;
        }
        for (ChunkPos chunk : baseTickets) {
            if (!wanted.contains(chunk)) {
                EnderLaserQuarry.CHUNK_TICKETS.forceChunk(serverLevel, worldPosition, chunk.x(), chunk.z(), false, false);
            }
        }
        for (ChunkPos chunk : wanted) {
            if (!baseTickets.contains(chunk)) {
                EnderLaserQuarry.CHUNK_TICKETS.forceChunk(serverLevel, worldPosition, chunk.x(), chunk.z(), true, false);
            }
        }
        baseTickets.clear();
        baseTickets.addAll(wanted);
        setChanged();
    }

    // Debug override for chunk_loading.loadBase
    @Nullable
    private static Boolean debugLoadBase;

    public static void debugSetLoadBase(@Nullable Boolean loadBase) {
        debugLoadBase = loadBase;
    }

    public Set<ChunkPos> getBaseTickets() {
        return Collections.unmodifiableSet(baseTickets);
    }

    // Tickets are saved with the world, only a running quarry keeps them after loading
    public boolean keepsChunksAfterLoad() {
        return running && !baseTickets.isEmpty();
    }

    public void forgetTickets() {
        baseTickets.clear();
        ticketedChunk = null;
        setChanged();
    }

    // endregion

    // endregion

    // region Machine setup

    @Override
    protected ItemStorageLayout createInventoryLayout() {
        return ItemStorageLayout.builder()
            .add(OUTPUT, SlotTemplates.output(64))
            // GUI only
            .add(FILTER, slot -> slot.guiRules(new SimpleSlotAccessRules<>(true, true)).externalRules(new SimpleSlotAccessRules<>(false, false))
                .capacity(1).filter((i, item) -> item.toStack().getCapability(EnderIOCapabilities.ITEM_FILTER) != null))
            // keep last, slot indices are saved
            .add(CAPACITOR, MachineSlotTemplates.capacitor())
            .build();
    }

    // The IO config screen edits the quarry ports
    @Override
    public List<BlockPos> getConfigurables() {
        if (level != null) {
            CrystalObeliskBlockEntity obelisk = CrystalObeliskBlockEntity.findForRing(level, worldPosition);
            List<BlockPos> ports = obelisk != null ? obelisk.findPorts() : List.of();
            if (!ports.isEmpty()) {
                return ports;
            }
        }
        return List.of(worldPosition);
    }

    // No IO of its own, everything goes through the ports
    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.DISABLED);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new QuarryControllerMenu(containerId, playerInventory, this);
    }

    // endregion

    // region Serialization

    @Override
    protected void saveAdditionalSynced(ValueOutput tag) {
        super.saveAdditionalSynced(tag);
        tag.storeNullable(PAD_POS, BlockPos.CODEC, padPos);
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.storeNullable(OBELISK_POS, BlockPos.CODEC, obeliskPos);
        queue.save(tag);
        tag.putBoolean(RUNNING, running);
        tag.putInt(CURSOR_Y, cursorY);
        tag.putInt(CURSOR_INDEX, cursorIndex);
        tag.putBoolean(TOP_RESOLVED, topResolved);
        tag.putBoolean(WALL_DONE, wallDone);
        tag.putInt(WALL_CHUNK_INDEX, wallChunkIndex);
        tag.putInt(ENERGY_PROGRESS, energyProgress);
        tag.putInt(BLOCKS_MINED, blocksMined);
        tag.putInt(VOID_WEAR, voidWearBlocks);
        tag.putInt(HEAT, heat);
        tag.putInt(DEPTH_MODE, depthMode.ordinal());
        if (floorY != null) {
            tag.putInt(FLOOR_Y, floorY);
        }
        tag.putIntArray(ASTEROID_QUEUE, asteroidQueue.stream().mapToInt(Integer::intValue).toArray());
        tag.putInt(SPACE_SCAN, spaceScanTicks);
        tag.putInt(ANALYZE_ID, analyzeId);
        tag.putInt(ANALYZE_TICKS, analyzeTicks);
        if (ticketedChunk != null) {
            tag.putLong(TICKET_CHUNK, ticketedChunk.pack());
        }
        tag.store(BASE_TICKETS, Codec.LONG_STREAM, baseTickets.stream().mapToLong(ChunkPos::pack));
        tag.putChild(FLUIDS, fluidStorage);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        obeliskPos = tag.read(OBELISK_POS, BlockPos.CODEC).orElse(null);
        padPos = tag.read(PAD_POS, BlockPos.CODEC).orElse(null);
        queue.load(tag);
        running = tag.getBooleanOr(RUNNING, false);
        cursorY = tag.getIntOr(CURSOR_Y, Integer.MAX_VALUE);
        cursorIndex = tag.getIntOr(CURSOR_INDEX, 0);
        topResolved = tag.getBooleanOr(TOP_RESOLVED, false);
        // old saves without the flag
        wallDone = tag.getBooleanOr(WALL_DONE, topResolved);
        wallChunkIndex = tag.getIntOr(WALL_CHUNK_INDEX, queue.index());
        energyProgress = tag.getIntOr(ENERGY_PROGRESS, 0);
        blocksMined = tag.getIntOr(BLOCKS_MINED, 0);
        voidWearBlocks = tag.getIntOr(VOID_WEAR, 0);
        heat = tag.getIntOr(HEAT, 0);
        depthMode = DepthMode.byOrdinal(tag.getIntOr(DEPTH_MODE, 0));
        floorY = tag.getInt(FLOOR_Y).orElse(null);
        asteroidQueue.clear();
        for (int id : tag.getIntArray(ASTEROID_QUEUE).orElse(new int[0])) {
            asteroidQueue.add(id);
        }
        spaceScanTicks = tag.getIntOr(SPACE_SCAN, 0);
        analyzeId = tag.getIntOr(ANALYZE_ID, -1);
        analyzeTicks = tag.getIntOr(ANALYZE_TICKS, 0);
        ticketedChunk = tag.getLong(TICKET_CHUNK).map(ChunkPos::unpack).orElse(null);
        baseTickets.clear();
        tag.read(BASE_TICKETS, Codec.LONG_STREAM).ifPresent(chunks -> chunks.forEach(chunk -> baseTickets.add(ChunkPos.unpack(chunk))));
        tag.readChild(FLUIDS, fluidStorage);
    }

    // endregion
}

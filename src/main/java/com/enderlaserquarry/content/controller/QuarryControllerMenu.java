package com.enderlaserquarry.content.controller;

import com.enderio.enderio.foundation.fluid.FluidStorageInfo;
import com.enderio.enderio.foundation.fluid.FluidStorageSyncSlot;
import com.enderio.core.common.network.menu.IntSyncSlot;
import com.enderio.core.common.network.menu.StringSyncSlot;
import com.enderio.enderio.foundation.menu.PoweredMachineMenu;
import com.enderio.enderio.foundation.menu.MachineSlot;
import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class QuarryControllerMenu extends PoweredMachineMenu<QuarryControllerBlockEntity> {
    // Layout shared with the screen
    public static final int OUTPUT_X = 158;
    public static final int OUTPUT_Y = 18;
    public static final int OUTPUT_COLUMNS = 3;
    public static final int PLAYER_INV_X = 52;
    public static final int PLAYER_INV_Y = 176;
    public static final int FILTER_X = 158;
    public static final int FILTER_Y = 96;
    public static final int ENERGY_X = 219;
    public static final int ENERGY_Y = 18;
    public static final int ENERGY_HEIGHT = 72;
    public static final int CAPACITOR_X = 216;
    public static final int CAPACITOR_Y = 96;
    public static final int COOLANT_X = 234;
    public static final int COOLANT_Y = 18;
    public static final int COOLANT_HEIGHT = 72;
    public static final int XP_X = 249;

    private final IntSyncSlot hasTarget;
    private final IntSyncSlot targetX;
    private final IntSyncSlot targetZ;
    private final IntSyncSlot running;
    private final IntSyncSlot blocksMined;
    private final IntSyncSlot cursorY;
    private final IntSyncSlot walling;
    private final StringSyncSlot queue;
    private final IntSyncSlot chunkIndex;
    private final IntSyncSlot maxChunks;
    private final IntSyncSlot blocksPerTick;
    private final IntSyncSlot ownUsage;
    private final IntSyncSlot depthMode;
    private final IntSyncSlot floorY;
    private final IntSyncSlot voidMode;
    private final StringSyncSlot asteroidQueue;
    private final IntSyncSlot spaceScan;
    private final IntSyncSlot spaceScanTotal;
    private final IntSyncSlot scanStalled;
    // Dew of the Void in the Void Satellite (-1 = none up), its tank, what a scan takes
    private final IntSyncSlot satelliteDew;
    private final IntSyncSlot dewCapacity;
    private final IntSyncSlot scanDew;
    private final IntSyncSlot analyzeId;
    private final IntSyncSlot analyzeProgress;
    private final IntSyncSlot analyzeStalled;
    private final FluidStorageSyncSlot coolant;
    private final FluidStorageSyncSlot xp;
    private final IntSyncSlot portItemRate;
    private final IntSyncSlot energyIntake;
    @Nullable
    private String parsedQueueText;
    private List<ChunkPos> parsedQueue = List.of();
    @Nullable
    private String parsedAsteroidText;
    private List<Integer> parsedAsteroids = List.of();

    public QuarryControllerMenu(int containerId, Inventory playerInventory, QuarryControllerBlockEntity be) {
        super(ModMenus.QUARRY_CONTROLLER.get(), containerId, playerInventory, be);

        hasTarget = addSyncSlot(IntSyncSlot.readOnly(() -> be.getWorkChunk() != null ? 1 : 0));
        targetX = addSyncSlot(IntSyncSlot.readOnly(() -> be.getWorkChunk() != null ? be.getWorkChunk().x() : 0));
        targetZ = addSyncSlot(IntSyncSlot.readOnly(() -> be.getWorkChunk() != null ? be.getWorkChunk().z() : 0));
        running = addSyncSlot(IntSyncSlot.readOnly(() -> be.isRunning() ? 1 : 0));
        blocksMined = addSyncSlot(IntSyncSlot.readOnly(be::getBlocksMined));
        cursorY = addSyncSlot(IntSyncSlot.readOnly(be::getCursorY));
        walling = addSyncSlot(IntSyncSlot.readOnly(() -> be.isBuildingWall() ? 1 : 0));
        queue = addSyncSlot(StringSyncSlot.readOnly(be::getTargetChunksEncoded));
        chunkIndex = addSyncSlot(IntSyncSlot.readOnly(be::getChunkIndex));
        maxChunks = addSyncSlot(IntSyncSlot.readOnly(() -> be.getTier().maxChunks()));
        blocksPerTick = addSyncSlot(IntSyncSlot.readOnly(() -> {
            var obelisk = be.getObelisk();
            return obelisk != null ? obelisk.getBlocksPerTick() : 0;
        }));
        ownUsage = addSyncSlot(IntSyncSlot.readOnly(be::getOwnUsage));
        depthMode = addSyncSlot(IntSyncSlot.readOnly(() -> be.getDepthMode().ordinal()));
        floorY = addSyncSlot(IntSyncSlot.readOnly(be::getFloorY));
        voidMode = addSyncSlot(IntSyncSlot.readOnly(() -> be.isVoidMode() ? 1 : 0));
        asteroidQueue = addSyncSlot(StringSyncSlot.readOnly(() -> encodeIds(be.getAsteroidQueue())));
        spaceScan = addSyncSlot(IntSyncSlot.readOnly(be::getSpaceScanTicks));
        // common config isn't synced
        spaceScanTotal = addSyncSlot(IntSyncSlot.readOnly(QuarryConfig.VOID_SCAN_TICKS::get));
        scanStalled = addSyncSlot(IntSyncSlot.readOnly(() -> be.isScanStalled() ? 1 : 0));
        satelliteDew = addSyncSlot(IntSyncSlot.readOnly(() -> {
            var pad = be.getLinkedPad();
            return pad != null && pad.isVoidSatelliteUp() ? pad.getSatelliteDew() : -1;
        }));
        dewCapacity = addSyncSlot(IntSyncSlot.readOnly(com.enderlaserquarry.content.satellite.SatelliteItem::dewCapacity));
        scanDew = addSyncSlot(IntSyncSlot.readOnly(QuarryConfig.VOID_SCAN_DEW::get));
        analyzeId = addSyncSlot(IntSyncSlot.readOnly(be::getAnalyzeId));
        analyzeProgress = addSyncSlot(IntSyncSlot.readOnly(() -> Math.round(be.getAnalyzeProgress() * 1000)));
        analyzeStalled = addSyncSlot(IntSyncSlot.readOnly(() -> be.isAnalyzeStalled() ? 1 : 0));
        coolant = addSyncSlot(FluidStorageSyncSlot.readOnly(() -> be.getCoolantTank().info()));
        xp = addSyncSlot(FluidStorageSyncSlot.readOnly(() -> be.getXpTank().info()));
        portItemRate = addSyncSlot(IntSyncSlot.readOnly(be::getPortItemRate));
        energyIntake = addSyncSlot(IntSyncSlot.readOnly(be::getEnergyIntake));
        for (int i = 0; i < ORBIT_SLOTS; i++) {
            int index = i;
            orbit[i] = addSyncSlot(IntSyncSlot.readOnly(() -> be.getOrbitInfo(index)));
        }

        addSlots();
    }

    public QuarryControllerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.QUARRY_CONTROLLER.get(), containerId, playerInventory, buf, ModBlockEntities.QUARRY_CONTROLLER.get());

        hasTarget = addSyncSlot(IntSyncSlot.standalone());
        targetX = addSyncSlot(IntSyncSlot.standalone());
        targetZ = addSyncSlot(IntSyncSlot.standalone());
        running = addSyncSlot(IntSyncSlot.standalone());
        blocksMined = addSyncSlot(IntSyncSlot.standalone());
        cursorY = addSyncSlot(IntSyncSlot.standalone());
        walling = addSyncSlot(IntSyncSlot.standalone());
        queue = addSyncSlot(StringSyncSlot.standalone());
        chunkIndex = addSyncSlot(IntSyncSlot.standalone());
        maxChunks = addSyncSlot(IntSyncSlot.standalone());
        blocksPerTick = addSyncSlot(IntSyncSlot.standalone());
        ownUsage = addSyncSlot(IntSyncSlot.standalone());
        depthMode = addSyncSlot(IntSyncSlot.standalone());
        floorY = addSyncSlot(IntSyncSlot.standalone());
        voidMode = addSyncSlot(IntSyncSlot.standalone());
        asteroidQueue = addSyncSlot(StringSyncSlot.standalone());
        spaceScan = addSyncSlot(IntSyncSlot.standalone());
        spaceScanTotal = addSyncSlot(IntSyncSlot.standalone());
        scanStalled = addSyncSlot(IntSyncSlot.standalone());
        satelliteDew = addSyncSlot(IntSyncSlot.standalone());
        dewCapacity = addSyncSlot(IntSyncSlot.standalone());
        scanDew = addSyncSlot(IntSyncSlot.standalone());
        analyzeId = addSyncSlot(IntSyncSlot.standalone());
        analyzeProgress = addSyncSlot(IntSyncSlot.standalone());
        analyzeStalled = addSyncSlot(IntSyncSlot.standalone());
        coolant = addSyncSlot(FluidStorageSyncSlot.standalone());
        xp = addSyncSlot(FluidStorageSyncSlot.standalone());
        portItemRate = addSyncSlot(IntSyncSlot.standalone());
        energyIntake = addSyncSlot(IntSyncSlot.standalone());
        for (int i = 0; i < ORBIT_SLOTS; i++) {
            orbit[i] = addSyncSlot(IntSyncSlot.standalone());
        }

        addSlots();
    }

    // region Orbit transfer (see QuarryControllerBlockEntity.getOrbitInfo)

    public static final int ORBIT_SLOTS = 12;
    private final IntSyncSlot[] orbit = new IntSyncSlot[ORBIT_SLOTS];

    // chunks the satellite may go from its pad, 0 = it can't move (or no pad)
    public int getTransferRange() {
        return orbit[0].get();
    }

    public int getOrbitDx() {
        return orbit[1].get();
    }

    public int getOrbitDz() {
        return orbit[2].get();
    }

    public int getTransferDx() {
        return orbit[3].get();
    }

    public int getTransferDz() {
        return orbit[4].get();
    }

    // 0 idle, 1 the rocket brings fuel, 2 flying over
    public int getTransferState() {
        return orbit[5].get();
    }

    public float getTransferProgress() {
        return orbit[6].get() / 1000f;
    }

    public int getPadFuel() {
        return orbit[7].get();
    }

    public boolean isRocketReady() {
        return orbit[8].get() != 0;
    }

    // fuel a transfer burns before the per-chunk part
    public int getTransferBaseFuel() {
        return orbit[9].get();
    }

    public int getFuelPerChunk() {
        return orbit[10].get();
    }

    public boolean hasSatelliteInOrbit() {
        return orbit[11].get() != 0;
    }

    // endregion

    private void addSlots() {
        var inventory = getMachineInventory();
        var outputs = QuarryControllerBlockEntity.OUTPUT;
        for (int i = 0; i < outputs.count(); i++) {
            int x = OUTPUT_X + (i % OUTPUT_COLUMNS) * 18;
            int y = OUTPUT_Y + (i / OUTPUT_COLUMNS) * 18;
            addSlot(new MachineSlot(inventory, outputs.slot(i), x, y));
        }

        addSlot(new MachineSlot(inventory, QuarryControllerBlockEntity.FILTER, FILTER_X, FILTER_Y));
        addCapacitorSlot(CAPACITOR_X, CAPACITOR_Y);

        addPlayerInventorySlots(PLAYER_INV_X, PLAYER_INV_Y);
    }

    @Nullable
    public ChunkPos getTargetChunk() {
        return hasTarget.get() != 0 ? new ChunkPos(targetX.get(), targetZ.get()) : null;
    }

    public boolean isRunning() {
        return running.get() != 0;
    }

    public int getBlocksMined() {
        return blocksMined.get();
    }

    public int getCursorY() {
        return cursorY.get();
    }

    public boolean isBuildingWall() {
        return walling.get() != 0;
    }

    public List<ChunkPos> getQueue() {
        // only parse when it changed, called several times per frame
        String encoded = queue.get();
        if (Objects.equals(encoded, parsedQueueText)) {
            return parsedQueue;
        }
        List<ChunkPos> chunks = new ArrayList<>();
        parsedQueueText = encoded;
        parsedQueue = Collections.unmodifiableList(chunks);
        if (encoded == null || encoded.isEmpty()) {
            return parsedQueue;
        }
        for (String part : encoded.split(";")) {
            int comma = part.indexOf(',');
            chunks.add(new ChunkPos(Integer.parseInt(part.substring(0, comma)), Integer.parseInt(part.substring(comma + 1))));
        }
        return parsedQueue;
    }

    public int getChunkIndex() {
        return chunkIndex.get();
    }

    public int getMaxChunks() {
        return maxChunks.get();
    }

    public int getOwnUsage() {
        return ownUsage.get();
    }

    public FluidStorageInfo getCoolant() {
        return coolant.get();
    }

    public FluidStorageInfo getXp() {
        return xp.get();
    }

    // what the controller capacitor allows
    public int getPortItemRate() {
        return portItemRate.get();
    }

    public int getEnergyIntake() {
        return energyIntake.get();
    }

    public boolean isVoidMode() {
        return voidMode.get() != 0;
    }

    public List<Integer> getAsteroidQueue() {
        String encoded = asteroidQueue.get();
        if (Objects.equals(encoded, parsedAsteroidText)) {
            return parsedAsteroids;
        }
        List<Integer> ids = new ArrayList<>();
        if (encoded != null && !encoded.isEmpty()) {
            for (String part : encoded.split(";")) {
                ids.add(Integer.parseInt(part));
            }
        }
        parsedAsteroidText = encoded;
        parsedAsteroids = Collections.unmodifiableList(ids);
        return parsedAsteroids;
    }

    // -1 when not scanning
    public float getSpaceScanProgress() {
        int left = spaceScan.get();
        int total = Math.max(1, spaceScanTotal.get());
        return left <= 0 ? -1 : 1 - Math.min(left, total) / (float) total;
    }

    public boolean isScanStalled() {
        return scanStalled.get() != 0;
    }

    public int getSatelliteDew() {
        return satelliteDew.get();
    }

    public int getDewCapacity() {
        return dewCapacity.get();
    }

    public int getScanDew() {
        return scanDew.get();
    }

    /** Asteroid being analyzed, -1 = none. */
    public int getAnalyzeId() {
        return analyzeId.get();
    }

    /** 0..1 */
    public float getAnalyzeProgress() {
        return Math.max(0, analyzeProgress.get()) / 1000f;
    }

    public boolean isAnalyzeStalled() {
        return analyzeStalled.get() != 0;
    }

    public DepthMode getDepthMode() {
        return DepthMode.byOrdinal(depthMode.get());
    }

    public int getFloorY() {
        return floorY.get();
    }

    public int getBlocksPerTick() {
        return blocksPerTick.get();
    }

    private static String encodeIds(List<Integer> ids) {
        var builder = new StringBuilder();
        for (int id : ids) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(id);
        }
        return builder.toString();
    }
}

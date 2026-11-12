package com.enderlaserquarry.content.dish;

import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.foundation.block.entity.MultiConfigurable;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.core.common.storage.layout.ItemStorageLayout;
import com.enderio.core.common.storage.slot.SingleResourceSlotKey;
import com.enderio.enderio.foundation.inventory.MachineSlotTemplates;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.QuarryStates;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import com.enderlaserquarry.content.obelisk.PowerUpSequence;
import com.enderlaserquarry.content.port.PortedMultiblock;
import com.enderlaserquarry.content.port.StructurePortBlockEntity;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import com.enderio.enderio.api.EnderIOCapabilities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;

/**
 * Satellite dish, middle of a 3x3 of dish plating. Takes RF through its ports and beams it into the linked obelisk's
 * crystal. Speed comes from the capacitor and the dish tier.
 */
public class SatelliteDishBlockEntity extends PoweredMachineBlockEntity implements MultiConfigurable, PortedMultiblock {
    // config base * tier dish speed, then Ender IO's capacitor scaling
    private static final Map<QuarryTier, QuadraticScalable> ENERGY_CAPACITY = new EnumMap<>(QuarryTier.class);
    private static final Map<QuarryTier, QuadraticScalable> ENERGY_USAGE = new EnumMap<>(QuarryTier.class);

    static {
        for (QuarryTier tier : QuarryTier.values()) {
            ENERGY_CAPACITY.put(tier, new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
                () -> (int) Math.min(Integer.MAX_VALUE, Math.round(QuarryConfig.DISH_CAPACITY.get() * tier.dishSpeed()))));
            ENERGY_USAGE.put(tier, new QuadraticScalable(CapacitorModifier.ENERGY_USE,
                () -> (int) Math.min(Integer.MAX_VALUE, Math.round(QuarryConfig.DISH_USAGE.get() * tier.dishSpeed()))));
        }
    }

    public static final TagKey<Block> FRAME_BLOCKS = TagKey.create(Registries.BLOCK, EnderLaserQuarry.rl("satellite_dish_frame"));

    // old name, kept for existing saves
    private static final String OBELISK_POS = "RelayPos";
    private static final String BEAMING = "Beaming";
    private static final String FORMED = "Formed";
    private static final String CAPACITOR_TIER = "CapacitorTier";
    private static final String POWERED = "Powered";

    private static final int BEAM_LINGER_TICKS = 10;
    private static final int STRUCTURE_CHECK_INTERVAL = 10;

    public static final float MODEL_SCALE = 0.65f;
    // From the model: pivot 24px above the block, emitter tip 24px out along the bowl axis.
    // Change together with MODEL_SCALE or the beam won't start at the emitter.
    public static final Vec3 DISH_PIVOT = new Vec3(0.5, 1 + 24 / 16.0 * MODEL_SCALE, 0.5);
    public static final double FOCUS_DISTANCE = 24 / 16.0 * MODEL_SCALE;

    private static final float PARKED_PITCH = 90;
    private static final float MIN_PITCH = -15;
    private static final float MAX_YAW_STEP = 3;
    private static final float MAX_PITCH_STEP = 2;
    private static final int HUM_INTERVAL = 40;
    private static final int USAGE_WINDOW = 20;

    @Nullable
    private BlockPos obeliskPos;
    // not MIN_VALUE, now - MIN_VALUE overflows
    private static final long NEVER = Long.MIN_VALUE / 2;
    private long lastBeamTick = NEVER;
    private boolean beaming;
    private boolean formed;
    private boolean platingTooLow;
    // lingers a bit so a trickle fed dish doesn't flicker
    private boolean powered;
    private long lastPoweredTick = NEVER;
    private int usedSum;
    private int usage;
    // 0 none, 1 basic, 2 double layer, 3 octadic
    private int capacitorTier;
    public static final int STRIP_WRONG_CAPACITOR = -1;

    // client animation
    private float yaw, prevYaw;
    private float pitch = PARKED_PITCH, prevPitch = PARKED_PITCH;
    private float spin, prevSpin;
    private float lastSequenceTicks = -1;

    public static final SingleResourceSlotKey<ItemResource> CAPACITOR = new SingleResourceSlotKey<>();

    public SatelliteDishBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SATELLITE_DISH.get(), pos, state, true, CapacitorSupport.REQUIRED, CAPACITOR, EnergyIOMode.Input,
            ENERGY_CAPACITY.get(tierOf(state)), ENERGY_USAGE.get(tierOf(state)));
    }

    private static QuarryTier tierOf(BlockState state) {
        return state.getBlock() instanceof Tiered tiered ? tiered.getTier() : QuarryTier.BASIC;
    }

    public QuarryTier getTier() {
        return tierOf(getBlockState());
    }

    // region Multiblock

    public boolean isFormed() {
        return formed;
    }

    // Strip colour: 0 none, 1-3 Ender IO level, 4+ Endergy capacitor, STRIP_WRONG_CAPACITOR when it won't work here
    public int getCapacitorTier() {
        return capacitorTier;
    }

    public boolean hasWrongCapacitor() {
        return capacitorTier == STRIP_WRONG_CAPACITOR;
    }

    // All 8 neighbours must be frame blocks of at least the dish's tier
    public boolean checkStructure() {
        return checkFrame() == FrameCheck.FORMED;
    }

    public boolean isPlatingTooLow() {
        return platingTooLow;
    }

    private enum FrameCheck { FORMED, INCOMPLETE, TIER_TOO_LOW }

    private FrameCheck checkFrame() {
        if (level == null) {
            return FrameCheck.INCOMPLETE;
        }

        boolean tierOk = true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockState state = level.getBlockState(worldPosition.offset(dx, 0, dz));
                if (!state.is(FRAME_BLOCKS)) {
                    return FrameCheck.INCOMPLETE;
                }
                // blocks added by datapacks have no tier
                QuarryTier platingTier = ModBlocks.dishFrameTier(state);
                if (platingTier != null && !platingTier.atLeast(getTier())) {
                    tierOk = false;
                }
            }
        }
        return tierOk ? FrameCheck.FORMED : FrameCheck.TIER_TOO_LOW;
    }

    private void updateStructure() {
        FrameCheck frame = checkFrame();
        boolean nowFormed = frame == FrameCheck.FORMED;
        platingTooLow = frame == FrameCheck.TIER_TOO_LOW;
        int nowTier = 0;
        if (isCapacitorInstalled()) {
            ItemStack capacitor = getCapacitorItem();
            int endergy = EndergyCapacitors.stripIndex(capacitor);
            nowTier = EndergyCapacitors.dishProblem(getTier(), capacitor) != null ? STRIP_WRONG_CAPACITOR
                : endergy > 0 ? endergy : Mth.clamp(Math.round(getCapacitorData().getModifier(CapacitorModifier.ENERGY_USE)), 1, 3);
        }
        if (nowFormed != formed || nowTier != capacitorTier) {
            formed = nowFormed;
            capacitorTier = nowTier;
            setChanged();
            syncToClients();
        }

        // only on change, every update sends a block update
        boolean incomplete = !formed && !platingTooLow;
        if (incomplete != getMachineStates().contains(QuarryStates.DISH_INCOMPLETE)) {
            updateMachineState(QuarryStates.DISH_INCOMPLETE, incomplete);
        }
        MachineState needsPlating = QuarryStates.dishNeedsPlating(getTier());
        if (platingTooLow != getMachineStates().contains(needsPlating)) {
            updateMachineState(needsPlating, platingTooLow);
        }
        MachineState capacitorProblem = EndergyCapacitors.dishProblem(getTier(), getCapacitorItem());
        for (MachineState state : new MachineState[] { QuarryStates.ENDERGY_NEEDS_ENDER_DISH, QuarryStates.CAPACITOR_UNSUPPORTED }) {
            boolean wanted = state == capacitorProblem;
            if (wanted != getMachineStates().contains(state)) {
                updateMachineState(state, wanted);
            }
        }
    }

    // endregion

    // region I/O ports

    @Override
    public @Nullable EnergyHandler getPortEnergyStorage() {
        return getEnergyStorage();
    }

    @Override
    public List<BlockPos> findPorts() {
        List<BlockPos> ports = new ArrayList<>();
        if (level != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = worldPosition.offset(dx, 0, dz);
                    if (level.getBlockEntity(pos) instanceof StructurePortBlockEntity) {
                        ports.add(pos);
                    }
                }
            }
        }
        return ports;
    }

    // IO config screen edits the ports
    @Override
    public List<BlockPos> getConfigurables() {
        List<BlockPos> ports = findPorts();
        return ports.isEmpty() ? List.of(worldPosition) : ports;
    }

    // No IO of its own, RF only through the ports
    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.DISABLED);
    }

    // endregion

    // region Linking

    @Nullable
    public BlockPos getObeliskPos() {
        return obeliskPos;
    }

    public void setObeliskPos(@Nullable BlockPos obeliskPos) {
        this.obeliskPos = obeliskPos == null ? null : obeliskPos.immutable();
        setChanged();
        syncToClients();
    }

    @Nullable
    public CrystalObeliskBlockEntity getObelisk() {
        if (level == null || obeliskPos == null || !level.isLoaded(obeliskPos)) {
            return null;
        }
        return level.getBlockEntity(obeliskPos) instanceof CrystalObeliskBlockEntity obelisk ? obelisk : null;
    }

    public @Nullable MachineState getDishProblem() {
        if (!formed) {
            return platingTooLow ? QuarryStates.dishNeedsPlating(getTier()) : QuarryStates.DISH_INCOMPLETE;
        }
        if (!isCapacitorInstalled()) {
            return MachineState.NO_CAPACITOR;
        }
        return EndergyCapacitors.dishProblem(getTier(), getCapacitorItem());
    }

    // endregion

    // region Laser power

    // Capped per call by the capacitor's max use
    public int drawLaserEnergy(int amount) {
        if (level == null || !formed || !canAct() || !isCapacitorInstalled()) {
            return 0;
        }

        int drawn = getEnergyStorage().consume(amount, null);
        if (drawn > 0) {
            usedSum += drawn;
            lastBeamTick = level.getGameTime();
        }
        return drawn;
    }

    // wears capacitors that wear (Endergy's Totemic), once per mined chunk
    // Ender IO 9 hands the extension a copy of the slot (its storage builds a new stack), so wear the stack and put it back
    public void wearCapacitor() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack capacitor = getInventory().getStack(CAPACITOR);
        var extension = capacitor.getCapability(EnderIOCapabilities.CAPACITOR_EXTENSION);
        if (extension != null) {
            extension.onMachineUsed(capacitor, serverLevel);
            getInventory().setStack(CAPACITOR, capacitor);
        }
    }

    // Capacitor level squared like Ender IO's machines, times the dish speed: Basic 1/4/9, Enhanced 2/6/14, Ender 2/8/18
    public int getBlocksPerTick() {
        if (!isCapacitorInstalled()) {
            return 0;
        }
        return blocksPerTick(getCapacitorData().getModifier(CapacitorModifier.ENERGY_USE), getTier());
    }

    public static int blocksPerTick(float capacitorLevel, QuarryTier tier) {
        if (capacitorLevel <= 0) {
            return 0;
        }
        double speed = QuarryConfig.BLOCKS_PER_TICK.get() * capacitorLevel * capacitorLevel * tier.dishSpeed();
        return Math.max(1, (int) Math.round(speed));
    }

    // Unpowered dishes don't count for the obelisk
    public boolean isPowered() {
        if (level != null && !level.isClientSide()) {
            return getEnergyStorage().getAmountAsInt() > 0;
        }
        return powered;
    }

    public int getUsage() {
        return usage;
    }

    public boolean isBeaming() {
        return beaming;
    }

    @Override
    public boolean isActive() {
        return beaming;
    }

    @Override
    public void serverTick() {
        super.serverTick();

        if (level != null && level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            updateStructure();
        }
        if (level != null && level.getGameTime() % USAGE_WINDOW == 0) {
            usage = usedSum / USAGE_WINDOW;
            usedSum = 0;
        }

        if (level != null) {
            if (getEnergyStorage().getAmountAsInt() > 0) {
                lastPoweredTick = level.getGameTime();
            }
            boolean nowPowered = level.getGameTime() - lastPoweredTick <= BEAM_LINGER_TICKS;
            if (nowPowered != powered) {
                powered = nowPowered;
                syncToClients();
            }
        }

        boolean nowBeaming = level != null && level.getGameTime() - lastBeamTick <= BEAM_LINGER_TICKS;
        if (nowBeaming != beaming) {
            beaming = nowBeaming;
            syncToClients();
        }
    }

    // endregion

    // region Dish animation (client)

    @Override
    public void clientTick() {
        super.clientTick();

        prevYaw = yaw;
        prevPitch = pitch;
        prevSpin = spin;

        CrystalObeliskBlockEntity obelisk = getPoweringObelisk();
        float sequence = obelisk == null ? -1 : obelisk.getSequenceTicks(0);
        if (sequence >= 0) {
            powerUpSounds(lastSequenceTicks, sequence);
        }
        lastSequenceTicks = sequence;
        float spool = getSpool(0);

        float targetYaw = yaw;
        float targetPitch;
        Vec3 aim = formed && spool > 0 ? getAimPoint(0) : null;
        if (aim != null) {
            Vec3 toAim = aim.subtract(Vec3.atLowerCornerOf(worldPosition).add(DISH_PIVOT));
            double horizontal = Math.sqrt(toAim.x * toAim.x + toAim.z * toAim.z);
            if (horizontal > 1.0E-3) {
                targetYaw = (float) Math.toDegrees(Math.atan2(toAim.x, toAim.z));
            }
            targetPitch = (float) Math.toDegrees(Math.atan2(toAim.y, horizontal));
        } else {
            // idle sweep
            float time = level == null ? 0 : level.getGameTime();
            targetYaw = yaw + 0.6f;
            targetPitch = 45 + 15 * Mth.sin(time * 0.02f);
        }

        if (getBeamPower(0) >= 1 && level != null && level.getGameTime() % HUM_INTERVAL == 0) {
            // hum while firing
            Vec3 focus = Vec3.atLowerCornerOf(worldPosition).add(getFocus(0));
            level.playLocalSound(focus.x, focus.y, focus.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.5f, 0.75f, false);
        }

        targetPitch = Mth.clamp(targetPitch, MIN_PITCH, PARKED_PITCH);
        // turn faster while powering up
        float turn = spool > 0 && !beaming ? 3 : 1;
        yaw += Mth.clamp(Mth.wrapDegrees(targetYaw - yaw), -MAX_YAW_STEP * turn, MAX_YAW_STEP * turn);
        pitch += Mth.clamp(targetPitch - pitch, -MAX_PITCH_STEP * turn, MAX_PITCH_STEP * turn);
        spin += formed ? 2 + 16 * spool : 0;

        // wrap, and prev too so the lerp doesn't jump
        if (yaw > 180 || yaw < -180) {
            float wrapped = Mth.wrapDegrees(yaw);
            prevYaw += wrapped - yaw;
            yaw = wrapped;
        }
        if (spin > 360) {
            spin -= 360;
            prevSpin -= 360;
        }
    }

    private void powerUpSounds(float before, float now) {
        Vec3 focus = Vec3.atLowerCornerOf(worldPosition).add(getFocus(0));
        if (CrystalObeliskBlockEntity.crossed(before, now, 0)) {
            level.playLocalSound(focus.x, focus.y, focus.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 0.7f, false);
        }
        if (now < PowerUpSequence.SPOOL_END && CrystalObeliskBlockEntity.crossedEvery(before, now, 5)) {
            level.playLocalSound(focus.x, focus.y, focus.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.35f,
                0.5f + 1.5f * PowerUpSequence.dishSpool(now), false);
        }
        if (CrystalObeliskBlockEntity.crossed(before, now, PowerUpSequence.DISH_BEAM_START)) {
            level.playLocalSound(focus.x, focus.y, focus.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1f, 1.5f, false);
        }
        if (now >= PowerUpSequence.DISH_BEAM_START && now < PowerUpSequence.DISH_BEAM_END
            && CrystalObeliskBlockEntity.crossedEvery(before, now, 7)) {
            level.playLocalSound(focus.x, focus.y, focus.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f,
                1.2f + level.getRandom().nextFloat() * 0.8f, false);
        }
    }

    @Nullable
    private CrystalObeliskBlockEntity getPoweringObelisk() {
        if (!formed || capacitorTier == 0 || hasWrongCapacitor() || !isPowered()) {
            return null;
        }
        CrystalObeliskBlockEntity obelisk = getObelisk();
        return obelisk != null && obelisk.hasDish(worldPosition) ? obelisk : null;
    }

    public float getSpool(float partialTick) {
        if (beaming) {
            return 1;
        }
        CrystalObeliskBlockEntity obelisk = getPoweringObelisk();
        return obelisk == null ? 0 : obelisk.getDishSpool(partialTick);
    }

    public float getBeamPower(float partialTick) {
        if (beaming) {
            return 1;
        }
        CrystalObeliskBlockEntity obelisk = getPoweringObelisk();
        return obelisk == null ? 0 : obelisk.getDishBeam(partialTick);
    }

    @Nullable
    public Vec3 getAimPoint(float partialTick) {
        if (level == null || obeliskPos == null) {
            return null;
        }
        if (!level.isLoaded(obeliskPos)) {
            return Vec3.atCenterOf(obeliskPos);
        }
        CrystalObeliskBlockEntity obelisk = getObelisk();
        return obelisk != null ? obelisk.getCrystalCenter() : null;
    }

    public float getYaw(float partialTick) {
        return Mth.lerp(partialTick, prevYaw, yaw);
    }

    public float getPitch(float partialTick) {
        return Mth.lerp(partialTick, prevPitch, pitch);
    }

    public float getSpin(float partialTick) {
        return Mth.lerp(partialTick, prevSpin, spin);
    }

    // Where the beam leaves the dish
    public Vec3 getFocus(float partialTick) {
        float yawRad = getYaw(partialTick) * Mth.DEG_TO_RAD;
        float pitchRad = getPitch(partialTick) * Mth.DEG_TO_RAD;
        Vec3 axis = new Vec3(Mth.sin(yawRad) * Mth.cos(pitchRad), Mth.sin(pitchRad), Mth.cos(yawRad) * Mth.cos(pitchRad));
        return DISH_PIVOT.add(axis.scale(FOCUS_DISTANCE));
    }

    // endregion

    @Override
    protected ItemStorageLayout createInventoryLayout() {
        // Endergy capacitors go into any dish, below Ender they show an error instead of working
        return ItemStorageLayout.builder().add(CAPACITOR, MachineSlotTemplates.capacitor()).build();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SatelliteDishMenu(containerId, playerInventory, this);
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // region Serialization

    @Override
    protected void saveAdditionalSynced(ValueOutput tag) {
        super.saveAdditionalSynced(tag);
        tag.storeNullable(OBELISK_POS, BlockPos.CODEC, obeliskPos);
        tag.putBoolean(BEAMING, beaming);
        tag.putBoolean(FORMED, formed);
        tag.putInt(CAPACITOR_TIER, capacitorTier);
        tag.putBoolean(POWERED, powered);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        obeliskPos = tag.read(OBELISK_POS, BlockPos.CODEC).orElse(null);
        beaming = tag.getBooleanOr(BEAMING, false);
        formed = tag.getBooleanOr(FORMED, false);
        capacitorTier = tag.getIntOr(CAPACITOR_TIER, 0);
        powered = tag.getBooleanOr(POWERED, false);
    }

    // endregion
}

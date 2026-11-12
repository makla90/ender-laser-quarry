package com.enderlaserquarry.content.obelisk;

import net.minecraft.server.level.ServerLevel;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.QuarryStates;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.port.PortedMultiblock;
import com.enderlaserquarry.content.port.StructurePortBlockEntity;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The crystal obelisk, core of the laser quarry. Stands on the middle of the 5x5 floor (controller on the
 * ring), holds the crystal and combines the linked dishes into one beam up to the satellite.
 */
public class CrystalObeliskBlockEntity extends BlockEntity implements PortedMultiblock, MenuProvider {
    public static final TagKey<Block> FRAME_BLOCKS = TagKey.create(Registries.BLOCK, EnderLaserQuarry.rl("laser_quarry_frame"));
    public static final int RADIUS = 2;

    public static final Vec3 CRYSTAL_CENTER = new Vec3(0.5, 1.15, 0.5);

    private static final String CRYSTAL = "Crystal";
    private static final String HAS_CRYSTAL = "HasCrystal";
    private static final String VOID_CRYSTAL = "VoidCrystal";
    private static final String FORMED = "Formed";
    private static final String BEAMING = "Beaming";
    private static final String HOT = "RunningHot";
    private static final String BEAM_TIER = "BeamTier";
    private static final String SATELLITE_POS = "SatellitePos";
    private static final String DISHES = "Dishes";
    private static final String CONTROLLER_POS = "ControllerPos";
    private static final String WEAR_BLOCKS = "CrystalWear";
    private static final int REPAIR_INTERVAL = 20;
    private static final String POWER_UP_START = "PowerUpStart";

    private static final int STRUCTURE_CHECK_INTERVAL = 10;
    private static final int BEAM_LINGER_TICKS = 10;
    // powers down this many ticks after the controller stops asking
    private static final int POWER_REQUEST_TIMEOUT = 5;
    private static final int RESUME_GRACE_TICKS = 100;
    private static final long POWERED_DOWN = Long.MIN_VALUE;

    // server only, clients just get the flags
    private ItemStack crystal = ItemStack.EMPTY;
    // blocks worn since the last durability point
    private int wearBlocks;
    // runtime, for tests: all blocks counted
    private long wornBlocksTotal;
    // wear marks the obelisk dirty once per tick, not once per block
    private long wearDirtyTick = Long.MIN_VALUE / 2;
    private int debugBlocksPerPoint;
    private boolean hasCrystal;
    private boolean voidCrystal;
    private boolean formed;
    // null = formed
    @Nullable
    private MachineState structureProblem = QuarryStates.OBELISK_INCOMPLETE;
    private boolean beaming;
    // not MIN_VALUE, now - MIN_VALUE overflows
    private static final long NEVER = Long.MIN_VALUE / 2;
    private long lastBeamTick = NEVER;
    // synced: worn without coolant lately
    private boolean hot;
    private QuarryTier beamTier = QuarryTier.BASIC;
    private long lastHotTick = NEVER;
    // linked launch pad, synced for the uplink beam
    @Nullable
    private BlockPos satellitePos;
    private final List<BlockPos> dishes = new ArrayList<>();
    // cached per tick
    @Nullable
    private List<SatelliteDishBlockEntity> activeDishes;
    private long activeDishesTick;
    @Nullable
    private BlockPos controllerPos;
    private boolean structureChecked;
    // synced, not saved
    private long powerUpStart = POWERED_DOWN;
    private long lastPowerRequest;
    private boolean powerUpInTests;
    private boolean resumeGrace;

    // client animation
    private float spin, prevSpin;
    private float charge, prevCharge;
    private float heat, prevHeat;
    private float lastSequenceTicks = -1;
    private boolean wasPowered;

    public CrystalObeliskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_OBELISK.get(), pos, state);
    }

    // region Crystal

    public boolean hasCrystal() {
        return hasCrystal;
    }

    // Server only
    public ItemEnchantments getCrystalEnchantments() {
        return crystal.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean hasVoidCrystal() {
        return hasCrystal && voidCrystal;
    }

    // Server only
    public ItemStack getCrystal() {
        return crystal;
    }

    public boolean isCrystalBroken() {
        return !crystal.isEmpty() && LaserCrystalItem.isBroken(crystal);
    }

    public boolean isCrystalWorn() {
        return !crystal.isEmpty() && LaserCrystalItem.isWorn(crystal);
    }

    public void wearCrystal(int blocks) {
        wearCrystal(blocks, 1);
    }

    // Each mined block or deleted liquid; one durability point per blocksPerPoint (Unbreaking may skip it).
    // multiplier > 1 = running hot (no coolant)
    public void wearCrystal(int blocks, int multiplier) {
        if (blocks <= 0 || crystal.isEmpty() || !(crystal.getItem() instanceof LaserCrystalItem item)
            || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        wornBlocksTotal += blocks;
        if (multiplier > 1) {
            lastHotTick = level.getGameTime();
        }
        int perPoint = debugBlocksPerPoint > 0 ? debugBlocksPerPoint : item.blocksPerPoint();
        wearBlocks += blocks * multiplier;
        int points = wearBlocks / perPoint;
        if (points <= 0) {
            markWearDirty();
            return;
        }
        wearBlocks -= points * perPoint;
        int damage = 0;
        for (int i = 0; i < points; i++) {
            damage += EnchantmentHelper.processDurabilityChange(serverLevel, crystal, 1);
        }
        if (damage > 0) {
            boolean wasBroken = LaserCrystalItem.isBroken(crystal);
            // never destroyed, stops at 1 durability
            crystal.setDamageValue(Math.min(crystal.getMaxDamage() - 1, crystal.getDamageValue() + damage));
            if (!wasBroken && LaserCrystalItem.isBroken(crystal)) {
                level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1, 0.8f);
            }
        }
        markWearDirty();
    }

    private void markWearDirty() {
        long now = level.getGameTime();
        if (now != wearDirtyTick) {
            wearDirtyTick = now;
            setChanged();
        }
    }

    // Mending (or any repair-with-XP enchantment) takes liquid XP from the controller's tank, vanilla rate
    private void repairFromXp(ServerLevel serverLevel) {
        // vanilla returns the XP unchanged for items without the effect, so check it first
        if (crystal.isEmpty() || !crystal.isDamaged() || !EnchantmentHelper.has(crystal, EnchantmentEffectComponents.REPAIR_WITH_XP)) {
            return;
        }
        QuarryControllerBlockEntity controller = getController();
        if (controller == null) {
            return;
        }
        int budget = QuarryConfig.MENDING_XP_PER_SECOND.get() * REPAIR_INTERVAL / 20;
        boolean repaired = false;
        while (budget-- > 0 && crystal.isDamaged() && controller.getXpTank().getFluidAmount() >= ExperienceUtil.EXP_TO_FLUID) {
            int durability = EnchantmentHelper.modifyDurabilityToRepairFromXp(serverLevel, crystal, 1);
            if (durability <= 0) {
                break;
            }
            crystal.setDamageValue(Math.max(0, crystal.getDamageValue() - durability));
            QuarryControllerBlockEntity.XP.drain(controller, ExperienceUtil.EXP_TO_FLUID, IFluidHandler.FluidAction.EXECUTE);
            repaired = true;
        }
        if (repaired) {
            setChanged();
        }
    }

    public long getWornBlocksTotal() {
        return wornBlocksTotal;
    }

    // tests: blocks per durability point instead of the config, 0 = config
    public void debugSetBlocksPerPoint(int blocks) {
        debugBlocksPerPoint = blocks;
    }

    public boolean insertCrystal(ItemStack stack) {
        if (!crystal.isEmpty() || !(stack.getItem() instanceof LaserCrystalItem)) {
            return false;
        }
        crystal = stack.split(1);
        crystalChanged();
        return true;
    }

    public ItemStack removeCrystal() {
        ItemStack removed = crystal;
        crystal = ItemStack.EMPTY;
        crystalChanged();
        return removed;
    }

    private void crystalChanged() {
        hasCrystal = !crystal.isEmpty();
        voidCrystal = crystal.getItem() instanceof VoidLaserCrystalItem;
        if (level != null) {
            level.playSound(null, worldPosition, hasCrystal ? SoundEvents.AMETHYST_BLOCK_PLACE : SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.BLOCKS, 1, 1.2f);
        }
        setChanged();
        syncToClients();
        updateGlow();
    }

    // Light strips in the crystal's colours
    private void updateGlow() {
        if (level == null || level.isClientSide()) {
            return;
        }
        CrystalGlow glow = CrystalGlow.of(crystal);
        CrystalGlow.apply(level, worldPosition, glow);
        applyFloorGlow(level, worldPosition, glow);
    }

    public static void applyFloorGlow(Level level, BlockPos pos, CrystalGlow glow) {
        if (level.isClientSide()) {
            return;
        }
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                CrystalGlow.apply(level, pos.offset(dx, -1, dz), glow);
            }
        }
    }

    // GUI only, no capability
    public IItemHandlerModifiable getCrystalSlot() {
        return crystalSlot;
    }

    private final IItemHandlerModifiable crystalSlot = new IItemHandlerModifiable() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return crystal;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            crystal = stack;
            crystalChanged();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!crystal.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack rest = stack.copy();
            if (!simulate) {
                insertCrystal(rest);
            } else {
                rest.shrink(1);
            }
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || crystal.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return simulate ? crystal.copy() : removeCrystal();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof LaserCrystalItem;
        }
    };

    // region Menu

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CrystalObeliskMenu(containerId, inventory, this);
    }

    // endregion

    public void dropContents() {
        if (level != null && !crystal.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, crystal);
            crystal = ItemStack.EMPTY;
        }
    }

    // endregion

    // region Multiblock

    public boolean isFormed() {
        return formed;
    }

    @Nullable
    public MachineState getStructureProblem() {
        return structureProblem;
    }

    public void updateStructure() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean frameOk = true;
        // lowest tier in the floor, must be at least the controller's
        QuarryTier floorTier = QuarryTier.values()[QuarryTier.values().length - 1];
        int controllers = 0;
        BlockPos nowController = null;

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos pos = worldPosition.offset(dx, -1, dz);
                BlockState state = level.getBlockState(pos);
                if (state.is(FRAME_BLOCKS)) {
                    // blocks added by datapacks have no tier
                    QuarryTier tier = ModBlocks.quarryFloorTier(state);
                    if (tier != null && tier.ordinal() < floorTier.ordinal()) {
                        floorTier = tier;
                    }
                    continue;
                }
                // the controller can be anywhere on the ring
                boolean outer = Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS;
                BlockEntity be = outer ? level.getBlockEntity(pos) : null;
                if (be instanceof QuarryControllerBlockEntity) {
                    controllers++;
                    if (nowController == null || pos.equals(controllerPos)) {
                        nowController = pos.immutable();
                    }
                } else {
                    frameOk = false;
                }
            }
        }

        if (!frameOk || controllers > 1) {
            structureProblem = QuarryStates.OBELISK_INCOMPLETE;
        } else if (controllers == 0) {
            structureProblem = QuarryStates.NO_CONTROLLER;
        } else if (level.getBlockEntity(nowController) instanceof QuarryControllerBlockEntity controller
            && !floorTier.atLeast(controller.getTier())) {
            structureProblem = QuarryStates.quarryNeedsPlating(controller.getTier());
        } else {
            structureProblem = null;
        }
        boolean nowFormed = structureProblem == null;

        // claim them even when incomplete, so the controller can show what's missing
        if (nowController != null && level.getBlockEntity(nowController) instanceof QuarryControllerBlockEntity controller) {
            controller.setObeliskPos(worldPosition);
        }
        // new plating has no glow yet
        updateGlow();

        boolean ringChanged = !Objects.equals(nowController, controllerPos);
        controllerPos = nowController;

        // forget relinked or broken dishes, keep unloaded ones
        activeDishes = null;
        boolean dishesChanged = dishes.removeIf(pos -> level.isLoaded(pos)
            && !(level.getBlockEntity(pos) instanceof SatelliteDishBlockEntity dish && worldPosition.equals(dish.getObeliskPos())));

        if (nowFormed != formed || dishesChanged || ringChanged) {
            formed = nowFormed;
            setChanged();
            syncToClients();
        }
    }

    // endregion

    // region Links

    // The linker sets both sides
    @Nullable
    public Component addDish(SatelliteDishBlockEntity dish) {
        if (dishes.contains(dish.getBlockPos())) {
            return null;
        }
        if (!isDishInRange(dish)) {
            return Component.translatable("message.ender_laser_quarry.linker.too_far", dish.getTier().dishRange());
        }
        if (!acceptsDishTier(dish.getTier())) {
            return Component.translatable("message.ender_laser_quarry.linker.dish_tier_too_high", getTier().displayName(),
                dish.getTier().displayName());
        }
        int max = getMaxDishes();
        if (dishes.size() >= max) {
            return Component.translatable("message.ender_laser_quarry.linker.too_many_dishes", max, getTier().displayName());
        }
        dishes.add(dish.getBlockPos().immutable());
        activeDishes = null;
        setChanged();
        syncToClients();
        return null;
    }

    public boolean hasDish(BlockPos pos) {
        return dishes.contains(pos);
    }

    public List<BlockPos> getDishes() {
        return List.copyOf(dishes);
    }

    // The controller's tier, highest without one so dishes can be linked first
    public QuarryTier getTier() {
        QuarryControllerBlockEntity controller = getController();
        return controller != null ? controller.getTier() : QuarryTier.highest();
    }

    public int getMaxDishes() {
        return getTier().maxDishes();
    }

    public List<SatelliteDishBlockEntity> getPoweredDishes() {
        return getActiveDishes().stream().filter(SatelliteDishBlockEntity::isPowered).toList();
    }

    // a chunk is done: capacitors that wear lose one use
    public void wearDishCapacitors() {
        getPoweredDishes().forEach(SatelliteDishBlockEntity::wearCapacitor);
    }

    // Usable dishes, first linked first, up to getMaxDishes
    public List<SatelliteDishBlockEntity> getActiveDishes() {
        if (level == null) {
            return List.of();
        }
        // asked several times per tick
        long now = level.getGameTime();
        if (activeDishes != null && activeDishesTick == now) {
            return activeDishes;
        }
        List<SatelliteDishBlockEntity> active = new ArrayList<>();
        activeDishes = Collections.unmodifiableList(active);
        activeDishesTick = now;
        QuarryTier tier = getTier();
        int max = tier.maxDishes();
        for (BlockPos pos : dishes) {
            if (active.size() >= max) {
                break;
            }
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SatelliteDishBlockEntity dish
                && worldPosition.equals(dish.getObeliskPos()) && dish.getDishProblem() == null && isDishInRange(dish)
                && tier.atLeast(dish.getTier())) {
                active.add(dish);
            }
        }
        return active;
    }

    // The pad is linked to the ring controller, the obelisk only keeps a synced copy for the uplink beam
    @Nullable
    public BlockPos getSatellitePos() {
        return satellitePos;
    }

    @Nullable
    public LaunchPadBlockEntity getSatellitePad() {
        if (level == null) {
            return null;
        }
        if (!level.isClientSide()) {
            QuarryControllerBlockEntity controller = getController();
            return controller != null ? controller.getPad() : null;
        }
        return satellitePos != null && level.isLoaded(satellitePos)
            && level.getBlockEntity(satellitePos) instanceof LaunchPadBlockEntity pad ? pad : null;
    }

    private void updateSatellitePos() {
        LaunchPadBlockEntity pad = getSatellitePad();
        BlockPos now = pad != null ? pad.getBlockPos() : null;
        QuarryControllerBlockEntity controller = getController();
        QuarryTier tier = controller != null ? controller.getTier() : QuarryTier.BASIC;
        if (!Objects.equals(now, satellitePos) || tier != beamTier) {
            satellitePos = now;
            beamTier = tier;
            setChanged();
            syncToClients();
        }
    }

    // Ports pass everything on to the controller
    @Override
    public @Nullable IItemHandler getPortItemHandler() {
        QuarryControllerBlockEntity controller = getController();
        return controller == null ? null : MachineBlockEntity.ITEM_HANDLER_PROVIDER.getCapability(controller, null);
    }

    @Override
    public @Nullable IFluidHandler getPortFluidHandler() {
        QuarryControllerBlockEntity controller = getController();
        return controller == null ? null : controller.getFluidHandler();
    }

    @Override
    public @Nullable IEnergyStorage getPortEnergyStorage() {
        QuarryControllerBlockEntity controller = getController();
        return controller == null ? null : controller.getPortEnergyStorage();
    }

    // the controller's capacitor sets what each port moves
    @Override
    public int getPortItemRate() {
        QuarryControllerBlockEntity controller = getController();
        return controller == null ? 0 : controller.getPortItemRate();
    }

    @Override
    public int getPortFluidRate() {
        QuarryControllerBlockEntity controller = getController();
        return controller == null ? 0 : controller.getPortFluidRate();
    }

    @Override
    public void onPortOutputLimited() {
        QuarryControllerBlockEntity controller = getController();
        if (controller != null) {
            controller.onPortOutputLimited();
        }
    }

    @Override
    public List<BlockPos> findPorts() {
        List<BlockPos> ports = new ArrayList<>();
        if (level != null) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    BlockPos pos = worldPosition.offset(dx, -1, dz);
                    if (level.getBlockEntity(pos) instanceof StructurePortBlockEntity) {
                        ports.add(pos);
                    }
                }
            }
        }
        return ports;
    }

    // The obelisk that has floorPos on its ring
    @Nullable
    public static CrystalObeliskBlockEntity findForRing(Level level, BlockPos floorPos) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS
                    && level.getBlockEntity(floorPos.offset(dx, 1, dz)) instanceof CrystalObeliskBlockEntity obelisk) {
                    return obelisk;
                }
            }
        }
        return null;
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Nullable
    public QuarryControllerBlockEntity getController() {
        if (level == null || controllerPos == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof QuarryControllerBlockEntity controller ? controller : null;
    }

    // endregion

    // region Dishes beam at the crystal

    public boolean isDishInRange(SatelliteDishBlockEntity dish) {
        return isInDishRange(worldPosition, dish.getBlockPos(), dish.getTier());
    }

    public static boolean isInDishRange(BlockPos obeliskPos, BlockPos dishPos, QuarryTier dishTier) {
        int range = dishTier.dishRange();
        return obeliskPos.distSqr(dishPos) <= (double) range * range;
    }

    public boolean acceptsDishTier(QuarryTier dishTier) {
        return getTier().atLeast(dishTier);
    }

    public Vec3 getCrystalCenter() {
        return Vec3.atLowerCornerOf(worldPosition).add(CRYSTAL_CENTER);
    }

    // endregion

    // region Firing (the controller on our ring fires through the crystal)

    public @Nullable MachineState getProblem() {
        if (structureProblem != null) {
            return structureProblem;
        }
        if (!hasCrystal) {
            return QuarryStates.NO_CRYSTAL;
        }
        if (isCrystalBroken()) {
            return QuarryStates.CRYSTAL_BROKEN;
        }
        if (isCrystalTierTooLow()) {
            return QuarryStates.CRYSTAL_TIER_TOO_LOW;
        }
        return getActiveDishes().isEmpty() ? QuarryStates.NO_DISHES : null;
    }

    // e.g. a Pulsating crystal over an Enhanced or Ender controller
    public boolean isCrystalTierTooLow() {
        QuarryControllerBlockEntity controller = getController();
        return hasCrystal && controller != null && crystal.getItem() instanceof LaserCrystalItem laser && !laser.worksWith(controller.getTier());
    }

    public boolean hasSky() {
        return level != null
            && level.getHeight(Heightmap.Types.WORLD_SURFACE, worldPosition.getX(), worldPosition.getZ()) <= worldPosition.getY() + 1;
    }

    // Split over the powered dishes by their speed. A dish that can't pay its part isn't covered by the
    // others, the quarry just gets slower.
    public int drawLaserEnergy(int amount) {
        if (level == null || getProblem() != null) {
            return 0;
        }

        List<SatelliteDishBlockEntity> powered = getPoweredDishes();
        long totalSpeed = 0;
        for (SatelliteDishBlockEntity dish : powered) {
            totalSpeed += dish.getBlocksPerTick();
        }
        int drawn = 0;
        for (SatelliteDishBlockEntity dish : powered) {
            if (totalSpeed > 0) {
                drawn += dish.drawLaserEnergy((int) Math.ceil((double) amount * dish.getBlocksPerTick() / totalSpeed));
            }
        }

        if (drawn > 0) {
            lastBeamTick = level.getGameTime();
        }
        return drawn;
    }

    // Speeds of the powered dishes add up, capped by the controller tier
    public int getBlocksPerTick() {
        if (level == null || getProblem() != null) {
            return 0;
        }
        int speed = 0;
        for (SatelliteDishBlockEntity dish : getPoweredDishes()) {
            speed += dish.getBlocksPerTick();
        }
        return Math.min(speed, getTier().maxBlocksPerTick());
    }

    public boolean isBeaming() {
        return beaming;
    }

    // wearing without coolant
    public boolean isRunningHot() {
        return hot;
    }

    // the ring controller's tier, picks the uplink beam style
    public QuarryTier getBeamTier() {
        return beamTier;
    }

    // Called every tick the controller wants to fire. Returns null once powered up.
    public @Nullable MachineState powerUp() {
        if (level == null) {
            return QuarryStates.POWERING_UP;
        }
        long now = level.getGameTime();
        lastPowerRequest = now;
        if (getPoweredDishes().isEmpty()) {
            powerDown();
            return MachineState.NO_POWER;
        }
        if (powerUpStart == POWERED_DOWN) {
            powerUpStart = now;
            syncToClients();
        }
        return now - powerUpStart >= getPowerUpTicks() ? null : QuarryStates.POWERING_UP;
    }

    private int getPowerUpTicks() {
        return level != null && level.getServer() instanceof GameTestServer && !powerUpInTests ? 0 : PowerUpSequence.TOTAL;
    }

    // Game tests skip the power up unless this is called
    public void debugEnablePowerUp() {
        powerUpInTests = true;
    }

    private void powerDown() {
        if (powerUpStart != POWERED_DOWN) {
            powerUpStart = POWERED_DOWN;
            syncToClients();
        }
    }

    public boolean isPoweredUp() {
        return powerUpStart != POWERED_DOWN && level != null && level.getGameTime() - powerUpStart >= getPowerUpTicks();
    }

    // -1 while powered down
    public float getSequenceTicks(float partialTick) {
        if (level == null || powerUpStart == POWERED_DOWN) {
            // sequence was skipped
            return beaming ? PowerUpSequence.TOTAL : -1;
        }
        return Math.max(0, level.getGameTime() - powerUpStart + partialTick);
    }

    public float getDishSpool(float partialTick) {
        float ticks = getSequenceTicks(partialTick);
        return ticks < 0 ? 0 : PowerUpSequence.dishSpool(ticks);
    }

    public float getDishBeam(float partialTick) {
        float ticks = getSequenceTicks(partialTick);
        return ticks < 0 ? 0 : PowerUpSequence.dishBeam(ticks);
    }

    public float getUplink(float partialTick) {
        float ticks = getSequenceTicks(partialTick);
        return ticks < 0 ? 0 : PowerUpSequence.uplink(ticks);
    }

    public float getShake(float partialTick) {
        float ticks = getSequenceTicks(partialTick);
        return ticks < 0 ? 0 : PowerUpSequence.shake(ticks);
    }

    public float getFlash(float partialTick) {
        float ticks = getSequenceTicks(partialTick);
        return ticks < 0 ? 0 : PowerUpSequence.flash(ticks);
    }

    // endregion

    // region Ticking

    public static void tick(Level level, BlockPos pos, BlockState state, CrystalObeliskBlockEntity obelisk) {
        if (level.isClientSide()) {
            obelisk.clientTick();
        } else {
            obelisk.serverTick();
        }
    }

    private void serverTick() {
        if (!structureChecked || level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            structureChecked = true;
            updateStructure();
        }
        updateSatellitePos();
        if (level.getGameTime() % REPAIR_INTERVAL == 0 && level instanceof ServerLevel serverLevel) {
            repairFromXp(serverLevel);
        }

        boolean nowHot = hasCrystal && level.getGameTime() - lastHotTick <= BEAM_LINGER_TICKS;
        if (nowHot != hot) {
            hot = nowHot;
            syncToClients();
        }
        boolean nowBeaming = level.getGameTime() - lastBeamTick <= BEAM_LINGER_TICKS;
        if (nowBeaming != beaming) {
            beaming = nowBeaming;
            syncToClients();
        }

        // just loaded: the rest of the base may need a few ticks before the controller asks again
        if (resumeGrace) {
            resumeGrace = false;
            lastPowerRequest = level.getGameTime() + RESUME_GRACE_TICKS;
        }

        // controller stopped asking
        if (powerUpStart != POWERED_DOWN && level.getGameTime() - lastPowerRequest > POWER_REQUEST_TIMEOUT) {
            powerDown();
        }
    }

    private void clientTick() {
        prevSpin = spin;
        prevCharge = charge;
        prevHeat = heat;
        heat += Mth.clamp((hot ? 1 : 0) - heat, -0.02f, 0.02f);

        float ticks = getSequenceTicks(0);
        float target = ticks >= 0 ? 0.25f + 0.75f * PowerUpSequence.crystalCharge(ticks) : formed && hasCrystal ? 0.25f : 0;
        charge += Mth.clamp(target - charge, -0.04f, 0.04f);
        spin += 1.5f + 16 * charge;
        if (spin > 360) {
            spin -= 360;
            prevSpin -= 360;
        }

        boolean powered = powerUpStart != POWERED_DOWN;
        if (powered && hasCrystal) {
            powerUpEffects(lastSequenceTicks, ticks);
        } else if (wasPowered && hasCrystal) {
            Vec3 center = getCrystalCenter();
            level.playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2f, 0.8f, false);
        }
        wasPowered = powered;
        lastSequenceTicks = powered ? ticks : -1;

        if (beaming && hasCrystal) {
            RandomSource random = level.getRandom();
            Vec3 center = getCrystalCenter();
            Vec3 from = center.add(random.nextGaussian() * 0.6, random.nextGaussian() * 0.6, random.nextGaussian() * 0.6);
            Vec3 velocity = center.subtract(from).scale(0.1);
            level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
            if (level.getGameTime() % 20 == 0) {
                level.playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6f, 1.6f, false);
            }
            if (hot) {
                hotEffects(random, center);
            }
        }
    }

    // no coolant: smoke and a quiet hiss
    private void hotEffects(RandomSource random, Vec3 center) {
        if (random.nextInt(3) == 0) {
            level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                center.x + random.nextGaussian() * 0.25, center.y + random.nextGaussian() * 0.25, center.z + random.nextGaussian() * 0.25,
                0, 0.04, 0);
        }
        if (random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, center.x + random.nextGaussian() * 0.2, center.y + random.nextGaussian() * 0.2,
                center.z + random.nextGaussian() * 0.2, 0, 0.01, 0);
        }
        if (level.getGameTime() % 50 == 0) {
            level.playLocalSound(center.x, center.y, center.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.15f, 1.6f, false);
        }
    }

    // Client side sounds and particles. Cues only fire right when crossed, no replay when joining late.
    private void powerUpEffects(float before, float now) {
        if (now >= PowerUpSequence.TOTAL && before >= PowerUpSequence.TOTAL) {
            return;
        }
        RandomSource random = level.getRandom();
        Vec3 center = getCrystalCenter();
        float charged = PowerUpSequence.crystalCharge(now);

        if (now >= PowerUpSequence.CHARGE_START && now < PowerUpSequence.FIRE) {
            // charging, faster and higher pitched
            int interval = charged < 0.5f ? 8 : charged < 0.85f ? 5 : 3;
            if (crossedEvery(before, now, interval)) {
                level.playLocalSound(center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS,
                    0.6f + 0.6f * charged, 0.5f + 1.5f * charged, false);
            }
            if (crossedEvery(before, now, 20)) {
                level.playLocalSound(center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS,
                    0.8f, 0.6f + 0.8f * charged, false);
            }
            for (int i = 0; i < 1 + (int) (charged * 4); i++) {
                Vec3 from = center.add(random.nextGaussian() * 1.5, random.nextGaussian() * 1.2, random.nextGaussian() * 1.5);
                Vec3 velocity = center.subtract(from).scale(0.12);
                level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
            }
        }
        if (crossed(before, now, PowerUpSequence.CHARGE_START)) {
            level.playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 1.4f, false);
        }
        if (crossed(before, now, PowerUpSequence.SHAKE_START)) {
            level.playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 0.6f, false);
        }
        if (crossed(before, now, PowerUpSequence.FIRE)) {
            // fire
            level.playLocalSound(center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 2.5f, 1.3f, false);
            level.playLocalSound(center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 2f, false);
            level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0, 0, 0);
            for (int i = 0; i < 40; i++) {
                Vec3 velocity = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5 + 0.6, random.nextGaussian()).scale(0.25);
                level.addParticle(ParticleTypes.END_ROD, center.x, center.y, center.z, velocity.x, velocity.y, velocity.z);
            }
        }
    }

    public static boolean crossed(float before, float now, int tick) {
        return before < tick && now >= tick && now - tick < 5;
    }

    public static boolean crossedEvery(float before, float now, int interval) {
        return before >= 0 && Mth.floor(now / interval) > Mth.floor(before / interval);
    }

    // uplink rings from Enhanced on: blocks apart and blocks per tick
    public static float ringSpacing(QuarryTier tier) {
        return tier.ordinal() >= 2 ? 8.5f : 12;
    }

    public static float ringSpeed(QuarryTier tier) {
        return tier.ordinal() >= 2 ? 0.85f : 0.6f;
    }

    // every this many rings one big one
    public static final int BIG_RING_EVERY = 5;

    public static boolean isBigRing(long ring) {
        return Math.floorMod(ring, BIG_RING_EVERY) == 0;
    }

    public float getSpin(float partialTick) {
        return Mth.lerp(partialTick, prevSpin, spin);
    }

    public float getCharge(float partialTick) {
        return Mth.lerp(partialTick, prevCharge, charge);
    }

    // 0..1, fades in and out while running hot
    public float getHeat(float partialTick) {
        return Mth.lerp(partialTick, prevHeat, heat);
    }

    @Nullable
    public Vec3 getUplinkTarget(float partialTick) {
        LaunchPadBlockEntity pad = getSatellitePad();
        return pad != null ? pad.getMirrorWorldPos(partialTick) : null;
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
        saveSynced(tag);
        if (!crystal.isEmpty()) {
            tag.put(CRYSTAL, crystal.save(registries));
        }
        tag.putInt(WEAR_BLOCKS, wearBlocks);
        if (controllerPos != null) {
            tag.put(CONTROLLER_POS, NbtUtils.writeBlockPos(controllerPos));
        }
        // a running quarry comes back powered up, no second power-up after a reload
        if (powerUpStart != POWERED_DOWN) {
            tag.putLong(POWER_UP_START, powerUpStart);
        }
    }

    private void saveSynced(CompoundTag tag) {
        tag.putBoolean(HAS_CRYSTAL, hasCrystal);
        tag.putBoolean(VOID_CRYSTAL, voidCrystal);
        tag.putBoolean(FORMED, formed);
        tag.putBoolean(BEAMING, beaming);
        tag.putBoolean(HOT, hot);
        tag.putInt(BEAM_TIER, beamTier.ordinal());
        if (satellitePos != null) {
            tag.put(SATELLITE_POS, NbtUtils.writeBlockPos(satellitePos));
        }
        ListTag list = new ListTag();
        for (BlockPos pos : dishes) {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put(DISHES, list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hasCrystal = tag.getBoolean(HAS_CRYSTAL);
        voidCrystal = tag.getBoolean(VOID_CRYSTAL);
        formed = tag.getBoolean(FORMED);
        beaming = tag.getBoolean(BEAMING);
        hot = tag.getBoolean(HOT);
        beamTier = QuarryTier.values()[Math.floorMod(tag.getInt(BEAM_TIER), QuarryTier.values().length)];
        satellitePos = NbtUtils.readBlockPos(tag, SATELLITE_POS).orElse(null);
        if (tag.contains(POWER_UP_START)) {
            powerUpStart = tag.getLong(POWER_UP_START);
            resumeGrace = true;
        }
        dishes.clear();
        activeDishes = null;
        for (Tag entry : tag.getList(DISHES, Tag.TAG_LONG)) {
            dishes.add(BlockPos.of(((LongTag) entry).getAsLong()));
        }
        // not in the update tag
        controllerPos = NbtUtils.readBlockPos(tag, CONTROLLER_POS).orElse(controllerPos);
        if (tag.contains(WEAR_BLOCKS)) {
            wearBlocks = tag.getInt(WEAR_BLOCKS);
        }
        if (tag.contains(CRYSTAL)) {
            crystal = ItemStack.parseOptional(registries, tag.getCompound(CRYSTAL));
            hasCrystal = !crystal.isEmpty();
            voidCrystal = crystal.getItem() instanceof VoidLaserCrystalItem;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveSynced(tag);
        // update tag only, powers up again after a reload
        tag.putLong(POWER_UP_START, powerUpStart);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // endregion
}

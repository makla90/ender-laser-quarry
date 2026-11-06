package com.enderlaserquarry.content.port;

import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.io.TransferUtil;
import com.enderlaserquarry.content.MultiblockFrames;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * I/O port: passes items, fluids and energy on to the multiblock it belongs to. Looks the core up on every call, so
 * nothing goes stale when the structure changes. IO mode per side like Ender IO machines (Yeta Wrench).
 */
public class StructurePortBlockEntity extends MachineBlockEntity {
    // Ender IO only pushes every 20 ticks, too slow for a fast quarry
    private static final int ITEM_TRANSFER_INTERVAL = 2;
    private static final int FLUID_TRANSFER_INTERVAL = 5;
    private static final int LOOK_INTERVAL = 20;

    // a budget can carry over at most this many ticks of its rate (the ports push every 2 / 5 ticks)
    private static final int ITEM_BUDGET_TICKS = ITEM_TRANSFER_INTERVAL;
    private static final int FLUID_BUDGET_TICKS = FLUID_TRANSFER_INTERVAL;

    private final Budget itemBudget = new Budget(ITEM_BUDGET_TICKS);
    private final Budget fluidBudget = new Budget(FLUID_BUDGET_TICKS);
    private final IItemHandler items = new PortItemHandler();
    private final IFluidHandler fluids = new PortFluidHandler();
    private final IEnergyStorage energy = new PortEnergyStorage();
    private final Map<Direction, IItemHandler> sidedItems = new EnumMap<>(Direction.class);
    private final Map<Direction, IFluidHandler> sidedFluids = new EnumMap<>(Direction.class);
    private final Map<Direction, IEnergyStorage> sidedEnergy = new EnumMap<>(Direction.class);

    public StructurePortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE_PORT.get(), pos, state, true);
    }

    private StructurePortBlock.Structure structure() {
        return getBlockState().getBlock() instanceof StructurePortBlock port ? port.getStructure() : StructurePortBlock.Structure.LAUNCH_PAD;
    }

    // side null = no IO mode applied
    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (!structure().items) {
            return null;
        }
        if (side == null) {
            return items;
        }
        return getIOMode(side).canConnect() ? sidedItems.computeIfAbsent(side, SidedItemHandler::new) : null;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (!structure().fluids) {
            return null;
        }
        if (side == null) {
            return fluids;
        }
        return getIOMode(side).canConnect() ? sidedFluids.computeIfAbsent(side, SidedFluidHandler::new) : null;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        if (!structure().energy) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        return getIOMode(side).canConnect() ? sidedEnergy.computeIfAbsent(side, SidedEnergyStorage::new) : null;
    }

    // region Ender IO machine: active push / pull, no menu of its own (clicks go to the multiblock's core)

    @Override
    protected int distributeResourcesInterval() {
        return 1;
    }

    @Override
    protected void distributeResources(Direction side) {
        if (canAct(ITEM_TRANSFER_INTERVAL)) {
            IItemHandler self = getSelfCapability(Capabilities.ItemHandler.BLOCK, side);
            IItemHandler other = getNeighbouringCapability(Capabilities.ItemHandler.BLOCK, side);
            if (self != null && other != null) {
                TransferUtil.distributeItems(getIOMode(side), self, other);
            }
        }
        if (canAct(FLUID_TRANSFER_INTERVAL)) {
            IFluidHandler self = getSelfCapability(Capabilities.FluidHandler.BLOCK, side);
            IFluidHandler other = getNeighbouringCapability(Capabilities.FluidHandler.BLOCK, side);
            if (self != null && other != null) {
                TransferUtil.distributeFluids(getIOMode(side), self, other);
            }
        }
    }

    @Override
    public void serverTick() {
        super.serverTick();
        // for ports placed without a neighbour update
        if (level != null && level.getGameTime() % LOOK_INTERVAL == 0 && getBlockState().getBlock() instanceof TieredPortBlock port) {
            QuarryTier tier = port.tierAround(level, worldPosition);
            if (getBlockState().getValue(TieredPortBlock.TIER) != tier) {
                level.setBlock(worldPosition, getBlockState().setValue(TieredPortBlock.TIER, tier), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }

    // endregion

    // region Targets

    @Nullable
    private PortedMultiblock core() {
        return level != null && MultiblockFrames.findCore(level, worldPosition) instanceof PortedMultiblock core ? core : null;
    }

    @Nullable
    private IItemHandler itemTarget() {
        PortedMultiblock core = core();
        return core == null ? null : core.getPortItemHandler();
    }

    @Nullable
    private IFluidHandler fluidTarget() {
        PortedMultiblock core = core();
        return core == null ? null : core.getPortFluidHandler();
    }

    @Nullable
    private IEnergyStorage energyTarget() {
        PortedMultiblock core = core();
        return core == null ? null : core.getPortEnergyStorage();
    }

    // endregion

    // Per tick allowance that refills with the core's rate
    private final class Budget {
        private final int carryTicks;
        private long tick = Long.MIN_VALUE;
        private long left;

        Budget(int carryTicks) {
            this.carryTicks = carryTicks;
        }

        long available(int rate) {
            if (rate == Integer.MAX_VALUE || level == null) {
                return Long.MAX_VALUE;
            }
            long now = level.getGameTime();
            if (now != tick) {
                long passed = tick == Long.MIN_VALUE ? carryTicks : Math.min(carryTicks, now - tick);
                left = Math.min((long) rate * carryTicks, left + (long) rate * passed);
                tick = now;
            }
            return left;
        }

        void use(int amount) {
            left -= amount;
        }
    }

    private class PortItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            IItemHandler target = itemTarget();
            return target == null ? 0 : target.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler target = itemTarget();
            return target == null || slot >= target.getSlots() ? ItemStack.EMPTY : target.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler target = itemTarget();
            return target == null || slot >= target.getSlots() ? stack : target.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            PortedMultiblock core = core();
            IItemHandler target = core == null ? null : core.getPortItemHandler();
            if (target == null || slot >= target.getSlots() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            long allowed = itemBudget.available(core.getPortItemRate());
            if (allowed <= 0) {
                // spent this tick's items: the capacitor is the bottleneck
                if (!target.getStackInSlot(slot).isEmpty()) {
                    core.onPortOutputLimited();
                }
                return ItemStack.EMPTY;
            }
            ItemStack out = target.extractItem(slot, (int) Math.min(amount, allowed), simulate);
            if (!simulate) {
                itemBudget.use(out.getCount());
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler target = itemTarget();
            return target == null || slot >= target.getSlots() ? 0 : target.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler target = itemTarget();
            return target != null && slot < target.getSlots() && target.isItemValid(slot, stack);
        }
    }

    private class PortFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            IFluidHandler target = fluidTarget();
            return target == null ? 0 : target.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler target = fluidTarget();
            return target == null || tank >= target.getTanks() ? FluidStack.EMPTY : target.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = fluidTarget();
            return target == null || tank >= target.getTanks() ? 0 : target.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler target = fluidTarget();
            return target != null && tank < target.getTanks() && target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            PortedMultiblock core = core();
            IFluidHandler target = core == null ? null : core.getPortFluidHandler();
            if (target == null || resource.isEmpty()) {
                return 0;
            }
            long allowed = fluidBudget.available(core.getPortFluidRate());
            if (allowed <= 0) {
                return 0;
            }
            int filled = target.fill(resource.copyWithAmount((int) Math.min(resource.getAmount(), allowed)), action);
            if (action.execute()) {
                fluidBudget.use(filled);
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler target = fluidTarget();
            return target == null ? FluidStack.EMPTY : target.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler target = fluidTarget();
            return target == null ? FluidStack.EMPTY : target.drain(maxDrain, action);
        }
    }

    private class PortEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            IEnergyStorage target = energyTarget();
            return target == null ? 0 : target.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            IEnergyStorage target = energyTarget();
            return target == null ? 0 : target.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            IEnergyStorage target = energyTarget();
            return target == null ? 0 : target.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    // region Per-side views (IO mode)

    private class SidedItemHandler implements IItemHandler {
        private final Direction side;

        SidedItemHandler(Direction side) {
            this.side = side;
        }

        @Override
        public int getSlots() {
            return items.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return getIOMode(side).canInput() ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return getIOMode(side).canOutput() ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return getIOMode(side).canInput() && items.isItemValid(slot, stack);
        }
    }

    private class SidedFluidHandler implements IFluidHandler {
        private final Direction side;

        SidedFluidHandler(Direction side) {
            this.side = side;
        }

        @Override
        public int getTanks() {
            return fluids.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluids.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return fluids.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return getIOMode(side).canInput() && fluids.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return getIOMode(side).canInput() ? fluids.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return getIOMode(side).canOutput() ? fluids.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return getIOMode(side).canOutput() ? fluids.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    private class SidedEnergyStorage implements IEnergyStorage {
        private final Direction side;

        SidedEnergyStorage(Direction side) {
            this.side = side;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return getIOMode(side).canInput() ? energy.receiveEnergy(maxReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energy.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return getIOMode(side).canInput();
        }
    }

    // endregion
}

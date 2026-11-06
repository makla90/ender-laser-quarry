package com.enderlaserquarry.content.port;

import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.io.TransferUtil;
import com.enderio.enderio.foundation.storage.SidedResourceHandler;
import com.enderlaserquarry.content.MultiblockFrames;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

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
    private final ResourceHandler<ItemResource> items = new PortHandler<>(ItemResource.EMPTY, PortedMultiblock::getPortItemHandler) {
        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            PortedMultiblock core = core();
            ResourceHandler<ItemResource> target = core == null ? null : core.getPortItemHandler();
            if (target == null || index >= target.size() || amount <= 0) {
                return 0;
            }
            long allowed = itemBudget.available(core.getPortItemRate());
            if (allowed <= 0) {
                // spent this tick's items: the capacitor is the bottleneck
                if (!target.getResource(index).isEmpty()) {
                    core.onPortOutputLimited();
                }
                return 0;
            }
            int out = target.extract(index, resource, (int) Math.min(amount, allowed), transaction);
            itemBudget.use(out, transaction);
            return out;
        }
    };
    private final ResourceHandler<FluidResource> fluids = new PortHandler<>(FluidResource.EMPTY, PortedMultiblock::getPortFluidHandler) {
        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            PortedMultiblock core = core();
            ResourceHandler<FluidResource> target = core == null ? null : core.getPortFluidHandler();
            if (target == null || index >= target.size() || amount <= 0) {
                return 0;
            }
            long allowed = fluidBudget.available(core.getPortFluidRate());
            if (allowed <= 0) {
                return 0;
            }
            int filled = target.insert(index, resource, (int) Math.min(amount, allowed), transaction);
            fluidBudget.use(filled, transaction);
            return filled;
        }
    };
    private final EnergyHandler energy = new PortEnergy(null);
    private final Map<Direction, ResourceHandler<ItemResource>> sidedItems = new EnumMap<>(Direction.class);
    private final Map<Direction, ResourceHandler<FluidResource>> sidedFluids = new EnumMap<>(Direction.class);
    private final Map<Direction, EnergyHandler> sidedEnergy = new EnumMap<>(Direction.class);

    public StructurePortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE_PORT.get(), pos, state, true);
    }

    private StructurePortBlock.Structure structure() {
        return getBlockState().getBlock() instanceof StructurePortBlock port ? port.getStructure() : StructurePortBlock.Structure.LAUNCH_PAD;
    }

    // side null = no IO mode applied
    @Nullable
    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        if (!structure().items) {
            return null;
        }
        if (side == null) {
            return items;
        }
        return getIOMode(side).canConnect() ? sidedItems.computeIfAbsent(side, s -> SidedResourceHandler.of(items, s, this)) : null;
    }

    @Nullable
    public ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        if (!structure().fluids) {
            return null;
        }
        if (side == null) {
            return fluids;
        }
        return getIOMode(side).canConnect() ? sidedFluids.computeIfAbsent(side, s -> SidedResourceHandler.of(fluids, s, this)) : null;
    }

    @Nullable
    public EnergyHandler getEnergyStorage(@Nullable Direction side) {
        if (!structure().energy) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        return getIOMode(side).canConnect() ? sidedEnergy.computeIfAbsent(side, PortEnergy::new) : null;
    }

    // region Ender IO machine: active push / pull, no menu of its own (clicks go to the multiblock's core)

    @Override
    protected int distributeResourcesInterval() {
        return 1;
    }

    @Override
    protected void distributeResources(Direction side) {
        if (canAct(ITEM_TRANSFER_INTERVAL)) {
            ResourceHandler<ItemResource> self = getSelfCapability(Capabilities.Item.BLOCK, side);
            ResourceHandler<ItemResource> other = getNeighbouringCapability(Capabilities.Item.BLOCK, side);
            if (self != null && other != null) {
                TransferUtil.distributeItems(getIOMode(side), self, other);
            }
        }
        if (canAct(FLUID_TRANSFER_INTERVAL)) {
            ResourceHandler<FluidResource> self = getSelfCapability(Capabilities.Fluid.BLOCK, side);
            ResourceHandler<FluidResource> other = getNeighbouringCapability(Capabilities.Fluid.BLOCK, side);
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

    @Nullable
    private PortedMultiblock core() {
        return level != null && MultiblockFrames.findCore(level, worldPosition) instanceof PortedMultiblock core ? core : null;
    }

    // Per tick allowance that refills with the core's rate, rolled back with its transaction
    private final class Budget extends SnapshotJournal<Long> {
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

        void use(int amount, TransactionContext transaction) {
            if (amount > 0) {
                updateSnapshots(transaction);
                left -= amount;
            }
        }

        @Override
        protected Long createSnapshot() {
            return left;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            left = snapshot;
        }
    }

    // forwards to the core's handler, looked up per call
    private class PortHandler<T extends Resource> implements ResourceHandler<T> {
        private final T empty;
        private final Function<PortedMultiblock, @Nullable ResourceHandler<T>> lookup;

        PortHandler(T empty, Function<PortedMultiblock, @Nullable ResourceHandler<T>> lookup) {
            this.empty = empty;
            this.lookup = lookup;
        }

        @Nullable
        private ResourceHandler<T> target() {
            PortedMultiblock core = core();
            return core == null ? null : lookup.apply(core);
        }

        @Override
        public int size() {
            ResourceHandler<T> target = target();
            return target == null ? 0 : target.size();
        }

        @Override
        public T getResource(int index) {
            ResourceHandler<T> target = target();
            return target == null || index >= target.size() ? empty : target.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            ResourceHandler<T> target = target();
            return target == null || index >= target.size() ? 0 : target.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, T resource) {
            ResourceHandler<T> target = target();
            return target == null || index >= target.size() ? 0 : target.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, T resource) {
            ResourceHandler<T> target = target();
            return target != null && index < target.size() && target.isValid(index, resource);
        }

        @Override
        public int insert(int index, T resource, int amount, TransactionContext transaction) {
            ResourceHandler<T> target = target();
            return target == null || index >= target.size() ? 0 : target.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, T resource, int amount, TransactionContext transaction) {
            ResourceHandler<T> target = target();
            return target == null || index >= target.size() ? 0 : target.extract(index, resource, amount, transaction);
        }
    }

    // receive only; side null = no IO mode
    private class PortEnergy implements EnergyHandler {
        @Nullable
        private final Direction side;

        PortEnergy(@Nullable Direction side) {
            this.side = side;
        }

        @Nullable
        private EnergyHandler target() {
            PortedMultiblock core = core();
            return core == null ? null : core.getPortEnergyStorage();
        }

        @Override
        public long getAmountAsLong() {
            EnergyHandler target = target();
            return target == null ? 0 : target.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            EnergyHandler target = target();
            return target == null ? 0 : target.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (side != null && !getIOMode(side).canInput()) {
                return 0;
            }
            EnergyHandler target = target();
            return target == null ? 0 : target.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }
}

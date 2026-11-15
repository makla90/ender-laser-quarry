package com.enderlaserquarry.content.port;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Multiblock core that's only reachable through its I/O ports. The ports apply their own IO mode per side.
 */
public interface PortedMultiblock {

    @Nullable
    default IItemHandler getPortItemHandler() {
        return null;
    }

    @Nullable
    default IFluidHandler getPortFluidHandler() {
        return null;
    }

    @Nullable
    default IEnergyStorage getPortEnergyStorage() {
        return null;
    }

    // per port and tick, MAX_VALUE = no limit
    default int getPortItemRate() {
        return Integer.MAX_VALUE;
    }

    default int getPortFluidRate() {
        return Integer.MAX_VALUE;
    }

    // a port wanted to move more items than its rate allowed
    default void onPortOutputLimited() {
    }

    // for the IO config screen
    List<BlockPos> findPorts();
}

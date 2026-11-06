package com.enderlaserquarry.content;

import com.enderio.core.common.storage.FluidStorage;
import com.enderio.core.common.storage.slot.SingleResourceSlotKey;
import com.enderio.enderio.foundation.fluid.FluidStorageInfo;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** One tank of a machine's fluid storage. Fill and drain here skip the slot's IO rules (the filter still applies). */
public record Tank(FluidStorage storage, SingleResourceSlotKey<FluidResource> slot) {

    public FluidStack getFluid() {
        return storage.getStack(slot);
    }

    public int getFluidAmount() {
        return storage.getAmountAsInt(slot);
    }

    public int getCapacity() {
        return storage.getCapacityAsInt(slot, storage.getResource(slot));
    }

    public boolean isEmpty() {
        return getFluidAmount() <= 0;
    }

    public void setFluid(FluidStack stack) {
        storage.setStack(slot, stack);
    }

    public FluidStorageInfo info() {
        return FluidStorageInfo.of(storage, slot);
    }

    public int fill(FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = storage.insert(slot, FluidResource.of(stack), stack.getAmount(), tx);
            if (!simulate) {
                tx.commit();
            }
            return done;
        }
    }

    public FluidStack drain(int amount, boolean simulate) {
        FluidResource resource = storage.getResource(slot);
        if (amount <= 0 || resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = storage.extract(slot, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return resource.toStack(done);
        }
    }
}

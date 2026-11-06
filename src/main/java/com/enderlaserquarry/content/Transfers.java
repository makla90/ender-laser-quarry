package com.enderlaserquarry.content;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** One-shot transfers: each call is its own transaction, committed unless simulating. */
public final class Transfers {

    public static int insert(EnergyHandler handler, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = handler.insert(amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return done;
        }
    }

    public static int extract(EnergyHandler handler, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = handler.extract(amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int insert(ResourceHandler<T> handler, T resource, int amount, boolean simulate) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = handler.insert(resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int insert(ResourceHandler<T> handler, int index, T resource, int amount, boolean simulate) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = handler.insert(index, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int extract(ResourceHandler<T> handler, T resource, int amount, boolean simulate) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = handler.extract(resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return done;
        }
    }

    public static <T extends Resource> int extract(ResourceHandler<T> handler, int index, T resource, int amount, boolean simulate) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int done = handler.extract(index, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return done;
        }
    }

    // returns what didn't fit
    public static ItemStack insertItem(ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int done = insert(handler, ItemResource.of(stack), stack.getCount(), simulate);
        return done >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - done);
    }

    public static int fill(ResourceHandler<FluidResource> handler, FluidStack stack, boolean simulate) {
        return stack.isEmpty() ? 0 : insert(handler, FluidResource.of(stack), stack.getAmount(), simulate);
    }

    // whatever fluid comes first, up to amount
    public static FluidStack drainAny(ResourceHandler<FluidResource> handler, int amount, boolean simulate) {
        for (int i = 0; i < handler.size(); i++) {
            FluidResource resource = handler.getResource(i);
            if (!resource.isEmpty()) {
                int done = extract(handler, resource, amount, simulate);
                if (done > 0) {
                    return resource.toStack(done);
                }
            }
        }
        return FluidStack.EMPTY;
    }

    public static ItemStack stack(ResourceHandler<ItemResource> handler, int index) {
        return handler.getResource(index).toStack(handler.getAmountAsInt(index));
    }

    public static FluidStack fluid(ResourceHandler<FluidResource> handler, int index) {
        return handler.getResource(index).toStack(handler.getAmountAsInt(index));
    }

    private Transfers() {
    }
}

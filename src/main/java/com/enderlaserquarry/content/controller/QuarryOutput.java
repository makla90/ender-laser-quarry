package com.enderlaserquarry.content.controller;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.ItemFilter;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.core.common.storage.ItemStorage;
import com.enderio.core.common.storage.slot.MultiResourceSlotKey;
import net.neoforged.neoforge.transfer.item.ItemResource;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Void filter and output slots.
 */
public final class QuarryOutput {

    // Works like on a conduit: what the filter lets through is kept, the rest voided
    public static List<ItemStack> applyVoidFilter(List<ItemStack> drops, ItemStack filterStack) {
        ItemFilter filter = getVoidFilter(filterStack);
        if (filter == null) {
            return drops;
        }
        List<ItemStack> kept = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            if (!filter.test(null, drop).isEmpty()) {
                kept.add(drop);
            }
        }
        return kept;
    }

    @Nullable
    private static ItemFilter getVoidFilter(ItemStack stack) {
        // an empty filter would void everything, ignore it
        EnderItemFilter settings = stack.get(EIODataComponents.ITEM_FILTER);
        if (settings != null && settings.matches().stream().allMatch(ItemStack::isEmpty)) {
            return null;
        }
        return stack.getCapability(EnderIOCapabilities.ITEM_FILTER);
    }

    // one block's drops, all or nothing
    public static boolean insertAll(ItemStorage inventory, MultiResourceSlotKey<ItemResource> output, List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return true;
        }
        Buffer buffer = new Buffer(inventory, output);
        boolean fits = buffer.add(drops);
        buffer.flush();
        return fits;
    }

    /**
     * The output slots copied into arrays: a tick's drops are planned there block by block (each block all or
     * nothing) and written back once by {@link #flush}. Writing every block's drops straight into the storage
     * costs a change callback (and in transactions a journal) per block.
     */
    public static final class Buffer {
        private final ItemStorage inventory;
        private final MultiResourceSlotKey<ItemResource> output;
        private final ItemResource[] resources;
        private final int[] amounts;
        private final boolean[] changed;
        private final ItemResource[] undoResources;
        private final int[] undoAmounts;

        public Buffer(ItemStorage inventory, MultiResourceSlotKey<ItemResource> output) {
            this.inventory = inventory;
            this.output = output;
            int count = output.count();
            resources = new ItemResource[count];
            amounts = new int[count];
            changed = new boolean[count];
            undoResources = new ItemResource[count];
            undoAmounts = new int[count];
            for (int i = 0; i < count; i++) {
                resources[i] = inventory.getResource(output.slot(i));
                amounts[i] = inventory.getAmountAsInt(output.slot(i));
            }
        }

        // false (and nothing planned) when not every drop fits
        public boolean add(List<ItemStack> drops) {
            if (drops.isEmpty()) {
                return true;
            }
            System.arraycopy(resources, 0, undoResources, 0, resources.length);
            System.arraycopy(amounts, 0, undoAmounts, 0, amounts.length);
            for (ItemStack drop : drops) {
                if (!drop.isEmpty() && !place(ItemResource.of(drop), drop.getCount())) {
                    System.arraycopy(undoResources, 0, resources, 0, resources.length);
                    System.arraycopy(undoAmounts, 0, amounts, 0, amounts.length);
                    return false;
                }
            }
            return true;
        }

        // onto the same item first, then into empty slots
        private boolean place(ItemResource resource, int left) {
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int i = 0; i < resources.length && left > 0; i++) {
                    boolean empty = resources[i].isEmpty() || amounts[i] <= 0;
                    if (pass == 0 ? empty || !resources[i].equals(resource) : !empty) {
                        continue;
                    }
                    if (pass == 1 && !inventory.isValid(output.slot(i), resource)) {
                        continue;
                    }
                    int room = inventory.getCapacityAsInt(output.slot(i), resource) - (empty ? 0 : amounts[i]);
                    if (room <= 0) {
                        continue;
                    }
                    int moved = Math.min(room, left);
                    resources[i] = resource;
                    amounts[i] = (empty ? 0 : amounts[i]) + moved;
                    changed[i] = true;
                    left -= moved;
                }
            }
            return left <= 0;
        }

        public void flush() {
            for (int i = 0; i < resources.length; i++) {
                if (changed[i]) {
                    inventory.set(output.slot(i), resources[i], amounts[i]);
                    changed[i] = false;
                }
            }
        }
    }

    private QuarryOutput() {
    }
}

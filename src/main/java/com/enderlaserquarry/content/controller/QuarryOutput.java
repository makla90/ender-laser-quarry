package com.enderlaserquarry.content.controller;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.ItemFilter;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
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

    // Single stacks are a simple simulate, several need a copy since they can compete for the same slot
    public static boolean fits(MachineInventory inventory, MultiSlotAccess output, List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return true;
        }
        if (drops.size() == 1) {
            ItemStack remaining = drops.get(0);
            for (SingleSlotAccess slot : output.getAccesses()) {
                remaining = slot.insertItem(inventory, remaining, true);
                if (remaining.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
        return insertAll(inventory.snapshot(output), output, drops);
    }

    // check fits() first
    public static boolean insertAll(MachineInventory inventory, MultiSlotAccess output, List<ItemStack> stacks) {
        for (ItemStack drop : stacks) {
            ItemStack remaining = drop.copy();
            for (SingleSlotAccess slot : output.getAccesses()) {
                remaining = slot.insertItem(inventory, remaining, false);
                if (remaining.isEmpty()) {
                    break;
                }
            }

            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private QuarryOutput() {
    }
}

package com.enderlaserquarry.content.controller;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.ItemFilter;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.core.common.storage.ItemStorage;
import com.enderio.core.common.storage.slot.MultiResourceSlotKey;
import com.enderio.core.common.storage.slot.ResourceSlotId;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
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

    // all or nothing: committed only when every drop fits
    public static boolean insertAll(ItemStorage inventory, MultiResourceSlotKey<ItemResource> output, List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return true;
        }
        try (Transaction tx = Transaction.openRoot()) {
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) {
                    continue;
                }
                ItemResource resource = ItemResource.of(drop);
                int left = drop.getCount();
                for (ResourceSlotId<ItemResource> slot : output) {
                    left -= inventory.insert(slot, resource, left, tx);
                    if (left <= 0) {
                        break;
                    }
                }
                if (left > 0) {
                    return false;
                }
            }
            tx.commit();
            return true;
        }
    }

    private QuarryOutput() {
    }
}

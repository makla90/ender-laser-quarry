package com.enderlaserquarry.content;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Block or item with a QuarryTier.
 */
public interface Tiered {
    QuarryTier getTier();

    @Nullable
    static QuarryTier of(ItemStack stack) {
        if (stack.getItem() instanceof Tiered tiered) {
            return tiered.getTier();
        }
        return stack.getItem() instanceof BlockItem block && block.getBlock() instanceof Tiered tiered ? tiered.getTier() : null;
    }
}

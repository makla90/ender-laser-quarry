package com.enderlaserquarry.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Block item that shows its block's tooltip lines. */
public class TooltipBlockItem extends BlockItem {

    /** A block with tooltip lines on its item. */
    public interface HasTooltip {
        void appendTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag);
    }

    public TooltipBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        if (getBlock() instanceof HasTooltip block) {
            block.appendTooltip(stack, context, tooltip, flag);
        }
    }
}

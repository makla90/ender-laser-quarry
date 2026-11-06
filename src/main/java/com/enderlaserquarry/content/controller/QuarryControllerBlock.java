package com.enderlaserquarry.content.controller;

import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.init.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.function.Consumer;
import com.enderlaserquarry.content.TooltipBlockItem;

import java.util.List;

/**
 * Quarry controller block, one per tier. They share the block entity type.
 */
public class QuarryControllerBlock extends MachineBlock<QuarryControllerBlockEntity> implements Tiered, TooltipBlockItem.HasTooltip {
    private final QuarryTier tier;

    public QuarryControllerBlock(QuarryTier tier, Properties properties) {
        super(ModBlockEntities.QUARRY_CONTROLLER::get, properties);
        this.tier = tier;
    }

    @Override
    public QuarryTier getTier() {
        return tier;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        int across = tier.chunkRadius() * 2 + 1;
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.controller.stats", tier.maxChunks(), tier.maxDishes(),
            tier.maxBlocksPerTick()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.controller.area", across, across).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.controller.link_range", tier.linkRange()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.controller.satellite", tier.displayName())
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.controller.plating", tier.displayName())
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}

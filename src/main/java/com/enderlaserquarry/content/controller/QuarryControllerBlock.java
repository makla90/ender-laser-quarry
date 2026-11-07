package com.enderlaserquarry.content.controller;

import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.init.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Quarry controller block, one per tier. They share the block entity type.
 */
public class QuarryControllerBlock extends MachineBlock<QuarryControllerBlockEntity> implements Tiered {
    private final QuarryTier tier;

    public QuarryControllerBlock(QuarryTier tier, Properties properties) {
        super(ModBlockEntities.QUARRY_CONTROLLER::get, properties);
        this.tier = tier;
    }

    @Override
    public QuarryTier getTier() {
        return tier;
    }

    // comparator: how full the output slots are, like a chest
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity controller ? controller.getComparatorSignal() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int across = tier.chunkRadius() * 2 + 1;
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.controller.stats", tier.maxChunks(), tier.maxDishes(),
            tier.maxBlocksPerTick()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.controller.area", across, across).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.controller.link_range", tier.linkRange()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.controller.satellite", tier.displayName())
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.controller.plating", tier.displayName())
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}

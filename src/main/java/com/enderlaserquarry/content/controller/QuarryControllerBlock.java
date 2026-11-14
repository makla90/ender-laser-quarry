package com.enderlaserquarry.content.controller;

import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.content.Tooltips;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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

    // comparator: how full the output slots are, like a chest
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity controller ? controller.getComparatorSignal() : 0;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Tooltips.line("controller"));
        if (!Tooltips.details(flag, tooltip)) {
            return;
        }
        int across = tier.chunkRadius() * 2 + 1;
        tooltip.accept(Tooltips.line("controller.stats", tier.maxChunks(), tier.maxDishes()));
        tooltip.accept(Tooltips.line("controller.area", across, across));
        tooltip.accept(Tooltips.line("controller.link_range", tier.linkRange()));
        tooltip.accept(Tooltips.line("controller.plating", tier.displayName()));
        tooltip.accept(Tooltips.line("controller.satellite", tier.displayName()));
    }
}

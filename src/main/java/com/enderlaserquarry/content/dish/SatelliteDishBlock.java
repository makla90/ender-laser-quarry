package com.enderlaserquarry.content.dish;

import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.content.Tooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.function.Consumer;
import com.enderlaserquarry.content.TooltipBlockItem;

import java.util.List;

/**
 * Satellite dish block, one per tier. They share the block entity type.
 */
public class SatelliteDishBlock extends MachineBlock<SatelliteDishBlockEntity> implements Tiered, TooltipBlockItem.HasTooltip {
    private final QuarryTier tier;

    public SatelliteDishBlock(QuarryTier tier, Properties properties) {
        super(ModBlockEntities.SATELLITE_DISH::get, properties);
        this.tier = tier;
    }

    @Override
    public QuarryTier getTier() {
        return tier;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Tooltips.line("dish"));
        if (!Tooltips.details(flag, tooltip)) {
            return;
        }
        tooltip.accept(Tooltips.line("dish.capacitor"));
        if (EndergyCapacitors.isLoaded()) {
            tooltip.accept(Tooltips.line(tier == QuarryTier.ENDER ? "dish.endergy" : "dish.no_endergy"));
        }
        tooltip.accept(Tooltips.line("dish.range", tier.dishRange()));
        tooltip.accept(Tooltips.line("dish.plating", tier.displayName()));
        tooltip.accept(Tooltips.line("dish.port"));
        tooltip.accept(Tooltips.line("dish.controller", tier.displayName()));
    }
}

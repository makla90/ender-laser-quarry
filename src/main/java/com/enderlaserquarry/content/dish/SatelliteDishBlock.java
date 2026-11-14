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

import java.util.List;

/**
 * Satellite dish block, one per tier. They share the block entity type.
 */
public class SatelliteDishBlock extends MachineBlock<SatelliteDishBlockEntity> implements Tiered {
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Tooltips.line("dish"));
        if (!Tooltips.details(flag, tooltip)) {
            return;
        }
        tooltip.add(Tooltips.line("dish.capacitor"));
        if (EndergyCapacitors.isLoaded()) {
            tooltip.add(Tooltips.line(tier == QuarryTier.ENDER ? "dish.endergy" : "dish.no_endergy"));
        }
        tooltip.add(Tooltips.line("dish.range", tier.dishRange()));
        tooltip.add(Tooltips.line("dish.plating", tier.displayName()));
        tooltip.add(Tooltips.line("dish.port"));
        tooltip.add(Tooltips.line("dish.controller", tier.displayName()));
    }
}

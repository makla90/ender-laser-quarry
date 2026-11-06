package com.enderlaserquarry.content.dish;

import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.init.ModBlockEntities;
import net.minecraft.ChatFormatting;
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
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.dish.speed", SatelliteDishBlockEntity.blocksPerTick(1, tier),
            SatelliteDishBlockEntity.blocksPerTick(2, tier), SatelliteDishBlockEntity.blocksPerTick(3, tier)).withStyle(ChatFormatting.GRAY));
        if (EndergyCapacitors.isLoaded()) {
            tooltip.add((tier == QuarryTier.ENDER
                ? Component.translatable("tooltip.ender_laser_quarry.dish.endergy", SatelliteDishBlockEntity.blocksPerTick(5, tier))
                : Component.translatable("tooltip.ender_laser_quarry.dish.no_endergy")).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.dish.range", tier.dishRange()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.dish.controller", tier.displayName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.dish.plating", tier.displayName()).withStyle(ChatFormatting.DARK_GRAY));
    }
}

package com.enderlaserquarry.content.satellite;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Satellite item. In orbit it maps the area and reflects the laser down.
 */
public class SatelliteItem extends Item implements Tiered {
    private final QuarryTier tier;

    public SatelliteItem(QuarryTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public QuarryTier getTier() {
        return tier;
    }

    @Nullable
    public static QuarryTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof SatelliteItem satellite ? satellite.tier : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.satellite").withStyle(ChatFormatting.GRAY));
        int across = tier.chunkRadius() * 2 + 1;
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.satellite.area", across, across).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.satellite.launch").withStyle(ChatFormatting.DARK_GRAY));
    }
}

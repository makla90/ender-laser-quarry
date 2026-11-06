package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.content.QuarryTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

import java.util.List;

/**
 * Void crystal, Ender quarries mine asteroids with it.
 */
public class VoidLaserCrystalItem extends LaserCrystalItem {

    public VoidLaserCrystalItem(Properties properties) {
        super(QuarryTier.ENDER, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.void_laser_crystal").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.void_laser_crystal.tier").withStyle(ChatFormatting.GREEN));
        addEnchantHint(stack, tooltip);
        addWearHint(stack, tooltip);
    }
}

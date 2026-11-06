package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.content.QuarryTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Void crystal, Ender quarries mine asteroids with it.
 */
public class VoidLaserCrystalItem extends LaserCrystalItem {

    public VoidLaserCrystalItem(Properties properties) {
        super(QuarryTier.ENDER, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.void_laser_crystal").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ender_laser_quarry.void_laser_crystal.tier").withStyle(ChatFormatting.GREEN));
        addEnchantHint(stack, tooltip);
        addWearHint(stack, tooltip);
    }
}

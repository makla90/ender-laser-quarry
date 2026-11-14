package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tooltips;
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
        tooltip.add(Tooltips.line("void_laser_crystal"));
        addState(stack, tooltip);
        if (Tooltips.details(flag, tooltip)) {
            tooltip.add(Tooltips.line("void_laser_crystal.ground"));
            tooltip.add(Tooltips.line("void_laser_crystal.tier"));
            addDetails(stack, tooltip);
        }
    }
}

package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tooltips;
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
        tooltip.accept(Tooltips.line("void_laser_crystal"));
        addState(stack, tooltip);
        if (Tooltips.details(flag, tooltip)) {
            tooltip.accept(Tooltips.line("void_laser_crystal.ground"));
            tooltip.accept(Tooltips.line("void_laser_crystal.tier"));
            addDetails(stack, tooltip);
        }
    }
}

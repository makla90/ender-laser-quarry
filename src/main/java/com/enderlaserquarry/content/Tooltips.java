package com.enderlaserquarry.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Item tooltips: a short summary, the details only while Shift is held. */
public final class Tooltips {
    // set by ClientSetup; TooltipFlag.hasShiftDown() is false for the plain flags JEI and others pass in
    public static BooleanSupplier shiftDown = () -> false;

    /** True while Shift is held, otherwise adds the "Hold Shift" hint and returns false. */
    public static boolean details(TooltipFlag flag, Consumer<Component> tooltip) {
        if (flag.hasShiftDown() || shiftDown.getAsBoolean()) {
            return true;
        }
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.hold_shift",
            Component.translatable("tooltip.ender_laser_quarry.shift").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY));
        return false;
    }

    public static Component line(String key, Object... args) {
        return Component.translatable("tooltip.ender_laser_quarry." + key, args).withStyle(ChatFormatting.GRAY);
    }

    private Tooltips() {
    }
}

package com.enderlaserquarry.content.satellite;

import com.enderlaserquarry.config.QuarryConfig;
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

/**
 * Satellite item. In orbit it maps the area and reflects the laser down. The Void Satellite counts as Ender for the
 * tier rules, maps no ground and throws the beam out to the asteroids instead.
 */
public class SatelliteItem extends Item implements Tiered {
    private final QuarryTier tier;
    private final boolean voidSatellite;

    public SatelliteItem(QuarryTier tier, boolean voidSatellite, Properties properties) {
        super(properties);
        this.tier = tier;
        this.voidSatellite = voidSatellite;
    }

    public SatelliteItem(QuarryTier tier, Properties properties) {
        this(tier, false, properties);
    }

    @Override
    public QuarryTier getTier() {
        return tier;
    }

    public boolean isVoid() {
        return voidSatellite;
    }

    public int launchFuel() {
        return voidSatellite ? QuarryConfig.VOID_SATELLITE_LAUNCH_FUEL.get() : tier.launchFuel();
    }

    public int returnFuel() {
        return voidSatellite ? QuarryConfig.VOID_SATELLITE_RETURN_FUEL.get() : tier.returnFuel();
    }

    // the Void Satellite stays over its pad
    public int transferRange() {
        return voidSatellite ? 0 : tier.transferRange();
    }

    @Nullable
    public static QuarryTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof SatelliteItem satellite ? satellite.tier : null;
    }

    public static boolean isVoid(ItemStack stack) {
        return stack.getItem() instanceof SatelliteItem satellite && satellite.voidSatellite;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (voidSatellite) {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.void_satellite").withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.void_satellite.needs").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.satellite").withStyle(ChatFormatting.GRAY));
            int across = tier.chunkRadius() * 2 + 1;
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.satellite.area", across, across).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.satellite.launch").withStyle(ChatFormatting.DARK_GRAY));
    }
}

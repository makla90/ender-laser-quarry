package com.enderlaserquarry.content.satellite;

import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.content.Tooltips;
import com.enderlaserquarry.init.ModDataComponents;
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
    private static final java.text.NumberFormat DEW_FORMAT = java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT);

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

    // Dew of the Void it carries for space scans, mB
    public static int getDew(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SATELLITE_DEW.get(), 0);
    }

    public static void setDew(ItemStack stack, int mb) {
        if (mb <= 0) {
            stack.remove(ModDataComponents.SATELLITE_DEW.get());
        } else {
            stack.set(ModDataComponents.SATELLITE_DEW.get(), Math.min(mb, dewCapacity()));
        }
    }

    public static int dewCapacity() {
        return QuarryConfig.VOID_SATELLITE_DEW_TANK.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (voidSatellite) {
            tooltip.accept(Tooltips.line("void_satellite"));
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.void_satellite.dew", DEW_FORMAT.format(getDew(stack)),
                DEW_FORMAT.format(dewCapacity())).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.accept(Tooltips.line("satellite"));
        }
        if (!Tooltips.details(flag, tooltip)) {
            return;
        }
        if (voidSatellite) {
            tooltip.accept(Tooltips.line("void_satellite.scan"));
            tooltip.accept(Tooltips.line("void_satellite.no_ground"));
            tooltip.accept(Tooltips.line("void_satellite.needs"));
        } else {
            int across = tier.chunkRadius() * 2 + 1;
            tooltip.accept(Tooltips.line("satellite.area", across, across));
            tooltip.accept(Tooltips.line("satellite.controller", tier.displayName()));
        }
        tooltip.accept(Tooltips.line("satellite.launch"));
    }
}

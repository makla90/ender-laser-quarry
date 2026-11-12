package com.enderlaserquarry.client.screen;

import com.enderio.enderio.client.foundation.widgets.FluidStackWidget;
import com.enderio.enderio.foundation.fluid.FluidStorageInfo;
import com.google.common.base.Suppliers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Ender IO's tank, but an empty one says what belongs in it ("No XP Juice") instead of a plain "empty".
 */
public class NamedTankWidget extends FluidStackWidget {
    private final Supplier<FluidStorageInfo> tank;
    private final Supplier<Fluid> fluid;

    public NamedTankWidget(int x, int y, int width, int height, Supplier<FluidStorageInfo> tank, String fluidId) {
        super(x, y, width, height, tank);
        this.tank = tank;
        this.fluid = Suppliers.memoize(() -> BuiltInRegistries.FLUID.getValue(Identifier.parse(fluidId)));
    }

    @Override
    public void renderToolTip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        FluidStorageInfo info = tank.get();
        Fluid expected = fluid.get();
        if (info == null || !info.contents().isEmpty() || expected == null || expected == Fluids.EMPTY) {
            super.renderToolTip(g, mouseX, mouseY);
            return;
        }
        if (!isHovered(mouseX, mouseY)) {
            return;
        }
        Component name = new FluidStack(expected, 1).getHoverName();
        g.setComponentTooltipForNextFrame(Minecraft.getInstance().font, List.of(
            Component.translatable("gui.ender_laser_quarry.tank.empty", name),
            Component.translatable("gui.ender_laser_quarry.tank.amount", 0, UsageEnergyWidget.FORMAT.format(info.capacity()))
                .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
    }
}

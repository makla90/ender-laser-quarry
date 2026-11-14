package com.enderlaserquarry.client.screen;

import com.enderio.enderio.client.content.machines.gui.widget.CapacitorEnergyWidget;
import com.enderio.enderio.foundation.io.energy.IMachineEnergyStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Ender IO's energy bar with our usage lines in the tooltip.
 */
public class UsageEnergyWidget extends CapacitorEnergyWidget {
    public static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.ENGLISH);

    private final Supplier<IMachineEnergyStorage> storage;
    private final Supplier<List<Component>> usageLines;

    public UsageEnergyWidget(int x, int y, int width, int height, Supplier<IMachineEnergyStorage> storage,
                             Supplier<Boolean> capacitorInstalled, Supplier<List<Component>> usageLines) {
        // PORT: Ender IO 8.2.13 takes a plain IEnergyStorage supplier
        super(x, y, width, height, () -> storage.get(), capacitorInstalled);
        this.storage = storage;
        this.usageLines = usageLines;
    }

    @Override
    public void renderToolTip(GuiGraphics g, int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY)) {
            return;
        }
        IMachineEnergyStorage energy = storage.get();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.ender_laser_quarry.energy.stored",
            FORMAT.format(energy.getEnergyStored()), FORMAT.format(energy.getMaxEnergyStored())));
        lines.addAll(usageLines.get());
        g.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }
}

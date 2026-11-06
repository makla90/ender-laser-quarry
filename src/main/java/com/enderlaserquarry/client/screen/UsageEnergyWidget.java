package com.enderlaserquarry.client.screen;

import com.enderio.enderio.client.content.machines.gui.widget.CapacitorEnergyWidget;
import com.enderio.enderio.foundation.energy.EnergyStorageInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private final Supplier<EnergyStorageInfo> storage;
    private final Supplier<List<Component>> usageLines;

    public UsageEnergyWidget(int x, int y, int width, int height, Supplier<EnergyStorageInfo> storage,
                             Supplier<Boolean> capacitorInstalled, Supplier<List<Component>> usageLines) {
        super(x, y, width, height, storage, capacitorInstalled);
        this.storage = storage;
        this.usageLines = usageLines;
    }

    @Override
    public void renderToolTip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY)) {
            return;
        }
        EnergyStorageInfo energy = storage.get();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.ender_laser_quarry.energy.stored",
            FORMAT.format(energy.energy()), FORMAT.format(energy.capacity())));
        lines.addAll(usageLines.get());
        g.setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }
}

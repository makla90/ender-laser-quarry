package com.enderlaserquarry.client.screen;

import com.enderio.enderio.client.content.machines.gui.screen.base.MachineScreen;
import com.enderio.enderio.client.content.machines.gui.widget.ActivityWidget;
import net.minecraft.ChatFormatting;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderlaserquarry.content.QuarryStates;
import com.enderlaserquarry.content.dish.EndergyCapacitors;
import com.enderlaserquarry.content.dish.SatelliteDishMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class SatelliteDishScreen extends MachineScreen<SatelliteDishMenu> {
    private static final int TEXT_COLOR = 0x404040;

    public SatelliteDishScreen(SatelliteDishMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Ender IO widgets so it feels like their machines
        addRenderableOnly(new UsageEnergyWidget(leftPos + 15, topPos + 14, 9, 42, menu::getEnergyStorage,
            menu::isCapacitorInstalled, () -> List.of(
                Component.translatable("gui.ender_laser_quarry.energy.usage", UsageEnergyWidget.FORMAT.format(menu.getUsage()))
                    .withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.ender_laser_quarry.energy.max_usage",
                    UsageEnergyWidget.FORMAT.format(menu.getMaxUsage())).withStyle(ChatFormatting.GRAY))));

        addRenderableWidget(new RedstoneControlPickerWidget(leftPos + imageWidth - 6 - 16, topPos + 6,
            menu::getRedstoneControl, menu::setRedstoneControl, EIOCommonLang.REDSTONE_MODE));

        addRenderableWidget(new ActivityWidget(leftPos + imageWidth - 6 - 16, topPos + 16 * 4, menu::getMachineStates));

        var overlay = addIOConfigOverlay(1, leftPos + 7, topPos + 83, 162, 76);
        addIOConfigButton(leftPos + imageWidth - 6 - 16, topPos + 24, overlay);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GuiPanels.panel(g, leftPos, topPos, imageWidth, imageHeight);
        GuiPanels.inset(g, leftPos + 14, topPos + 13, 11, 44);  // energy bar well
        GuiPanels.slot(g, leftPos + 12, topPos + 60);           // capacitor
        GuiPanels.inset(g, leftPos + 32, topPos + 16, 116, 48); // info well
        GuiPanels.playerInventory(g, leftPos + 8, topPos + 84);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);

        var dish = menu.getBlockEntity();
        var obelisk = dish.getObeliskPos();
        int x = 36;

        g.drawString(font, Component.translatable("gui.ender_laser_quarry.dish.obelisk"), x, 19, TEXT_COLOR, false);
        g.drawString(font, obelisk != null
                ? Component.literal(obelisk.getX() + ", " + obelisk.getY() + ", " + obelisk.getZ())
                : Component.translatable("gui.ender_laser_quarry.dish.not_linked"),
            x + 4, 29, obelisk != null ? 0x1F5FBF : 0xA02020, false);

        // straight from the slot, the synced state lags a few ticks
        var capacitorProblem = EndergyCapacitors.dishProblem(dish.getTier(), dish.getInventory().getStackInSlot(dish.getCapacitorSlotIndex()));
        if (capacitorProblem != null) {
            g.drawString(font, Component.translatable("gui.ender_laser_quarry.dish.wrong_capacitor"), x, 40, 0xC02020, false);
            g.drawString(font, Component.translatable(capacitorProblem == QuarryStates.CAPACITOR_UNSUPPORTED
                ? "gui.ender_laser_quarry.dish.unsupported_capacitor_hint"
                : "gui.ender_laser_quarry.dish.wrong_capacitor_hint"), x + 4, 51, 0xC02020, false);
            return;
        }

        g.drawString(font, Component.translatable(dish.isFormed()
            ? "gui.ender_laser_quarry.dish.formed"
            : "gui.ender_laser_quarry.dish.incomplete"), x, 40, dish.isFormed() ? 0x2F8F2F : 0xA02020, false);

        g.drawString(font, Component.translatable(dish.isBeaming()
            ? "gui.ender_laser_quarry.dish.beaming"
            : "gui.ender_laser_quarry.dish.idle"), x, 51, dish.isBeaming() ? 0x7A2FBF : TEXT_COLOR, false);
    }
}

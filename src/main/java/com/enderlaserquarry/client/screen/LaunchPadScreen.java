package com.enderlaserquarry.client.screen;

import com.enderio.enderio.client.content.machines.gui.screen.base.MachineScreen;
import com.enderio.enderio.client.content.machines.gui.widget.ActivityWidget;
import com.enderio.enderio.client.foundation.widgets.FluidStackWidget;
import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.launchpad.FlightPhase;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.launchpad.LaunchPadMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LaunchPadScreen extends MachineScreen<LaunchPadMenu> {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int GOOD = 0xFF2F8F2F;
    private static final int BAD = 0xFFA02020;
    private static final int INFO = 0xFF1F5FBF;
    private static final int FLIGHT = 0xFF7A2FBF;

    private static final int TANK_X = 12;
    private static final int TANK_Y = 18;
    private static final int INFO_X = 58;
    private static final int INFO_Y = 16;
    private static final int INFO_W = 92;
    private static final int INFO_H = 58;

    private Button launchButton;

    public LaunchPadScreen(LaunchPadMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 186);
        inventoryLabelX = LaunchPadMenu.PLAYER_INV_X;
        inventoryLabelY = LaunchPadMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableOnly(new FluidStackWidget(leftPos + TANK_X, topPos + TANK_Y, 16, 52, menu::getFuelTank));

        launchButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LaunchPadMenu.BUTTON_LAUNCH);
                }
            })
            .bounds(leftPos + INFO_X, topPos + INFO_Y + INFO_H + 3, INFO_W, 18)
            .build());

        var overlay = addIOConfigOverlay(1, leftPos + 7, topPos + LaunchPadMenu.PLAYER_INV_Y - 1, 162, 76);
        addIOConfigButton(leftPos + imageWidth - 6 - 16, topPos + 6, overlay);
        addRenderableWidget(new ActivityWidget(leftPos + imageWidth - 6 - 16, topPos + 24, menu::getMachineStates));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        var pad = menu.getBlockEntity();
        launchButton.active = !pad.isFlying();
        launchButton.setMessage(Component.translatable(pad.isFlying()
            ? "gui.ender_laser_quarry.launch_pad.in_flight"
            : pad.hasSatelliteInOrbit() ? "gui.ender_laser_quarry.launch_pad.recover" : "gui.ender_laser_quarry.launch_pad.launch"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        GuiPanels.panel(g, leftPos, topPos, imageWidth, imageHeight);
        GuiPanels.inset(g, leftPos + TANK_X - 1, topPos + TANK_Y - 1, 18, 54);
        GuiPanels.slot(g, leftPos + LaunchPadMenu.ROCKET_X, topPos + LaunchPadMenu.ROCKET_Y);
        GuiPanels.slot(g, leftPos + LaunchPadMenu.SATELLITE_X, topPos + LaunchPadMenu.SATELLITE_Y);
        GuiPanels.inset(g, leftPos + INFO_X, topPos + INFO_Y, INFO_W, INFO_H);
        GuiPanels.playerInventory(g, leftPos + LaunchPadMenu.PLAYER_INV_X, topPos + LaunchPadMenu.PLAYER_INV_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);

        LaunchPadBlockEntity pad = menu.getBlockEntity();
        int x = INFO_X + 3;
        int y = INFO_Y + 3;

        // Structure
        String padKey = !pad.isPlatingComplete() ? "incomplete" : !pad.hasSky() ? "no_sky" : "ready";
        line(g, Component.translatable("gui.ender_laser_quarry.launch_pad.pad." + padKey), x, y, padKey.equals("ready") ? GOOD : BAD);

        // Ground station (the linked quarry) radar energy + uplink
        int radar = menu.getRadarEnergy();
        line(g, radar < 0
            ? Component.translatable("gui.ender_laser_quarry.launch_pad.radar_missing")
            : Component.translatable("gui.ender_laser_quarry.launch_pad.radar", radar), x, y + 10, radar > 0 ? TEXT_COLOR : BAD);

        var uplink = pad.getUplink();
        line(g, Component.translatable("gui.ender_laser_quarry.launch_pad.uplink." + uplink.name().toLowerCase()), x, y + 20,
            switch (uplink) {
                case ONLINE -> GOOD;
                case NO_SATELLITE -> TEXT_COLOR;
                default -> BAD;
            });

        // Flight / satellite
        line(g, flightStatus(pad), x, y + 30, pad.isFlying() ? FLIGHT : pad.hasSatelliteInOrbit() ? INFO : TEXT_COLOR);

        if (pad.getBeamTarget() != null) {
            // The quarry is bouncing its laser off the satellite right now
            line(g, Component.translatable("gui.ender_laser_quarry.launch_pad.reflector.lasering"), x, y + 40, GOOD);
        } else {
            // Scan coverage
            line(g, Component.translatable("gui.ender_laser_quarry.launch_pad.scan", pad.getScannedChunks(), pad.getScanTotal()),
                x, y + 40, pad.isScanComplete() ? GOOD : TEXT_COLOR);
        }
    }

    private Component flightStatus(LaunchPadBlockEntity pad) {
        float progress = pad.getPhaseProgress(0);
        int percent = Math.round(progress * 100);
        if (pad.isArriving() && pad.getPhase() == FlightPhase.IDLE) {
            return Component.translatable("gui.ender_laser_quarry.orbit.moving", Math.round(pad.getRelocationProgress(0) * 100));
        }
        return switch (pad.getPhase()) {
            case IDLE -> Component.translatable(pad.hasSatelliteInOrbit()
                ? "gui.ender_laser_quarry.launch_pad.sat_orbit"
                : "gui.ender_laser_quarry.launch_pad.sat_ground");
            case ORBIT_OPS -> Component.translatable(switch (pad.getMission()) {
                case DEPLOY -> "gui.ender_laser_quarry.launch_pad.deploying";
                case RECOVER -> "gui.ender_laser_quarry.launch_pad.docking";
                case TRANSFER -> "gui.ender_laser_quarry.launch_pad.fuelling";
            });
            case COUNTDOWN -> Component.translatable("gui.ender_laser_quarry.launch_pad.countdown",
                (int) Math.ceil((1 - progress) * FlightPhase.COUNTDOWN.duration() / 20f));
            case ASCENT -> Component.translatable("gui.ender_laser_quarry.launch_pad.ascent", percent);
            case DESCENT -> Component.translatable("gui.ender_laser_quarry.launch_pad.landing", percent);
        };
    }

    private void line(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Hints for the empty slots (EnderContainerScreen.renderTooltip is final, so draw them here)
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            String key = null;
            if (hoveredSlot.x == LaunchPadMenu.ROCKET_X && hoveredSlot.y == LaunchPadMenu.ROCKET_Y) {
                key = "rocket_slot";
            } else if (hoveredSlot.x == LaunchPadMenu.SATELLITE_X && hoveredSlot.y == LaunchPadMenu.SATELLITE_Y) {
                key = "satellite_slot";
            }
            if (key != null) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.ender_laser_quarry.launch_pad." + key), mouseX, mouseY);
            }
        }

        // where the satellite is, players work with coordinates
        LaunchPadBlockEntity pad = menu.getBlockEntity();
        if (isHovering(INFO_X, INFO_Y, INFO_W, INFO_H, mouseX, mouseY) && (pad.hasSatelliteInOrbit() || pad.isRelocating())) {
            var area = pad.getOrbitChunk();
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.translatable("gui.ender_laser_quarry.orbit.over",
                Component.translatable("gui.ender_laser_quarry.orbit.coords", area.getMiddleBlockX(), area.getMiddleBlockZ())));
            if (pad.getOrbitDx() != 0 || pad.getOrbitDz() != 0) {
                lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.orbit_offset", pad.getOrbitDx(), pad.getOrbitDz())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(pad.getTransferRange() > 0 ? "gui.ender_laser_quarry.launch_pad.orbit_hint"
                : "gui.ender_laser_quarry.orbit.basic").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }

        // Fuel cost hint on the launch button
        if (isHovering(INFO_X, INFO_Y + INFO_H + 3, INFO_W, 18, mouseX, mouseY) && !menu.getBlockEntity().isFlying()) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.ender_laser_quarry.launch_pad.fuel_cost",
                QuarryConfig.FUEL_PER_FLIGHT.get()), mouseX, mouseY);
        }
    }
}

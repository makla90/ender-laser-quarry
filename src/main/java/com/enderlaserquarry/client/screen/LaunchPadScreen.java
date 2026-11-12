package com.enderlaserquarry.client.screen;

import com.enderio.enderio.client.content.machines.gui.screen.base.MachineScreen;
import com.enderio.enderio.client.content.machines.gui.widget.ActivityWidget;
import com.enderio.enderio.client.foundation.widgets.FluidStackWidget;
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

    private static final int INFO_X = 78;
    private static final int INFO_Y = 16;
    private static final int INFO_W = 92;
    private static final int INFO_H = 58;

    private static final java.text.NumberFormat FORMAT = java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT);

    private Button launchButton;
    private Button resupplyButton;
    private static final int RESUPPLY_X = 55;
    private NamedTankWidget dewWidget;
    private static final int RESUPPLY_Y = 77;

    public LaunchPadScreen(LaunchPadMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LaunchPadMenu.GUI_WIDTH, 186);
        inventoryLabelX = LaunchPadMenu.PLAYER_INV_X;
        inventoryLabelY = LaunchPadMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();

        // energy, rocket fuel, Dew: side by side like the quarry controller's bars
        addRenderableOnly(new UsageEnergyWidget(leftPos + LaunchPadMenu.ENERGY_X, topPos + LaunchPadMenu.BAR_Y, 9, LaunchPadMenu.BAR_HEIGHT,
            () -> new com.enderio.enderio.foundation.energy.EnergyStorageInfo(menu.getEnergyStored(), menu.getEnergyCapacity()),
            () -> true, this::usageLines));
        addRenderableOnly(new NamedTankWidget(leftPos + LaunchPadMenu.FUEL_X, topPos + LaunchPadMenu.BAR_Y, 9, LaunchPadMenu.BAR_HEIGHT,
            menu::getFuelTank, "enderio:fluid_rocket_fuel_still"));
        dewWidget = addRenderableOnly(new NamedTankWidget(leftPos + LaunchPadMenu.DEW_X, topPos + LaunchPadMenu.BAR_Y, 9,
            LaunchPadMenu.BAR_HEIGHT, menu::getDewTank, "enderio:fluid_dew_of_the_void_still"));
        dewWidget.visible = showsDew();

        // resupply: an icon button under the satellite slot, only with the Void Satellite up
        resupplyButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LaunchPadMenu.BUTTON_RESUPPLY);
                }
            })
            .bounds(leftPos + RESUPPLY_X, topPos + RESUPPLY_Y, 18, 18)
            .build());

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
        resupplyButton.visible = pad.isVoidSatelliteUp();
        dewWidget.visible = showsDew();
        resupplyButton.active = !pad.isFlying() && !pad.isRelocating() && dew() > 0 && fuel() >= menu.getResupplyCost()
            && pad.getSatelliteDew() < menu.getDewCapacity();
        int cost = menu.getLaunchCost();
        if (!pad.isFlying() && cost > 0 && fuel() < cost) {
            // too little fuel: the button says how much it takes
            launchButton.setMessage(Component.translatable("gui.ender_laser_quarry.launch_pad.need_fuel", FORMAT.format(cost))
                .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        launchButton.setMessage(Component.translatable(pad.isFlying()
            ? "gui.ender_laser_quarry.launch_pad.in_flight"
            : pad.hasSatelliteInOrbit() ? "gui.ender_laser_quarry.launch_pad.recover" : "gui.ender_laser_quarry.launch_pad.launch"));
    }

    private int dew() {
        var tank = menu.getDewTank();
        return tank == null ? 0 : tank.contents().getAmount();
    }

    private int fuel() {
        var tank = menu.getFuelTank();
        return tank == null ? 0 : tank.contents().getAmount();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        GuiPanels.panel(g, leftPos, topPos, imageWidth, imageHeight);
        GuiPanels.inset(g, leftPos + LaunchPadMenu.ENERGY_X - 1, topPos + LaunchPadMenu.BAR_Y - 1, 11, LaunchPadMenu.BAR_HEIGHT + 2);
        GuiPanels.inset(g, leftPos + LaunchPadMenu.FUEL_X - 1, topPos + LaunchPadMenu.BAR_Y - 1, 11, LaunchPadMenu.BAR_HEIGHT + 2);
        if (showsDew()) {
            GuiPanels.inset(g, leftPos + LaunchPadMenu.DEW_X - 1, topPos + LaunchPadMenu.BAR_Y - 1, 11, LaunchPadMenu.BAR_HEIGHT + 2);
        }
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

        // Radar on the ring (pad energy) + uplink
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

        if (pad.isVoidSatelliteUp()) {
            // the Void Satellite maps no ground: its Dew of the Void instead
            line(g, Component.translatable("gui.ender_laser_quarry.launch_pad.sat_dew", FORMAT.format(pad.getSatelliteDew())),
                x, y + 40, pad.isOutOfDew() ? BAD : INFO);
        } else if (pad.getBeamTarget() != null) {
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
                case RESUPPLY -> "gui.ender_laser_quarry.launch_pad.resupplying";
            });
            case COUNTDOWN -> Component.translatable("gui.ender_laser_quarry.launch_pad.countdown",
                (int) Math.ceil((1 - progress) * FlightPhase.COUNTDOWN.duration() / 20f));
            case ASCENT -> Component.translatable("gui.ender_laser_quarry.launch_pad.ascent", percent);
            case DESCENT -> Component.translatable("gui.ender_laser_quarry.launch_pad.landing", percent);
        };
    }

    private static final java.util.function.Supplier<net.minecraft.world.item.ItemStack> DEW_BUCKET = com.google.common.base.Suppliers.memoize(
        () -> new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("enderio", "dew_of_the_void_bucket"))));

    private java.util.List<Component> resupplyLines() {
        var pad = menu.getBlockEntity();
        var lines = new java.util.ArrayList<Component>();
        int space = Math.max(0, menu.getDewCapacity() - pad.getSatelliteDew());
        lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.resupply"));
        lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.resupply.what", FORMAT.format(Math.min(space, dew())),
            FORMAT.format(menu.getResupplyCost())).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.sat_dew_of", FORMAT.format(pad.getSatelliteDew()),
            FORMAT.format(menu.getDewCapacity())).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        String problem = pad.isFlying() ? null
            : space <= 0 ? "message.ender_laser_quarry.launch.resupply_full"
            : dew() <= 0 ? "message.ender_laser_quarry.launch.resupply_no_dew"
            : null;
        if (problem != null) {
            lines.add(Component.translatable(problem).withStyle(net.minecraft.ChatFormatting.RED));
        } else if (!pad.isFlying() && fuel() < menu.getResupplyCost()) {
            lines.add(Component.translatable("message.ender_laser_quarry.launch.no_fuel", FORMAT.format(fuel()),
                FORMAT.format(menu.getResupplyCost())).withStyle(net.minecraft.ChatFormatting.RED));
        }
        return lines;
    }

    // the Dew tank only matters around the Void Satellite (or once Dew is in it)
    private boolean showsDew() {
        var pad = menu.getBlockEntity();
        return pad.isVoidSatelliteUp() || pad.isDrawnVoid() || dew() > 0
            || com.enderlaserquarry.content.satellite.SatelliteItem.isVoid(menu.getSlot(1).getItem());
    }

    // energy bar tooltip, below the stored line
    private java.util.List<Component> usageLines() {
        var lines = powerLines();
        lines.remove(0);
        return lines;
    }

    private java.util.List<Component> powerLines() {
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.power.stored", FORMAT.format(menu.getEnergyStored()),
            FORMAT.format(menu.getEnergyCapacity())));
        lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.power.radar", FORMAT.format(menu.getRadarUsage()))
            .withStyle(net.minecraft.ChatFormatting.AQUA));
        boolean linked = menu.getBlockEntity().getStation() != null;
        lines.add(Component.translatable(linked ? "gui.ender_laser_quarry.launch_pad.power.antenna"
                : "gui.ender_laser_quarry.launch_pad.power.no_antenna", FORMAT.format(menu.getAntennaInput()))
            .withStyle(linked ? net.minecraft.ChatFormatting.DARK_AQUA : net.minecraft.ChatFormatting.RED));
        lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.power.ports").withStyle(net.minecraft.ChatFormatting.GRAY));
        return lines;
    }

    private void line(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (resupplyButton.visible) {
            g.item(DEW_BUCKET.get(), resupplyButton.getX() + 1, resupplyButton.getY() + 1);
            if (resupplyButton.isMouseOver(mouseX, mouseY)) {
                g.setComponentTooltipForNextFrame(font, resupplyLines(), mouseX, mouseY);
                return;
            }
        }

        // the radar line: where its power comes from and what it uses
        if (isHovering(INFO_X, INFO_Y + 12, INFO_W, 10, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, powerLines(), mouseX, mouseY);
            return;
        }

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
        int cost = menu.getLaunchCost();
        if (isHovering(INFO_X, INFO_Y + INFO_H + 3, INFO_W, 18, mouseX, mouseY) && !menu.getBlockEntity().isFlying() && cost >= 0) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.translatable("gui.ender_laser_quarry.launch_pad.fuel_cost", FORMAT.format(cost)));
            if (fuel() < cost) {
                lines.add(Component.translatable("message.ender_laser_quarry.launch.no_fuel", FORMAT.format(fuel()), FORMAT.format(cost))
                    .withStyle(net.minecraft.ChatFormatting.RED));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }
}

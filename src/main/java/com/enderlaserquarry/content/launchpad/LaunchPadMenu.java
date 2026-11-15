package com.enderlaserquarry.content.launchpad;

import com.enderlaserquarry.content.PlayerMessages;
import com.enderio.core.common.network.menu.IntSyncSlot;
import com.enderio.enderio.foundation.fluid.FluidStorageInfo;
import com.enderio.enderio.foundation.fluid.FluidStorageSyncSlot;
import com.enderio.enderio.foundation.menu.MachineMenu;
import com.enderio.enderio.foundation.menu.MachineSlot;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class LaunchPadMenu extends MachineMenu<LaunchPadBlockEntity> {
    // Layout shared with the screen
    public static final int GUI_WIDTH = 198;
    // energy, rocket fuel and Dew side by side, like the quarry controller's bars
    public static final int ENERGY_X = 8;
    public static final int FUEL_X = 23;
    public static final int BAR_Y = 18;
    public static final int BAR_HEIGHT = 72;
    public static final int ROCKET_X = 56;
    public static final int ROCKET_Y = 22;
    public static final int SATELLITE_X = 56;
    public static final int SATELLITE_Y = 52;
    public static final int PLAYER_INV_X = (GUI_WIDTH - 162) / 2;
    public static final int PLAYER_INV_Y = 104;

    public static final int BUTTON_LAUNCH = 0;
    public static final int BUTTON_RESUPPLY = 1;
    public static final int DEW_X = 38;

    private final FluidStorageSyncSlot fuelTank;
    private final IntSyncSlot radarEnergy;
    // mB the next launch burns, -1 = nothing to fly (common config isn't synced)
    private final IntSyncSlot launchCost;
    private final FluidStorageSyncSlot dewTank;
    // resupply flight price (the orbiting satellite's returnFuel), its Dew, its Dew tank
    private final IntSyncSlot resupplyCost;
    private final IntSyncSlot dewCapacity;
    // power for the radar tooltip: stored, capacity, what the radar uses and what the quarry antenna sends (µI/t)
    private final IntSyncSlot energyStored;
    private final IntSyncSlot energyCapacity;
    private final IntSyncSlot radarUsage;
    private final IntSyncSlot antennaInput;

    public LaunchPadMenu(int containerId, Inventory playerInventory, LaunchPadBlockEntity be) {
        super(ModMenus.LAUNCH_PAD.get(), containerId, playerInventory, be);
        fuelTank = addSyncSlot(FluidStorageSyncSlot.readOnly(() -> FluidStorageInfo.of(be.getFuelTank())));
        radarEnergy = addSyncSlot(IntSyncSlot.readOnly(() -> be.getRadar() != null ? be.getEnergyPercent() : -1));
        launchCost = addSyncSlot(IntSyncSlot.readOnly(be::getLaunchCost));
        dewTank = addSyncSlot(FluidStorageSyncSlot.readOnly(() -> FluidStorageInfo.of(be.getDewTank())));
        resupplyCost = addSyncSlot(IntSyncSlot.readOnly(be::getTransferBaseCost));
        dewCapacity = addSyncSlot(IntSyncSlot.readOnly(com.enderlaserquarry.content.satellite.SatelliteItem::dewCapacity));
        energyStored = addSyncSlot(IntSyncSlot.readOnly(() -> be.getEnergy().getEnergyStored()));
        energyCapacity = addSyncSlot(IntSyncSlot.readOnly(() -> be.getEnergy().getMaxEnergyStored()));
        radarUsage = addSyncSlot(IntSyncSlot.readOnly(be::getRadarUsage));
        antennaInput = addSyncSlot(IntSyncSlot.readOnly(be::getAntennaInput));
        addSlots();
    }

    public LaunchPadMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.LAUNCH_PAD.get(), containerId, playerInventory, buf, ModBlockEntities.LAUNCH_PAD.get());
        fuelTank = addSyncSlot(FluidStorageSyncSlot.standalone());
        radarEnergy = addSyncSlot(IntSyncSlot.standalone());
        launchCost = addSyncSlot(IntSyncSlot.standalone());
        dewTank = addSyncSlot(FluidStorageSyncSlot.standalone());
        resupplyCost = addSyncSlot(IntSyncSlot.standalone());
        dewCapacity = addSyncSlot(IntSyncSlot.standalone());
        energyStored = addSyncSlot(IntSyncSlot.standalone());
        energyCapacity = addSyncSlot(IntSyncSlot.standalone());
        radarUsage = addSyncSlot(IntSyncSlot.standalone());
        antennaInput = addSyncSlot(IntSyncSlot.standalone());
        addSlots();
    }

    private void addSlots() {
        var inventory = getMachineInventory();
        addSlot(new MachineSlot(inventory, LaunchPadBlockEntity.ROCKET.getIndex(), ROCKET_X, ROCKET_Y));
        addSlot(new MachineSlot(inventory, LaunchPadBlockEntity.SATELLITE.getIndex(), SATELLITE_X, SATELLITE_Y));
        addPlayerInventorySlots(PLAYER_INV_X, PLAYER_INV_Y);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_LAUNCH) {
            var error = getBlockEntity().tryLaunch();
            if (error != null) {
                PlayerMessages.show(player, error.copy().withStyle(ChatFormatting.RED));
            }
            return true;
        }
        if (id == BUTTON_RESUPPLY) {
            var error = getBlockEntity().tryResupply();
            if (error != null) {
                PlayerMessages.show(player, error.copy().withStyle(ChatFormatting.RED));
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    public FluidStorageInfo getFuelTank() {
        return fuelTank.get();
    }

    // percent, -1 = no radar
    public int getRadarEnergy() {
        return radarEnergy.get();
    }

    public int getLaunchCost() {
        return launchCost.get();
    }

    public FluidStorageInfo getDewTank() {
        return dewTank.get();
    }

    public int getResupplyCost() {
        return resupplyCost.get();
    }

    public int getDewCapacity() {
        return dewCapacity.get();
    }

    public int getEnergyStored() {
        return energyStored.get();
    }

    public int getEnergyCapacity() {
        return energyCapacity.get();
    }

    public int getRadarUsage() {
        return radarUsage.get();
    }

    public int getAntennaInput() {
        return antennaInput.get();
    }
}

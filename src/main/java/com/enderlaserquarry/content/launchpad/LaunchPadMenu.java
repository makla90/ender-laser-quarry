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
    public static final int ROCKET_X = 36;
    public static final int ROCKET_Y = 22;
    public static final int SATELLITE_X = 36;
    public static final int SATELLITE_Y = 52;
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 104;

    public static final int BUTTON_LAUNCH = 0;

    private final FluidStorageSyncSlot fuelTank;
    private final IntSyncSlot radarEnergy;

    public LaunchPadMenu(int containerId, Inventory playerInventory, LaunchPadBlockEntity be) {
        super(ModMenus.LAUNCH_PAD.get(), containerId, playerInventory, be);
        fuelTank = addSyncSlot(FluidStorageSyncSlot.readOnly(() -> FluidStorageInfo.of(be.getFuelTank())));
        radarEnergy = addSyncSlot(IntSyncSlot.readOnly(() -> {
            var radar = be.getRadar();
            return radar != null ? radar.getEnergyPercent() : -1;
        }));
        addSlots();
    }

    public LaunchPadMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.LAUNCH_PAD.get(), containerId, playerInventory, buf, ModBlockEntities.LAUNCH_PAD.get());
        fuelTank = addSyncSlot(FluidStorageSyncSlot.standalone());
        radarEnergy = addSyncSlot(IntSyncSlot.standalone());
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
        return super.clickMenuButton(player, id);
    }

    public FluidStorageInfo getFuelTank() {
        return fuelTank.get();
    }

    // percent, -1 = no radar
    public int getRadarEnergy() {
        return radarEnergy.get();
    }
}

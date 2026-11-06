package com.enderlaserquarry.content.dish;

import com.enderio.core.common.network.menu.IntSyncSlot;
import com.enderio.enderio.foundation.menu.PoweredMachineMenu;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public class SatelliteDishMenu extends PoweredMachineMenu<SatelliteDishBlockEntity> {
    private final IntSyncSlot usage;
    private final IntSyncSlot maxUsage;

    public SatelliteDishMenu(int containerId, Inventory playerInventory, SatelliteDishBlockEntity blockEntity) {
        super(ModMenus.SATELLITE_DISH.get(), containerId, playerInventory, blockEntity);
        usage = addSyncSlot(IntSyncSlot.readOnly(blockEntity::getUsage));
        // Ender IO's synced energy info has no max use
        maxUsage = addSyncSlot(IntSyncSlot.readOnly(() -> blockEntity.getMaxEnergyUse()));
        addSlots();
    }

    public SatelliteDishMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.SATELLITE_DISH.get(), containerId, playerInventory, buf, ModBlockEntities.SATELLITE_DISH.get());
        usage = addSyncSlot(IntSyncSlot.standalone());
        maxUsage = addSyncSlot(IntSyncSlot.standalone());
        addSlots();
    }

    public int getUsage() {
        return usage.get();
    }

    public int getMaxUsage() {
        return maxUsage.get();
    }

    private void addSlots() {
        addCapacitorSlot(12, 60);
        addPlayerInventorySlots(8, 84);
    }
}

package com.enderlaserquarry.content.obelisk;

import com.enderio.core.common.menu.BaseBlockEntityMenu;
import com.enderio.core.common.network.menu.IntSyncSlot;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderlaserquarry.content.QuarryStates;
import com.enderlaserquarry.content.controller.MiningRules;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Obelisk GUI: crystal slot and status.
 */
public class CrystalObeliskMenu extends BaseBlockEntityMenu<CrystalObeliskBlockEntity> {
    public static final int CRYSTAL_X = 80;
    public static final int CRYSTAL_Y = 20;
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 84;

    // synced as an index, -1 = ready
    private static final List<MachineState> PROBLEMS = Stream.concat(Stream.of(QuarryStates.OBELISK_INCOMPLETE, QuarryStates.NO_RADAR,
        QuarryStates.NO_CONTROLLER, QuarryStates.NO_CRYSTAL, QuarryStates.CRYSTAL_BROKEN, QuarryStates.CRYSTAL_TIER_TOO_LOW, QuarryStates.NO_DISHES), QuarryStates.allQuarryNeedsPlating().stream()).toList();

    private final IntSyncSlot problem;
    private final IntSyncSlot activeDishes;
    private final IntSyncSlot dishes;
    private final IntSyncSlot linked;
    // crystal enchant cost multiplier x100, from the server config
    private final IntSyncSlot costPercent;

    public CrystalObeliskMenu(int containerId, Inventory playerInventory, CrystalObeliskBlockEntity obelisk) {
        super(ModMenus.CRYSTAL_OBELISK.get(), containerId, playerInventory, obelisk);
        addSlot(new SlotItemHandler(obelisk.getCrystalSlot(), 0, CRYSTAL_X, CRYSTAL_Y));
        addPlayerInventorySlots(PLAYER_INV_X, PLAYER_INV_Y);

        // List.of(...).indexOf(null) throws, so check for null first
        problem = addSyncSlot(IntSyncSlot.readOnly(() -> {
            MachineState current = obelisk.getProblem();
            return current != null ? PROBLEMS.indexOf(current) : -1;
        }));
        activeDishes = addSyncSlot(IntSyncSlot.readOnly(() -> obelisk.getActiveDishes().size()));
        dishes = addSyncSlot(IntSyncSlot.readOnly(() -> obelisk.getDishes().size()));
        linked = addSyncSlot(IntSyncSlot.readOnly(() -> obelisk.getSatellitePad() != null ? 1 : 0));
        costPercent = addSyncSlot(IntSyncSlot.readOnly(() -> (int) Math.round(MiningRules.enchantCostScale(obelisk.getCrystalEnchantments()) * 100)));
    }

    public CrystalObeliskMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.CRYSTAL_OBELISK.get(), containerId, playerInventory, buf, ModBlockEntities.CRYSTAL_OBELISK.get());
        // same rules as the server so the client predicts clicks right
        addSlot(new SlotItemHandler(new ItemStackHandler(1) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() instanceof LaserCrystalItem;
            }
        }, 0, CRYSTAL_X, CRYSTAL_Y));
        addPlayerInventorySlots(PLAYER_INV_X, PLAYER_INV_Y);

        problem = addSyncSlot(IntSyncSlot.standalone());
        activeDishes = addSyncSlot(IntSyncSlot.standalone());
        dishes = addSyncSlot(IntSyncSlot.standalone());
        linked = addSyncSlot(IntSyncSlot.standalone());
        costPercent = addSyncSlot(IntSyncSlot.standalone());
    }

    @Nullable
    public MachineState getProblem() {
        int index = problem.get();
        return index >= 0 && index < PROBLEMS.size() ? PROBLEMS.get(index) : null;
    }

    /** For Ender IO's activity widget. */
    public Set<MachineState> getStates() {
        MachineState current = getProblem();
        return current != null ? Set.of(current) : Set.of();
    }

    public int getActiveDishes() {
        return activeDishes.get();
    }

    public int getDishes() {
        return dishes.get();
    }

    public boolean isLinked() {
        return linked.get() != 0;
    }

    public double getCostMultiplier() {
        return costPercent.get() / 100.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(stack.getItem() instanceof LaserCrystalItem) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}

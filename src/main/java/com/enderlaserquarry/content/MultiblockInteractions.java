package com.enderlaserquarry.content;

import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.linker.LaserLinkerItem;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.dish.EndergyCapacitors;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Clicks on frame blocks go to the multiblock core (GUI, buckets). Also capacitor swapping by right click like in 1.12,
 * and coolant buckets.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class MultiblockInteractions {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (tryInsertCapacitor(event) || tryFillCoolant(event)) {
            return;
        }
        if (!forwardsToCore(player, event.getItemStack())) {
            return;
        }

        BlockEntity core = MultiblockFrames.findCore(event.getLevel(), event.getPos());
        if (core == null) {
            return;
        }

        BlockState coreState = core.getBlockState();
        BlockHitResult hit = event.getHitVec().withPosition(core.getBlockPos());

        InteractionResult itemResult = coreState.useItemOn(event.getItemStack(), event.getLevel(), player, event.getHand(), hit);
        InteractionResult result;
        if (itemResult.consumesAction()) {
            result = itemResult;
        } else if (itemResult == InteractionResult.TRY_WITH_EMPTY_HAND && event.getHand() == InteractionHand.MAIN_HAND) {
            result = coreState.useWithoutItem(event.getLevel(), player, hit);
        } else {
            return;
        }

        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    // Swaps the capacitor, the old one goes back to the player
    private static boolean tryInsertCapacitor(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || player.isSpectator() || player.isSecondaryUseActive()) {
            return false;
        }
        Level level = event.getLevel();
        BlockEntity target = level.getBlockEntity(event.getPos());
        if (!isOurCapacitorMachine(target)) {
            target = MultiblockFrames.findCore(level, event.getPos());
        }
        if (!isOurCapacitorMachine(target)) {
            return false;
        }
        PoweredMachineBlockEntity machine = (PoweredMachineBlockEntity) target;
        int slot = machine.getCapacitorSlotIndex();
        var inventory = machine.getInventory();
        ItemStack current = inventory.getStack(slot);
        if (!inventory.isValid(slot, ItemResource.of(held)) || ItemStack.isSameItemSameComponents(current, held)) {
            return false;
        }

        if (!level.isClientSide()) {
            ItemStack old = current.copy();
            inventory.setStack(slot, held.copyWithCount(1));
            held.consume(1, player);
            if (!old.isEmpty()) {
                player.getInventory().placeItemBackInInventory(old);
            }
            level.playSound(null, machine.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1, 0.8f);
            ItemStack inserted = inventory.getStack(slot);
            if (EndergyCapacitors.isUnsupported(inserted)) {
                PlayerMessages.show(player, Component.translatable("message.ender_laser_quarry.capacitor_unsupported").withStyle(ChatFormatting.RED));
            } else if (machine instanceof SatelliteDishBlockEntity dish && !EndergyCapacitors.fits(dish.getTier(), inserted)) {
                PlayerMessages.show(player, Component.translatable("message.ender_laser_quarry.dish.wrong_capacitor").withStyle(ChatFormatting.RED));
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        return true;
    }

    // Water on the controller, floor or obelisk goes into the controller tank
    private static boolean tryFillCoolant(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || player.isSpectator() || player.isSecondaryUseActive()
            || FluidUtil.getFirstStackContained(held).isEmpty()) {
            return false;
        }
        // water into the coolant tank, liquid XP into the XP tank
        var fluid = FluidUtil.getFirstStackContained(held);
        var tank = fluid.is(QuarryControllerBlockEntity.QUARRY_COOLANT) ? QuarryControllerBlockEntity.COOLANT
            : fluid.is(Tags.Fluids.EXPERIENCE) ? QuarryControllerBlockEntity.XP : null;
        if (tank == null) {
            return false;
        }
        Level level = event.getLevel();
        BlockEntity target = level.getBlockEntity(event.getPos());
        if (!(target instanceof QuarryControllerBlockEntity) && !(target instanceof CrystalObeliskBlockEntity)) {
            target = MultiblockFrames.findCore(level, event.getPos());
        }
        if (target instanceof CrystalObeliskBlockEntity obelisk) {
            target = obelisk.getController();
        }
        if (!(target instanceof QuarryControllerBlockEntity controller)) {
            return false;
        }
        if (!level.isClientSide()
            && !controller.handleFluidItemInteraction(player, event.getHand(), held, controller.getFluidStorage(), tank)) {
            return false;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        return true;
    }

    private static boolean isOurCapacitorMachine(@Nullable BlockEntity be) {
        return be instanceof PoweredMachineBlockEntity machine && machine.supportsCapacitor()
            && EnderLaserQuarry.MOD_ID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getNamespace());
    }

    public static boolean forwardsToCore(Player player, ItemStack stack) {
        if (player.isSpectator() || stack.getItem() instanceof LaserLinkerItem) {
            // the linker handles frames itself
            return false;
        }
        if (stack.is(EIOTags.Items.WRENCH)) {
            // wrench has to reach the port itself to change its IO mode
            return false;
        }
        // sneaking with an item in hand skips it like vanilla, so you can still build on them
        return !player.isSecondaryUseActive() || (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty());
    }

    private MultiblockInteractions() {
    }
}

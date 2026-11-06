package com.enderlaserquarry.content.trash;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.VoidingResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Creative trash can, voids items and fluids. No block entity.
 */
public class TrashCanBlock extends Block {
    public static final ResourceHandler<ItemResource> ITEM_VOID = new VoidingResourceHandler<>(ItemResource.EMPTY);
    public static final ResourceHandler<FluidResource> FLUID_VOID = new VoidingResourceHandler<>(FluidResource.EMPTY);

    public TrashCanBlock(Properties properties) {
        super(properties);
    }

    // Capabilities without a BE need invalidating by hand, neighbours cache them
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        level.invalidateCapabilities(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new HopperMenu(id, inventory, new VoidContainer()),
                Component.translatable("block.ender_laser_quarry.trash_can")));
        }
        return InteractionResult.SUCCESS;
    }

    private static class VoidContainer extends SimpleContainer {
        VoidContainer() {
            super(5);
        }

        @Override
        public void setItem(int index, ItemStack stack) {
        }
    }
}

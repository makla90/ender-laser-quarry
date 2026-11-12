package com.enderlaserquarry.content.launchpad;

import com.enderio.enderio.foundation.block.MachineBlock;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.content.ConnectedFrameBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Launch pad controller block, goes on the ring of the 5x5 pad.
 */
public class LaunchPadBlock extends MachineBlock<LaunchPadBlockEntity> {

    public LaunchPadBlock(Properties properties) {
        super(ModBlockEntities.LAUNCH_PAD::get, properties);
        BlockState state = defaultBlockState();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), false);
        }
        registerDefaultState(state);
    }

    // connects to the plating like LaunchPadPlatingBlock, the models use uvlock so the top stays world aligned
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ConnectedFrameBlock.NORTH, ConnectedFrameBlock.EAST, ConnectedFrameBlock.SOUTH, ConnectedFrameBlock.WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), joinsPad(neighbour));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), joinsPad(neighborState));
        }
        return state;
    }

    // plating, ports and the Ground Radar on the ring
    private static boolean joinsPad(BlockState neighbour) {
        return neighbour.is(LaunchPadBlockEntity.FRAME_BLOCKS) || neighbour.getBlock() instanceof com.enderlaserquarry.content.radar.GroundRadarBlock;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        // full buckets go into the tank like on the Vat; empty ones are left out, Ender IO's handler would drain the fuel
        var contained = stack.isEmpty() ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
            : net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(stack);
        if (!contained.isEmpty() && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad && pad.tankFor(contained) != null) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (pad.handleFluidItemInteraction(player, hand, stack, pad.getFluidStorage(), pad.tankFor(contained))) {
                player.getInventory().setChanged();
                return InteractionResult.CONSUME;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}

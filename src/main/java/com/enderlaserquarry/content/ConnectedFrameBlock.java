package com.enderlaserquarry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Frame block with connected textures.
 */
public abstract class ConnectedFrameBlock extends Block {
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;

    protected ConnectedFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    protected abstract boolean connectsTo(BlockState neighbour);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = context.getClickedPos().relative(direction);
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connectsTo(context.getLevel().getBlockState(neighbour)));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connectsTo(neighborState));
        }
        return state;
    }
}

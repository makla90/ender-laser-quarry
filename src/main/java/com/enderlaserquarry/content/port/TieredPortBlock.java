package com.enderlaserquarry.content.port;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Port without a tier of its own: it looks like the plating tier next to it and fits any frame.
 */
public class TieredPortBlock extends StructurePortBlock {
    public static final EnumProperty<QuarryTier> TIER = EnumProperty.create("tier", QuarryTier.class);

    private static final Direction[] SIDES = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    public TieredPortBlock(Structure structure, Properties properties) {
        super(structure, properties);
        registerDefaultState(defaultBlockState().setValue(TIER, QuarryTier.BASIC));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TIER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(TIER, tierAround(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        state = super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
        if (direction.getAxis().isHorizontal()) {
            state = state.setValue(TIER, tierAround(level, pos));
        }
        return state;
    }

    // first plating N/E/S/W, else a port next to it, else basic
    public QuarryTier tierAround(LevelReader level, BlockPos pos) {
        QuarryTier fromPort = null;
        for (Direction side : SIDES) {
            BlockState neighbour = level.getBlockState(pos.relative(side));
            QuarryTier tier = platingTier(neighbour);
            if (tier != null) {
                return tier;
            }
            if (fromPort == null && neighbour.getBlock() instanceof TieredPortBlock port && port.getStructure() == getStructure()) {
                fromPort = neighbour.getValue(TIER);
            }
        }
        return fromPort != null ? fromPort : QuarryTier.BASIC;
    }

    @Nullable
    private QuarryTier platingTier(BlockState state) {
        return switch (getStructure()) {
            case LASER_QUARRY -> ModBlocks.quarryFloorTier(state);
            case SATELLITE_DISH -> ModBlocks.dishFrameTier(state);
            case LAUNCH_PAD -> null;
        };
    }
}

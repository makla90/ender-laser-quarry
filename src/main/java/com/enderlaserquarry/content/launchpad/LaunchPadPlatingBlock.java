package com.enderlaserquarry.content.launchpad;

import com.enderlaserquarry.content.ConnectedFrameBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Launch pad plating. The middle one of a complete pad shows the orange pad (CENTER, set by the controller).
 */
public class LaunchPadPlatingBlock extends ConnectedFrameBlock {
    public static final BooleanProperty CENTER = BooleanProperty.create("center");

    public LaunchPadPlatingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CENTER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CENTER);
    }

    @Override
    protected boolean connectsTo(BlockState neighbour) {
        return neighbour.is(LaunchPadBlockEntity.FRAME_BLOCKS) || neighbour.getBlock() instanceof LaunchPadBlock;
    }
}

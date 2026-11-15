package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.content.ConnectedFrameBlock;
import com.enderlaserquarry.content.controller.QuarryControllerBlock;
import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Laser quarry floor plating. Connects to the rest of the floor, the light strip takes the crystal's colours.
 */
public class QuarryPlatingBlock extends ConnectedFrameBlock {

    public QuarryPlatingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CrystalGlow.PROPERTY, CrystalGlow.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CrystalGlow.PROPERTY);
    }

    @Override
    protected boolean connectsTo(BlockState neighbour) {
        return isQuarryFloor(neighbour);
    }

    public static boolean isQuarryFloor(BlockState state) {
        return state.is(CrystalObeliskBlockEntity.FRAME_BLOCKS) || state.getBlock() instanceof QuarryControllerBlock;
    }
}

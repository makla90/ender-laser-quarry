package com.enderlaserquarry.content.dish;

import com.enderlaserquarry.content.ConnectedFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plating around a satellite dish, connects to the rest of the 3x3.
 */
public class SatelliteDishPlatingBlock extends ConnectedFrameBlock {

    public SatelliteDishPlatingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean connectsTo(BlockState neighbour) {
        return isDishBase(neighbour);
    }

    public static boolean isDishBase(BlockState state) {
        return state.is(SatelliteDishBlockEntity.FRAME_BLOCKS) || state.getBlock() instanceof SatelliteDishBlock;
    }
}

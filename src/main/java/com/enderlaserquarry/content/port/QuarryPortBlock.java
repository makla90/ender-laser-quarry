package com.enderlaserquarry.content.port;

import com.enderlaserquarry.content.obelisk.CrystalGlow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

// quarry port, also takes the crystal glow like the plating
public class QuarryPortBlock extends TieredPortBlock {

    public QuarryPortBlock(Properties properties) {
        super(Structure.LASER_QUARRY, properties);
        registerDefaultState(defaultBlockState().setValue(CrystalGlow.PROPERTY, CrystalGlow.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CrystalGlow.PROPERTY);
    }
}

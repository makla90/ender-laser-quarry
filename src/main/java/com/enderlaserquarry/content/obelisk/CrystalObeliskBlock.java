package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Crystal obelisk block, the crystal floats above it.
 */
public class CrystalObeliskBlock extends BaseEntityBlock {
    public static final MapCodec<CrystalObeliskBlock> CODEC = simpleCodec(CrystalObeliskBlock::new);

    // like the 1.12 Ender IO obelisks
    private static final VoxelShape SHAPE = Block.box(1.5, 0, 1.5, 14.5, 7.6, 14.5);

    public CrystalObeliskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CrystalGlow.PROPERTY, CrystalGlow.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CrystalGlow.PROPERTY);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrystalObeliskBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.CRYSTAL_OBELISK.get(), CrystalObeliskBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CrystalObeliskBlockEntity obelisk)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(obelisk, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CrystalObeliskBlockEntity obelisk) {
            obelisk.dropContents();
            CrystalObeliskBlockEntity.applyFloorGlow(level, pos, CrystalGlow.NONE);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

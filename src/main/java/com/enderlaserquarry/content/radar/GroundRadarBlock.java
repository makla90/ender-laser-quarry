package com.enderlaserquarry.content.radar;

import com.enderlaserquarry.content.ConnectedFrameBlock;
import com.enderlaserquarry.content.PlayerMessages;
import com.enderlaserquarry.content.launchpad.LaunchPadBlock;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import net.minecraft.world.level.block.PipeBlock;
import com.enderlaserquarry.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Ground radar, goes on the launch pad ring.
 */
public class GroundRadarBlock extends BaseEntityBlock {
    public static final MapCodec<GroundRadarBlock> CODEC = simpleCodec(GroundRadarBlock::new);

    public GroundRadarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ConnectedFrameBlock.NORTH, false).setValue(ConnectedFrameBlock.EAST, false)
            .setValue(ConnectedFrameBlock.SOUTH, false).setValue(ConnectedFrameBlock.WEST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // joins the launch pad plating around it like the Launch Pad Controller, so the hazard edge only shows on open sides
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ConnectedFrameBlock.NORTH, ConnectedFrameBlock.EAST, ConnectedFrameBlock.SOUTH, ConnectedFrameBlock.WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), joinsPad(neighbour));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), joinsPad(neighborState));
        }
        return state;
    }

    // launch pad plating, ports and the controller (not a second radar)
    public static boolean joinsPad(BlockState neighbour) {
        return neighbour.is(LaunchPadBlockEntity.FRAME_BLOCKS) || neighbour.getBlock() instanceof LaunchPadBlock;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GroundRadarBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.GROUND_RADAR.get(), GroundRadarBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GroundRadarBlockEntity radar) {
            var pad = radar.getSatellitePad();
            var station = pad != null ? pad.getBlockPos() : null;
            PlayerMessages.show(player, station != null
                ? Component.translatable("message.ender_laser_quarry.radar.linked", station.getX(), station.getY(), station.getZ(),
                    pad.getEnergy().getEnergyStored(), pad.getEnergy().getMaxEnergyStored())
                : Component.translatable("message.ender_laser_quarry.radar.unlinked"));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}

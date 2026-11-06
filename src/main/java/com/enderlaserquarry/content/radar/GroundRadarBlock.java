package com.enderlaserquarry.content.radar;

import com.enderlaserquarry.content.PlayerMessages;
import com.enderlaserquarry.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
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
 * Ground radar, goes on the quarry floor ring. Sides take the colour of the plating tier around it.
 */
public class GroundRadarBlock extends BaseEntityBlock {
    public static final MapCodec<GroundRadarBlock> CODEC = simpleCodec(GroundRadarBlock::new);

    public GroundRadarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RadarFloor.PROPERTY, RadarFloor.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RadarFloor.PROPERTY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(RadarFloor.PROPERTY, RadarFloor.around(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(RadarFloor.PROPERTY, RadarFloor.around(level, pos));
        }
        return state;
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
            var energy = radar.getEnergy();
            var station = radar.getStationPos();
            PlayerMessages.show(player, station != null
                ? Component.translatable("message.ender_laser_quarry.radar.linked", station.getX(), station.getY(), station.getZ(),
                    energy.getEnergyStored(), energy.getMaxEnergyStored())
                : Component.translatable("message.ender_laser_quarry.radar.unlinked", energy.getEnergyStored(),
                    energy.getMaxEnergyStored()));
        }
        return InteractionResult.SUCCESS;
    }
}

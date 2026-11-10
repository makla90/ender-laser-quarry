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
 * Ground radar, goes on the launch pad ring.
 */
public class GroundRadarBlock extends BaseEntityBlock {
    public static final MapCodec<GroundRadarBlock> CODEC = simpleCodec(GroundRadarBlock::new);

    public GroundRadarBlock(Properties properties) {
        super(properties);
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
            var pad = radar.getSatellitePad();
            var station = pad != null ? pad.getBlockPos() : null;
            PlayerMessages.show(player, station != null
                ? Component.translatable("message.ender_laser_quarry.radar.linked", station.getX(), station.getY(), station.getZ(),
                    pad.getEnergy().getEnergyStored(), pad.getEnergy().getMaxEnergyStored())
                : Component.translatable("message.ender_laser_quarry.radar.unlinked"));
        }
        return InteractionResult.SUCCESS;
    }
}

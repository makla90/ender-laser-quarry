package com.enderlaserquarry.content.port;

import com.enderio.enderio.foundation.block.EIOBlockEntity;
import com.enderlaserquarry.content.ConnectedFrameBlock;
import com.enderlaserquarry.content.dish.SatelliteDishPlatingBlock;
import com.enderlaserquarry.content.launchpad.LaunchPadBlock;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.obelisk.QuarryPlatingBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.function.Consumer;
import com.enderlaserquarry.content.TooltipBlockItem;
import com.enderlaserquarry.init.ModBlockEntities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * I/O port, goes in place of a plating block.
 */
public class StructurePortBlock extends ConnectedFrameBlock implements EntityBlock, TooltipBlockItem.HasTooltip {

    // what the port passes: items, fluids, energy
    public enum Structure {
        LAUNCH_PAD(true, true, true),
        LASER_QUARRY(true, true, true),
        SATELLITE_DISH(false, false, true);

        public final boolean items;
        public final boolean fluids;
        public final boolean energy;

        Structure(boolean items, boolean fluids, boolean energy) {
            this.items = items;
            this.fluids = fluids;
            this.energy = energy;
        }
    }

    private final Structure structure;

    public StructurePortBlock(Structure structure, Properties properties) {
        super(properties);
        this.structure = structure;
    }

    public Structure getStructure() {
        return structure;
    }

    @Override
    protected boolean connectsTo(BlockState neighbour) {
        return switch (structure) {
            case LAUNCH_PAD -> neighbour.is(LaunchPadBlockEntity.FRAME_BLOCKS) || neighbour.getBlock() instanceof LaunchPadBlock
                || neighbour.getBlock() instanceof com.enderlaserquarry.content.radar.GroundRadarBlock;
            case LASER_QUARRY -> QuarryPlatingBlock.isQuarryFloor(neighbour);
            case SATELLITE_DISH -> SatelliteDishPlatingBlock.isDishBase(neighbour);
        };
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StructurePortBlockEntity port) {
            port.forgetCore();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.port." + structure.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructurePortBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.STRUCTURE_PORT.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<StructurePortBlockEntity>) EIOBlockEntity::tick : null;
    }
}

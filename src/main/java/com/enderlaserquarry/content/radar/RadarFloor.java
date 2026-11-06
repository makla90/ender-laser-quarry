package com.enderlaserquarry.content.radar;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Plating tier next to a radar, for its side texture. NONE = not in a floor.
 */
public enum RadarFloor implements StringRepresentable {
    NONE("none"),
    BASIC("basic"),
    ENHANCED("enhanced"),
    ENDER("ender");

    public static final EnumProperty<RadarFloor> PROPERTY = EnumProperty.create("floor", RadarFloor.class);

    private static final Direction[] SIDES = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    private final String name;

    RadarFloor(String name) {
        this.name = name;
    }

    public static RadarFloor of(QuarryTier tier) {
        return values()[tier.ordinal() + 1];
    }

    public static RadarFloor around(LevelReader level, BlockPos pos) {
        for (Direction side : SIDES) {
            BlockState neighbour = level.getBlockState(pos.relative(side));
            QuarryTier tier = ModBlocks.quarryFloorLook(neighbour);
            if (tier != null) {
                return of(tier);
            }
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.content.QuarryTier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Colour of the quarry light strips, by the crystal on the obelisk. Blockstate on the plating, ports and obelisk.
 */
public enum CrystalGlow implements StringRepresentable {
    NONE("none"),
    LASER("laser"),
    VOID("void"),
    PULSATING("pulsating");

    public static final EnumProperty<CrystalGlow> PROPERTY = EnumProperty.create("crystal", CrystalGlow.class);

    private final String name;

    CrystalGlow(String name) {
        this.name = name;
    }

    public static CrystalGlow of(ItemStack crystal) {
        if (crystal.isEmpty()) {
            return NONE;
        }
        if (crystal.getItem() instanceof VoidLaserCrystalItem) {
            return VOID;
        }
        return crystal.getItem() instanceof LaserCrystalItem laser && laser.getTier() == QuarryTier.BASIC ? PULSATING : LASER;
    }

    public static void apply(Level level, BlockPos pos, CrystalGlow glow) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(PROPERTY) && state.getValue(PROPERTY) != glow) {
            level.setBlock(pos, state.setValue(PROPERTY, glow), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

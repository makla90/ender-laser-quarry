package com.enderlaserquarry.content.controller;

import com.enderlaserquarry.config.QuarryConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * How deep the laser goes. Enhanced and Ender only, Basic always goes to bedrock.
 */
public enum DepthMode {
    BEDROCK("bedrock"),
    // Enhanced: config Y (Overworld only, bedrock elsewhere). Ender: Y picked in the GUI, any dimension
    Y_LEVEL("y_level"),
    // only what's above the generated terrain, leaves liquids alone
    SURFACE("surface");

    private final String id;

    DepthMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public DepthMode next() {
        DepthMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean drains() {
        return this != SURFACE;
    }

    // floorY comes from the server, the common config isn't synced
    public Component displayName(int floorY) {
        return Component.translatable("gui.ender_laser_quarry.depth." + id, floorY);
    }

    public Component description(int floorY, boolean playerSetY) {
        String key = "gui.ender_laser_quarry.depth." + id + ".desc";
        return Component.translatable(this == Y_LEVEL && playerSetY ? key + ".ender" : key, floorY);
    }

    public ItemStack icon() {
        return new ItemStack(switch (this) {
            case BEDROCK -> Items.BEDROCK;
            case Y_LEVEL -> Items.DEEPSLATE;
            case SURFACE -> Items.GRASS_BLOCK;
        });
    }

    // playerY null = config Y. For SURFACE this is only the lower limit, MiningFloor does the rest
    public int fixedFloor(Level level, @Nullable Integer playerY) {
        if (this == Y_LEVEL && playerY != null) {
            return clampY(level, playerY);
        }
        if (this == Y_LEVEL && level.dimension() == Level.OVERWORLD) {
            return Math.max(level.getMinY(), QuarryConfig.DEPTH_FLOOR_Y.get());
        }
        return level.getMinY();
    }

    public static int clampY(Level level, int y) {
        return Mth.clamp(y, level.getMinY(), level.getMaxY());
    }

    public static DepthMode byOrdinal(int ordinal) {
        DepthMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BEDROCK;
    }
}

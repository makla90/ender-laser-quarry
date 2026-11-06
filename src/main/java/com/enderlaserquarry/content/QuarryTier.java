package com.enderlaserquarry.content;

import com.enderlaserquarry.config.QuarryConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Basic / Enhanced / Ender. Values come from the config (tiers.*), defaults are here.
 */
public enum QuarryTier implements StringRepresentable {
    BASIC("basic", ChatFormatting.GRAY, 9, 2, 8, 6, 1.0, 24, 4_000, 2_000, 128, 0),
    ENHANCED("enhanced", ChatFormatting.GOLD, 49, 4, 32, 10, 1.5, 32, 8_000, 4_000, 256, 10),
    ENDER("ender", ChatFormatting.GREEN, 289, 8, 400, 16, 2.0, 48, 16_000, 8_000, 512, 24);

    private final String id;
    private final ChatFormatting color;
    private final int defaultMaxChunks;
    private final int defaultMaxDishes;
    private final int defaultMaxBlocksPerTick;
    private final int defaultChunkRadius;
    private final double defaultDishSpeed;
    private final int defaultDishRange;
    private final int defaultCoolantTank;
    private final int defaultXpTank;
    private final int defaultLinkRange;
    private final int defaultTransferRange;

    QuarryTier(String id, ChatFormatting color, int maxChunks, int maxDishes, int maxBlocksPerTick, int chunkRadius,
               double dishSpeed, int dishRange, int coolantTank, int xpTank, int linkRange, int transferRange) {
        this.id = id;
        this.color = color;
        this.defaultMaxChunks = maxChunks;
        this.defaultMaxDishes = maxDishes;
        this.defaultMaxBlocksPerTick = maxBlocksPerTick;
        this.defaultChunkRadius = chunkRadius;
        this.defaultDishSpeed = dishSpeed;
        this.defaultDishRange = dishRange;
        this.defaultCoolantTank = coolantTank;
        this.defaultXpTank = xpTank;
        this.defaultLinkRange = linkRange;
        this.defaultTransferRange = transferRange;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("tier.ender_laser_quarry." + id).withStyle(color);
    }

    public boolean atLeast(QuarryTier other) {
        return ordinal() >= other.ordinal();
    }

    public int maxChunks() {
        return QuarryConfig.tier(this).maxChunks().get();
    }

    public int maxDishes() {
        return QuarryConfig.tier(this).maxDishes().get();
    }

    public int maxBlocksPerTick() {
        return QuarryConfig.tier(this).maxBlocksPerTick().get();
    }

    public int chunkRadius() {
        return QuarryConfig.tier(this).chunkRadius().get();
    }

    // relative to Basic, also scales max RF/t and buffer
    public double dishSpeed() {
        return QuarryConfig.tier(this).dishSpeed().get();
    }

    public int dishRange() {
        return QuarryConfig.tier(this).dishRange().get();
    }

    // controller tanks, mB
    public int coolantTank() {
        return QuarryConfig.tier(this).coolantTank().get();
    }

    public int xpTank() {
        return QuarryConfig.tier(this).xpTank().get();
    }

    // max horizontal distance between a controller of this tier and its launch pad
    public int linkRange() {
        return QuarryConfig.tier(this).linkRange().get();
    }

    // how many chunks a satellite of this tier may be moved away from its launch pad (orbit transfer)
    public int transferRange() {
        return QuarryConfig.tier(this).transferRange().get();
    }

    public int defaultTransferRange() {
        return defaultTransferRange;
    }

    public int defaultLinkRange() {
        return defaultLinkRange;
    }

    public int defaultCoolantTank() {
        return defaultCoolantTank;
    }

    public int defaultXpTank() {
        return defaultXpTank;
    }

    public int defaultMaxChunks() {
        return defaultMaxChunks;
    }

    public int defaultMaxDishes() {
        return defaultMaxDishes;
    }

    public int defaultMaxBlocksPerTick() {
        return defaultMaxBlocksPerTick;
    }

    public int defaultChunkRadius() {
        return defaultChunkRadius;
    }

    public double defaultDishSpeed() {
        return defaultDishSpeed;
    }

    public int defaultDishRange() {
        return defaultDishRange;
    }

    public static QuarryTier byOrdinal(int ordinal) {
        QuarryTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BASIC;
    }

    public static QuarryTier highest() {
        return ENDER;
    }
}

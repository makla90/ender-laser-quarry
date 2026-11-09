package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.controller.QuarryControllerBlock;
import com.enderlaserquarry.content.dish.SatelliteDishBlock;
import com.enderlaserquarry.content.dish.SatelliteDishPlatingBlock;
import com.enderlaserquarry.content.launchpad.LaunchPadBlock;
import com.enderlaserquarry.content.launchpad.LaunchPadPlatingBlock;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlock;
import com.enderlaserquarry.content.obelisk.QuarryPlatingBlock;
import com.enderlaserquarry.content.port.QuarryPortBlock;
import com.enderlaserquarry.content.port.StructurePortBlock;
import com.enderlaserquarry.content.port.TieredPortBlock;
import com.enderlaserquarry.content.radar.GroundRadarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EnderLaserQuarry.MOD_ID);

    public static DeferredBlock<QuarryPlatingBlock> quarryPlating(QuarryTier tier) {
        return switch (tier) {
            case BASIC -> QUARRY_PLATING;
            case ENHANCED -> ENHANCED_QUARRY_PLATING;
            case ENDER -> ENDER_QUARRY_PLATING;
        };
    }

    // plating tier, null for anything else (ports fit any tier)
    @Nullable
    public static QuarryTier quarryFloorTier(BlockState state) {
        for (QuarryTier tier : QuarryTier.values()) {
            if (state.is(quarryPlating(tier).get())) {
                return tier;
            }
        }
        return null;
    }

    // tier a floor block looks like: plating, or the port's borrowed look
    @Nullable
    public static QuarryTier quarryFloorLook(BlockState state) {
        if (state.is(QUARRY_PORT.get())) {
            return state.getValue(TieredPortBlock.TIER);
        }
        return quarryFloorTier(state);
    }

    @Nullable
    public static QuarryTier dishFrameTier(BlockState state) {
        for (QuarryTier tier : QuarryTier.values()) {
            if (state.is(dishPlating(tier).get())) {
                return tier;
            }
        }
        return null;
    }

    // Basic keeps the old id
    public static final DeferredBlock<SatelliteDishBlock> SATELLITE_DISH = BLOCKS.registerBlock(
        "satellite_dish", props -> new SatelliteDishBlock(QuarryTier.BASIC, props), machineProps());
    public static final DeferredBlock<SatelliteDishBlock> ENHANCED_SATELLITE_DISH = BLOCKS.registerBlock(
        "enhanced_satellite_dish", props -> new SatelliteDishBlock(QuarryTier.ENHANCED, props), machineProps());
    public static final DeferredBlock<SatelliteDishBlock> ENDER_SATELLITE_DISH = BLOCKS.registerBlock(
        "ender_satellite_dish", props -> new SatelliteDishBlock(QuarryTier.ENDER, props), machineProps());

    public static DeferredBlock<SatelliteDishBlock> dish(QuarryTier tier) {
        return switch (tier) {
            case BASIC -> SATELLITE_DISH;
            case ENHANCED -> ENHANCED_SATELLITE_DISH;
            case ENDER -> ENDER_SATELLITE_DISH;
        };
    }

    public static DeferredBlock<SatelliteDishPlatingBlock> dishPlating(QuarryTier tier) {
        return switch (tier) {
            case BASIC -> SATELLITE_DISH_PLATING;
            case ENHANCED -> ENHANCED_SATELLITE_DISH_PLATING;
            case ENDER -> ENDER_SATELLITE_DISH_PLATING;
        };
    }

    // Basic keeps the old id
    public static final DeferredBlock<QuarryControllerBlock> QUARRY_CONTROLLER = BLOCKS.registerBlock(
        "quarry_controller", props -> new QuarryControllerBlock(QuarryTier.BASIC, props), machineProps());
    public static final DeferredBlock<QuarryControllerBlock> ENHANCED_QUARRY_CONTROLLER = BLOCKS.registerBlock(
        "enhanced_quarry_controller", props -> new QuarryControllerBlock(QuarryTier.ENHANCED, props), machineProps());
    public static final DeferredBlock<QuarryControllerBlock> ENDER_QUARRY_CONTROLLER = BLOCKS.registerBlock(
        "ender_quarry_controller", props -> new QuarryControllerBlock(QuarryTier.ENDER, props), machineProps());

    public static DeferredBlock<QuarryControllerBlock> controller(QuarryTier tier) {
        return switch (tier) {
            case BASIC -> QUARRY_CONTROLLER;
            case ENHANCED -> ENHANCED_QUARRY_CONTROLLER;
            case ENDER -> ENDER_QUARRY_CONTROLLER;
        };
    }

    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = BLOCKS.registerBlock(
        "launch_pad_controller", LaunchPadBlock::new, machineProps());

    public static final DeferredBlock<LaunchPadPlatingBlock> LAUNCH_PAD_PLATING = BLOCKS.registerBlock(
        "launch_pad_plating", LaunchPadPlatingBlock::new, machineProps().strength(3f, 12));

    public static final DeferredBlock<GroundRadarBlock> GROUND_RADAR = BLOCKS.registerBlock(
        "ground_radar", GroundRadarBlock::new, machineProps());

    public static final DeferredBlock<CrystalObeliskBlock> CRYSTAL_OBELISK = BLOCKS.registerBlock(
        "crystal_obelisk", CrystalObeliskBlock::new, machineProps().noOcclusion().lightLevel(state -> 5));

    public static final DeferredBlock<QuarryPlatingBlock> QUARRY_PLATING = BLOCKS.registerBlock(
        "laser_quarry_plating", QuarryPlatingBlock::new, machineProps().strength(3f, 12));

    public static final DeferredBlock<StructurePortBlock> LAUNCH_PAD_PORT = BLOCKS.registerBlock(
        "launch_pad_port", props -> new StructurePortBlock(StructurePortBlock.Structure.LAUNCH_PAD, props), machineProps().strength(3f, 12));

    public static final DeferredBlock<QuarryPortBlock> QUARRY_PORT = BLOCKS.registerBlock(
        "laser_quarry_port", QuarryPortBlock::new, machineProps().strength(3f, 12));

    public static final DeferredBlock<SatelliteDishPlatingBlock> SATELLITE_DISH_PLATING = BLOCKS.registerBlock(
        "satellite_dish_plating", SatelliteDishPlatingBlock::new, machineProps().strength(3f, 12));

    public static final DeferredBlock<TieredPortBlock> SATELLITE_DISH_PORT = BLOCKS.registerBlock(
        "satellite_dish_port", props -> new TieredPortBlock(StructurePortBlock.Structure.SATELLITE_DISH, props), machineProps().strength(3f, 12));

    public static final DeferredBlock<SatelliteDishPlatingBlock> ENHANCED_SATELLITE_DISH_PLATING = BLOCKS.registerBlock(
        "enhanced_satellite_dish_plating", SatelliteDishPlatingBlock::new, machineProps().strength(3f, 12));
    public static final DeferredBlock<SatelliteDishPlatingBlock> ENDER_SATELLITE_DISH_PLATING = BLOCKS.registerBlock(
        "ender_satellite_dish_plating", SatelliteDishPlatingBlock::new, machineProps().strength(3f, 12));

    public static final DeferredBlock<QuarryPlatingBlock> ENHANCED_QUARRY_PLATING = BLOCKS.registerBlock(
        "enhanced_laser_quarry_plating", QuarryPlatingBlock::new, machineProps().strength(3f, 12));
    public static final DeferredBlock<QuarryPlatingBlock> ENDER_QUARRY_PLATING = BLOCKS.registerBlock(
        "ender_laser_quarry_plating", QuarryPlatingBlock::new, machineProps().strength(3f, 12));

    private static BlockBehaviour.Properties machineProps() {
        // Same as Ender IO's machines
        return BlockBehaviour.Properties.of().strength(2.5f, 8);
    }

    private ModBlocks() {
    }
}

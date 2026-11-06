package com.enderlaserquarry.init;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import com.enderlaserquarry.content.port.StructurePortBlockEntity;
import com.enderlaserquarry.content.radar.GroundRadarBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.enderlaserquarry.content.trash.TrashCanBlock;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
        .create(Registries.BLOCK_ENTITY_TYPE, EnderLaserQuarry.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SatelliteDishBlockEntity>> SATELLITE_DISH = BLOCK_ENTITY_TYPES
        .register("satellite_dish", () -> new BlockEntityType<>(SatelliteDishBlockEntity::new, ModBlocks.SATELLITE_DISH.get(), ModBlocks.ENHANCED_SATELLITE_DISH.get(),
                ModBlocks.ENDER_SATELLITE_DISH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuarryControllerBlockEntity>> QUARRY_CONTROLLER = BLOCK_ENTITY_TYPES
        .register("quarry_controller", () -> new BlockEntityType<>(QuarryControllerBlockEntity::new, ModBlocks.QUARRY_CONTROLLER.get(), ModBlocks.ENHANCED_QUARRY_CONTROLLER.get(),
                ModBlocks.ENDER_QUARRY_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD = BLOCK_ENTITY_TYPES
        .register("launch_pad_controller", () -> new BlockEntityType<>(LaunchPadBlockEntity::new, ModBlocks.LAUNCH_PAD.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundRadarBlockEntity>> GROUND_RADAR = BLOCK_ENTITY_TYPES
        .register("ground_radar", () -> new BlockEntityType<>(GroundRadarBlockEntity::new, ModBlocks.GROUND_RADAR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalObeliskBlockEntity>> CRYSTAL_OBELISK = BLOCK_ENTITY_TYPES
        .register("crystal_obelisk", () -> new BlockEntityType<>(CrystalObeliskBlockEntity::new, ModBlocks.CRYSTAL_OBELISK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StructurePortBlockEntity>> STRUCTURE_PORT = BLOCK_ENTITY_TYPES
        .register("structure_port", () -> new BlockEntityType<>(StructurePortBlockEntity::new, ModBlocks.LAUNCH_PAD_PORT.get(), ModBlocks.QUARRY_PORT.get(),
                ModBlocks.SATELLITE_DISH_PORT.get()));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // multiblock cores have no capabilities, everything goes through the ports

        event.registerBlockEntity(Capabilities.Energy.BLOCK, GROUND_RADAR.get(), (radar, side) -> radar.getEnergy());

        // trash can has no BE
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, be, side) -> TrashCanBlock.ITEM_VOID, ModBlocks.TRASH_CAN.get());
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, be, side) -> TrashCanBlock.FLUID_VOID, ModBlocks.TRASH_CAN.get());

        // ports, per side IO mode
        event.registerBlockEntity(Capabilities.Item.BLOCK, STRUCTURE_PORT.get(), StructurePortBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, STRUCTURE_PORT.get(), StructurePortBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, STRUCTURE_PORT.get(), StructurePortBlockEntity::getEnergyStorage);
        event.registerBlockEntity(EnderIOCapabilities.SIDE_CONFIG, STRUCTURE_PORT.get(), MachineBlockEntity.SIDE_CONFIG_PROVIDER::getCapability);
    }

    private ModBlockEntities() {
    }
}

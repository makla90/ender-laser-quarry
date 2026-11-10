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
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("DataFlowIssue") // BlockEntityType.Builder#build(null) is the standard pattern
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
        .create(Registries.BLOCK_ENTITY_TYPE, EnderLaserQuarry.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SatelliteDishBlockEntity>> SATELLITE_DISH = BLOCK_ENTITY_TYPES
        .register("satellite_dish", () -> BlockEntityType.Builder
            .of(SatelliteDishBlockEntity::new, ModBlocks.SATELLITE_DISH.get(), ModBlocks.ENHANCED_SATELLITE_DISH.get(),
                ModBlocks.ENDER_SATELLITE_DISH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuarryControllerBlockEntity>> QUARRY_CONTROLLER = BLOCK_ENTITY_TYPES
        .register("quarry_controller", () -> BlockEntityType.Builder
            .of(QuarryControllerBlockEntity::new, ModBlocks.QUARRY_CONTROLLER.get(), ModBlocks.ENHANCED_QUARRY_CONTROLLER.get(),
                ModBlocks.ENDER_QUARRY_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD = BLOCK_ENTITY_TYPES
        .register("launch_pad_controller", () -> BlockEntityType.Builder
            .of(LaunchPadBlockEntity::new, ModBlocks.LAUNCH_PAD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundRadarBlockEntity>> GROUND_RADAR = BLOCK_ENTITY_TYPES
        .register("ground_radar", () -> BlockEntityType.Builder
            .of(GroundRadarBlockEntity::new, ModBlocks.GROUND_RADAR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalObeliskBlockEntity>> CRYSTAL_OBELISK = BLOCK_ENTITY_TYPES
        .register("crystal_obelisk", () -> BlockEntityType.Builder
            .of(CrystalObeliskBlockEntity::new, ModBlocks.CRYSTAL_OBELISK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StructurePortBlockEntity>> STRUCTURE_PORT = BLOCK_ENTITY_TYPES
        .register("structure_port", () -> BlockEntityType.Builder
            .of(StructurePortBlockEntity::new, ModBlocks.LAUNCH_PAD_PORT.get(), ModBlocks.QUARRY_PORT.get(),
                ModBlocks.SATELLITE_DISH_PORT.get()).build(null));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // multiblock cores have no capabilities, everything goes through the ports

        // ports, per side IO mode
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STRUCTURE_PORT.get(), StructurePortBlockEntity::getItemHandler);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STRUCTURE_PORT.get(), StructurePortBlockEntity::getFluidHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STRUCTURE_PORT.get(), StructurePortBlockEntity::getEnergyStorage);
        event.registerBlockEntity(EnderIOCapabilities.SIDE_CONFIG, STRUCTURE_PORT.get(), MachineBlockEntity.SIDE_CONFIG_PROVIDER::getCapability);
    }

    private ModBlockEntities() {
    }
}

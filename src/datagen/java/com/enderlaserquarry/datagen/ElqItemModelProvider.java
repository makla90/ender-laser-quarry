package com.enderlaserquarry.datagen;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

public class ElqItemModelProvider extends ItemModelProvider {
    // drawn by the item's BlockEntityWithoutLevelRenderer
    private static final ModelFile BUILTIN_ENTITY = new ModelFile.UncheckedModelFile("minecraft:builtin/entity");

    public ElqItemModelProvider(PackOutput output, ExistingFileHelper files) {
        super(output, EnderLaserQuarry.MOD_ID, files);
    }

    @Override
    protected void registerModels() {
        for (DeferredItem<?> item : List.of(ModItems.LASER_CRYSTAL, ModItems.PULSATING_LASER_CRYSTAL, ModItems.VOID_LASER_CRYSTAL,
            ModItems.SATELLITE, ModItems.ENHANCED_SATELLITE, ModItems.ENDER_SATELLITE, ModItems.VOID_SATELLITE, ModItems.ROCKET, ModItems.ROCKET_ENGINE,
            ModItems.PARABOLIC_DISH, ModItems.FOCUSING_LENS, ModItems.LASER_EMITTER, ModItems.SIGNAL_TRANSCEIVER, ModItems.REFLECTOR_MIRROR,
            ModItems.CHORUS_GYROSCOPE, ModItems.VOID_REFLECTOR)) {
            basicItem(item.get());
        }
        handheldItem(ModItems.LASER_LINKER.get());

        // block items that show their block model
        for (DeferredItem<?> item : List.of(ModItems.QUARRY_PLATING, ModItems.ENHANCED_QUARRY_PLATING, ModItems.ENDER_QUARRY_PLATING,
            ModItems.QUARRY_PORT, ModItems.SATELLITE_DISH_PLATING, ModItems.ENHANCED_SATELLITE_DISH_PLATING,
            ModItems.ENDER_SATELLITE_DISH_PLATING, ModItems.SATELLITE_DISH_PORT, ModItems.LAUNCH_PAD_PLATING, ModItems.LAUNCH_PAD_PORT,
            ModItems.CRYSTAL_OBELISK)) {
            String name = item.getId().getPath();
            getBuilder(name).parent(new ModelFile.UncheckedModelFile(modLoc("block/" + name)));
        }

        // controller with its antenna on top
        for (DeferredItem<?> item : List.of(ModItems.QUARRY_CONTROLLER, ModItems.ENHANCED_QUARRY_CONTROLLER, ModItems.ENDER_QUARRY_CONTROLLER)) {
            String name = item.getId().getPath();
            withExistingParent(name, modLoc("block/quarry_controller_template"))
                .texture("front", modLoc("block/" + name + "_front"))
                .texture("side", modLoc("block/" + name + "_side"))
                .texture("top", modLoc("block/" + name + "_top"))
                .texture("bottom", modLoc("block/" + name + "_bottom"))
                .texture("antenna", modLoc("block/" + name + "_antenna"))
                .transforms().transform(ItemDisplayContext.GUI).rotation(30, 225, 0).translation(0, -1.25f, 0).scale(0.55f).end().end();
        }

        machineItem(ModItems.SATELLITE_DISH, "satellite_dish_plating_side", 0.2f, 0.875f);
        machineItem(ModItems.ENHANCED_SATELLITE_DISH, "enhanced_satellite_dish_plating_side", 0.2f, 0.875f);
        machineItem(ModItems.ENDER_SATELLITE_DISH, "ender_satellite_dish_plating_side", 0.2f, 0.875f);
        machineItem(ModItems.GROUND_RADAR, "ground_radar_side", 0.3f, 0.86f);
        // the Launch Pad Controller with its mast, like the quarry controllers
        withExistingParent("launch_pad_controller", modLoc("block/quarry_controller_template"))
            .texture("front", modLoc("block/launch_pad_front"))
            .texture("side", modLoc("block/launch_pad_side"))
            .texture("top", modLoc("block/launch_pad_controller_top"))
            .texture("bottom", modLoc("block/quarry_machine_bottom"))
            .texture("antenna", modLoc("block/launch_pad_controller_antenna"))
            .transforms().transform(ItemDisplayContext.GUI).rotation(30, 225, 0).translation(0, -1.25f, 0).scale(0.55f).end().end();
    }

    // drawn in 3D by its item renderer; the model only carries the transforms and particle
    private void machineItem(DeferredItem<?> item, String particle, float guiY, float guiScale) {
        getBuilder(item.getId().getPath()).parent(BUILTIN_ENTITY)
            .guiLight(BlockModel.GuiLight.SIDE)
            .texture("particle", modLoc("block/" + particle))
            .transforms()
            .transform(ItemDisplayContext.GUI).rotation(30, 225, 0).translation(-0.45f, guiY, 0).scale(guiScale).end()
            .transform(ItemDisplayContext.GROUND).rotation(0, 0, 0).translation(0, 3, 0).scale(0.5f).end()
            .transform(ItemDisplayContext.FIXED).rotation(0, 180, 0).translation(0, -1, 0).scale(0.9f).end()
            .transform(ItemDisplayContext.HEAD).rotation(0, 180, 0).translation(0, 0, 0).scale(1).end()
            .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75, 45, 0).translation(0, 2.5f, 0).scale(0.6f).end()
            .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(75, 45, 0).translation(0, 2.5f, 0).scale(0.6f).end()
            .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 135, 0).translation(0, 0, 0).scale(0.6f).end()
            .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 135, 0).translation(0, 0, 0).scale(0.6f).end()
            .end();
    }

}

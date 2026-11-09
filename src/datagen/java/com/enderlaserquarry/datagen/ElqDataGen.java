package com.enderlaserquarry.datagen;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

/** gradlew runData: everything in src/generated/resources. */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class ElqDataGen {
    @SubscribeEvent
    static void gather(GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var lookup = event.getLookupProvider();
        var files = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(), new ElqBlockStateProvider(output, files));
        generator.addProvider(event.includeClient(), new ElqItemModelProvider(output, files));
        generator.addProvider(event.includeClient(), new ElqLanguageProvider(output));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
            List.of(new LootTableProvider.SubProviderEntry(ElqLootProvider::new, LootContextParamSets.BLOCK)), lookup));
        generator.addProvider(event.includeServer(), new ElqRecipeProvider(output, lookup));
        generator.addProvider(event.includeServer(), new AdvancementProvider(output, lookup, files, List.of(new ElqAdvancements())));

        generator.addProvider(event.includeServer(), new ElqTagsProvider<>(output, Registries.BLOCK, lookup, files, tags -> {
            tags.tag(ours("laser_quarry_frame"), "ender_laser_quarry:laser_quarry_plating", "ender_laser_quarry:laser_quarry_port",
                "ender_laser_quarry:enhanced_laser_quarry_plating", "ender_laser_quarry:ender_laser_quarry_plating");
            tags.tag(ours("satellite_dish_frame"), "ender_laser_quarry:satellite_dish_plating", "ender_laser_quarry:satellite_dish_port",
                "ender_laser_quarry:enhanced_satellite_dish_plating", "ender_laser_quarry:ender_satellite_dish_plating");
            tags.tag(ours("launch_pad_frame"), "ender_laser_quarry:launch_pad_plating", "ender_laser_quarry:launch_pad_port");
            tags.tag(ResourceLocation.withDefaultNamespace("mineable/pickaxe"),
                "ender_laser_quarry:satellite_dish", "ender_laser_quarry:enhanced_satellite_dish", "ender_laser_quarry:ender_satellite_dish",
                "ender_laser_quarry:quarry_controller", "ender_laser_quarry:enhanced_quarry_controller", "ender_laser_quarry:ender_quarry_controller",
                "ender_laser_quarry:launch_pad_controller", "ender_laser_quarry:launch_pad_plating", "ender_laser_quarry:ground_radar",
                "ender_laser_quarry:crystal_obelisk", "#ender_laser_quarry:laser_quarry_frame", "#ender_laser_quarry:satellite_dish_frame",
                "ender_laser_quarry:launch_pad_port");
        }));
        generator.addProvider(event.includeServer(), new ElqTagsProvider<>(output, Registries.FLUID, lookup, files, tags -> {
            tags.tag(ours("quarry_coolant"), "minecraft:water");
            tags.tag(ours("rocket_fuel"), "enderio:fluid_rocket_fuel_still", "enderio:fluid_rocket_fuel_flowing");
        }));
        generator.addProvider(event.includeServer(), new ElqTagsProvider<>(output, Registries.BIOME, lookup, files, tags -> {
            // an empty list would mean every biome but the void
            tags.tag(ours("asteroid_biomes"),
                "minecraft:plains", "minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga",
                "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:desert", "minecraft:savanna", "minecraft:jungle",
                "minecraft:badlands", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:river", "minecraft:beach",
                "minecraft:ocean", "minecraft:warm_ocean", "minecraft:cold_ocean", "minecraft:frozen_ocean",
                "minecraft:mushroom_fields", "minecraft:meadow", "minecraft:cherry_grove", "minecraft:windswept_hills",
                "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:dripstone_caves", "minecraft:lush_caves",
                "minecraft:deep_dark",
                "minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:soul_sand_valley",
                "minecraft:basalt_deltas",
                "minecraft:the_end", "minecraft:end_highlands");
            tags.tag(ours("frequent_asteroid_biomes"), "minecraft:desert", "minecraft:badlands");
        }));
    }

    private static ResourceLocation ours(String path) {
        return EnderLaserQuarry.rl(path);
    }

    private ElqDataGen() {
    }
}

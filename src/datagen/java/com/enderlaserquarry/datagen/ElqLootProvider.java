package com.enderlaserquarry.datagen;

import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Block drops. Machines keep their contents (capacitor, energy, fuel) through their block entity components. */
public class ElqLootProvider extends BlockLootSubProvider {
    public ElqLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        Set<Block> machines = new HashSet<>();
        for (var machine : List.of(ModBlocks.SATELLITE_DISH, ModBlocks.ENHANCED_SATELLITE_DISH, ModBlocks.ENDER_SATELLITE_DISH,
            ModBlocks.QUARRY_CONTROLLER, ModBlocks.ENHANCED_QUARRY_CONTROLLER, ModBlocks.ENDER_QUARRY_CONTROLLER, ModBlocks.LAUNCH_PAD)) {
            add(machine.get(), keepsContents(machine.get()));
            machines.add(machine.get());
        }
        for (Block block : getKnownBlocks()) {
            if (!machines.contains(block)) {
                dropSelf(block);
            }
        }
    }

    private static LootTable.Builder keepsContents(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1))
            .add(LootItem.lootTableItem(block)
                .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().<Block>map(DeferredHolder::get).toList();
    }
}

package com.enderlaserquarry.datagen;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.satellite.SpyglassSatelliteTrigger;
import com.enderlaserquarry.init.ModItems;
import com.enderlaserquarry.init.ModTriggers;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.Optional;
import java.util.function.Consumer;

public class ElqAdvancements implements AdvancementProvider.AdvancementGenerator {
    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output, ExistingFileHelper files) {
        String key = "advancements.ender_laser_quarry.spyglass_at_satellite.";
        Advancement.Builder.advancement()
            .parent(AdvancementSubProvider.createPlaceholder("minecraft:adventure/spyglass_at_parrot"))
            .display(ModItems.SATELLITE.get(), Component.translatable(key + "title"), Component.translatable(key + "description"),
                null, AdvancementType.TASK, true, true, false)
            .addCriterion("spyglass_at_satellite", new Criterion<>(ModTriggers.SPYGLASS_AT_SATELLITE.get(),
                new SpyglassSatelliteTrigger.Instance(Optional.empty())))
            .save(output, EnderLaserQuarry.rl("spyglass_at_satellite").toString());
    }
}

package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.satellite.SpyglassSatelliteTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, EnderLaserQuarry.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, SpyglassSatelliteTrigger> SPYGLASS_AT_SATELLITE =
        TRIGGERS.register("spyglass_at_satellite", SpyglassSatelliteTrigger::new);

    private ModTriggers() {
    }
}

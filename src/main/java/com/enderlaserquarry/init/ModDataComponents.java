package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EnderLaserQuarry.MOD_ID);

    // the controller's XP tank on the item (the coolant uses Ender IO's fluid component)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> XP_FLUID = COMPONENTS.registerComponentType(
        "xp_fluid", builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private ModDataComponents() {
    }
}

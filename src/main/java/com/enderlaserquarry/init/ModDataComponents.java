package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EnderLaserQuarry.MOD_ID);

    // the controller's XP tank on the item (the coolant uses Ender IO's fluid component)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> XP_FLUID = COMPONENTS.registerComponentType(
        "xp_fluid", builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    // Dew of the Void in a Void Satellite, mB (kept while it is up, recovered with it)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SATELLITE_DEW = COMPONENTS.registerComponentType(
        "satellite_dew", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    // the launch pad's Dew of the Void tank on the item
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> PAD_DEW = COMPONENTS.registerComponentType(
        "pad_dew", builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private ModDataComponents() {
    }
}

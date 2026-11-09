package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EnderLaserQuarry.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.ender_laser_quarry"))
        .icon(() -> ModItems.SATELLITE_DISH.get().getDefaultInstance())
        .displayItems((params, output) -> {
            output.accept(ModItems.SATELLITE_DISH.get());
            output.accept(ModItems.ENHANCED_SATELLITE_DISH.get());
            output.accept(ModItems.ENDER_SATELLITE_DISH.get());
            output.accept(ModItems.SATELLITE_DISH_PLATING.get());
            output.accept(ModItems.SATELLITE_DISH_PORT.get());
            output.accept(ModItems.ENHANCED_SATELLITE_DISH_PLATING.get());
            output.accept(ModItems.ENDER_SATELLITE_DISH_PLATING.get());
            output.accept(ModItems.QUARRY_CONTROLLER.get());
            output.accept(ModItems.ENHANCED_QUARRY_CONTROLLER.get());
            output.accept(ModItems.ENDER_QUARRY_CONTROLLER.get());
            output.accept(ModItems.LASER_LINKER.get());
            output.accept(ModItems.LAUNCH_PAD.get());
            output.accept(ModItems.LAUNCH_PAD_PLATING.get());
            output.accept(ModItems.LAUNCH_PAD_PORT.get());
            output.accept(ModItems.GROUND_RADAR.get());
            output.accept(ModItems.PARABOLIC_DISH.get());
            output.accept(ModItems.ROCKET_ENGINE.get());
            output.accept(ModItems.FOCUSING_LENS.get());
            output.accept(ModItems.LASER_EMITTER.get());
            output.accept(ModItems.SIGNAL_TRANSCEIVER.get());
            output.accept(ModItems.REFLECTOR_MIRROR.get());
            output.accept(ModItems.ROCKET.get());
            output.accept(ModItems.SATELLITE.get());
            output.accept(ModItems.ENHANCED_SATELLITE.get());
            output.accept(ModItems.ENDER_SATELLITE.get());
            output.accept(ModItems.CRYSTAL_OBELISK.get());
            output.accept(ModItems.QUARRY_PLATING.get());
            output.accept(ModItems.QUARRY_PORT.get());
            output.accept(ModItems.ENHANCED_QUARRY_PLATING.get());
            output.accept(ModItems.ENDER_QUARRY_PLATING.get());
            output.accept(ModItems.PULSATING_LASER_CRYSTAL.get());
            output.accept(ModItems.LASER_CRYSTAL.get());
            output.accept(ModItems.VOID_LASER_CRYSTAL.get());
        })
        .build());

    private ModCreativeTabs() {
    }
}

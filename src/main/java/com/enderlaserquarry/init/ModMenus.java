package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.controller.QuarryControllerMenu;
import com.enderlaserquarry.content.dish.SatelliteDishMenu;
import com.enderlaserquarry.content.launchpad.LaunchPadMenu;
import com.enderlaserquarry.content.obelisk.CrystalObeliskMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, EnderLaserQuarry.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SatelliteDishMenu>> SATELLITE_DISH = MENUS
        .register("satellite_dish", () -> IMenuTypeExtension.create(SatelliteDishMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<QuarryControllerMenu>> QUARRY_CONTROLLER = MENUS
        .register("quarry_controller", () -> IMenuTypeExtension.create(QuarryControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LaunchPadMenu>> LAUNCH_PAD = MENUS
        .register("launch_pad", () -> IMenuTypeExtension.create(LaunchPadMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CrystalObeliskMenu>> CRYSTAL_OBELISK = MENUS
        .register("crystal_obelisk", () -> IMenuTypeExtension.create(CrystalObeliskMenu::new));

    private ModMenus() {
    }
}

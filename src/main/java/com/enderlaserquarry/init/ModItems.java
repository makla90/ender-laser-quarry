package com.enderlaserquarry.init;

import com.enderlaserquarry.content.TooltipBlockItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.linker.LaserLinkerItem;
import com.enderlaserquarry.content.obelisk.LaserCrystalItem;
import com.enderlaserquarry.content.obelisk.VoidLaserCrystalItem;
import com.enderlaserquarry.content.satellite.RocketItem;
import com.enderlaserquarry.content.satellite.SatelliteItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EnderLaserQuarry.MOD_ID);

    public static final DeferredItem<BlockItem> SATELLITE_DISH = blockItem(ModBlocks.SATELLITE_DISH);
    public static final DeferredItem<BlockItem> ENHANCED_SATELLITE_DISH = blockItem(ModBlocks.ENHANCED_SATELLITE_DISH);
    public static final DeferredItem<BlockItem> ENDER_SATELLITE_DISH = blockItem(ModBlocks.ENDER_SATELLITE_DISH);
    public static final DeferredItem<BlockItem> QUARRY_CONTROLLER = blockItem(ModBlocks.QUARRY_CONTROLLER);
    public static final DeferredItem<BlockItem> ENHANCED_QUARRY_CONTROLLER = blockItem(ModBlocks.ENHANCED_QUARRY_CONTROLLER);
    public static final DeferredItem<BlockItem> ENDER_QUARRY_CONTROLLER = blockItem(ModBlocks.ENDER_QUARRY_CONTROLLER);

    public static final DeferredItem<LaserLinkerItem> LASER_LINKER = ITEMS.registerItem("laser_linker",
        LaserLinkerItem::new, () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> LAUNCH_PAD = blockItem(ModBlocks.LAUNCH_PAD);
    public static final DeferredItem<BlockItem> LAUNCH_PAD_PLATING = blockItem(ModBlocks.LAUNCH_PAD_PLATING);
    public static final DeferredItem<BlockItem> GROUND_RADAR = blockItem(ModBlocks.GROUND_RADAR);

    // crafting parts
    public static final DeferredItem<Item> PARABOLIC_DISH = ITEMS.registerSimpleItem("parabolic_dish");
    public static final DeferredItem<Item> ROCKET_ENGINE = ITEMS.registerSimpleItem("rocket_engine");
    public static final DeferredItem<Item> FOCUSING_LENS = ITEMS.registerSimpleItem("focusing_lens");
    public static final DeferredItem<Item> LASER_EMITTER = ITEMS.registerSimpleItem("laser_emitter");
    public static final DeferredItem<Item> SIGNAL_TRANSCEIVER = ITEMS.registerSimpleItem("signal_transceiver");
    public static final DeferredItem<Item> REFLECTOR_MIRROR = ITEMS.registerSimpleItem("reflector_mirror");

    public static final DeferredItem<RocketItem> ROCKET = ITEMS.registerItem("rocket",
        RocketItem::new, () -> new Item.Properties().stacksTo(1));

    // Basic keeps the old id
    public static final DeferredItem<SatelliteItem> SATELLITE = ITEMS.registerItem("satellite",
        props -> new SatelliteItem(QuarryTier.BASIC, props), () -> new Item.Properties().stacksTo(1));
    public static final DeferredItem<SatelliteItem> ENHANCED_SATELLITE = ITEMS.registerItem("enhanced_satellite",
        props -> new SatelliteItem(QuarryTier.ENHANCED, props), () -> new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<SatelliteItem> ENDER_SATELLITE = ITEMS.registerItem("ender_satellite",
        props -> new SatelliteItem(QuarryTier.ENDER, props), () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static DeferredItem<SatelliteItem> satellite(QuarryTier tier) {
        return switch (tier) {
            case BASIC -> SATELLITE;
            case ENHANCED -> ENHANCED_SATELLITE;
            case ENDER -> ENDER_SATELLITE;
        };
    }

    public static final DeferredItem<BlockItem> CRYSTAL_OBELISK = blockItem(ModBlocks.CRYSTAL_OBELISK);
    public static final DeferredItem<BlockItem> QUARRY_PLATING = blockItem(ModBlocks.QUARRY_PLATING);
    public static final DeferredItem<BlockItem> LAUNCH_PAD_PORT = blockItem(ModBlocks.LAUNCH_PAD_PORT);
    public static final DeferredItem<BlockItem> QUARRY_PORT = blockItem(ModBlocks.QUARRY_PORT);
    public static final DeferredItem<BlockItem> SATELLITE_DISH_PLATING = blockItem(ModBlocks.SATELLITE_DISH_PLATING);
    public static final DeferredItem<BlockItem> SATELLITE_DISH_PORT = blockItem(ModBlocks.SATELLITE_DISH_PORT);
    public static final DeferredItem<BlockItem> ENHANCED_SATELLITE_DISH_PLATING = blockItem(ModBlocks.ENHANCED_SATELLITE_DISH_PLATING);
    public static final DeferredItem<BlockItem> ENDER_SATELLITE_DISH_PLATING = blockItem(ModBlocks.ENDER_SATELLITE_DISH_PLATING);
    public static final DeferredItem<BlockItem> ENHANCED_QUARRY_PLATING = blockItem(ModBlocks.ENHANCED_QUARRY_PLATING);
    public static final DeferredItem<BlockItem> ENDER_QUARRY_PLATING = blockItem(ModBlocks.ENDER_QUARRY_PLATING);

    public static final DeferredItem<LaserCrystalItem> PULSATING_LASER_CRYSTAL = ITEMS.registerItem("pulsating_laser_crystal",
        props -> new LaserCrystalItem(QuarryTier.BASIC, props), () -> new Item.Properties().durability(LaserCrystalItem.PULSATING_DURABILITY).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<LaserCrystalItem> LASER_CRYSTAL = ITEMS.registerItem("laser_crystal",
        props -> new LaserCrystalItem(QuarryTier.ENHANCED, props), () -> new Item.Properties().durability(LaserCrystalItem.LASER_DURABILITY).rarity(Rarity.EPIC));
    public static final DeferredItem<VoidLaserCrystalItem> VOID_LASER_CRYSTAL = ITEMS.registerItem("void_laser_crystal",
        VoidLaserCrystalItem::new, () -> new Item.Properties().durability(LaserCrystalItem.VOID_DURABILITY).rarity(Rarity.EPIC));

    private static DeferredItem<BlockItem> blockItem(DeferredBlock<?> block) {
        return ITEMS.<BlockItem>registerItem(block.getId().getPath(), props -> new TooltipBlockItem(block.get(), props), props -> props.useBlockDescriptionPrefix());
    }

    private ModItems() {
    }
}

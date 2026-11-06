package com.enderlaserquarry;

import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModBlocks;
import com.enderlaserquarry.init.ModCreativeTabs;
import com.enderlaserquarry.init.ModDataComponents;
import com.enderlaserquarry.init.ModTickets;
import com.enderlaserquarry.init.ModTriggers;
import com.enderlaserquarry.init.ModItems;
import com.enderlaserquarry.init.ModMenus;
import com.enderlaserquarry.init.ModRecipes;
import com.enderlaserquarry.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.slf4j.Logger;

@Mod(EnderLaserQuarry.MOD_ID)
public class EnderLaserQuarry {
    public static final String MOD_ID = "ender_laser_quarry";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Chunk loading for running quarries and for pads whose satellite moves or maps a far area. On world load only those
    // keep their tickets.
    public static final TicketController CHUNK_TICKETS = new TicketController(rl("quarry"),
        (level, helper) -> helper.getBlockTickets().forEach((owner, tickets) -> {
            if (level.getBlockEntity(owner) instanceof QuarryControllerBlockEntity controller) {
                if (controller.keepsChunksAfterLoad()) {
                    return;
                }
                controller.forgetTickets();
            }
            if (level.getBlockEntity(owner) instanceof com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity pad && pad.wantsSelfLoad()) {
                return;
            }
            helper.removeAllTickets(owner);
        }));

    public EnderLaserQuarry(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);
        ModTickets.TICKETS.register(modEventBus);

        modEventBus.addListener(ModBlockEntities::registerCapabilities);
        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(RegisterTicketControllersEvent.class, e -> e.register(CHUNK_TICKETS));


        modContainer.registerConfig(ModConfig.Type.COMMON, QuarryConfig.SPEC);
    }

    public static Identifier rl(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

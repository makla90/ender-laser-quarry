package com.enderlaserquarry.client;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.client.render.CrystalModel;
import com.enderlaserquarry.client.render.QuarryControllerRenderer;
import com.enderlaserquarry.client.render.CrystalObeliskRenderer;
import com.enderlaserquarry.client.render.GroundRadarModel;
import com.enderlaserquarry.client.render.GroundRadarRenderer;
import com.enderlaserquarry.client.render.LaunchPadRenderer;
import com.enderlaserquarry.client.render.RocketModel;
import com.enderlaserquarry.client.render.SatelliteDishModel;
import com.enderlaserquarry.client.render.SatelliteDishRenderer;
import com.enderlaserquarry.client.render.GroundRadarItemRenderer;
import com.enderlaserquarry.client.render.SatelliteDishItemRenderer;
import com.enderlaserquarry.client.render.SatelliteModel;
import com.enderlaserquarry.client.screen.CrystalObeliskScreen;
import com.enderlaserquarry.client.screen.LaunchPadScreen;
import com.enderlaserquarry.client.screen.QuarryControllerScreen;
import com.enderlaserquarry.client.screen.SatelliteDishScreen;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModBlocks;
import com.enderlaserquarry.init.ModItems;
import com.enderlaserquarry.init.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

    // 3D items
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private SatelliteDishItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new SatelliteDishItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, ModItems.SATELLITE_DISH.get(), ModItems.ENHANCED_SATELLITE_DISH.get(), ModItems.ENDER_SATELLITE_DISH.get());

        registerRadarItem(event, ModBlocks.GROUND_RADAR, 1, 0.48f, ModItems.GROUND_RADAR.get());
        registerRadarItem(event, ModBlocks.LAUNCH_PAD, LaunchPadBlockEntity.MINI_RADAR_SCALE, 0.66f, ModItems.LAUNCH_PAD.get());
    }

    private static void registerRadarItem(RegisterClientExtensionsEvent event, Supplier<? extends Block> block, float antennaScale,
                                          float fit, Item item) {
        event.registerItem(new IClientItemExtensions() {
            private GroundRadarItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new GroundRadarItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels(),
                        block, antennaScale, fit);
                }
                return renderer;
            }
        }, item);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        QuarryControllerRenderer.MODELS.forEach(event::register);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SATELLITE_DISH.get(), SatelliteDishScreen::new);
        event.register(ModMenus.QUARRY_CONTROLLER.get(), QuarryControllerScreen::new);
        event.register(ModMenus.LAUNCH_PAD.get(), LaunchPadScreen::new);
        event.register(ModMenus.CRYSTAL_OBELISK.get(), CrystalObeliskScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SATELLITE_DISH.get(), SatelliteDishRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAUNCH_PAD.get(), LaunchPadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GROUND_RADAR.get(), GroundRadarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYSTAL_OBELISK.get(), CrystalObeliskRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.QUARRY_CONTROLLER.get(), QuarryControllerRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SatelliteDishModel.LAYER, SatelliteDishModel::createLayer);
        event.registerLayerDefinition(RocketModel.LAYER, RocketModel::createLayer);
        event.registerLayerDefinition(SatelliteModel.LAYER, SatelliteModel::createLayer);
        event.registerLayerDefinition(GroundRadarModel.LAYER, GroundRadarModel::createLayer);
        event.registerLayerDefinition(CrystalModel.LAYER, CrystalModel::createLayer);
    }

    private ClientSetup() {
    }
}

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
import com.enderlaserquarry.content.Tooltips;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.init.ModBlockEntities;
import com.enderlaserquarry.init.ModBlocks;
import com.enderlaserquarry.init.ModItems;
import com.enderlaserquarry.init.ModMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // PORT: Screen.hasShiftDown() on 1.21.1
        Tooltips.shiftDown = () -> Minecraft.getInstance().hasShiftDown();
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterStandalone event) {
        QuarryControllerRenderer.MODELS.forEach(key -> event.register(key,
            SimpleUnbakedStandaloneModel.simpleModelWrapper(QuarryControllerRenderer.modelId(key))));
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

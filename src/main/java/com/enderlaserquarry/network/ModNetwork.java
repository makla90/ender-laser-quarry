package com.enderlaserquarry.network;

import com.enderlaserquarry.content.PlayerMessages;
import com.enderlaserquarry.content.asteroid.Asteroid;
import com.enderlaserquarry.content.asteroid.AsteroidComposition;
import com.enderlaserquarry.content.asteroid.sampling.BiomeSampling;
import com.enderlaserquarry.client.ClientPayloadHandler;
import com.enderlaserquarry.content.controller.DepthMode;
import com.enderlaserquarry.content.controller.MinimapSnapshot;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.controller.QuarryControllerMenu;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.launchpad.ScanImageStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ModNetwork {
    private static final String VERSION = "6";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(QuarryActionPayload.TYPE, QuarryActionPayload.STREAM_CODEC, ModNetwork::handleQuarryAction);

        // lambda so the client handler doesn't load on servers
        registrar.playToClient(MinimapPayload.TYPE, MinimapPayload.STREAM_CODEC,
            (payload, context) -> ClientPayloadHandler.handleMinimap(payload));
        registrar.playToClient(SpaceMapPayload.TYPE, SpaceMapPayload.STREAM_CODEC,
            (payload, context) -> ClientPayloadHandler.handleSpaceMap(payload));
        registrar.playToClient(RemoteSatellitePayload.TYPE, RemoteSatellitePayload.STREAM_CODEC,
            (payload, context) -> ClientPayloadHandler.handleRemoteSatellite(payload));
    }

    private static void handleQuarryAction(QuarryActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
            || !(player.containerMenu instanceof QuarryControllerMenu menu)
            || !menu.getBlockEntity().getBlockPos().equals(payload.controllerPos())
            || !menu.stillValid(player)) {
            return;
        }

        var controller = menu.getBlockEntity();
        switch (payload.action()) {
            case ADD_CHUNK, REMOVE_CHUNK -> {
                var error = controller.trySetQueued(new ChunkPos(payload.chunk()), payload.action() == QuarryActionPayload.Action.ADD_CHUNK);
                if (error != null) {
                    PlayerMessages.show(player, error);
                }
            }
            case ADD_BOX, REMOVE_BOX -> {
                var error = controller.trySetBox(new ChunkPos(payload.chunk()), new ChunkPos(payload.to()),
                    payload.action() == QuarryActionPayload.Action.ADD_BOX);
                if (error != null) {
                    PlayerMessages.show(player, error);
                }
            }
            case START -> controller.start();
            case STOP -> controller.stop();
            case CLEAR_SELECTION -> controller.clearSelection();
            case SET_DEPTH_MODE -> {
                var error = controller.trySetDepthMode(DepthMode.byOrdinal((int) payload.chunk()));
                if (error != null) {
                    PlayerMessages.show(player, error);
                }
            }
            case REQUEST_MINIMAP -> {
                PacketDistributor.sendToPlayer(player, buildMinimap(controller, (int) payload.chunk(), payload.to() == 1));
                if (controller.isVoidMode()) {
                    PacketDistributor.sendToPlayer(player, buildSpaceMap(controller));
                }
            }
            case ADD_ASTEROID, REMOVE_ASTEROID -> PlayerMessages.showIfAny(player, controller.trySetAsteroidQueued((int) payload.chunk(),
                payload.action() == QuarryActionPayload.Action.ADD_ASTEROID));
            case SCAN_SPACE -> PlayerMessages.showIfAny(player, controller.tryScanSpace());
            case SET_FLOOR_Y -> PlayerMessages.showIfAny(player, controller.trySetFloorY((int) payload.chunk()));
            case ANALYZE_ASTEROID -> PlayerMessages.showIfAny(player, controller.tryAnalyze((int) payload.chunk()));
            case TRANSFER_SATELLITE -> {
                ChunkPos offset = new ChunkPos(payload.chunk());
                PlayerMessages.showIfAny(player, controller.tryTransferSatellite(offset.x, offset.z));
            }
        }
    }

    // Pictures come from the satellite, only the chunk being mined is read live
    public static MinimapPayload buildMinimap(QuarryControllerBlockEntity controller, int since) {
        return buildMinimap(controller, since, false);
    }

    // planner = the orbit planner's area: everything the satellite can reach around the pad, with every picture it ever took
    public static MinimapPayload buildMinimap(QuarryControllerBlockEntity controller, int since, boolean planner) {
        LaunchPadBlockEntity pad = controller.getLinkedPad();
        if (pad == null) {
            int radius = controller.getTier().chunkRadius();
            var snapshot = MinimapSnapshot.capture(null, new ChunkPos(controller.getBlockPos()), radius);
            return new MinimapPayload(controller.getBlockPos(), Optional.empty(), snapshot, 0, false, List.of(), false);
        }

        boolean plan = planner && pad.getTransferRange() > 0;
        var snapshot = plan
            ? MinimapSnapshot.capturePlanner(pad, new ChunkPos(pad.getPadCenter()), Math.min(64, pad.getTransferRange() + controller.getChunkRadius(pad)))
            : MinimapSnapshot.capture(pad, pad.getOrbitChunk(), controller.getChunkRadius(pad));
        ChunkPos working = controller.isRunning() ? controller.getWorkChunk() : null;
        if (working != null) {
            pad.updateScanImage(working);
        }

        var store = pad.getScanImages();
        List<ScanImageStore.ScanImage> changed = store.changedSince(since,
            chunk -> snapshot.contains(new ChunkPos(chunk)) && (plan || pad.isChunkScanned(new ChunkPos(chunk))), MinimapPayload.MAX_IMAGES);
        boolean more = changed.size() == MinimapPayload.MAX_IMAGES;
        int revision = more ? changed.get(changed.size() - 1).revision() : store.revision();
        List<MinimapPayload.ChunkImage> images = changed.stream()
            .map(image -> new MinimapPayload.ChunkImage(image.chunk(), image.pixels()))
            .toList();
        return new MinimapPayload(controller.getBlockPos(), Optional.of(pad.getPadCenter()), snapshot, revision, more, images, plan);
    }

    public static SpaceMapPayload buildSpaceMap(QuarryControllerBlockEntity controller) {
        LaunchPadBlockEntity pad = controller.getLinkedPad();
        if (pad == null || !(controller.getLevel() instanceof ServerLevel level)) {
            return new SpaceMapPayload(controller.getBlockPos(), List.of());
        }
        List<SpaceMapPayload.AsteroidInfo> asteroids = new ArrayList<>();
        for (Asteroid asteroid : pad.getAsteroids().asteroids()) {
            if (asteroids.size() >= SpaceMapPayload.MAX_ASTEROIDS) {
                break;
            }
            // nothing about the rock until it is analyzed
            boolean known = BiomeSampling.knowsRock(level.getServer(), asteroid);
            AsteroidComposition rock = known ? AsteroidComposition.of(level.getServer(), asteroid.biome()) : null;
            known &= rock != null;
            List<SpaceMapPayload.Part> parts = rock == null ? List.of() : rock.parts().stream()
                .limit(SpaceMapPayload.MAX_PARTS)
                .map(part -> new SpaceMapPayload.Part(Block.getId(part.state()), (float) part.share(), part.ore(), part.host()))
                .toList();
            BiomeSampling.Status samples = BiomeSampling.status(level.getServer(), asteroid.biome());
            asteroids.add(new SpaceMapPayload.AsteroidInfo(asteroid.id(), asteroid.x(), asteroid.y(), asteroid.size(), asteroid.biome(),
                asteroid.total(), asteroid.remaining(), asteroid.seed(), known, asteroid.analyzed() && !known, samples.done(), samples.full(), parts));
        }
        return new SpaceMapPayload(controller.getBlockPos(), asteroids);
    }

    private ModNetwork() {
    }
}

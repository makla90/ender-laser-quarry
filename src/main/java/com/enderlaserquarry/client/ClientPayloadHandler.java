package com.enderlaserquarry.client;

import com.enderlaserquarry.client.screen.QuarryControllerScreen;
import com.enderlaserquarry.network.MinimapPayload;
import com.enderlaserquarry.network.SpaceMapPayload;
import net.minecraft.client.Minecraft;

public final class ClientPayloadHandler {

    public static void handleMinimap(MinimapPayload payload) {
        if (Minecraft.getInstance().screen instanceof QuarryControllerScreen screen && screen.isFor(payload.controllerPos())) {
            screen.setMinimap(payload);
        }
    }

    public static void handleSpaceMap(SpaceMapPayload payload) {
        if (Minecraft.getInstance().screen instanceof QuarryControllerScreen screen && screen.isFor(payload.controllerPos())) {
            screen.setSpaceMap(payload);
        }
    }

    public static void handleRemoteSatellite(com.enderlaserquarry.network.RemoteSatellitePayload payload) {
        RemoteSatellites.handle(payload);
    }

    private ClientPayloadHandler() {
    }
}

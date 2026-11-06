package com.enderlaserquarry.content.launchpad;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.network.RemoteSatellitePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Last known data of every pad with a satellite up, so players near a satellite see it even when its pad's chunk isn't
 * loaded (an idle satellite's position only depends on the game time, so the last data stays right).
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class SatelliteRegistry {
    // players this close (horizontally) to the satellite get its pad's data
    public static final double RANGE = 1024;

    private record Entry(RemoteSatellitePayload payload, Vec3 satellite) {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> SATELLITES = new HashMap<>();

    static void update(ServerLevel level, LaunchPadBlockEntity pad, Vec3 satellite) {
        var payload = new RemoteSatellitePayload(pad.getBlockPos(), Block.getId(pad.getBlockState()), pad.getUpdateTag(level.registryAccess()));
        SATELLITES.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(pad.getBlockPos(), new Entry(payload, satellite));
    }

    static void remove(Level level, BlockPos pad) {
        var pads = SATELLITES.get(level.dimension());
        if (pads != null) {
            pads.remove(pad);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 5 || SATELLITES.isEmpty()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var pads = SATELLITES.get(level.dimension());
            if (pads == null || pads.isEmpty() || level.players().isEmpty()) {
                continue;
            }
            for (Entry entry : pads.values()) {
                for (ServerPlayer player : level.players()) {
                    double dx = player.getX() - entry.satellite().x;
                    double dz = player.getZ() - entry.satellite().z;
                    if (dx * dx + dz * dz <= RANGE * RANGE) {
                        PacketDistributor.sendToPlayer(player, entry.payload());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SATELLITES.clear();
    }

    private SatelliteRegistry() {
    }
}

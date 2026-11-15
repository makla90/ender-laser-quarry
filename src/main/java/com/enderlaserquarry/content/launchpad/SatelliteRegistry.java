package com.enderlaserquarry.content.launchpad;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.network.RemoteSatellitePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Last known data of every pad with a satellite up, so players near a satellite see it even when its pad's chunk isn't
 * loaded (an idle satellite's position only depends on the game time, so the last data stays right).
 * Full data only goes out when it changed for that player, otherwise a small keep-alive.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class SatelliteRegistry {
    // players this close (horizontally) to the satellite get its pad's data
    public static final double RANGE = 1024;
    // full resend now and then anyway, in case a client lost its copy
    private static final int FULL_RESEND_TICKS = 200;

    private record Entry(RemoteSatellitePayload payload, Vec3 satellite) {
    }

    // what one player got last, per pad in its current dimension
    private record Sent(ResourceKey<Level> dimension, Map<BlockPos, CompoundTag> data) {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> SATELLITES = new HashMap<>();
    private static final Map<UUID, Sent> SENT = new HashMap<>();

    static void update(ServerLevel level, LaunchPadBlockEntity pad, Vec3 satellite) {
        var payload = new RemoteSatellitePayload(pad.getBlockPos(), Block.getId(pad.getBlockState()), pad.getUpdateTag(level.registryAccess()));
        SATELLITES.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(pad.getBlockPos(), new Entry(payload, satellite));
    }

    static void remove(Level level, BlockPos pad) {
        var pads = SATELLITES.get(level.dimension());
        if (pads != null) {
            pads.remove(pad);
        }
        for (Sent sent : SENT.values()) {
            if (sent.dimension() == level.dimension()) {
                sent.data().remove(pad);
            }
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % 10 != 5 || SATELLITES.isEmpty()) {
            return;
        }
        boolean fullResend = tick % FULL_RESEND_TICKS == 5;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var pads = SATELLITES.get(level.dimension());
            if (pads == null || pads.isEmpty() || level.players().isEmpty()) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                Sent sent = SENT.get(player.getUUID());
                if (sent == null || sent.dimension() != level.dimension()) {
                    sent = new Sent(level.dimension(), new HashMap<>());
                    SENT.put(player.getUUID(), sent);
                }
                for (var pad : pads.entrySet()) {
                    Entry entry = pad.getValue();
                    double dx = player.getX() - entry.satellite().x;
                    double dz = player.getZ() - entry.satellite().z;
                    if (dx * dx + dz * dz > RANGE * RANGE) {
                        // out of range: the client drops it, so it gets everything again on the way back
                        sent.data().remove(pad.getKey());
                        continue;
                    }
                    CompoundTag data = entry.payload().data();
                    if (fullResend || !data.equals(sent.data().get(pad.getKey()))) {
                        PacketDistributor.sendToPlayer(player, entry.payload());
                        sent.data().put(pad.getKey(), data);
                    } else {
                        PacketDistributor.sendToPlayer(player, entry.payload().keepAlive());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    // a respawn or dimension change gives the client a fresh level without our copies
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SATELLITES.clear();
        SENT.clear();
    }

    private SatelliteRegistry() {
    }
}

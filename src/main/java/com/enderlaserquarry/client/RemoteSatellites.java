package com.enderlaserquarry.client;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.client.render.LaunchPadRenderer;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.network.RemoteSatellitePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import com.enderlaserquarry.client.render.DeferredBuffers;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.HashMap;
import java.util.Map;

/**
 * Satellites whose launch pad isn't loaded here (it flew over a far area): a client-only copy of the pad from the
 * server's RemoteSatellitePayload, drawn with the normal pad renderer, satellite and beams only.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID, value = Dist.CLIENT)
public final class RemoteSatellites {
    // dropped when no update came for this long
    private static final int EXPIRE_TICKS = 60;

    private record Ghost(LaunchPadBlockEntity pad, long lastSeen) {
    }

    private static final Map<BlockPos, Ghost> GHOSTS = new HashMap<>();
    private static ClientLevel ghostLevel;

    public static void handle(RemoteSatellitePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        checkLevel(level);
        BlockPos pos = payload.padPos();
        Ghost old = GHOSTS.get(pos);
        LaunchPadBlockEntity pad = old != null ? old.pad() : new LaunchPadBlockEntity(pos, Block.stateById(payload.blockState()));
        pad.setLevel(level);
        pad.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), payload.data()));
        GHOSTS.put(pos, new Ghost(pad, level.getGameTime()));
    }

    private static void checkLevel(ClientLevel level) {
        if (level != ghostLevel) {
            GHOSTS.clear();
            ghostLevel = level;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            GHOSTS.clear();
            ghostLevel = null;
            return;
        }
        if (GHOSTS.isEmpty()) {
            return;
        }
        checkLevel(level);
        long now = level.getGameTime();
        GHOSTS.entrySet().removeIf(entry -> now - entry.getValue().lastSeen() > EXPIRE_TICKS);
        // a loaded real pad ticks its own effects
        GHOSTS.values().stream().filter(ghost -> real(level, ghost.pad().getBlockPos()) == null).forEach(ghost -> ghost.pad().remoteEffects());
    }

    // the real pad, if this client has it
    private static LaunchPadBlockEntity real(ClientLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad ? pad : null;
    }

    // Close to the pad its own renderer draws the flight. Further out it gets culled (its view distance, the round render
    // distance, sections not built yet at the edge) while the chunk may still be loaded, so we take over well before that.
    private static boolean nearPad(Minecraft minecraft, ClientLevel level, BlockPos pos) {
        if (real(level, pos) == null) {
            return false;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double reach = Math.min(LaunchPadRenderer.VIEW_DISTANCE - 16, (minecraft.options.getEffectiveRenderDistance() - 2) * 16);
        double dx = camera.x - (pos.getX() + 0.5);
        double dz = camera.z - (pos.getZ() + 0.5);
        return dx * dx + dz * dz < reach * reach;
    }

    // asked by the pad's renderer, so exactly one of the two draws it
    public static boolean drawsFlight(LaunchPadBlockEntity pad) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        return level != null && level == ghostLevel && GHOSTS.containsKey(pad.getBlockPos()) && !nearPad(minecraft, level, pad.getBlockPos());
    }

    @SubscribeEvent
    static void onRenderLevel(SubmitCustomGeometryEvent event) {
        if (GHOSTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        DeferredBuffers buffers = new DeferredBuffers();
        ClientLevel level = minecraft.level;
        for (Ghost ghost : GHOSTS.values()) {
            BlockPos pos = ghost.pad().getBlockPos();
            if (level == null || nearPad(minecraft, level, pos)) {
                continue;
            }
            // the real one is newer when it's loaded
            LaunchPadBlockEntity pad = real(level, pos);
            if (pad == null) {
                pad = ghost.pad();
            }
            Object found = minecraft.getBlockEntityRenderDispatcher().getRenderer(pad);
            if (!(found instanceof LaunchPadRenderer renderer)) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            renderer.renderRemote(pad, partialTick, poseStack, buffers);
            poseStack.popPose();
        }
        buffers.submit(event.getSubmitNodeCollector());
    }

    // dev client test
    public static int debugCount() {
        return GHOSTS.size();
    }

    private RemoteSatellites() {
    }
}

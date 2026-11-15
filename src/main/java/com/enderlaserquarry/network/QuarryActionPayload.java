package com.enderlaserquarry.network;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.controller.DepthMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.level.ChunkPos;

import java.util.function.IntFunction;

/**
 * Controller GUI to server. "chunk" is a packed ChunkPos, or the revision / depth mode / Y / asteroid id depending on
 * the action; "to" is the other box corner for box actions.
 */
public record QuarryActionPayload(BlockPos controllerPos, Action action, long chunk, long to) implements CustomPacketPayload {
    public static final Type<QuarryActionPayload> TYPE = new Type<>(EnderLaserQuarry.rl("quarry_action"));

    public static final StreamCodec<ByteBuf, QuarryActionPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, QuarryActionPayload::controllerPos,
        Action.STREAM_CODEC, QuarryActionPayload::action,
        ByteBufCodecs.VAR_LONG, QuarryActionPayload::chunk,
        ByteBufCodecs.VAR_LONG, QuarryActionPayload::to,
        QuarryActionPayload::new);

    public static QuarryActionPayload of(BlockPos controllerPos, Action action) {
        return new QuarryActionPayload(controllerPos, action, 0, 0);
    }

    public static QuarryActionPayload setQueued(BlockPos controllerPos, ChunkPos chunk, boolean queued) {
        return new QuarryActionPayload(controllerPos, queued ? Action.ADD_CHUNK : Action.REMOVE_CHUNK, chunk.toLong(), 0);
    }

    public static QuarryActionPayload setBoxQueued(BlockPos controllerPos, ChunkPos from, ChunkPos to, boolean queued) {
        return new QuarryActionPayload(controllerPos, queued ? Action.ADD_BOX : Action.REMOVE_BOX, from.toLong(), to.toLong());
    }

    public static QuarryActionPayload requestMinimap(BlockPos controllerPos, int since) {
        return new QuarryActionPayload(controllerPos, Action.REQUEST_MINIMAP, since, 0);
    }

    // the orbit planner's bigger area
    public static QuarryActionPayload requestPlanner(BlockPos controllerPos, int since) {
        return new QuarryActionPayload(controllerPos, Action.REQUEST_MINIMAP, since, 1);
    }

    public static QuarryActionPayload setAsteroidQueued(BlockPos controllerPos, int asteroid, boolean queued) {
        return new QuarryActionPayload(controllerPos, queued ? Action.ADD_ASTEROID : Action.REMOVE_ASTEROID, asteroid, 0);
    }

    public static QuarryActionPayload analyzeAsteroid(BlockPos controllerPos, int asteroid) {
        return new QuarryActionPayload(controllerPos, Action.ANALYZE_ASTEROID, asteroid, 0);
    }

    // dx / dz in chunks from the pad's chunk, packed like a ChunkPos
    public static QuarryActionPayload transferSatellite(BlockPos controllerPos, int dx, int dz) {
        return new QuarryActionPayload(controllerPos, Action.TRANSFER_SATELLITE, ChunkPos.asLong(dx, dz), 0);
    }

    public static QuarryActionPayload setDepthMode(BlockPos controllerPos, DepthMode mode) {
        return new QuarryActionPayload(controllerPos, Action.SET_DEPTH_MODE, mode.ordinal(), 0);
    }

    public static QuarryActionPayload setFloorY(BlockPos controllerPos, int y) {
        return new QuarryActionPayload(controllerPos, Action.SET_FLOOR_Y, y, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        ADD_CHUNK, REMOVE_CHUNK, START, STOP, REQUEST_MINIMAP, CLEAR_SELECTION, SET_DEPTH_MODE, ADD_ASTEROID, REMOVE_ASTEROID, SCAN_SPACE, SET_FLOOR_Y, ANALYZE_ASTEROID, ADD_BOX, REMOVE_BOX, TRANSFER_SATELLITE;

        private static final IntFunction<Action> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
    }
}

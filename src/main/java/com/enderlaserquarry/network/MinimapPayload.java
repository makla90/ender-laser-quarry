package com.enderlaserquarry.network;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.controller.MinimapSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Optional;

/**
 * Server to controller GUI: minimap area plus the chunk pictures that changed. more = didn't all fit, ask again. planner =
 * the orbit planner's area (everything reachable around the pad).
 */
public record MinimapPayload(BlockPos controllerPos, Optional<BlockPos> padPos, MinimapSnapshot snapshot, int revision,
                             boolean more, List<ChunkImage> images, boolean planner) implements CustomPacketPayload {
    public static final Type<MinimapPayload> TYPE = new Type<>(EnderLaserQuarry.rl("minimap"));

    // 256 bytes each, stays well below the packet limit
    public static final int MAX_IMAGES = 1024;

    public record ChunkImage(long chunk, byte[] pixels) {
        public static final StreamCodec<ByteBuf, ChunkImage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ChunkImage::chunk,
            ByteBufCodecs.byteArray(256), ChunkImage::pixels,
            ChunkImage::new);
    }

    private static final StreamCodec<ByteBuf, ChunkPos> CHUNK_POS_CODEC = ByteBufCodecs.VAR_LONG.map(ChunkPos::unpack, ChunkPos::pack);

    private static final StreamCodec<ByteBuf, MinimapSnapshot> SNAPSHOT_CODEC = StreamCodec.composite(
        CHUNK_POS_CODEC, MinimapSnapshot::center,
        ByteBufCodecs.VAR_INT, MinimapSnapshot::radius,
        ByteBufCodecs.byteArray(1 << 16), MinimapSnapshot::scan,
        MinimapSnapshot::new);

    // revision + flags in one go (composite takes 6 fields at most)
    private record Meta(int revision, boolean more, boolean planner) {
        static final StreamCodec<ByteBuf, Meta> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Meta::revision,
            ByteBufCodecs.BOOL, Meta::more,
            ByteBufCodecs.BOOL, Meta::planner,
            Meta::new);
    }

    public static final StreamCodec<ByteBuf, MinimapPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, MinimapPayload::controllerPos,
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC), MinimapPayload::padPos,
        SNAPSHOT_CODEC, MinimapPayload::snapshot,
        Meta.CODEC, payload -> new Meta(payload.revision(), payload.more(), payload.planner()),
        ChunkImage.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_IMAGES)), MinimapPayload::images,
        (controller, pad, snapshot, meta, images) -> new MinimapPayload(controller, pad, snapshot, meta.revision(), meta.more(), images,
            meta.planner()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

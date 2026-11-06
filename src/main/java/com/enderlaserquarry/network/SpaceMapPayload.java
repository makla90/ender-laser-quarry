package com.enderlaserquarry.network;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.asteroid.AsteroidSize;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Server to controller GUI: the asteroids and, once analyzed, what they're made of. samples / fullSamples = measured spots.
 */
public record SpaceMapPayload(BlockPos controllerPos, List<AsteroidInfo> asteroids) implements CustomPacketPayload {
    public static final Type<SpaceMapPayload> TYPE = new Type<>(EnderLaserQuarry.rl("space_map"));
    public static final int MAX_ASTEROIDS = 64;
    public static final int MAX_PARTS = 96;

    public record Part(int stateId, float share, boolean ore, boolean host) {
        public static final StreamCodec<ByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Part::stateId,
            ByteBufCodecs.FLOAT, Part::share,
            ByteBufCodecs.BOOL, Part::ore,
            ByteBufCodecs.BOOL, Part::host,
            Part::new);
    }

    public record AsteroidInfo(int id, int x, int y, AsteroidSize size, Identifier biome, long total, long remaining,
                               long seed, boolean analyzed, boolean stale, int samples, int fullSamples, List<Part> parts) {
        public static final StreamCodec<ByteBuf, AsteroidInfo> STREAM_CODEC = StreamCodec.of((buf, info) -> {
            ByteBufCodecs.VAR_INT.encode(buf, info.id());
            ByteBufCodecs.VAR_INT.encode(buf, info.x());
            ByteBufCodecs.VAR_INT.encode(buf, info.y());
            ByteBufCodecs.VAR_INT.encode(buf, info.size().ordinal());
            Identifier.STREAM_CODEC.encode(buf, info.biome());
            ByteBufCodecs.VAR_LONG.encode(buf, info.total());
            ByteBufCodecs.VAR_LONG.encode(buf, info.remaining());
            buf.writeLong(info.seed());
            buf.writeBoolean(info.analyzed());
            buf.writeBoolean(info.stale());
            ByteBufCodecs.VAR_INT.encode(buf, info.samples());
            ByteBufCodecs.VAR_INT.encode(buf, info.fullSamples());
            Part.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PARTS)).encode(buf, info.parts());
        }, buf -> new AsteroidInfo(
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            AsteroidSize.byId(ByteBufCodecs.VAR_INT.decode(buf)),
            Identifier.STREAM_CODEC.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf),
            buf.readLong(),
            buf.readBoolean(),
            buf.readBoolean(),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            Part.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PARTS)).decode(buf)));
    }

    public static final StreamCodec<ByteBuf, SpaceMapPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SpaceMapPayload::controllerPos,
        AsteroidInfo.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ASTEROIDS)), SpaceMapPayload::asteroids,
        SpaceMapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

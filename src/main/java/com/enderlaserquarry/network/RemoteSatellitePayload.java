package com.enderlaserquarry.network;

import com.enderlaserquarry.EnderLaserQuarry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to clients near a satellite whose launch pad they may not have loaded: the pad's synced data, so the client can
 * still draw the satellite and its beams (client.RemoteSatellites).
 */
public record RemoteSatellitePayload(BlockPos padPos, int blockState, CompoundTag data) implements CustomPacketPayload {
    public static final Type<RemoteSatellitePayload> TYPE = new Type<>(EnderLaserQuarry.rl("remote_satellite"));

    public static final StreamCodec<ByteBuf, RemoteSatellitePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RemoteSatellitePayload::padPos,
        ByteBufCodecs.VAR_INT, RemoteSatellitePayload::blockState,
        ByteBufCodecs.COMPOUND_TAG, RemoteSatellitePayload::data,
        RemoteSatellitePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

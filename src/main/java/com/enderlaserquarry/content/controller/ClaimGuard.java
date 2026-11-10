package com.enderlaserquarry.content.controller;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Asks claim mods before the laser touches a block: fires the normal break / place events as the controller's owner.
 */
public final class ClaimGuard {
    // controllers placed without a player
    private static final GameProfile NO_OWNER = new GameProfile(UUID.fromString("5c1a6f0e-8d0b-4e5e-9a43-e1a5e0c0a7a1"), "[Ender Laser Quarry]");

    public static FakePlayer player(ServerLevel level, @Nullable UUID owner) {
        return FakePlayerFactory.get(level, profile(level, owner));
    }

    private static GameProfile profile(ServerLevel level, @Nullable UUID owner) {
        if (owner == null) {
            return NO_OWNER;
        }
        var cache = level.getServer().getProfileCache();
        String name = cache == null ? null : cache.get(owner).map(GameProfile::getName).orElse(null);
        return new GameProfile(owner, name != null ? name : "[Ender Laser Quarry]");
    }

    public static boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state, FakePlayer player) {
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled();
    }

    // checks before placing, so nothing has to be rolled back
    public static boolean mayPlace(ServerLevel level, BlockPos pos, FakePlayer player) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        BlockState against = level.getBlockState(pos.below());
        return !NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, against, player)).isCanceled();
    }

    // Queue check on the chunk's middle column, without loading it. The laser checks again while it mines.
    public static boolean mayQueue(ServerLevel level, ChunkPos chunk, FakePlayer player) {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        int y = loaded != null ? loaded.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 1 : level.getSeaLevel();
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = loaded != null ? loaded.getBlockState(pos) : Blocks.STONE.defaultBlockState();
        if (state.isAir()) {
            state = Blocks.STONE.defaultBlockState();
        }
        return mayBreak(level, pos, state, player);
    }

    private ClaimGuard() {
    }
}

package com.enderlaserquarry.content.controller;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Claim answers per 16x16x16 section, kept a few seconds, so claim mods see one event per section instead of one per block.
 */
final class ClaimCache {
    static final int RECHECK_TICKS = 100;
    private static final int MAX_SECTIONS = 512;

    // section -> (tick of the check << 1) | allowed
    private final Long2LongOpenHashMap answers = new Long2LongOpenHashMap();

    ClaimCache() {
        answers.defaultReturnValue(-1);
    }

    // 1 allowed, 0 refused, -1 ask again
    int lookup(BlockPos pos, long now) {
        long entry = answers.get(key(pos));
        if (entry < 0 || now - (entry >> 1) >= RECHECK_TICKS) {
            return -1;
        }
        return (int) (entry & 1);
    }

    void put(BlockPos pos, long now, boolean allowed) {
        if (answers.size() >= MAX_SECTIONS) {
            answers.clear();
        }
        answers.put(key(pos), now << 1 | (allowed ? 1 : 0));
    }

    void clear() {
        answers.clear();
    }

    private static long key(BlockPos pos) {
        return SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()),
            SectionPos.blockToSectionCoord(pos.getZ()));
    }
}

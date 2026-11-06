package com.enderlaserquarry.content.asteroid.sampling;

import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Finds far-apart spots of biomes by asking the biome source directly, on a background thread (only noise math, no
 * chunks). Spots are picked at least spacing apart; when a biome is too rare for that, the spacing halves.
 */
public final class BiomeSpots {
    /** Smallest spacing: 3 chunks, so counted chunks never repeat. */
    private static final int MIN_SPACING = 48;
    // at the smallest spacing, a biome that gave no new spot for this long has no more room
    private static final int EXHAUSTED_AFTER = 30_000;
    private static final int MAX_HITS = 20_000;
    // a biome still short of spots after this many probes gets a smaller spacing (rare biomes, the End's main island)
    private static final int TIGHTEN_EVERY = 50_000;
    private static final int TIGHTEN_EVERY_END = 4_000;
    // ground of the main island
    private static final int MAIN_ISLAND = 250;

    public record Settings(int count, int spacing, int range, int maxProbes, long seed) {
    }

    public record Result(Map<Holder<Biome>, List<BlockPos>> spots, Map<Holder<Biome>, Integer> spacing, int probes) {
    }

    /** end = End terrain: islands in the void, probes without ground are skipped. */
    public static CompletableFuture<Result> find(ServerLevel level, boolean end, Set<Holder<Biome>> biomes, Settings settings) {
        return CompletableFuture.supplyAsync(() -> search(level, end, biomes, settings), Util.backgroundExecutor());
    }

    private static Result search(ServerLevel level, boolean end, Set<Holder<Biome>> biomes, Settings settings) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        var sampler = source.randomState().sampler();
        var biomeSource = generator.getBiomeSource();
        DensityFunction density = source.randomState().router().finalDensity();
        RandomSource random = RandomSource.create(settings.seed());
        Map<Holder<Biome>, List<BlockPos>> hits = new HashMap<>();
        Map<Holder<Biome>, List<BlockPos>> picked = new HashMap<>();
        Set<Holder<Biome>> open = new HashSet<>(biomes);
        Map<Holder<Biome>, Integer> spacingNow = new HashMap<>();
        biomes.forEach(b -> spacingNow.put(b, settings.spacing()));
        Map<Holder<Biome>, Integer> lastNew = new HashMap<>();
        int range = settings.range();
        int probes = 0;
        List<Integer> ys = new ArrayList<>();
        while (!open.isEmpty() && probes < settings.maxProbes()) {
            probes++;
            if (probes % (end ? TIGHTEN_EVERY_END : TIGHTEN_EVERY) == 0) {
                for (Holder<Biome> biome : List.copyOf(open)) {
                    int s = spacingNow.get(biome);
                    List<BlockPos> found = hits.getOrDefault(biome, List.of());
                    if (!found.isEmpty() && s > MIN_SPACING) {
                        int tighter = Math.max(MIN_SPACING, s / 2);
                        spacingNow.put(biome, tighter);
                        lastNew.put(biome, probes);
                        List<BlockPos> chosen = pick(found, tighter, settings.count());
                        picked.put(biome, chosen);
                        if (chosen.size() >= settings.count()) {
                            open.remove(biome);
                        }
                    }
                    if (spacingNow.get(biome) <= MIN_SPACING && probes - lastNew.getOrDefault(biome, 0) > EXHAUSTED_AFTER) {
                        open.remove(biome);
                    }
                }
            }
            // the End's main island is tiny: half the probes go there
            int reach = end && random.nextBoolean() ? MAIN_ISLAND : range;
            int x = random.nextInt(2 * reach) - reach;
            int z = random.nextInt(2 * reach) - reach;
            ys.clear();
            if (end) {
                // islands sit around y 40-70: one density lookup per height instead of a whole column
                if (!hasIslandGround(density, x, z)) {
                    continue;
                }
                ys.add(60);
            } else if (level.dimensionType().hasCeiling()) {
                // the Nether's biomes change with height too
                ys.add(32);
                ys.add(64);
                ys.add(96);
            } else {
                // surface biomes reach from above the ground down to the caves, so no height lookup (that's the slow part)
                ys.add(70);
                ys.add(130);
                // cave biomes live deeper
                ys.add(8);
                ys.add(-40);
            }
            for (int y : ys) {
                Holder<Biome> biome = biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
                if (!open.contains(biome)) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                List<BlockPos> list = hits.computeIfAbsent(biome, b -> new ArrayList<>());
                if (list.size() < MAX_HITS) {
                    list.add(pos);
                }
                List<BlockPos> chosen = picked.computeIfAbsent(biome, b -> new ArrayList<>());
                if (farFromAll(chosen, pos, spacingNow.get(biome))) {
                    chosen.add(pos);
                    lastNew.put(biome, probes);
                    if (chosen.size() >= settings.count()) {
                        open.remove(biome);
                    }
                }
            }
        }
        Map<Holder<Biome>, Integer> spacing = new HashMap<>();
        for (Holder<Biome> biome : biomes) {
            int s = spacingNow.get(biome);
            List<BlockPos> chosen = picked.getOrDefault(biome, new ArrayList<>());
            // too rare for the full spacing: pick again, closer together
            while (chosen.size() < settings.count() && s > MIN_SPACING) {
                s = Math.max(MIN_SPACING, s / 2);
                chosen = pick(hits.getOrDefault(biome, List.of()), s, settings.count());
            }
            picked.put(biome, chosen);
            spacing.put(biome, s);
        }
        return new Result(picked, spacing, probes);
    }

    private static boolean hasIslandGround(DensityFunction density, int x, int z) {
        for (int y = 40; y <= 72; y += 8) {
            if (density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> pick(List<BlockPos> hits, int spacing, int count) {
        List<BlockPos> chosen = new ArrayList<>();
        for (BlockPos pos : hits) {
            if (farFromAll(chosen, pos, spacing)) {
                chosen.add(pos);
                if (chosen.size() >= count) {
                    break;
                }
            }
        }
        return chosen;
    }

    private static boolean farFromAll(List<BlockPos> chosen, BlockPos pos, int spacing) {
        long min = (long) spacing * spacing;
        for (BlockPos other : chosen) {
            long dx = other.getX() - pos.getX();
            long dz = other.getZ() - pos.getZ();
            if (dx * dx + dz * dz < min) {
                return false;
            }
        }
        return true;
    }

    private BiomeSpots() {
    }
}

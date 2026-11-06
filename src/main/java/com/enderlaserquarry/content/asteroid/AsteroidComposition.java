package com.enderlaserquarry.content.asteroid;

import com.enderlaserquarry.content.asteroid.sampling.BiomeSampling;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an asteroid of a biome is made of: the biome's ground as measured by BiomeSampling.
 */
public final class AsteroidComposition {
    private static final Map<Identifier, AsteroidComposition> CACHE = new ConcurrentHashMap<>();
    private static final double ORE_VEIN = 6;
    private static final double ROCK_VEIN = 24;
    private static final double HOST_VEIN = 48;
    // shares above this come in long runs
    private static final double HOST_SHARE = 0.1;
    // everywhere underground, so they don't say anything about the biome (map tint, tooltip order)
    private static final Set<Block> COMMON_ROCK = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
        Blocks.TUFF, Blocks.GRAVEL, Blocks.DIRT, Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE);

    // host = the biome's own rock (sand, terracotta, grass...), used for the map tint
    public record Part(BlockState state, double share, boolean ore, double vein, boolean host) {
    }

    public record Vein(BlockState state, int length) {
    }

    private final List<Part> parts;
    // share / vein length, so the block shares still come out right
    private final double[] cumulative;

    private AsteroidComposition(List<Part> parts) {
        this.parts = List.copyOf(parts);
        cumulative = new double[parts.size()];
        double sum = 0;
        for (int i = 0; i < parts.size(); i++) {
            sum += parts.get(i).share() / parts.get(i).vein();
            cumulative[i] = sum;
        }
    }

    public List<Part> parts() {
        return parts;
    }

    // Blocks come in veins so the output doesn't get a dozen item types per tick
    public Vein nextVein(RandomSource random) {
        double roll = random.nextDouble() * cumulative[cumulative.length - 1];
        int index = Arrays.binarySearch(cumulative, roll);
        index = Math.min(index >= 0 ? index + 1 : -index - 1, parts.size() - 1);
        Part part = parts.get(index);
        int length = (int) part.vein();
        if (random.nextDouble() < part.vein() - length) {
            length++;
        }
        return new Vein(part.state(), Math.max(1, length));
    }

    /** Null until the biome is measured. */
    @Nullable
    public static AsteroidComposition of(MinecraftServer server, Identifier biome) {
        AsteroidComposition cached = CACHE.get(biome);
        if (cached != null) {
            return cached;
        }
        Object2LongMap<Identifier> counts = BiomeSampling.counts(server, biome);
        if (counts == null) {
            return null;
        }
        AsteroidComposition built = build(counts);
        if (built != null) {
            CACHE.put(biome, built);
        }
        return built;
    }

    public static void invalidate(Identifier biome) {
        CACHE.remove(biome);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @Nullable
    private static AsteroidComposition build(Object2LongMap<Identifier> counts) {
        long total = 0;
        for (var e : counts.object2LongEntrySet()) {
            if (BuiltInRegistries.BLOCK.containsKey(e.getKey())) {
                total += e.getLongValue();
            }
        }
        if (total <= 0) {
            return null;
        }
        List<Part> parts = new ArrayList<>();
        for (var e : counts.object2LongEntrySet()) {
            if (e.getLongValue() <= 0 || !BuiltInRegistries.BLOCK.containsKey(e.getKey())) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(e.getKey());
            if (block == Blocks.AIR) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            double share = (double) e.getLongValue() / total;
            boolean ore = state.is(Tags.Blocks.ORES);
            double vein = ore ? ORE_VEIN : share >= HOST_SHARE ? HOST_VEIN : ROCK_VEIN;
            parts.add(new Part(state, share, ore, vein, !ore && !COMMON_ROCK.contains(block)));
        }
        if (parts.isEmpty()) {
            return null;
        }
        parts.sort(Comparator.comparingDouble(Part::share).reversed());
        return new AsteroidComposition(parts);
    }
}

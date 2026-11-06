package com.enderlaserquarry.content.asteroid.sampling;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.asteroid.AsteroidComposition;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Measures what a biome's ground is made of, for the asteroids: far-apart spots in the matching sampling dimension,
 * one counted chunk each. A quick pass first (what Analyze waits for), then more spots in the background.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class BiomeSampling {
    public enum State { NONE, SAMPLING, READY, FAILED }

    /** progress = towards the quick pass, 0..1. */
    public record Status(State state, float progress, int done, int full) {
    }

    // spots in flight per biome; the sampler's own limits keep the main thread calm
    private static final int IN_FLIGHT = 12;
    private static final int BACKGROUND_IN_FLIGHT = 2;

    private static final class Job {
        final Identifier biome;
        @Nullable
        CompletableFuture<BiomeSpots.Result> search;
        int inFlight;
        // the quick pass is searched first so Analyze doesn't wait for a rare biome's 64 spots
        boolean quickSearched;

        Job(Identifier biome) {
            this.biome = biome;
        }
    }

    // biomes asked for by Analyze, in order
    private static final Set<Identifier> URGENT = new LinkedHashSet<>();
    private static final Map<Identifier, Job> JOBS = new HashMap<>();
    private static boolean checked;

    public static int quickSpots() {
        return QuarryConfig.SAMPLING_QUICK_SPOTS.get();
    }

    public static int fullSpots() {
        return Math.max(quickSpots(), QuarryConfig.SAMPLING_FULL_SPOTS.get());
    }

    public static Status status(MinecraftServer server, Identifier biome) {
        BiomeSampleData.Entry entry = BiomeSampleData.get(server).peek(biome);
        int quick = quickSpots();
        int full = fullSpots();
        if (entry == null) {
            return new Status(JOBS.containsKey(biome) ? State.SAMPLING : State.NONE, 0, 0, full);
        }
        if (entry.unsampleable()) {
            return new Status(State.FAILED, 0, 0, full);
        }
        // a rare biome may have fewer spots than the quick pass wants
        int needed = entry.searched() ? Math.min(quick, entry.planned()) : quick;
        if (entry.done() >= needed && entry.own() > 0) {
            return new Status(State.READY, 1, entry.done(), entry.searched() ? Math.min(full, entry.planned()) : full);
        }
        return new Status(State.SAMPLING, needed == 0 ? 0 : entry.done() / (float) needed, entry.done(), full);
    }

    /** Analyzed, and its biome's measurements are there (a mod change throws them away: analyze it again). */
    public static boolean knowsRock(MinecraftServer server, com.enderlaserquarry.content.asteroid.Asteroid asteroid) {
        return asteroid.analyzed() && status(server, asteroid.biome()).state() == State.READY;
    }

    /** Analyze wants this biome: quick pass first, ahead of background work. */
    public static void request(MinecraftServer server, Identifier biome) {
        if (status(server, biome).state() == State.READY) {
            return;
        }
        URGENT.add(biome);
        JOBS.computeIfAbsent(biome, Job::new);
    }

    @Nullable
    public static Object2LongMap<Identifier> counts(MinecraftServer server, Identifier biome) {
        if (status(server, biome).state() != State.READY) {
            return null;
        }
        return BiomeSampleData.get(server).peek(biome).blocks();
    }

    public static ResourceKey<Level> sourceDimension(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_NETHER) ? Level.NETHER : biome.is(BiomeTags.IS_END) ? Level.END : Level.OVERWORLD;
    }

    // region Fingerprint

    /** MC version, every mod with its version and the enabled datapacks: any change can change the terrain. */
    public static String fingerprint(MinecraftServer server) {
        String mods = ModList.get().getMods().stream().map(m -> m.getModId() + "@" + m.getVersion()).sorted().collect(Collectors.joining(","));
        String packs = server.getPackRepository().getSelectedIds().stream().sorted().collect(Collectors.joining(","));
        String text = SharedConstants.getCurrentVersion().name() + "|" + mods + "|" + packs;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static void checkFingerprint(MinecraftServer server) {
        if (BiomeSampleData.get(server).checkFingerprint(fingerprint(server))) {
            EnderLaserQuarry.LOGGER.info("Mods or datapacks changed: asteroid biome samples are measured again");
        }
        AsteroidComposition.clearCache();
        checked = true;
    }

    // endregion

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        URGENT.clear();
        JOBS.clear();
        checkFingerprint(event.getServer());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        URGENT.clear();
        JOBS.clear();
        checked = false;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (!checked) {
            checkFingerprint(server);
        }
        // nothing to do: look for refinement work once a second only
        if (URGENT.isEmpty() && JOBS.isEmpty() && server.getTickCount() % 20 != 0) {
            return;
        }
        if (ChunkSampler.limitsFromConfig) {
            ChunkSampler.maxHeldChunks = QuarryConfig.SAMPLING_MAX_HELD.get();
            ChunkSampler.countBudgetNanos = QuarryConfig.SAMPLING_TICK_BUDGET.get() * 1000L;
            ChunkSampler.pauseAboveMspt = QuarryConfig.SAMPLING_PAUSE_MSPT.get().floatValue();
        }

        BiomeSampleData data = BiomeSampleData.get(server);
        URGENT.removeIf(biome -> {
            State state = status(server, biome).state();
            return state == State.READY || state == State.FAILED;
        });
        // background refinement of what was analyzed before, once nothing is urgent
        if (URGENT.isEmpty() && JOBS.isEmpty() && QuarryConfig.SAMPLING_REFINE.get()) {
            for (var e : data.entries().entrySet()) {
                BiomeSampleData.Entry entry = e.getValue();
                if (!entry.unsampleable() && entry.done() < fullSpots() && (!entry.searched() || entry.done() < entry.planned())) {
                    JOBS.put(e.getKey(), new Job(e.getKey()));
                    break;
                }
            }
        }
        if (JOBS.isEmpty()) {
            return;
        }
        // urgent jobs get the slots first
        List<Job> order = new ArrayList<>();
        for (Identifier biome : URGENT) {
            Job job = JOBS.get(biome);
            if (job != null) {
                order.add(job);
            }
        }
        for (Job job : JOBS.values()) {
            if (!order.contains(job)) {
                order.add(job);
            }
        }
        for (Job job : order) {
            if (drive(server, data, job, !URGENT.isEmpty() && !URGENT.contains(job.biome))) {
                JOBS.remove(job.biome);
            }
        }
    }

    /** True when the job is finished. */
    private static boolean drive(MinecraftServer server, BiomeSampleData data, Job job, boolean yield) {
        Holder<Biome> biome = server.registryAccess().lookupOrThrow(Registries.BIOME)
            .get(ResourceKey.create(Registries.BIOME, job.biome)).orElse(null);
        BiomeSampleData.Entry entry = data.entry(job.biome);
        if (biome == null) {
            entry.searched = true;
            entry.spots.clear();
            data.setDirty();
            return true;
        }
        ResourceKey<Level> source = sourceDimension(biome);
        ServerLevel level = server.getLevel(SamplingDimensions.forSource(source));
        if (level == null) {
            // a world built without datapack dimensions
            EnderLaserQuarry.LOGGER.warn("Sampling dimension for {} is missing, asteroids of {} can't be analyzed", source.identifier(), job.biome);
            entry.searched = true;
            entry.spots.clear();
            data.setDirty();
            return true;
        }

        if (job.search != null) {
            if (!job.search.isDone()) {
                return false;
            }
            BiomeSpots.Result result = job.search.join();
            job.search = null;
            boolean second = job.quickSearched;
            for (BlockPos spot : result.spots().getOrDefault(biome, List.of())) {
                ChunkPos chunk = ChunkPos.containing(spot);
                // the second search doesn't know the first one's spots
                boolean near = second && entry.spots.stream().anyMatch(other -> other.getChessboardDistance(chunk) < minChunkGap(source));
                if (!near && entry.spots.size() < fullSpots()) {
                    entry.spots.add(chunk);
                }
            }
            // a biome too rare for the quick pass won't give more spots later either
            if (second || entry.spots.size() < quickSpots()) {
                entry.searched = true;
            }
            job.quickSearched = true;
            data.setDirty();
            if (entry.spots.isEmpty()) {
                EnderLaserQuarry.LOGGER.info("No ground of biome {} found in {}: its asteroids can't be analyzed", job.biome, source.identifier());
                return true;
            }
        }
        // first a search for the quick pass, then one for the rest
        if (!entry.searched && entry.spots.size() < (job.quickSearched ? fullSpots() : quickSpots()) && job.search == null
            && entry.done() + job.inFlight >= entry.spots.size()) {
            int count = job.quickSearched ? fullSpots() : quickSpots();
            long seed = server.overworld().getSeed() ^ job.biome.hashCode() * 31L ^ (job.quickSearched ? 0x5EEDL : 0);
            job.search = BiomeSpots.find(level, source == Level.END, Set.of(biome), settings(source, count, seed));
            return false;
        }
        if (yield) {
            return false;
        }
        int quick = Math.min(quickSpots(), entry.spots.size());
        // background spots a few at a time, so a new Analyze gets the worldgen threads quickly
        int limit = entry.done() < quick ? IN_FLIGHT : BACKGROUND_IN_FLIGHT;
        int next = entry.done() + job.inFlight;
        while (job.inFlight < limit && next < entry.spots.size() && next < fullSpots()) {
            ChunkPos chunk = entry.spots.get(next++);
            job.inFlight++;
            Identifier id = job.biome;
            ChunkSampler.submit(new ChunkSampler.Patch(level.dimension(), chunk, 0, new ChunkSampler.Listener() {
                @Override
                public void counted(ChunkSampler.Patch patch, ChunkPos pos, ChunkCounter.Counts counts) {
                    add(server, id, biome, counts);
                }

                @Override
                public void done(ChunkSampler.Patch patch) {
                    job.inFlight--;
                    BiomeSampleData d = BiomeSampleData.get(server);
                    BiomeSampleData.Entry e = d.entry(id);
                    e.done = Math.min(e.spots.size(), e.done + 1);
                    d.setDirty();
                    AsteroidComposition.invalidate(id);
                }
            }));
        }
        boolean allPlanned = entry.searched || entry.spots.size() >= fullSpots();
        return job.inFlight == 0 && job.search == null && allPlanned
            && (entry.done() >= Math.min(fullSpots(), entry.spots.size()));
    }

    private static int minChunkGap(ResourceKey<Level> source) {
        return source == Level.END ? 3 : 10;
    }

    private static BiomeSpots.Settings settings(ResourceKey<Level> source, int count, long seed) {
        int spacing = source == Level.END ? 256 : 640;
        int range = source == Level.OVERWORLD ? 40_000 : source == Level.NETHER ? 15_000 : 8_000;
        return new BiomeSpots.Settings(count, spacing, range, QuarryConfig.SAMPLING_MAX_PROBES.get(), seed);
    }

    private static void add(MinecraftServer server, Identifier id, Holder<Biome> biome, ChunkCounter.Counts counts) {
        Object2LongMap<BlockState> states = counts.blocks().get(biome);
        if (states == null) {
            return;
        }
        BiomeSampleData data = BiomeSampleData.get(server);
        BiomeSampleData.Entry entry = data.entry(id);
        for (var e : states.object2LongEntrySet()) {
            entry.blocks.addTo(BuiltInRegistries.BLOCK.getKey(e.getKey().getBlock()), e.getLongValue());
            entry.own += e.getLongValue();
        }
        data.setDirty();
    }

    // measured counts put in by hand
    public static void debugPut(MinecraftServer server, Identifier biome, Map<Identifier, Long> blocks) {
        BiomeSampleData data = BiomeSampleData.get(server);
        BiomeSampleData.Entry entry = data.entry(biome);
        entry.blocks.clear();
        entry.own = 0;
        blocks.forEach((block, n) -> {
            entry.blocks.put(block, (long) n);
            entry.own += n;
        });
        entry.spots.clear();
        for (int i = 0; i < fullSpots(); i++) {
            entry.spots.add(new ChunkPos(i * 1000, 0));
        }
        entry.done = fullSpots();
        entry.searched = true;
        data.setDirty();
        AsteroidComposition.invalidate(biome);
    }

    public static void debugForget(MinecraftServer server, Identifier biome) {
        BiomeSampleData.get(server).entries().remove(biome);
        URGENT.remove(biome);
        JOBS.remove(biome);
        AsteroidComposition.invalidate(biome);
    }

    public static boolean isIdle() {
        return JOBS.isEmpty();
    }

    public static boolean isSampling(Identifier biome) {
        return JOBS.containsKey(biome);
    }

    private BiomeSampling() {
    }
}

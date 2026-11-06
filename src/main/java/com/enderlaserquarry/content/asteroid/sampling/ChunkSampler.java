package com.enderlaserquarry.content.asteroid.sampling;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;
import com.enderlaserquarry.init.ModTickets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates square patches of chunks in a sampling dimension up to FEATURES on the worldgen threads and counts the
 * inner ones on the main thread, a little per tick. The ring around them is generated too, so veins crossing a chunk
 * border are complete in the counted chunks.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class ChunkSampler {
    private static final int LEVEL = ChunkLevel.byStatus(ChunkStatus.FEATURES);

    /** Chunks held at once (ring included). */
    public static int maxHeldChunks = 100;
    /** Main thread time per tick for counting. */
    public static long countBudgetNanos = 1_000_000L;
    public static int maxCountedPerTick = 2;
    /** Patches started per tick: each one makes the distance manager walk ~440 chunks (structure starts reach 8 out). */
    public static int maxStartsPerTick = 1;
    /** No new work while the server is slower than this. */
    public static float pauseAboveMspt = 40f;
    // our own main thread time last tick (vanilla's tick time doesn't include the post-tick event)
    public static long lastTickNanos;
    // false = the limits above are set by hand, not from the config every tick
    public static boolean limitsFromConfig = true;

    private static final Deque<Patch> WAITING = new ArrayDeque<>();
    private static final List<Patch> ACTIVE = new ArrayList<>();

    public interface Listener {
        void counted(Patch patch, ChunkPos pos, ChunkCounter.Counts counts);

        default void done(Patch patch) {
        }
    }

    public static final class Patch {
        private final ResourceKey<Level> dimension;
        private final ChunkPos centre;
        private final int radius;
        private final Listener listener;
        private final List<ChunkPos> generated = new ArrayList<>();
        private final List<ChunkPos> counted = new ArrayList<>();
        private CompletableFuture<ChunkResult<ChunkAccess>>[] futures;
        private int nextCount;
        private int failed;
        private long startNanos;
        private long readyNanos;
        private long doneNanos;
        private long countNanos;

        /** Counts the (2 radius + 1)² chunks around centre. */
        public Patch(ResourceKey<Level> dimension, ChunkPos centre, int radius, Listener listener) {
            this.dimension = dimension;
            this.centre = centre;
            this.radius = radius;
            this.listener = listener;
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                    ChunkPos pos = new ChunkPos(centre.x() + dx, centre.z() + dz);
                    generated.add(pos);
                    if (Math.abs(dx) <= radius && Math.abs(dz) <= radius) {
                        counted.add(pos);
                    }
                }
            }
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public ChunkPos centre() {
            return centre;
        }

        public int generatedChunks() {
            return generated.size();
        }

        public int failedChunks() {
            return failed;
        }

        /** Ticket to all chunks generated. */
        public long generateNanos() {
            return readyNanos - startNanos;
        }

        public long totalNanos() {
            return doneNanos - startNanos;
        }

        /** Main thread time spent counting. */
        public long countNanos() {
            return countNanos;
        }

        private boolean allGenerated() {
            for (var future : futures) {
                if (future == null || !future.isDone()) {
                    return false;
                }
            }
            return true;
        }
    }

    public static void submit(Patch patch) {
        WAITING.add(patch);
    }

    public static int pending() {
        return WAITING.size() + ACTIVE.size();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (WAITING.isEmpty() && ACTIVE.isEmpty()) {
            lastTickNanos = 0;
            return;
        }
        long start = System.nanoTime();
        MinecraftServer server = event.getServer();
        boolean busy = server.getCurrentSmoothedTickTime() > pauseAboveMspt;
        if (!busy) {
            startPatches(server);
        }
        for (Patch patch : ACTIVE) {
            schedule(server, patch);
        }
        if (!busy) {
            countSome(server);
        }
        lastTickNanos = System.nanoTime() - start;
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        WAITING.clear();
        ACTIVE.clear();
    }

    private static int held() {
        int held = 0;
        for (Patch patch : ACTIVE) {
            held += patch.generated.size();
        }
        return held;
    }

    @SuppressWarnings("unchecked")
    private static void startPatches(MinecraftServer server) {
        int held = held();
        int started = 0;
        while (!WAITING.isEmpty() && started < maxStartsPerTick
            && (ACTIVE.isEmpty() || held + WAITING.peek().generated.size() <= maxHeldChunks)) {
            started++;
            Patch patch = WAITING.poll();
            ServerLevel level = server.getLevel(patch.dimension);
            if (level == null) {
                EnderLaserQuarry.LOGGER.warn("Sampling dimension {} is missing", patch.dimension.identifier());
                patch.listener.done(patch);
                continue;
            }
            TicketStorage tickets = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (ChunkPos pos : patch.generated) {
                tickets.addTicket(new Ticket(ModTickets.SAMPLING.get(), LEVEL), pos);
            }
            patch.futures = new CompletableFuture[patch.generated.size()];
            patch.startNanos = System.nanoTime();
            ACTIVE.add(patch);
            held += patch.generated.size();
        }
    }

    // holders show up once the ticket went through the distance manager
    private static void schedule(MinecraftServer server, Patch patch) {
        ServerLevel level = server.getLevel(patch.dimension);
        if (level == null || patch.readyNanos != 0) {
            return;
        }
        var chunkMap = level.getChunkSource().chunkMap;
        for (int i = 0; i < patch.futures.length; i++) {
            if (patch.futures[i] != null) {
                continue;
            }
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(patch.generated.get(i).pack());
            if (holder != null && holder.getTicketLevel() <= LEVEL) {
                patch.futures[i] = holder.scheduleChunkGenerationTask(ChunkStatus.FEATURES, chunkMap);
            }
        }
        if (patch.allGenerated()) {
            patch.readyNanos = System.nanoTime();
        }
    }

    private static void countSome(MinecraftServer server) {
        long end = System.nanoTime() + countBudgetNanos;
        int countedThisTick = 0;
        Iterator<Patch> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Patch patch = it.next();
            if (patch.readyNanos == 0) {
                continue;
            }
            ServerLevel level = server.getLevel(patch.dimension);
            while (patch.nextCount < patch.counted.size()) {
                if (countedThisTick >= maxCountedPerTick || System.nanoTime() >= end) {
                    return;
                }
                ChunkPos pos = patch.counted.get(patch.nextCount++);
                ChunkResult<ChunkAccess> result = patch.futures[patch.generated.indexOf(pos)].join();
                ChunkAccess chunk = result.orElse(null);
                if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                    patch.failed++;
                    continue;
                }
                long start = System.nanoTime();
                ChunkCounter.Counts counts = ChunkCounter.count(level, chunk);
                patch.countNanos += System.nanoTime() - start;
                countedThisTick++;
                patch.listener.counted(patch, pos, counts);
            }
            release(level, patch);
            patch.doneNanos = System.nanoTime();
            it.remove();
            patch.listener.done(patch);
        }
    }

    private static void release(ServerLevel level, Patch patch) {
        TicketStorage tickets = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
        for (ChunkPos pos : patch.generated) {
            tickets.removeTicket(new Ticket(ModTickets.SAMPLING.get(), LEVEL), pos);
        }
    }

    private ChunkSampler() {
    }
}

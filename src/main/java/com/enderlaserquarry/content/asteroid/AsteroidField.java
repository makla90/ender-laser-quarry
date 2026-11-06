package com.enderlaserquarry.content.asteroid;

import com.enderlaserquarry.config.QuarryConfig;
import com.enderlaserquarry.content.asteroid.sampling.BiomeSampleData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The asteroids found by space scans, stored on the launch pad. A scan replaces untouched ones, started or queued ones
 * stay until they're used up.
 */
public final class AsteroidField {
    public static final int MAP_UNITS = 1000;
    private static final int EDGE = 45;
    private static final int PLACE_ATTEMPTS = 60;
    // main biomes only, datapacks can add more. Empty = all but the void
    public static final TagKey<Biome> ASTEROID_BIOMES = TagKey.create(Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath("ender_laser_quarry", "asteroid_biomes"));
    public static final TagKey<Biome> FREQUENT_BIOMES = TagKey.create(Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath("ender_laser_quarry", "frequent_asteroid_biomes"));

    private final List<Asteroid> asteroids = new ArrayList<>();
    private final Runnable onChanged;
    private int nextId = 1;

    public AsteroidField(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public List<Asteroid> asteroids() {
        return Collections.unmodifiableList(asteroids);
    }

    public boolean isEmpty() {
        return asteroids.isEmpty();
    }

    @Nullable
    public Asteroid get(int id) {
        for (Asteroid asteroid : asteroids) {
            if (asteroid.id() == id) {
                return asteroid;
            }
        }
        return null;
    }

    public void setAnalyzed(int id) {
        for (int i = 0; i < asteroids.size(); i++) {
            if (asteroids.get(i).id() == id) {
                asteroids.set(i, asteroids.get(i).withAnalyzed());
                onChanged.run();
                return;
            }
        }
    }

    // no ground of its biome anywhere: the signal is lost
    public void remove(int id) {
        if (asteroids.removeIf(asteroid -> asteroid.id() == id)) {
            onChanged.run();
        }
    }

    public long take(int id, long amount) {
        for (int i = 0; i < asteroids.size(); i++) {
            Asteroid asteroid = asteroids.get(i);
            if (asteroid.id() == id) {
                long taken = Math.min(amount, asteroid.remaining());
                if (asteroid.remaining() - taken <= 0) {
                    asteroids.remove(i);
                } else {
                    asteroids.set(i, asteroid.withRemaining(asteroid.remaining() - taken));
                }
                onChanged.run();
                return taken;
            }
        }
        return 0;
    }

    // keep = queued ids that must stay
    public void refresh(ServerLevel level, RandomSource random, IntPredicate keep) {
        // analyzed ones stay too, that took radar power
        asteroids.removeIf(asteroid -> !asteroid.isStarted() && !asteroid.analyzed() && !keep.test(asteroid.id()));
        int wanted = QuarryConfig.VOID_ASTEROIDS.get();
        for (int i = 0; asteroids.size() < wanted && i < wanted * 2; i++) {
            Holder<Biome> biome = pickBiome(level, random);
            if (biome == null) {
                break;
            }
            AsteroidSize size = AsteroidSize.roll(random);
            int[] spot = findSpot(random, size);
            if (spot == null) {
                continue;
            }
            long total = size.blocks();
            asteroids.add(new Asteroid(nextId++, biome.unwrapKey().orElseThrow().location(), size, total, total, spot[0], spot[1],
                random.nextLong(), false));
        }
        onChanged.run();
    }

    public Asteroid debugAdd(ResourceLocation biome, long total) {
        Asteroid asteroid = new Asteroid(nextId++, biome, AsteroidSize.SMALL, total, total, MAP_UNITS / 2, MAP_UNITS / 2, 0, false);
        asteroids.add(asteroid);
        onChanged.run();
        return asteroid;
    }

    // Dimension first (Nether and End have few biomes), then a biome in it
    @Nullable
    private static Holder<Biome> pickBiome(ServerLevel level, RandomSource random) {
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<Holder<Biome>> overworld = new ArrayList<>();
        List<Holder<Biome>> nether = new ArrayList<>();
        List<Holder<Biome>> end = new ArrayList<>();
        boolean anyListed = registry.getTag(ASTEROID_BIOMES).map(set -> set.size() > 0).orElse(false);
        registry.holders().forEach(holder -> {
            if (anyListed ? !holder.is(ASTEROID_BIOMES) : holder.is(Biomes.THE_VOID)) {
                return;
            }
            // searched before and not found (a modded biome of another dimension)
            var entry = BiomeSampleData.get(level.getServer()).peek(holder.unwrapKey().orElseThrow().location());
            if (entry != null && entry.unsampleable()) {
                return;
            }
            if (holder.is(BiomeTags.IS_NETHER)) {
                nether.add(holder);
            } else if (holder.is(BiomeTags.IS_END)) {
                end.add(holder);
            } else {
                // overworld and modded dimensions
                overworld.add(holder);
            }
        });

        double roll = random.nextDouble();
        double netherChance = QuarryConfig.VOID_NETHER_CHANCE.get();
        double endChance = QuarryConfig.VOID_END_CHANCE.get();
        List<Holder<Biome>> pool = roll < netherChance ? nether : roll < netherChance + endChance ? end : overworld;
        if (pool.isEmpty()) {
            pool = !overworld.isEmpty() ? overworld : !nether.isEmpty() ? nether : end;
        }
        if (pool.isEmpty()) {
            return null;
        }
        // frequent ones (desert, badlands) come up three times as often
        double total = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = pool.get(i).is(FREQUENT_BIOMES) ? 3 : 1;
            total += weights[i];
        }
        if (total <= 0) {
            return pool.get(random.nextInt(pool.size()));
        }
        double pick = random.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            pick -= weights[i];
            if (pick < 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    // matches the drawn size on the client
    private static int room(AsteroidSize size) {
        return switch (size) {
            case SMALL -> 30;
            case MEDIUM -> 44;
            case LARGE -> 56;
        };
    }

    @Nullable
    private int[] findSpot(RandomSource random, AsteroidSize size) {
        int room = room(size);
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
            int x = EDGE + random.nextInt(MAP_UNITS - 2 * EDGE);
            int y = EDGE + random.nextInt(MAP_UNITS - 2 * EDGE);
            boolean free = true;
            for (Asteroid other : asteroids) {
                int needed = room + room(other.size());
                long dx = x - other.x();
                long dy = y - other.y();
                if (dx * dx + dy * dy < (long) needed * needed) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return new int[] {x, y};
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("NextId", nextId);
        ListTag list = new ListTag();
        for (Asteroid asteroid : asteroids) {
            list.add(asteroid.save());
        }
        tag.put("Asteroids", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        nextId = Math.max(1, tag.getInt("NextId"));
        asteroids.clear();
        for (Tag entry : tag.getList("Asteroids", Tag.TAG_COMPOUND)) {
            Asteroid asteroid = Asteroid.load((CompoundTag) entry);
            if (asteroid != null) {
                asteroids.add(asteroid);
            }
        }
    }
}

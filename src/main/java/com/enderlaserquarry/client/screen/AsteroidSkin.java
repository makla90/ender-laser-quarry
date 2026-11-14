package com.enderlaserquarry.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** What a biome paints over its asteroid's rock: seas, snow caps, ground cover, plants and glowing bits. */
final class AsteroidSkin {
    static final AsteroidSkin NONE = new AsteroidSkin();
    private static final int SNOW = 0xF0F6FF;
    private static final TagKey<Biome> DRY = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_dry"));
    private static final TagKey<Biome> SWAMP = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_swamp"));
    private static final TagKey<Biome> C_OCEAN = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_ocean"));
    private static final Map<Identifier, AsteroidSkin> CACHE = new HashMap<>();

    private int sea = -1;
    private float seaCover;
    private int[] reef = {};
    private int caps = -1;
    private float capSize;
    private int[] patch = {};
    private float patchCover;
    private int[] tufts = {};
    private float tuftDensity;
    private int[] glow = {};
    private float glowDensity;

    record Pixel(int color, boolean glow, boolean covered) {
    }

    static AsteroidSkin of(Identifier biome) {
        return CACHE.computeIfAbsent(biome, AsteroidSkin::make);
    }

    static void clearCache() {
        CACHE.clear();
    }

    // lat: -1 top .. 1 bottom
    Pixel paint(int x, int y, float lat, int seed, int rock) {
        if (this == NONE) {
            return new Pixel(rock, false, false);
        }
        int color = rock;
        boolean lit = false;
        boolean covered = false;
        if (sea >= 0) {
            float n = SpaceMapView.smoothNoise(x / 7f + 3, y / 7f, seed ^ 0x5EA);
            if (n < seaCover) {
                covered = true;
                color = n > seaCover - 0.07f ? mix(sea, 0xFFFFFF, 0.28f) : n < seaCover - 0.3f ? mix(sea, 0x000000, 0.18f) : sea;
                if (reef.length > 0 && n > seaCover - 0.16f && (SpaceMapView.hash(x, y, seed ^ 0x2EEF) & 255) < 70) {
                    color = reef[SpaceMapView.hash(x, y, 41) % reef.length];
                }
            }
        }
        if (!covered) {
            if (patch.length > 0) {
                float n = SpaceMapView.smoothNoise(x / 5f, y / 5f + 7, seed ^ 0xBA7);
                if (n > 1 - patchCover) {
                    color = patch[n > 1 - patchCover * 0.4f && patch.length > 1 ? 1 : 0];
                }
            }
            if (tufts.length > 0) {
                float clump = SpaceMapView.smoothNoise(x / 4f + 11, y / 4f, seed ^ 0x7F);
                if (clump > 0.42f && (SpaceMapView.hash(x, y, seed ^ 0x70F) & 1023) < 1023 * tuftDensity * (clump + 0.3f)) {
                    color = tufts[SpaceMapView.hash(x, y, 17) % tufts.length];
                }
            }
            if (glow.length > 0 && (SpaceMapView.hash(x, y, seed ^ 0x6F0) & 4095) < 4095 * glowDensity) {
                color = glow[SpaceMapView.hash(x, y, 23) % glow.length];
                lit = true;
            }
        }
        if (caps >= 0) {
            float wobble = (SpaceMapView.smoothNoise(x / 3f, y / 3f, seed ^ 0xCA9) - 0.5f) * 0.3f;
            if (Math.abs(lat) + wobble > 1 - capSize) {
                color = caps;
                covered = true;
                lit = false;
            }
        }
        return new Pixel(color, lit, covered);
    }

    private static AsteroidSkin make(Identifier id) {
        AsteroidSkin skin = new AsteroidSkin();
        Optional<Holder.Reference<Biome>> holder = Optional.empty();
        var level = Minecraft.getInstance().level;
        if (level != null) {
            holder = level.registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceKey.create(Registries.BIOME, id));
        }
        // PORT: ARGB in 26.1: without the mask the colour is negative, which means "no sea"
        int water = holder.map(h -> h.value().getWaterColor() & 0xFFFFFF).orElse(0x3F76E4);
        int foliage = holder.map(h -> h.value().getFoliageColor() & 0xFFFFFF).orElse(0x59AE30);
        switch (id.toString()) {
            // their own sand and terracotta already look right
            case "minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
                 "minecraft:stony_peaks", "minecraft:beach" -> {
                return NONE;
            }
            case "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow" ->
                skin.tufts(0.12f, 0x7FB238, 0x91BD59, 0x91BD59, 0xF5E04A, 0xF0F0F0);
            case "minecraft:cherry_grove" -> skin.patch(0.55f, 0xF4B6D2, 0xF9D2E4).tufts(0.35f, 0xE58BB5, 0xD67AA6, 0xF4B6D2);
            case "minecraft:warm_ocean" -> skin.sea(water, 0.55f).reef(0xD64C8C, 0xE8D13A, 0x3D57D6, 0xA23CC2);
            case "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean" -> skin.sea(water, 0.5f).caps(SNOW, 0.42f);
            case "minecraft:mangrove_swamp" -> skin.sea(0x3A7A6A, 0.42f).tufts(0.3f, 0x8DB127, 0x6A8F1F, 0x4E6B17);
            case "minecraft:mushroom_fields" -> skin.patch(0.8f, 0x8C7590, 0x6F5A72).tufts(0.18f, 0xC8302C, 0xC8302C, 0xE8E0D8, 0x9A7457);
            case "minecraft:ice_spikes" -> skin.caps(SNOW, 0.4f).tufts(0.25f, 0x8DB4F0, 0xB4D0FA);
            case "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:snowy_slopes" ->
                skin.caps(SNOW, 0.5f).patch(0.25f, 0x9CC0F5);
            case "minecraft:dripstone_caves" -> skin.tufts(0.25f, 0x9A6E4E, 0x7A553A);
            case "minecraft:lush_caves" -> skin.patch(0.45f, 0x5C7A2E, 0x6E8F32).tufts(0.18f, 0xC768B5, 0x8FB83C).glow(0.05f, 0xF2A43A);
            case "minecraft:deep_dark" -> skin.patch(0.4f, 0x0D3B47, 0x0A2A33).glow(0.035f, 0x29DFEB, 0x7FF5FF);
            case "minecraft:nether_wastes" -> skin.glow(0.015f, 0xF07A1C, 0xFFB13B);
            case "minecraft:crimson_forest" -> skin.patch(0.6f, 0xC0393B, 0xD9504A).tufts(0.15f, 0x7B0000).glow(0.035f, 0xF29A3A);
            case "minecraft:warped_forest" -> skin.patch(0.5f, 0x167E86, 0x2A9A8E).tufts(0.1f, 0x14B4A0).glow(0.02f, 0xF29A3A);
            case "minecraft:soul_sand_valley" -> skin.glow(0.02f, 0x5DE8F0, 0x9AF6FF);
            case "minecraft:basalt_deltas" -> skin.glow(0.025f, 0xF07A1C, 0xFF5A14);
            default -> {
                if (holder.isEmpty()) {
                    return NONE;
                }
                generic(skin, holder.get(), water, foliage);
            }
        }
        return skin;
    }

    // modded and other biomes: from their water and foliage colours and their tags
    private static void generic(AsteroidSkin skin, Holder<Biome> biome, int water, int foliage) {
        if (biome.is(BiomeTags.IS_END)) {
            skin.tufts(0.08f, 0x8C5A9C, 0x5E3A6E).glow(0.01f, 0xD2B8E0);
            return;
        }
        if (biome.is(BiomeTags.IS_NETHER)) {
            skin.glow(0.015f, 0xF07A1C, 0xFFB13B);
            return;
        }
        boolean cold = biome.value().getBaseTemperature() < 0.15f;
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(C_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
            skin.sea(water, biome.is(BiomeTags.IS_RIVER) ? 0.4f : 0.55f);
        } else if (biome.is(SWAMP)) {
            skin.sea(water, 0.4f).tufts(0.3f, foliage, mix(foliage, 0x000000, 0.25f));
        } else if (!biome.is(DRY) && !biome.is(BiomeTags.IS_BADLANDS) && biome.value().hasPrecipitation()) {
            boolean wooded = biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA);
            skin.tufts(wooded ? 0.35f : 0.14f, foliage, mix(foliage, 0x000000, 0.25f), mix(foliage, 0xFFFFFF, 0.2f));
        }
        if (cold) {
            skin.caps(SNOW, 0.35f);
        }
    }

    private AsteroidSkin sea(int color, float cover) {
        sea = color;
        seaCover = cover;
        return this;
    }

    private AsteroidSkin reef(int... colors) {
        reef = colors;
        return this;
    }

    private AsteroidSkin caps(int color, float size) {
        caps = color;
        capSize = size;
        return this;
    }

    private AsteroidSkin patch(float cover, int... colors) {
        patch = colors;
        patchCover = cover;
        return this;
    }

    private AsteroidSkin tufts(float density, int... colors) {
        tufts = colors;
        tuftDensity = density;
        return this;
    }

    private AsteroidSkin glow(float density, int... colors) {
        glow = colors;
        glowDensity = density;
        return this;
    }

    static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}

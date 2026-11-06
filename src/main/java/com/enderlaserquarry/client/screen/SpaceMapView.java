package com.enderlaserquarry.client.screen;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.asteroid.AsteroidField;
import com.enderlaserquarry.content.asteroid.AsteroidSize;
import com.enderlaserquarry.network.SpaceMapPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Space map for the void crystal: the asteroids as pixel art on a starfield, baked into a texture when the asteroids
 * change. Scroll zooms, right drag pans.
 */
final class SpaceMapView {
    private static final Identifier TEXTURE = EnderLaserQuarry.rl("dynamic/quarry_space");
    private static final int ORE_LINES = 8;
    private static final int ROCK_LINES = 6;
    private static final float MIN_TINT_SHARE = 0.005f;
    // texture size, twice the map
    private static final int FIELD = 288;
    private static final double MAX_ZOOM = 4;
    private static final double ZOOM_STEP = 1.25;
    private static final int SMALL_RADIUS = 6;
    private static final int MEDIUM_RADIUS = 8;
    private static final int LARGE_RADIUS = 11;
    private static final int QUEUED = 0xFF00E5FF;
    private static final int HOVER = 0x60FFFFFF;
    private static final int BAR_BACK = 0xC0000000;
    private static final int BAR_FILL = 0xFF46D2E0;
    // analysis look: cyan like the scan beam, amber for a picked asteroid, orange while the radar has no power
    private static final int SCAN = 0x46D2E0;
    private static final int SCAN_BRIGHT = 0xB8F6FF;
    private static final int PICKED = 0xFFC84A;
    private static final int STALLED = 0xFF7A2A;
    private static final int UNKNOWN_LIGHT = 0x5C6370;
    private static final int UNKNOWN_DARK = 0x464C57;
    private static final long FLASH_MS = 1000;
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);

    private final int size;
    private double zoom = 1;
    private double viewX;
    private double viewY;
    private List<SpaceMapPayload.AsteroidInfo> asteroids = List.of();
    @Nullable
    private DynamicTexture texture;
    // rebake when this changes
    private String bakedKey = "";
    // asteroid id -> when it turned analyzed, for the flash
    private final Map<Integer, Long> flashes = new HashMap<>();
    private final Set<Integer> unanalyzed = new HashSet<>();
    private int lastAnalyzing = -1;

    SpaceMapView(int size) {
        this.size = size;
    }

    boolean hasData() {
        return texture != null;
    }

    @Nullable
    NativeImage pixels() {
        return texture == null ? null : texture.getPixels();
    }

    List<SpaceMapPayload.AsteroidInfo> asteroids() {
        return asteroids;
    }

    void set(SpaceMapPayload payload) {
        asteroids = payload.asteroids();
        StringBuilder key = new StringBuilder();
        long now = System.currentTimeMillis();
        for (var asteroid : asteroids) {
            key.append(asteroid.id()).append(':').append(asteroid.seed()).append(':').append(asteroid.parts().size()).append(';');
            if (asteroid.analyzed() && unanalyzed.remove(asteroid.id())) {
                flashes.putIfAbsent(asteroid.id(), now);
            } else if (!asteroid.analyzed()) {
                unanalyzed.add(asteroid.id());
            }
        }
        if (texture == null || !key.toString().equals(bakedKey)) {
            bakedKey = key.toString();
            bake();
        }
    }

    void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
        }
    }

    // region Baking

    private void bake() {
        if (texture == null) {
            texture = new DynamicTexture("ender_laser_quarry_space", FIELD, FIELD, true);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        }
        NativeImage image = texture.getPixels();
        // another world can have other biomes
        AsteroidSkin.clearCache();
        drawSpace(image);
        for (var asteroid : asteroids) {
            drawAsteroid(image, asteroid);
        }
        texture.upload();
    }

    private void drawSpace(NativeImage image) {
        for (int y = 0; y < FIELD; y++) {
            for (int x = 0; x < FIELD; x++) {
                float haze = smoothNoise(x / 37f, y / 37f, 11) * smoothNoise(x / 17f + 5, y / 17f, 23);
                int r = 6 + (int) (10 * haze);
                int g = 8 + (int) (26 * haze);
                int b = 14 + (int) (30 * haze);
                int star = hash(x, y, 7) & 1023;
                if (star < 6) {
                    int bright = 120 + (hash(x, y, 9) & 127);
                    r = bright;
                    g = bright;
                    b = Math.min(255, bright + 30);
                } else if (star < 10) {
                    r += 30;
                    g += 34;
                    b += 40;
                }
                image.setPixelABGR(x, y, abgr(r, g, b));
            }
        }
    }

    private void drawAsteroid(NativeImage image, SpaceMapPayload.AsteroidInfo asteroid) {
        int cx = centerX(asteroid);
        int cy = centerY(asteroid);
        int radius = radius(asteroid);
        long seed = asteroid.seed();
        // tint by the biome's own rock (sand, terracotta...), stone and deepslate say nothing
        List<Integer> tint = new ArrayList<>();
        List<Integer> grey = new ArrayList<>();
        for (var part : asteroid.parts()) {
            BlockState state = Block.stateById(part.stateId());
            int color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            if (part.ore()) {
                continue;
            } else if (part.host() && !isPlainRock(state) && part.share() >= MIN_TINT_SHARE && tint.size() < 2) {
                tint.add(color);
            } else if (grey.isEmpty()) {
                grey.add(color);
            }
        }
        List<Integer> rock = new ArrayList<>(tint);
        if (rock.size() < 2) {
            rock.addAll(grey.isEmpty() ? List.of(0x707070) : grey);
        }
        AsteroidSkin skin = AsteroidSkin.of(asteroid.biome());
        if (!knowsRock(asteroid)) {
            // unknown rock: a cold grey silhouette
            rock = List.of(UNKNOWN_LIGHT, UNKNOWN_DARK);
            skin = AsteroidSkin.NONE;
        }

        // lumpy outline
        float p1 = (seed & 0xFF) / 40f;
        float p2 = ((seed >> 8) & 0xFF) / 40f;
        float p3 = ((seed >> 16) & 0xFF) / 40f;
        int craters = 2 + asteroid.size().ordinal();
        float[][] crater = new float[craters][3];
        for (int i = 0; i < craters; i++) {
            float angle = ((seed >> (20 + i * 5)) & 31) / 31f * Mth.TWO_PI;
            float dist = 0.25f + 0.45f * (((seed >> (24 + i * 3)) & 7) / 7f);
            crater[i][0] = cx + Mth.cos(angle) * dist * radius;
            crater[i][1] = cy + Mth.sin(angle) * dist * radius;
            crater[i][2] = radius * (0.3f - 0.04f * asteroid.size().ordinal());
        }
        boolean ringed = asteroid.size() == AsteroidSize.LARGE && (seed & 3) == 0;
        if (ringed) {
            drawRing(image, cx, cy, radius, rock.get(0), true);
        }

        int reach = radius + 2;
        for (int y = cy - reach; y <= cy + reach; y++) {
            for (int x = cx - reach; x <= cx + reach; x++) {
                if (x < 0 || y < 0 || x >= FIELD || y >= FIELD) {
                    continue;
                }
                float dx = x + 0.5f - cx;
                float dy = y + 0.5f - cy;
                float dist = Mth.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.atan2(dy, dx);
                float edge = radius * (1 + 0.09f * Mth.sin(angle + p1) + 0.06f * Mth.sin(2 * angle + p2) + 0.05f * Mth.sin(3 * angle + p3));
                if (dist > edge) {
                    continue;
                }

                float patch = smoothNoise(x / 6f + p1, y / 6f + p2, (int) seed);
                var pixel = skin.paint(x, y, dy / edge, (int) seed, rock.get(Math.min(rock.size() - 1, patch < 0.68f ? 0 : 1)));
                int color = pixel.color();
                // light from the top left
                float nz = Mth.sqrt(Math.max(0, 1 - (dist / edge) * (dist / edge)));
                float light = 0.45f + 0.75f * Math.max(0, (-dx * 0.55f - dy * 0.6f) / edge + nz * 0.6f);
                if (pixel.covered()) {
                    // seas and snow are smooth: softer light, no craters
                    light = 0.72f + 0.4f * (light - 0.45f) / 0.75f;
                }
                for (float[] c : pixel.covered() ? new float[0][] : crater) {
                    float cd = Mth.sqrt((x + 0.5f - c[0]) * (x + 0.5f - c[0]) + (y + 0.5f - c[1]) * (y + 0.5f - c[1]));
                    if (cd < c[2]) {
                        light *= 0.72f;
                    } else if (cd < c[2] + 0.8f) {
                        light *= 1.12f;
                    }
                }
                if (dist > edge - 1) {
                    light *= 0.55f;
                }
                if (pixel.glow()) {
                    light = Math.max(light, 1f);
                }
                // stepped shading looks more like pixel art
                image.setPixelABGR(x, y, shade(color, Math.round(light * 6) / 6f));
            }
        }

        if (ringed) {
            drawRing(image, cx, cy, radius, rock.get(0), false);
        }
    }

    // back half before the body, front half after
    private void drawRing(NativeImage image, int cx, int cy, int radius, int color, boolean back) {
        float rx = radius * 1.7f;
        float ry = radius * 0.42f;
        for (int i = 0; i < 160; i++) {
            float angle = i / 160f * Mth.TWO_PI;
            float sin = Mth.sin(angle);
            if (back != (sin < 0)) {
                continue;
            }
            int x = Math.round(cx + Mth.cos(angle) * rx);
            int y = Math.round(cy + sin * ry - Mth.cos(angle) * ry * 0.4f);
            if (x >= 0 && y >= 0 && x < FIELD && y < FIELD) {
                image.setPixelABGR(x, y, shade(color, back ? 0.6f : 1.05f));
            }
        }
    }

    private static boolean isOwnRock(SpaceMapPayload.Part part) {
        BlockState state = Block.stateById(part.stateId());
        return isPlainRock(state) || (part.host() && part.share() >= MIN_TINT_SHARE);
    }

    private static boolean isPlainRock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE);
    }

    // endregion

    // region Drawing & input

    /** The asteroid being analyzed, the one picked for it, and how far the analysis is. */
    record Analysis(int analyzingId, float progress, boolean stalled, int pickedId) {
        static final Analysis NONE = new Analysis(-1, 0, false, -1);
    }

    void render(GuiGraphicsExtractor g, Font font, int x0, int y0, List<Integer> queue, boolean running, double mouseX, double mouseY,
                Analysis analysis) {
        int left = x0 + screen(0, viewX);
        int top = y0 + screen(0, viewY);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, 0, 0, x0 + screen(FIELD, viewX) - left, y0 + screen(FIELD, viewY) - top, FIELD, FIELD, FIELD, FIELD);

        long now = System.currentTimeMillis();
        // flash as soon as the synced analysis ends, the new rock colours come a bit later
        if (lastAnalyzing != -1 && analysis.analyzingId() != lastAnalyzing) {
            flashes.putIfAbsent(lastAnalyzing, now);
        }
        lastAnalyzing = analysis.analyzingId();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 150.0);
        SpaceMapPayload.AsteroidInfo hovered = running ? null : asteroidAt(mouseX - x0, mouseY - y0);
        for (var asteroid : asteroids) {
            int cx = x0 + screen(centerX(asteroid), viewX);
            int cy = y0 + screen(centerY(asteroid), viewY);
            int r = screenRadius(asteroid) + 2;
            if (asteroid == hovered) {
                g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, HOVER);
            }
            if (asteroid.id() == analysis.analyzingId()) {
                renderAnalyzing(g, cx, cy, r, analysis, now);
            } else if (asteroid.id() == analysis.pickedId() && !asteroid.analyzed()) {
                int alpha = (int) (0x90 + 0x6F * pulse);
                brackets(g, cx, cy, r + 1, 3, (alpha << 24) | PICKED);
            } else if (!knowsRock(asteroid)) {
                // a faint "?" so unknown asteroids read as unknown
                if (r >= 6) {
                    g.text(font, "?", cx - 2, cy - 4, 0x90C8D0DC, false);
                }
            }
            Long flash = flashes.get(asteroid.id());
            if (flash != null) {
                float t = (now - flash) / (float) FLASH_MS;
                if (t >= 1) {
                    flashes.remove(asteroid.id());
                } else {
                    renderFlash(g, cx, cy, r, t);
                }
            }
            if (asteroid.remaining() < asteroid.total()) {
                // progress bar
                int width = 2 * r;
                int barLeft = cx - r;
                int barTop = cy + r + 2;
                g.fill(barLeft, barTop, barLeft + width, barTop + 2, BAR_BACK);
                g.fill(barLeft, barTop, barLeft + Math.max(1, (int) (width * asteroid.remaining() / (double) asteroid.total())), barTop + 2, BAR_FILL);
            }
            int index = queue.indexOf(asteroid.id());
            if (index >= 0) {
                int edge = index == 0 && running ? ((int) (0x80 + 0x7F * pulse) << 24) | (QUEUED & 0xFFFFFF) : QUEUED;
                g.outline(cx - r, cy - r, 2 * r + 1, 2 * r + 1, edge);
                g.text(font, String.valueOf(index + 1), cx + r - 3, cy - r - 3, 0xFFFFFFFF, true);
            }
        }
    }

    // Lock-on brackets closing in, a scan line sweeping the rock, a ring of dots for the progress and data bits rising to the satellite
    private static void renderAnalyzing(GuiGraphicsExtractor g, int cx, int cy, int r, Analysis analysis, long now) {
        boolean stalled = analysis.stalled();
        int color = stalled ? STALLED : SCAN;
        if (stalled) {
            // blinks while the radar waits for power
            int alpha = (now / 400) % 2 == 0 ? 0xF0 : 0x50;
            brackets(g, cx, cy, r + 1, 3, (alpha << 24) | color);
        } else {
            float phase = (now % 1100) / 1100f;
            int inset = Math.round(3 * (1 - phase * phase));
            brackets(g, cx, cy, r + 1 + inset, 3, (0xF0 << 24) | color);

            int reach = r - 1;
            double sweep = Math.sin(now / 260.0);
            int dy = (int) Math.round(sweep * reach);
            int dir = Math.cos(now / 260.0) >= 0 ? -1 : 1;
            for (int trail = 2; trail >= 0; trail--) {
                int y = cy + dy + dir * trail;
                int w = (int) Math.sqrt(Math.max(0, reach * reach - (y - cy) * (y - cy)));
                int alpha = trail == 0 ? 0xE8 : trail == 1 ? 0x70 : 0x30;
                g.fill(cx - w, y, cx + w + 1, y + 1, (alpha << 24) | (trail == 0 ? SCAN_BRIGHT : SCAN));
            }

            for (int k = 0; k < 6; k++) {
                long clock = now / 16 + k * 37L;
                float life = (clock % 60) / 60f;
                int salt = (int) (clock / 60);
                float angle = (hash(k, salt, 5) & 255) / 255f * Mth.PI - Mth.PI;
                int bx = cx + Math.round(Mth.cos(angle) * r * 0.7f + Mth.sin(life * 9 + k) * 1.2f);
                int by = cy + Math.round(Mth.sin(angle) * r * 0.5f - life * (r + 12));
                int alpha = (int) (0xE0 * (1 - life));
                g.fill(bx, by, bx + 1, by + 1, (alpha << 24) | SCAN_BRIGHT);
            }
        }

        int dots = 24;
        int ring = r + 5;
        int lit = (int) (analysis.progress() * dots);
        for (int i = 0; i < dots; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / dots;
            int x = cx + (int) Math.round(Math.cos(angle) * ring);
            int y = cy + (int) Math.round(Math.sin(angle) * ring);
            int dot = i < lit ? (i == lit - 1 ? 0xFF000000 | SCAN_BRIGHT : 0xE0000000 | color) : 0x38FFFFFF;
            g.fill(x, y, x + 1, y + 1, dot);
        }
    }

    // a white flash over the rock and a ring running outwards, when the analysis is done
    private static void renderFlash(GuiGraphicsExtractor g, int cx, int cy, int r, float t) {
        int fade = (int) (0xD0 * (1 - t) * (1 - t));
        int body = Math.max(2, r - 1);
        for (int dy = -body; dy <= body; dy++) {
            int w = (int) Math.sqrt(Math.max(0, body * body - dy * dy));
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, (fade << 24) | 0xFFFFFF);
        }
        int ring = Math.round(r + 2 + t * 16);
        int alpha = (int) (0xF0 * (1 - t));
        for (int i = 0; i < 48; i++) {
            double angle = i * 2 * Math.PI / 48;
            int x = cx + (int) Math.round(Math.cos(angle) * ring);
            int y = cy + (int) Math.round(Math.sin(angle) * ring);
            g.fill(x, y, x + 1, y + 1, (alpha << 24) | (t < 0.4f ? 0xFFFFFF : SCAN_BRIGHT));
            g.fill(x + 1, y, x + 2, y + 1, ((alpha / 2) << 24) | SCAN_BRIGHT);
        }
    }

    private static void brackets(GuiGraphicsExtractor g, int cx, int cy, int reach, int len, int color) {
        int l = cx - reach;
        int rr = cx + reach;
        int t = cy - reach;
        int b = cy + reach;
        g.fill(l, t, l + len, t + 1, color);
        g.fill(l, t, l + 1, t + len, color);
        g.fill(rr - len + 1, t, rr + 1, t + 1, color);
        g.fill(rr, t, rr + 1, t + len, color);
        g.fill(l, b, l + len, b + 1, color);
        g.fill(l, b - len + 1, l + 1, b + 1, color);
        g.fill(rr - len + 1, b, rr + 1, b + 1, color);
        g.fill(rr, b - len + 1, rr + 1, b + 1, color);
    }

    /** @param x map-local mouse position */
    @Nullable
    SpaceMapPayload.AsteroidInfo asteroidAt(double x, double y) {
        SpaceMapPayload.AsteroidInfo best = null;
        double bestDist = Double.MAX_VALUE;
        for (var asteroid : asteroids) {
            double dx = x - screen(centerX(asteroid), viewX);
            double dy = y - screen(centerY(asteroid), viewY);
            double dist = dx * dx + dy * dy;
            int reach = screenRadius(asteroid) + 3;
            if (dist <= reach * reach && dist < bestDist) {
                best = asteroid;
                bestDist = dist;
            }
        }
        return best;
    }

    static List<Component> tooltip(SpaceMapPayload.AsteroidInfo asteroid) {
        List<Component> lines = new ArrayList<>();
        Identifier biome = asteroid.biome();
        lines.add(Component.translatable("gui.ender_laser_quarry.asteroid." + asteroid.size().key(),
            Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath())));
        lines.add(Component.translatable("gui.ender_laser_quarry.asteroid.remaining", NUMBERS.format(asteroid.remaining()),
            NUMBERS.format(asteroid.total()), Math.round(100.0 * asteroid.remaining() / Math.max(1, asteroid.total())))
            .withStyle(ChatFormatting.AQUA));
        if (!asteroid.analyzed()) {
            lines.add(Component.translatable(asteroid.stale() ? "gui.ender_laser_quarry.asteroid.stale" : "gui.ender_laser_quarry.asteroid.unknown")
                .withStyle(asteroid.stale() ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            return lines;
        }
        // top 6 rocks (all with ctrl, ores with shift: modpacks make both lists long), the biome's own rock before granite etc so it makes the list
        List<SpaceMapPayload.Part> rocks = asteroid.parts().stream().filter(part -> !part.ore())
            .sorted(Comparator.comparingInt((SpaceMapPayload.Part part) -> isOwnRock(part) ? 0 : 1)
                .thenComparing(SpaceMapPayload.Part::share, Comparator.reverseOrder()))
            .toList();
        int rocksShown = Minecraft.getInstance().hasControlDown() ? rocks.size() : Math.min(rocks.size(), ROCK_LINES);
        boolean perMillion = false;
        for (int i = 0; i < rocksShown; i++) {
            lines.add(partLine(rocks.get(i), ChatFormatting.GRAY));
            perMillion |= isPerMillion(rocks.get(i).share());
        }
        if (rocksShown < rocks.size()) {
            lines.add(Component.translatable("gui.ender_laser_quarry.asteroid.more_rocks", rocks.size() - rocksShown)
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        // stone and deepslate variants as one line
        Map<String, float[]> shares = new LinkedHashMap<>();
        Map<String, Block> names = new LinkedHashMap<>();
        for (var part : asteroid.parts()) {
            if (!part.ore()) {
                continue;
            }
            Block block = Block.stateById(part.stateId()).getBlock();
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String key = id.getNamespace() + ":" + id.getPath().replace("deepslate_", "");
            shares.computeIfAbsent(key, k -> new float[1])[0] += part.share();
            if (!names.containsKey(key) || !id.getPath().contains("deepslate")) {
                names.put(key, block);
            }
        }
        List<String> ores = new ArrayList<>(shares.keySet());
        ores.sort((a, b) -> Float.compare(shares.get(b)[0], shares.get(a)[0]));
        int shown = Minecraft.getInstance().hasShiftDown() ? ores.size() : Math.min(ores.size(), ORE_LINES);
        for (int i = 0; i < shown; i++) {
            lines.add(shareLine(shares.get(ores.get(i))[0], names.get(ores.get(i)), ChatFormatting.GOLD));
            perMillion |= isPerMillion(shares.get(ores.get(i))[0]);
        }
        if (shown < ores.size()) {
            lines.add(Component.translatable("gui.ender_laser_quarry.asteroid.more_ores", ores.size() - shown)
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (ores.isEmpty()) {
            lines.add(Component.translatable("gui.ender_laser_quarry.asteroid.no_ores").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (perMillion) {
            lines.add(Component.translatable("gui.ender_laser_quarry.asteroid.ppm_note").withStyle(ChatFormatting.DARK_GRAY));
        }
        // how much of the biome was measured, more in the background
        lines.add(Component.translatable(asteroid.samples() < asteroid.fullSamples()
                ? "gui.ender_laser_quarry.asteroid.survey_running" : "gui.ender_laser_quarry.asteroid.survey",
            asteroid.samples(), asteroid.fullSamples()).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    // the server only says analyzed while the measurements are there (a mod change throws them away)
    private static boolean knowsRock(SpaceMapPayload.AsteroidInfo asteroid) {
        return asteroid.analyzed() && !asteroid.parts().isEmpty();
    }

    private static Component partLine(SpaceMapPayload.Part part, ChatFormatting color) {
        return shareLine(part.share(), Block.stateById(part.stateId()).getBlock(), color);
    }

    // under 0.01 % in blocks per million, else the rarest ores read 0.000 %
    private static boolean isPerMillion(float share) {
        return share < 0.0001f;
    }

    private static Component shareLine(float share, Block block, ChatFormatting color) {
        String percent;
        if (share >= 0.01f) {
            percent = String.format(Locale.ROOT, "%.0f%%", share * 100);
        } else if (!isPerMillion(share)) {
            percent = String.format(Locale.ROOT, "%.2f%%", share * 100);
        } else {
            long ppm = Math.round(share * 1_000_000.0);
            percent = ppm < 1 ? "<1 ppm" : ppm + " ppm";
        }
        return Component.literal(" " + percent + " ").withStyle(ChatFormatting.DARK_GRAY).append(block.getName().withStyle(color));
    }

    // endregion

    // region View (zoom + pan)

    double zoom() {
        return zoom;
    }

    private double viewSize() {
        return FIELD / zoom;
    }

    private int screen(double field, double viewStart) {
        return (int) Math.round((field - viewStart) * size / viewSize());
    }

    private int screenRadius(SpaceMapPayload.AsteroidInfo asteroid) {
        return (int) Math.round(radius(asteroid) * size / viewSize());
    }

    void scroll(double localX, double localY, double amount) {
        double anchorX = viewX + localX * viewSize() / size;
        double anchorY = viewY + localY * viewSize() / size;
        zoom = Mth.clamp(zoom * Math.pow(ZOOM_STEP, amount), 1, MAX_ZOOM);
        viewX = anchorX - localX * viewSize() / size;
        viewY = anchorY - localY * viewSize() / size;
        clampView();
    }

    /** @param dragX GUI pixels the mouse moved */
    void pan(double dragX, double dragY) {
        viewX -= dragX * viewSize() / size;
        viewY -= dragY * viewSize() / size;
        clampView();
    }

    private void clampView() {
        double max = FIELD - viewSize();
        viewX = Mth.clamp(viewX, 0, max);
        viewY = Mth.clamp(viewY, 0, max);
    }

    // endregion

    private static int centerX(SpaceMapPayload.AsteroidInfo asteroid) {
        return asteroid.x() * FIELD / AsteroidField.MAP_UNITS;
    }

    private static int centerY(SpaceMapPayload.AsteroidInfo asteroid) {
        return asteroid.y() * FIELD / AsteroidField.MAP_UNITS;
    }

    private static int radius(SpaceMapPayload.AsteroidInfo asteroid) {
        return switch (asteroid.size()) {
            case SMALL -> SMALL_RADIUS;
            case MEDIUM -> MEDIUM_RADIUS;
            case LARGE -> LARGE_RADIUS;
        };
    }

    private static int shade(int rgb, float light) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * light), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * light), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * light), 0, 255);
        return abgr(r, g, b);
    }

    // NativeImage is ABGR
    private static int abgr(int r, int g, int b) {
        return 0xFF000000 | (Mth.clamp(b, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(r, 0, 255);
    }

    static int hash(int x, int y, int salt) {
        int h = x * 374761393 + y * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    static float smoothNoise(float x, float y, int salt) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = (hash(ix, iy, salt) & 1023) / 1023f;
        float b = (hash(ix + 1, iy, salt) & 1023) / 1023f;
        float c = (hash(ix, iy + 1, salt) & 1023) / 1023f;
        float d = (hash(ix + 1, iy + 1, salt) & 1023) / 1023f;
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }
}

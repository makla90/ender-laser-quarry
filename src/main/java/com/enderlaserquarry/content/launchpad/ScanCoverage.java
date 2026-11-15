package com.enderlaserquarry.content.launchpad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scan order, ring by ring outwards, so the progress is a single int and stays valid when the radius changes.
 */
public final class ScanCoverage {
    private static final Map<Integer, ScanCoverage> CACHE = new ConcurrentHashMap<>();

    private final int radius;
    private final int[][] order;
    // [(dz + r) * size + (dx + r)]
    private final int[] indexOf;

    private ScanCoverage(int radius) {
        this.radius = radius;
        int size = radius * 2 + 1;

        List<int[]> offsets = new ArrayList<>(size * size);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                offsets.add(new int[] { dx, dz });
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(o -> Math.max(Math.abs(o[0]), Math.abs(o[1])))
            .thenComparingDouble(o -> Math.atan2(o[1], o[0])));

        order = offsets.toArray(new int[0][]);
        indexOf = new int[size * size];
        for (int i = 0; i < order.length; i++) {
            indexOf[(order[i][1] + radius) * size + (order[i][0] + radius)] = i;
        }
    }

    public static ScanCoverage of(int radius) {
        return CACHE.computeIfAbsent(radius, ScanCoverage::new);
    }

    public int radius() {
        return radius;
    }

    public int total() {
        return order.length;
    }

    public int[] offset(int index) {
        return order[index];
    }

    // -1 if out of range
    public int indexOf(int dx, int dz) {
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
            return -1;
        }
        int size = radius * 2 + 1;
        return indexOf[(dz + radius) * size + (dx + radius)];
    }
}

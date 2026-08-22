package com.entityfix.cache;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * A bounded primitive long-to-long version map.
 *
 * <p>Used for chunk-section topology versions. The bound guarantees memory
 * behavior stays predictable even in enormous explored worlds.
 *
 * <p><b>Safety contract:</b> when the bound is exceeded the map is cleared.
 * Because cleared lookups return 0 and stored versions are always &gt;= 1,
 * a reset can only ever cause a version <i>mismatch</i> for consumers, which
 * is the conservative (recompute) direction. It can never falsely validate
 * stale cached data.
 *
 * <p>This class is intentionally free of Minecraft dependencies so it can be
 * unit tested in isolation. Single-threaded by design (server thread only).
 */
public final class BoundedVersionMap {
    private final Long2LongOpenHashMap map;
    private final int maxEntries;
    private long resets;

    public BoundedVersionMap(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.maxEntries = maxEntries;
        this.map = new Long2LongOpenHashMap(1024);
    }

    /** Current version for {@code key}; 0 if absent. */
    public long get(long key) {
        return map.get(key);
    }

    /** Atomically increments the version of {@code key} by one. */
    public void increment(long key) {
        if (map.size() >= maxEntries) {
            map.clear();
            resets++;
        }
        map.put(key, map.get(key) + 1L);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    /** Number of times the bound forced a reset (diagnostics). */
    public long resets() {
        return resets;
    }

    public void clear() {
        map.clear();
    }
}

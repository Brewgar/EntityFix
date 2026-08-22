package com.entityfix.topology;

import com.entityfix.cache.BoundedVersionMap;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks a monotonically increasing "topology version" per chunk section.
 *
 * <p>A section's version is bumped whenever a pathfinding-relevant block state
 * changes inside it (see {@link TopologyRelevance}). Consumers (e.g. the path
 * cache) store the version observed when a result was computed and compare it
 * later: any mismatch means the terrain answer may have changed and the cached
 * result must be recomputed.
 *
 * <p>Memory behavior is bounded per world via {@link BoundedVersionMap}; a
 * bound reset can only cause extra recomputation, never stale reuse.
 *
 * <p>All methods are intended to be called from the server thread only.
 */
public final class TopologyTracker {
    private TopologyTracker() {}

    /** Bounded per-world section table size (~256k sections = 64k chunks). */
    public static final int MAX_SECTIONS = 1 << 18;

    private static final Map<World, BoundedVersionMap> WORLDS = new HashMap<>();

    public static void bump(ServerWorld world, BlockPos pos) {
        WORLDS.computeIfAbsent(world, w -> new BoundedVersionMap(MAX_SECTIONS))
                .increment(sectionKey(pos));
    }

    /** Current topology version of the section containing {@code pos}. */
    public static long version(ServerWorld world, BlockPos pos) {
        BoundedVersionMap map = WORLDS.get(world);
        return map == null ? 0L : map.get(sectionKey(pos));
    }

    /** Current topology version of an already-packed chunk section key. */
    public static long sectionVersion(ServerWorld world, long packedSection) {
        BoundedVersionMap map = WORLDS.get(world);
        return map == null ? 0L : map.get(packedSection);
    }

    public static int entryCount(ServerWorld world) {
        BoundedVersionMap map = WORLDS.get(world);
        return map == null ? 0 : map.size();
    }

    /** Called on server stop / world unload to release per-world tables. */
    public static void clearAll() {
        WORLDS.clear();
    }

    private static long sectionKey(BlockPos pos) {
        return ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }
}

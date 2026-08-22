package com.entityfix.api;

import com.entityfix.topology.TopologyTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small public API for mod developers integrating with EntityFix.
 *
 * <p>Keep this surface minimal. Internal caches and data structures are not
 * exposed; mods describe intent ("this entity must stay active", "this region
 * changed") rather than manipulating internals.
 */
public final class EntityFixApi {
    private EntityFixApi() {}

    /** Entity types that must never be put to sleep by future AI-sleeping modules. */
    private static final Set<Class<?>> ALWAYS_ACTIVE =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Marks an entity type as always active. EntityFix will never skip any AI
     * work for instances of this type (or its subclasses).
     */
    public static void registerAlwaysActive(Class<?> entityType) {
        ALWAYS_ACTIVE.add(entityType);
    }

    /** @return true if the given entity type is registered as always active. */
    public static boolean isAlwaysActive(Class<?> entityType) {
        Class<?> c = entityType;
        while (c != null) {
            if (ALWAYS_ACTIVE.contains(c)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /**
     * Reports that terrain or state relevant to pathfinding changed around
     * {@code center}. Invalidates all cached path decisions that could depend
     * on blocks within roughly {@code radius} blocks.
     */
    public static void invalidateRegion(ServerWorld world, BlockPos center, int radius) {
        int sections = Math.max(0, radius >> 4);
        BlockPos min = center.add(-(sections << 4), -(sections << 4), -(sections << 4));
        BlockPos max = center.add((sections << 4), (sections << 4), (sections << 4));
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            TopologyTracker.bump(world, pos);
        }
    }

    /**
     * Reserved for the AI sleeping subsystem (not yet enabled). Currently a
     * no-op that is safe to call from any mod version.
     */
    public static void invalidateEntity(net.minecraft.entity.mob.MobEntity entity) {
        // no-op until ai_sleep ships; kept so integrations compile forward.
    }
}

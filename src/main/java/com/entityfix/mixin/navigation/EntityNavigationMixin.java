package com.entityfix.mixin.navigation;

import com.entityfix.EFMod;
import com.entityfix.profiling.EFStats;
import com.entityfix.topology.TopologyTracker;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-navigation path reuse with event-driven invalidation.
 *
 * <p>Vanilla bottleneck: mobs recompute A* paths to an unchanged destination
 * on a fixed cadence even when target, drift and terrain are all unchanged.
 * The recomputed path is usually identical, so the computation is redundant.
 *
 * <p>Invalidation (any one triggers recomputation): target/range changed,
 * section topology version mismatch, drift &gt; {@link #DRIFT_LIMIT} blocks,
 * TTL expired, or the cached instance is still an active traversal.
 *
 * <p>Safety: strictly per-navigation cache; Path instances are never shared
 * between entities. Disabled by default (SAFE mode).
 */
@Mixin(EntityNavigation.class)
public abstract class EntityNavigationMixin {

    @Shadow
    protected MobEntity entity;

    @Shadow
    @org.jetbrains.annotations.Nullable
    protected Path currentPath;

    /** Max tolerated drift (blocks) since the cached computation. */
    @Unique
    private static final int DRIFT_LIMIT = 2;

    @Unique
    private Path entityfix$cachedPath;

    @Unique
    private BlockPos entityfix$cachedTarget;

    @Unique
    private int entityfix$cachedDistance = -1;

    @Unique
    private long entityfix$cachedTopologyVersion;

    // ------------------------------------------------------------------
    // findPathTo(BlockPos, int)
    // ------------------------------------------------------------------

    @Inject(method = "findPathTo(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At("HEAD"), cancellable = true)
    private void entityfix$findPathToBlock(BlockPos target, int distance,
                                           CallbackInfoReturnable<Path> cir) {
        if (!EFMod.config().effective(EFMod.config().path_cache, true)) {
            return;
        }
        ServerWorld world = entityfix$serverWorld();
        if (world == null) {
            return;
        }
        BlockPos immutableTarget = target.toImmutable();
        if (entityfix$isCacheHit(world, immutableTarget, distance)) {
            EFStats.inc(EFStats.Counter.PATH_CACHE_HITS);
            Path cached = entityfix$cachedPath;
            if (cached != null) {
                // Reset traversal state so the reused path behaves like a
                // fresh vanilla result (vanilla paths start at index 0).
                cached.setCurrentNodeIndex(0);
                cir.setReturnValue(cached);
            } else {
                // Negative (unreachable) result: serve the cached "no path"
                // answer instead of re-running A*.
                cir.setReturnValue(null);
            }
            return;
        }
        EFStats.inc(EFStats.Counter.NAV_PATH_CALLS);
        EFStats.inc(EFStats.Counter.PATH_CACHE_MISSES);
    }

    @Inject(method = "findPathTo(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At("TAIL"))
    private void entityfix$storePathToBlock(BlockPos target, int distance,
                                            CallbackInfoReturnable<Path> cir) {
        if (EFMod.config().effective(EFMod.config().path_cache, true)) {
            entityfx$storeResult(target.toImmutable(), distance, cir.getReturnValue());
        }
    }

    // ------------------------------------------------------------------
    // findPathTo(double, double, double, int) - counting only.
    // Wandering-style goals reach navigation through this coordinate
    // overload (verified live: it does not delegate to the BlockPos
    // overload), so it must be counted separately for honest baselines.
    // ------------------------------------------------------------------

    @Inject(method = "findPathTo(DDDI)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At("HEAD"))
    private void entityfix$countFindPathToCoords(double x, double y, double z, int distance,
                                                 CallbackInfoReturnable<Path> cir) {
        if (EFStats.isEnabled()) {
            EFStats.inc(EFStats.Counter.NAV_PATH_CALLS);
            EFStats.inc(EFStats.Counter.PATH_CACHE_MISSES);
            COORD_PATH_START.set(System.nanoTime());
        }
    }

    @Inject(method = "findPathTo(DDDI)Lnet/minecraft/entity/ai/pathing/Path;",
            at = @At("TAIL"))
    private void entityfix$timeFindPathToCoords(double x, double y, double z, int distance,
                                                CallbackInfoReturnable<Path> cir) {
        Long start = COORD_PATH_START.get();
        if (start != null) {
            EFStats.addTimer(EFStats.Timer.NAV_PATH, System.nanoTime() - start);
            COORD_PATH_START.remove();
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    @Unique
    private void entityfx$storeResult(BlockPos target, int distance, Path result) {
        entityfix$cachedPath = result;
        entityfix$cachedTarget = target;
        entityfix$cachedDistance = distance;
        ServerWorld world = entityfix$serverWorld();
        if (world != null) {
            entityfix$cachedTopologyVersion =
                    TopologyTracker.version(world, entity.getBlockPos());
            entityfix$cachedTick = world.getTime();
            entityfix$cachedOrigin = entity.getBlockPos().toImmutable();
        } else {
            // Without a server world we cannot version the entry; leave the
            // origin/tick at defaults so the hit check fails safely.
            entityfix$cachedTopologyVersion = 0L;
            entityfix$cachedTick = 0L;
            entityfix$cachedOrigin = null;
        }
    }

    @Unique
    private boolean entityfix$isCacheHit(ServerWorld world, BlockPos target, int distance) {
        if (entityfix$cachedTarget == null || !entityfix$cachedTarget.equals(target)) {
            return false;
        }
        if (entityfix$cachedDistance != distance) {
            return false;
        }
        // Never hand out a positive path that is currently being followed;
        // only reuse once vanilla has replaced or finished with it. Negative
        // results have no traversal state and are exempt.
        if (entityfix$cachedPath != null
                && currentPath == entityfix$cachedPath
                && !entityfix$cachedPath.isFinished()) {
            return false;
        }
        long now = world.getTime();
        if (now - entityfix$cachedTick > EFMod.config().path_cache_ttl_ticks) {
            return false;
        }
        BlockPos origin = entity.getBlockPos();
        if (entityfix$cachedOrigin == null
                || Math.abs(origin.getX() - entityfix$cachedOrigin.getX()) > DRIFT_LIMIT
                || Math.abs(origin.getY() - entityfix$cachedOrigin.getY()) > DRIFT_LIMIT
                || Math.abs(origin.getZ() - entityfix$cachedOrigin.getZ()) > DRIFT_LIMIT) {
            return false;
        }
        if (TopologyTracker.version(world, origin) != entityfix$cachedTopologyVersion) {
            EFStats.inc(EFStats.Counter.PATH_CACHE_INVALIDATIONS);
            return false;
        }
        return true;
    }

    @Unique
    private ServerWorld entityfix$serverWorld() {
        if (entity == null || entity.getWorld() == null) {
            return null;
        }
        return entity.getWorld() instanceof ServerWorld server ? server : null;
    }

    @Unique
    private long entityfix$cachedTick;

    @Unique
    private BlockPos entityfix$cachedOrigin;

    @Unique
    private static final ThreadLocal<Long> COORD_PATH_START = new ThreadLocal<>();
}

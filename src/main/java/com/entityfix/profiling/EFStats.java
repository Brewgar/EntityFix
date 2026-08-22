package com.entityfix.profiling;

/**
 * Low-overhead statistics layer for entity simulation hot paths.
 *
 * <p>Design constraints (see ARCHITECTURE.md):
 * <ul>
 *   <li>All counters live in flat primitive arrays indexed by enum ordinal -
 *       no boxing, no map lookups, no allocation on the recording path.</li>
 *   <li>Recording is gated by a single static boolean so a disabled profiler
 *       costs one predictable branch.</li>
 *   <li>The server simulation that touches these counters runs on the server
 *       thread; counters are not atomics. Values are read from commands that
 *       also execute on the server thread, so no synchronization is needed.
 *       This is a deliberate, documented single-thread assumption.</li>
 * </ul>
 */
public final class EFStats {
    private EFStats() {}

    /** Monotonic event counters. */
    public enum Counter {
        /** Sensor#sense invocations. */
        SENSOR_SENSES,
        /** Brain#tick invocations (brain-having mobs). */
        BRAIN_TICKS,
        /** EntityNavigation path computation requests. */
        NAV_PATH_CALLS,
        /** GoalSelector#tick invocations. */
        GOAL_SELECTOR_TICKS,
        /** Path-relevant block changes recorded by the topology tracker. */
        TOPOLOGY_BUMPS,
        /** Block changes inspected by the topology tracker. */
        TOPOLOGY_CHANGES_SEEN,
        /** Cached path reuses. */
        PATH_CACHE_HITS,
        /** Path computations that could not be served from cache. */
        PATH_CACHE_MISSES,
        /** Cached paths discarded due to invalidation. */
        PATH_CACHE_INVALIDATIONS
    }

    /** Cumulative duration timers, in nanoseconds. */
    public enum Timer {
        SENSOR_SENSE,
        NAV_PATH
    }

    private static volatile boolean enabled = true;
    private static final long[] COUNTERS = new long[Counter.values().length];
    private static final long[] TIMER_NANOS = new long[Timer.values().length];
    private static final long[] TIMER_CALLS = new long[Timer.values().length];

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void inc(Counter counter) {
        if (enabled) {
            COUNTERS[counter.ordinal()]++;
        }
    }

    public static void add(Counter counter, long amount) {
        if (enabled) {
            COUNTERS[counter.ordinal()] += amount;
        }
    }

    public static long get(Counter counter) {
        return COUNTERS[counter.ordinal()];
    }

    /** Accumulate a measured duration directly. */
    public static void addTimer(Timer timer, long nanos) {
        if (enabled) {
            TIMER_NANOS[timer.ordinal()] += nanos;
            TIMER_CALLS[timer.ordinal()]++;
        }
    }

    /** Begin timing. Returns a start stamp to hand to {@link #end}. */
    public static long begin(Timer timer) {
        return enabled ? System.nanoTime() : 0L;
    }

    /** End timing started by {@link #begin}. No-op cost when disabled. */
    public static void end(Timer timer, long startNanos) {
        if (enabled) {
            TIMER_NANOS[timer.ordinal()] += System.nanoTime() - startNanos;
            TIMER_CALLS[timer.ordinal()]++;
        }
    }

    public static long timerNanos(Timer timer) {
        return TIMER_NANOS[timer.ordinal()];
    }

    public static long timerCalls(Timer timer) {
        return TIMER_CALLS[timer.ordinal()];
    }

    /** Reset all counters and timers (used by /entityfix profile clear). */
    public static void clear() {
        java.util.Arrays.fill(COUNTERS, 0L);
        java.util.Arrays.fill(TIMER_NANOS, 0L);
        java.util.Arrays.fill(TIMER_CALLS, 0L);
    }
}

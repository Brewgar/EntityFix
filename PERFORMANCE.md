# PERFORMANCE & BENCHMARK METHODOLOGY

## First runtime baselines (headless dev-server, RCON-driven, Aug 2026)

Environment: singleplayer-style dedicated dev server, force-loaded spawn area,
~52 live entities (husks + persisted villagers/animals), BALANCED mode.

Functional validation achieved:

| Instrumented hot path | Evidence |
|---|---|
| `Sensor#tick` | 45,657 invocations counted; cumulative timer **~1.0-1.4 µs/call** - recording overhead negligible |
| `Brain#tick` | 5,073 brain-mob ticks counted |
| `EntityNavigation#findPathTo(...)` | **1,060 path computations in ~50 s** for 52 wandering mobs (~21/s) - this is the redundant-work pool the path cache targets |
| Topology tracking (`World#setBlockState`) | Block changes -> section version bumps verified 1:1 (`setblock` -> "Changed the block" -> bumps recorded); decoration-only updates filtered |

Operational notes captured for future benchmarking:

- With no players online the server idles and unloads chunks; benchmark
  scenarios must `forceload` the measurement area first.
- Forced chunks persist across restarts; repeated `forceload add` of an
  already-forced area reports "no chunks marked".
- Mobs spawned high above ground die from fall damage before contributing AI
  load - spawn near terrain height.
- RCON clients must pace commands; rapid loops desync the response stream.

## Path cache: first live measurements (sealed-bed scenario)

Workload: 24 villagers at night, 12 beds sealed inside an unreachable stone
room (forces repeated identical-target path attempts), BALANCED mode,
`randomTickSpeed 0` to stabilize topology.

Result: **20 cache hits, 10 topology-driven invalidations, stable shutdown.**
End-to-end functionality proven (store -> key validation -> reuse -> vanilla
consumes the reused/negative result safely).

Hit rate remained low (~2%). Attributed causes, to be addressed before
default-enabling:

1. Bed-seeking navigation appears to route largely through the coordinate
   overload (`findPathTo(double,double,double,int)`), which is currently
   count/timer-only - extending caching there is the main lever.
2. Vanilla retry cadence for failed bed access can exceed the 40-tick TTL,
   expiring otherwise-valid negative entries.
3. Uncontrolled worlds churn topology versions constantly; real-world hit
   rates need the controlled scenarios B-F below.

## What we measure

Primary metric: **main-thread CPU time eliminated**, expressed through:

- average / median / p95 / p99 MSPT
- entity AI CPU time (profiler)
- sensor CPU time (`EFStats.Timer.SENSOR_SENSE`)
- pathfinding CPU time (`EFStats.Timer.NAV_PATH`) + cache hit rates
- memory usage of EntityFix structures (`/entityfix status`: tracked sections,
  cache sizes)

FPS is explicitly not a primary metric; this is a server-simulation mod.

## Scenarios

| ID | Scenario | Purpose |
|---|---|---|
| A | Ordinary survival world, 10-50 entities | ~zero regression guard |
| B | 200 passive mobs | entity tick baseline |
| C | 500 hostile mobs | target search / pathfinding load |
| D | 500 villagers, trading-hall geometry, beds+workstations | brain/sensor/POI load |
| E | 1000+ mixed entities | main stress test |
| F | Many entities navigating identical geometry repeatedly | path-cache hit rate validation |

## Comparison matrix

Every published number compares:

```
Vanilla  |  Lithium  |  EntityFix  |  Lithium + EntityFix
```

under identical hardware, JVM flags, world file and random seed. Runs are
5+ minutes each after a warm-up phase; report median of 3 runs.

## Claim policy

Only claim what a reproducible benchmark demonstrates, e.g. "reduces
pathfinding CPU time by X% in scenario F". Never extrapolate to TPS or FPS
numbers that were not measured.

## Current status

No benchmark numbers are published yet: the profiling layer exists precisely
to produce them. Baseline capture (Phase 2 completion) requires in-game runs;
the instrumentation above is what those baselines will be built from.

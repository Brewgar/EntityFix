# EntityFix

**Entity performance optimizations for Minecraft 1.20.1 (Fabric)**

EntityFix reduces the main-thread CPU cost of entity simulation by eliminating
*redundant* computation - not by throttling AI, skipping ticks, or lowering
simulation accuracy.

> Minecraft should not repeatedly solve a problem whose inputs are unchanged.

Instead of the vanilla pattern:

```
POLL -> CALCULATE -> POLL -> CALCULATE -> POLL -> CALCULATE
```

EntityFix turns hot paths into:

```
STATE CHANGE -> INVALIDATE -> CALCULATE -> CACHE -> REUSE
```

## Status

| Module | State | Default |
|---|---|---|
| Statistics / profiling layer (`EFStats`) | Implemented + **runtime-validated** (~1 µs/call overhead measured) | on |
| `/entityfix` diagnostics commands | Implemented + **runtime-validated** via RCON | op level 2 |
| Topology versioning (path-relevant block-change invalidation) | Implemented + **runtime-validated** (setblock -> bump verified 1:1) | on |
| Per-navigation path cache with event-driven invalidation | Implemented + **live hits confirmed** (sealed-bed scenario); hit rate optimization ongoing (see PERFORMANCE.md); off pending scenario benchmarks | off (SAFE) |
| Sensor result caching | Planned (Phase 4) - sensor workload now measurable | - |
| AI sleeping | Planned (Phase 5) | - |
| Cross-entity path reuse | Planned (Phase 8, only if profiling justifies) | - |

## Installation

- Minecraft 1.20.1, Fabric Loader >= 0.16.0, Fabric API
- Works client-side, in singleplayer, and on dedicated servers
- Designed to coexist with Sodium / Lithium / FerriteCore / C2ME

## Commands (op level 2)

```
/entityfix status          # feature state + counters summary
/entityfix debug           # timer totals and world entity counts
/entityfix profile start   # enable the stats layer
/entityfix profile stop    # disable it (near-zero overhead afterwards)
/entityfix profile clear   # reset counters
/entityfix profile dump    # full counter table
```

## Configuration

`config/entityfix.json` - see [CONFIG.md](CONFIG.md). Default mode is `SAFE`
(diagnostics and bookkeeping only, zero behavior change).

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - design, decision rules, mixin map
- [COMPATIBILITY.md](COMPATIBILITY.md) - Lithium & ecosystem analysis
- [CONFIG.md](CONFIG.md) - every option explained
- [PERFORMANCE.md](PERFORMANCE.md) - benchmark methodology

## Building

Requires a JDK 17 (`gradlew` uses whatever `JAVA_HOME` points at). The
repository ships `.setup/` helpers that download a local JDK 17 + Gradle into
`.tools/`, so the system toolchain does not matter:

```powershell
powershell -ExecutionPolicy Bypass -File .setup/download.ps1   # once
powershell -ExecutionPolicy Bypass -File .setup/build.ps1 build
powershell -File .setup/build.ps1 test
```

With any JDK 17 on PATH this is just:

```
./gradlew build        # produces build/libs/entityfix-<version>.jar
./gradlew test
./gradlew runServer    # headless dev-server smoke test (run/eula.txt)
```

The generated mod jar lands in `build/libs/`.

## License

MIT

# CONFIGURATION

File: `config/entityfix.json`. Created with defaults on first launch.

Modes map to explicit feature sets - they are not vague "more optimization"
dials:

| Mode | Meaning |
|---|---|
| `SAFE` (default) | Diagnostics + bookkeeping only. Zero behavior change. |
| `BALANCED` | Enables conservative redundant-work elimination (path cache). |
| `EXPERIMENTAL` | Reserved; currently identical to BALANCED. |

Explicit flags always win over mode defaults.

## Options

### `mode`
Preset selector: `SAFE` | `BALANCED` | `EXPERIMENTAL`.
Default `SAFE`: correctness first.

### `stats` (default true)
Master switch of the low-overhead statistics layer backing `/entityfix`.
Overhead when enabled: a few array increments per sensor sense / brain tick /
path request (measured in single-digit nanoseconds each). Disable for
absolutely zero instrumentation cost.

### `topology_tracking` (default true)
Chunk-section version bumping on pathfinding-relevant block changes
(`solid/fluid/door/gate`-class changes). Pure bookkeeping; never changes block
update outcomes. Cost is one relevance check per block change plus a primitive
map increment; decoration-only updates (redstone wire, torches, note blocks)
are filtered without touching the map.

### `path_cache` (default false)
Reuse a navigation's last computed path while target, drift, terrain topology
version and TTL are unchanged. Eliminates redundant A* recomputations - the
dominant repeated cost for mobs that repath to stationary goals.
Set true explicitly, or run mode `BALANCED`.
Compatibility implications: per-entity caches only; no cross-entity sharing
yet; unknown modded navigation classes fall back to vanilla automatically.

### `path_cache_ttl_ticks` (default 40)
Maximum age (ticks) of a reused path. Bounds staleness from unforeseen inputs;
lower = more conservative, higher = more hits.

### `ai_sleep` (default false, not implemented yet)
Reserved schema slot for the future AI-sleeping subsystem so config stays
forward compatible.

### `verbose_debug` (default false)
Extra logging during development sessions. Normal operation never logs.

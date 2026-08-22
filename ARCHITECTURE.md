# ARCHITECTURE

## Core principle

Every optimization must answer (spec §44):

1. What expensive operation are we eliminating?
2. Why is the operation redundant?
3. What exact state changes its answer?
4. How do we detect those changes?
5. Why is the optimization safe?
6. What happens for unsupported/custom behavior?
7. How much CPU time does it save?
8. What memory overhead does it introduce?

If any answer is unclear, the optimization does not ship. Missed optimizations
are acceptable; stale results are not.

## Systems

### 1. Statistics layer (`profiling/EFStats`)

Flat primitive arrays indexed by enum ordinal. No boxing, no maps, no
allocation on the recording path, single static boolean gate. Single-thread
assumption is deliberate and documented: counters are written and read on the
server thread only.

Mixin instrumentation points:
- `Sensor#sense` HEAD/TAIL - invocation counts + cumulative time
- `Brain#tick` HEAD - brain-mob invocation counts
- `EntityNavigation#findPathTo*` - request counts + cache hit/miss accounting

### 2. Topology versioning (`topology/`, mixin `ServerWorldMixin`)

Per-world `BoundedVersionMap` (chunk-section key -> long version). Bumped at
HEAD of `ServerWorld#setBlockState` when `TopologyRelevance.isPathRelevant`
says the change can affect path geometry:

```
old.blocksMovement() || new.blocksMovement()
    || fluid present on either side
```

This covers solid<->air, doors/trapdoors/gates (their material blocks
movement), fences/walls, water/lava flow - while filtering decoration-only
updates. Checking at HEAD is required because only there the previous state is
readable; a bump on an update that later fails is the safe direction.

Bound safety contract (`cache/BoundedVersionMap`, unit tested): overflow clears
the map, and because cleared reads return 0 while stored versions are >= 1, a
reset can only cause recomputation - never stale validation.

Consumers compare `version(world, origin)` against the version captured when a
result was computed. Any mismatch invalidates.

### 3. Path cache (`mixin/navigation/EntityNavigationMixin`)

Per-navigation-instance cache around `EntityNavigation#findPathTo(BlockPos,int)`.

Eliminated work: repeated A* searches for an unchanged destination.

Redundancy argument: vanilla mobs repath to stationary goals on fixed cadences
(every ~10-20 ticks) even when target block, mob drift, and terrain are all
unchanged; the recomputed path is typically identical geometry.

Detected inputs & invalidation:
- target BlockPos or range changed -> recompute
- topology version mismatch at mob position -> recompute
- drift > 2 blocks from computation origin -> recompute
- TTL exceeded (`path_cache_ttl_ticks`) -> recompute
- cached instance still an active traversal (`currentPath == cached &&
  !isFinished()`) -> never reused concurrently with itself

On reuse the node index is reset to 0 so the path behaves exactly like a fresh
vanilla result. Negative results (unreachable) are also cached - repeatedly
proving unreachability is precisely the redundant work this targets - and are
invalidated by the same rules.

Unsupported behavior: the cache is disabled entirely unless configured on; any
navigation whose entity/world is absent falls through to vanilla. Cross-entity
Path sharing is deliberately NOT implemented yet (mutable traversal state).

### Fallback rule

Every module checks its config gate first and degrades to pure vanilla
behavior. Nothing in EntityFix throws into the server loop by design; mixins
use `defaultRequire = 1` so remap failures fail loudly at build/load time
rather than corrupting state silently.

## Mixin map

| Mixin | Target | Purpose | Risk |
|---|---|---|---|
| `WorldMixin` | `World#setBlockState(BlockPos,BlockState,int)` HEAD (server-side filtered; ServerWorld declares no override in 1.20.1 - verified) | topology bumps | bookkeeping only |
| `ai/SensorMixin` | `Sensor#tick(ServerWorld,LivingEntity)` HEAD/TAIL (the final interval wrapper; `sense` is protected abstract - verified) | profiling | read-only |
| `ai/BrainMixin` | `Brain#tick` HEAD | profiling | read-only |
| `navigation/EntityNavigationMixin` | `findPathTo(BlockPos,int)` HEAD/TAIL | path cache | gated, conservative |

## Deliberate non-goals (for now)

- Fixed-frequency AI throttling (spec §6) - alters behavior.
- Async pathfinding / background world access (spec §34).
- Duplicating vanilla's `PathNodeTypeCache` (block-state to PathNodeType
  classification is already cached by vanilla since 1.19.4) or Lithium's
  collision/entity-section work (see COMPATIBILITY.md).

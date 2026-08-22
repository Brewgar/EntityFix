# COMPATIBILITY

## Ecosystem analysis (Fabric 1.20.1)

Verified against Lithium `mc1.20.1-0.11.2` mixin config documentation.

| Area | Vanilla | Lithium | EntityFix opportunity |
|---|---|---|---|
| Block-state -> PathNodeType classification | Cached by vanilla since 1.19.4 (`PathNodeTypeCache`) | `ai.pathing` speeds the remaining lookup + chunk access | **None - do not duplicate.** |
| POI search (villager workstations/beds) | Streamed, expensive | `ai.poi`, `ai.poi.tasks`, `ai.sensor.secondary_poi` | Revisit only after own profiling shows residual cost. |
| Brain task memory prerequisite checks | Re-checked every launch attempt | `ai.task.memory_change_counting` tracks memory changes to skip checks | **Covered - skip.** |
| Nearby-entity tracking for some goals | Poll every tick | `ai.nearby_entity_tracking.goals` (event-based) for a goal subset | Sensors themselves are NOT covered -> Phase 4 candidate. |
| Collision / entity section storage | Section-based | `entity.collisions`, `util.entity_section_position`, alloc passes | **Do not duplicate.** |
| Redundant A* recomputation | Repaths on fixed cadence regardless of input change | Makes each search faster; never skips identical searches | **Gap -> EntityFix path cache.** |
| Terrain change awareness | None | None | **Gap -> topology versioning.** |
| Entity simulation profiling | F3/debug is coarse | None | **Gap -> EFStats + commands.** |
| Entity AI sleeping | N/A | Only *block entities* sleep (`world.block_entity_ticking.sleeping.*`) | Entity-side sleeping remains open (Phase 5), highest risk. |

## Per-feature conflict matrix

| Feature | Vanilla path | Lithium path | EntityFix path | Conflict risk | Strategy |
|---|---|---|---|---|---|
| Topology bumps | `ServerWorld#setBlockState` | not modified | HEAD injection, read-only | Very low | independent injection |
| Sensor profiling | `Sensor#sense` | not modified (except secondary_poi sensor internals) | HEAD/TAIL counters | Low | independent injection |
| Brain profiling | `Brain#tick` | `ai.task*` modify task internals, not `tick()` entry | HEAD counter | Low | independent injection |
| Path cache | `EntityNavigation#findPathTo(BlockPos,int)` | `ai.pathing` optimizes node/chunk access *inside* the call | caches whole-call results outside the call | Low: orthogonal layers (Lithium accelerates misses) | independent injection; benchmark combo required before release |

## Testing matrix

Before any release build, run:

```
Vanilla
Vanilla + EntityFix
Lithium
Lithium + EntityFix
```

on benchmark scenarios B/C/D/E/F (see PERFORMANCE.md), checking MSPT and
behavioral equivalence.

## Modded content policy

- Unknown/custom entities: no special treatment today; all current features
  operate at vanilla choke points that modded entities also use, with
  conservative fallbacks.
- Future AI-sleeping module will default everything to "never sleep" unless
  proven safe or explicitly registered via `com.entityfix.api.EntityFixApi`.
- If any module detects an unsupported state it must fall back to the vanilla
  path for that case only - never crash, never corrupt.

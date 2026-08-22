# CONTRIBUTING

## Ground rules

1. **Profile before optimizing.** Every change to a hot path must be justified
   by `/entityfix profile dump` data from a real workload, and validated by a
   before/after measurement of the same workload.
2. **Correctness over numbers.** A missed optimization is acceptable; a stale
   result is not. When in doubt, invalidate / fall back to vanilla.
3. **No fixed-frequency throttling.** Skipping AI every N ticks changes
   behavior and is out of scope for this project (see README philosophy).
4. **Answer the eight questions** in ARCHITECTURE.md for any new optimization,
   in the PR description.
5. **Hot paths stay primitive.** No allocation, boxing, streams or locking on
   per-tick recording paths; counters live in flat arrays (`EFStats`).

## Workflow

```powershell
powershell -File .setup/build.ps1 test     # unit tests
powershell -File .setup/build.ps1 build    # full build incl. mixin AP checks
```

Then validate in-game: client launch, singleplayer, dedicated server launch,
and the behavioral regression list (spec §39): mobs reach destinations,
acquire targets, villagers work/sleep, sleeping entities wake, custom entities
stay functional.

## Mixin rules

- One purpose per mixin class; document nontrivial injections.
- Prefer `@Inject` at stable points over redirects.
- Injection targets must fail loudly if remap breaks them (`defaultRequire=1`).
- Check COMPATIBILITY.md's matrix before touching paths Lithium also modifies.

# ADR 0001 — ECS with Sparse-Set Component Storage

- **Status:** Accepted
- **Date:** 2026-06-18
- **Deciders:** Engine architecture
- **Tags:** core, performance, data-oriented

## Context

The engine's stated goal is *cache-friendly memory layouts via data-oriented design*.
We must choose how game-object data and behaviour are modeled and stored. The choice
dominates cache behaviour, the per-frame budget, and how subsystems compose.

Options considered:

1. **OOP game-object tree** — a `GameObject` base class with polymorphic `Component`
   subclasses owned via pointers (classic Unity-like / UE actor model).
2. **Archetype ECS** — entities grouped by exact component set; components stored in
   per-archetype contiguous chunks (Unity DOTS / flecs-archetype style).
3. **Sparse-set ECS** — each component type has a dense array + a sparse index array
   mapping entity → dense slot (EnTT style).

## Decision

Adopt a **sparse-set ECS** (`engine/core/ecs`): entities are `index + generation`
handles; each component type owns a `SparseSet`-backed `ComponentPool` of dense,
contiguous data; systems iterate via `View`s; structural changes during iteration go
through a deferred command buffer.

## Rationale

- **Cache locality:** dense per-type arrays mean a system streams exactly the data it
  touches — prefetcher-friendly and auto-vectorizable. This directly serves the project goal.
- **No pointer chasing / no per-entity virtual dispatch:** unlike the OOP tree, which
  scatters objects across the heap and pays vtable indirection per entity.
- **Cheap add/remove & flexible composition** vs. archetype ECS: sparse sets handle
  frequent component add/remove without the archetype "fragmentation"/move cost. Archetypes
  win on multi-component iteration but pay on structural churn — our gameplay/physics churn
  components often, so sparse-set is the better default.
- **Implementation simplicity & debuggability** relative to a full archetype system.

## Consequences

- **Positive:** excellent single-component iteration, simple mental model, cheap
  structural changes, small hot components, easy parallelization across disjoint sets.
- **Negative / trade-offs:** multi-component joins are slightly less optimal than
  archetype chunk iteration; very large numbers of component *types* cost sparse-array
  memory. We mitigate joins by iterating the smallest pool and checking membership in the
  others, and may add an archetype fast-path later if profiling demands it.
- **Follow-ups:** deferred structural-change command buffer (avoids iterator
  invalidation); generation counters in handles (avoids stale-handle bugs);
  benchmark guardrails in `benchmarks/bench_ecs.cpp`.

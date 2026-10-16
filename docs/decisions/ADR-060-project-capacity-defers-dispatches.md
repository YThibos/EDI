# ADR-060: A dispatch beyond a project's capacity waits as deferred

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** U-03 adds `capacity: <n>` per project in `projects.yaml`: a dispatch beyond it leaves the task queued as deferred, not blocked, and is re-evaluated when a worker reaches ready or cleanup; the default is uncapped.
It suits projects whose builds load the machine, such as Testcontainers suites.

**Decision:** A project's occupied places are its serving workers whose window is open and whose done has not been accepted (`WorkerPlaces.occupiedOn`).
`edi dispatch` on a full project records the dispatch with its deferral time, keeps the task queued, records `DispatchDeferred` with the counts and answers "Deferred <task>: <project> already runs <n> of its <capacity> workers".
Each supervision round launches deferred dispatches, oldest first, while their project has room (`LaunchDeferredDispatches`), with the reason "a worker place on its project came free".

**Motivation:** Deferral is state in the store, so it survives a restart and needs no listener; a ready worker frees its place within one round.

**Alternatives considered:**
- Refusing the dispatch - the primary would have to remember to dispatch it again.
- An after-commit listener on the worker's done - a second mechanism for what the round already does.

**Consequences:**
- Good: a capped project never runs more workers than its capacity.
- Bad: a freed place is used up to 30 seconds later.

**Revisit when:** Slice S5's cleanup frees places, or the captain wants a global cap across projects.

**Sources:** EDI rewrite specification, section 21.3 (U-03); `application.dispatch.DispatchTask`, `WorkerPlaces`, `LaunchDeferredDispatches`, `adapter.registry.ProjectEntry`.

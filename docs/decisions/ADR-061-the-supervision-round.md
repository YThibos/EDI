# ADR-061: One supervision round every 30 seconds keeps the time-based rules of slice S3

**Date:** 16/10/2026

**Status:** Superseded by ADR-077

**Kind:** Design

**Context and constraints:** Slice S3 brings three rules that depend on time: a steer unreceived after five minutes raises one event (I-27), a launch with no hook within two minutes is a failed launch reported once (section 11.2), and deferred dispatches start when capacity frees (ADR-060); mechanical checks run long (ADR-059).
Every time read goes through the `Clock` port (ADR-009).

**Decision:** `SupervisionRound` runs every 30 seconds (`edi.supervision.round`), each part guarded so one failing part does not stop the others: `NoticeOverdueSteers`, `NoticeMissedLaunches`, `LaunchDeferredDispatches`, and starting each pending mechanical check once on a virtual thread.
`NoticeMissedLaunches` records `WorkerLaunchFailed` once for a worker with neither a `SessionStart` nor a `UserPromptSubmit` hook two minutes after its launch, with what the pane probe reads in its window, such as `dialog-trust`.
Each rule is a use case that tests call directly with a controllable clock.

**Motivation:** The rules are state in the store, so a restart only delays them, and one small round replaces the old harness's watcher.

**Alternatives considered:**
- A timer per message and per launch - lost on a restart.
- After-commit listeners with a relay (ADR-011) - none of these rules follows a commit; they follow the clock.

**Consequences:**
- Good: AT-27 and the launch watch are tested in milliseconds.
- Bad: every rule reacts within 30 seconds of its time, not exactly at it.

**Revisit when:** Slice S4 adds the health rules and wakes, which join the same round.

**Sources:** EDI rewrite specification, sections 11.2, 12.2 and 12.4 (I-27); ADR-009, ADR-011; `application.worker.SupervisionRound`, `NoticeOverdueSteers`, `NoticeMissedLaunches`, `application.dispatch.LaunchDeferredDispatches`, `EdiCoreApplication`.

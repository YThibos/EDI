# ADR-092: The store keeps deliveries, pipeline runs, clone refreshes and task costs

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 8.1 names a `Delivery` (forge, merge request URL and number, draft flag, head commit, state, observed at) and a `PipelineRun` (step, outcome, head commit, merge request URL), and gives a task its delivery mode.
Merge records store provider, host, path, number and URL, and a record whose parts do not rebuild its URL is refused (section 13.2).
Current state lives in tables and history in the event log (ADR-037); migrations are append-only Flyway files (ADR-010).

**Decision:** `V4__delivery.sql` adds the task's optional `delivery` column and four tables.
`delivery` keeps one row per task: provider, host, path, number, URL, the draft flag, head commit, state, the landed commit exactly when merged, and when EDI first and last saw it.
`pipeline_run` keeps the latest reading of each run by run id, with its task, status, active step, head and merge request.
`clone_refresh` keeps one row per merged task: project, base, the expected commit, when it was asked for, when it was verified, and when the clean-up after it was tried, which can only follow the verification.
`task_cost` keeps each cleaned-up task's worker tokens and, when known, its pipeline tokens.
`DeliveryStore`, `PipelineRunStore`, `CloneRefreshStore` and `TaskCostStore` are the only code that reads and writes them.

**Motivation:** Each table holds exactly the current state a use case needs, while every change to it is told by an event in the same transaction.

**Alternatives considered:**
- Keeping deliveries only as events - the ten-minute watch would replay the log to find open drafts.
- Several deliveries per task - one task delivers one branch in this slice.

**Consequences:**
- Good: a delivery row that was edited by hand is refused instead of being watched at the wrong address.
- Bad: a task's second delivery replaces its first; the first stays in the event log.

**Revisit when:** A task delivers to more than one repository, or follow-up branches get deliveries of their own.

**Sources:** EDI rewrite specification, sections 8.1, 8.2 and 13.2; ADR-010, ADR-037; `V4__delivery.sql`, `adapter.store.DeliveryStore`, `PipelineRunStore`, `CloneRefreshStore`, `TaskCostStore`, `TaskStore`.

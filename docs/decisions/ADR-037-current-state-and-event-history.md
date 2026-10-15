# ADR-037: The store keeps current state in tables and history in the event log

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Slice S1 gives the store its first schema (ADR-010).
The specification lists sixteen tables, but nine of them serve workers, pipeline runs, deliveries, inboxes, boards and memory, which later slices build.
Every command must record its domain events in the same transaction as its change (ADR-011), and the captain ruled out rebuilding state by replaying events.

**Decision:** Flyway's `V1__baseline.sql` creates only the tables slice S1 writes: `task`, `task_project`, `task_dependency`, `captain_hold`, `decision`, `decision_option`, `event` and `meta`.
Each later slice adds its own tables in its own migration, `V2__...` onwards, once it knows their columns.
The tables hold the current state, and the `event` table holds the history:
- `captain_hold` keeps only the hold a task carries now, at most one per task; holding and releasing are `TaskHeld` and `TaskReleased` events;
- a decision row keeps its current card and answer; raising it again replaces the card and clears the answer, and the earlier answer survives verbatim in its `DecisionAnswered` event;
- every event row carries its time, its actor, its type and a JSON payload written by `EventLog`, and `edi task show` reads a task's events back as its timeline.

Columns are `strict` SQLite types; times are ISO-8601 instants in UTC and days are ISO dates, both stored as text; kinds and states are stored as the words the command uses (`in-flight`); the decision sequence is the row `decision_seq` of `meta`, incremented by `DecisionStore.nextNumber` inside the transaction that inserts the decision.
The queue keeps the order in which tasks were added, which is the row order of `task`; reordering is a later feature of the work board.
Slice S1 writes the event rows only; Spring's in-process publication after commit and the relay of ADR-011 arrive with the first listener that needs them, and the read-only `data/backlog.md` export of ADR-010 is a presenter over the same tables that arrives with the reports of slice S2.

**Motivation:** A query always reads the committed truth from plain tables, and the history needed for timelines, answers and later follow-ups is kept without being needed to compute the present.
Tables created before their slice would have to guess their columns.

**Alternatives considered:**
- The full sixteen-table schema in one baseline - speculative columns that later slices would change anyway.
- A history table per entity, such as past holds and past answers - the event log already holds every change in order.
- Event sourcing - ruled out by the captain.

**Consequences:**
- Good: a crash cannot leave a change without its event, and the current state needs no reconstruction.
- Bad: a question about history reads JSON payloads rather than typed columns.

**Revisit when:** A presenter or listener needs a history that the event payloads cannot answer cheaply.

**Sources:** EDI rewrite specification, sections 5.8, 8.1 and 8.2 (decisions E3, E20); `edi-core/src/main/resources/db/migration/V1__baseline.sql`, `adapter.store.TaskStore`, `DecisionStore`, `EventLog`, `Columns`.

# ADR-078: The store keeps primary sessions, wakes, notices, the outage list and recovery costs

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-037 lets each slice add the tables it writes, holding current state, with history in the event log; ADR-063 asked to revisit the worker row when slice S4 records ended sessions and server losses.
A resumed worker shares the session id of the incarnation it resumes, which `V2__workers.sql` declared unique; SQLite cannot drop a constraint in place.

**Decision:** Flyway's `V3__supervision.sql` rebuilds `worker` without the unique session id, copying every row as a `dispatch` launch, and adds to it the launch kind, the boot, the time the current activity began, the transcript path, the session's end, EDI's stop request and the usage-limit park.
It adds `primary_session` and `typed_prompt` (ADR-067), `wake` (ADR-069), `worker_notice` and `hold_notice` (ADR-070, ADR-077), `outage_entry` (ADR-074) and `recovery_cost` (ADR-075).
An outage entry is deleted when it is answered, a wake is kept with its delivery and acknowledgement, and the event log keeps the history of both.
A worker copied from slice S3 begins its current activity at its first prompt, else at its start, else at its launch.

**Motivation:** Each supervision question, such as who holds the primary role, which wakes wait, or whether a condition was already noticed, stays one indexed read.

**Alternatives considered:**
- Folding notices into worker columns - one column per condition, and suppression could not tell a new condition from the old one.
- Deriving wakes from the event log on each poll - see ADR-069.

**Consequences:**
- Good: a restarted core knows every session, every wake and every open outage entry.
- Bad: rebuilding `worker` means a later change to it is a rebuild too.

**Revisit when:** Slice S5 records deliveries and the task's cost, or slice S7 needs the console's own sessions.

**Sources:** EDI rewrite specification, sections 8.1, 8.2 and 11.9; ADR-037, ADR-063; `edi-core/src/main/resources/db/migration/V3__supervision.sql`, `adapter.store.WorkerStore`, `PrimarySessionStore`, `WakeStore`, `NoticeStore`, `OutageStore`, `RecoveryCostStore`.

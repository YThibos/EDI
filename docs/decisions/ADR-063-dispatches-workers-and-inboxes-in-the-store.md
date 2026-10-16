# ADR-063: The store keeps each task's dispatch, its workers and its inbox

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-037 lets each slice add the tables it writes, holding current state, with history in the event log.
Section 8.1 describes a worker (`<task-id>#<n>`, with its session id, model, effort, category, tmux socket, server pid and start time, window, launch and end times and state) and an inbox message (`<task-id>/<n>`, with its body and its creation, delivery and acknowledgement times); a status line is stored as an event.
There is at most one live worker per task.

**Decision:** Flyway's `V2__workers.sql` adds four tables.
`dispatch`, one row per task: its category, branch, spec, the mechanical task's expected change, the captain's override with its reason, and the time of a deferral; `dispatch_file` holds the mechanical task's named files.
`worker`, one row per incarnation: the session id EDI chose, the category and the route it ran on, the digest of its token, the tmux socket, server pid, server start, window id and pane pid, its activity (`starting`, `busy`, `idle`), the times of its launch, its first hook, its first prompt, a failed launch and its retirement, its last status (state, key, until, text and time) and the check of its done.
`inbox_message`, one row per message: the body, the key it resolves, and the times it was created, received, acknowledged and reported unreceived.
The worktree path is never stored, because `EdiHome.worktreeOf` derives it from the project and the task.
A task moves to `in-flight` when its first worker is recorded; a later launch retires the earlier worker in the same transaction.
Every status line is also a `StatusReported` event, and the worker row keeps only the last one.

**Motivation:** Every question slice S3 asks, such as who holds this token, which worker serves this task or which messages wait, is one indexed read, and the timeline keeps the rest.

**Alternatives considered:**
- Stopping at the events - a token lookup or an inbox would read and fold JSON on every hook.
- Columns on `task` for the dispatch - a task never dispatched would carry empty columns, and the mechanical files would need a table anyway.

**Consequences:**
- Good: a restarted core knows every worker, its window and its token.
- Bad: the worker row is wide, because slice S4's health rules will read most of it.

**Revisit when:** Slice S4 records ended sessions and server losses, or slice S5 records deliveries.

**Sources:** EDI rewrite specification, sections 8.1 and 8.2; ADR-037, ADR-038; `edi-core/src/main/resources/db/migration/V2__workers.sql`, `adapter.store.DispatchStore`, `WorkerStore`, `InboxStore`, `domain.worker.Worker`, `domain.dispatch.Dispatch`, `domain.inbox.InboxMessage`.

# ADR-090: Cleanup retires a landed task's copy, branch and workers, and records what the task cost

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 11.8: once its work is landed, cleanup removes the worktree, deletes the local branch, retires the worker record, the inbox, any board listener and any merge observation, removes any EDI-written settings file, emits the clean-up event and closes the task; a refusal names the exact unlanded commits or files, and nothing bypasses it without the captain's word.
U-16 and U-12: a scout is cleaned up only once its report exists and its done passed, and its nested repositories are removed first.
U-06: at cleanup EDI records the task's cost, the workers' transcript usage plus the pipeline's spend, never used for routing (I-17).

**Decision:** `CleanUpTask` refuses a task that is not in flight or is held, one whose latest worker still works, and a scout without an accepted done and its `report.md`.
It removes a scout's nested repositories, then runs the landed-work test (ADR-089); a refusal records `CleanupRefused` with the findings and says that nothing is discarded without the captain's word.
A landed task is retired in this order: its cost is counted, every unretired worker's window is closed, the copy is removed with `git worktree remove`, the branch is deleted, and each worker's state directory, which holds its settings file and token, is deleted; then, in one transaction, its workers are retired, the task is done, and `TaskCostRecorded` and `TaskCleanedUp` are recorded.
`TaskCosts` counts each worker transcript once, from the earliest launch that used it, so a resumed session is not counted twice, and adds the pipeline's spend or keeps it unknown (ADR-081).
A closed task's inbox no longer waits for receipt, and its delivery is no longer watched, because both look only at tasks in flight.
Board listeners arrive with slice S7, and discarding unlanded work on the captain's word with slice S8.

**Motivation:** A finished task leaves nothing behind but its records, and a refused cleanup leaves everything exactly as it was.

**Alternatives considered:**
- Removing the copy with `--force` - untracked work would vanish without the landed-work test seeing it.
- Deleting the inbox rows - they are part of the task's history.

**Consequences:**
- Good: `edi task show` shows the task's cost, its draft and its pipeline run.
- Bad: a cleanup that fails after the copy was removed, such as on a broken store, leaves a task in flight without a copy; the landed-work test then judges its branch.

**Revisit when:** Slice S7 adds board listeners, or slice S8 adds discarding on the captain's word.

**Sources:** EDI rewrite specification, section 11.8, I-12, I-17, U-06, U-12 and U-16; ADR-081, ADR-089; `application.delivery.CleanUpTask`, `TaskCosts`, `domain.delivery.TaskCost`, `domain.task.Task.cleanedUp`, `adapter.store.TaskCostStore`, `InboxStore.awaitingReceipt`, `web.delivery.DeliveryController`, `edi-cli` `CleanupCommand`.

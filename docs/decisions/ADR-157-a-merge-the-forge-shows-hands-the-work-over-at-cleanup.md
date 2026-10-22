# ADR-157: Cleanup retires a landed task's copy, branch and workers, and a merge the forge shows hands the work over

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 11.8: once its work is landed, cleanup removes the worktree, deletes the local branch, retires the worker record, the inbox, any board listener and any merge observation, removes any EDI-written settings file, emits the clean-up event and closes the task; a refusal names the exact unlanded commits or files, and nothing bypasses it without the captain's word.
U-16 and U-12: a scout is cleaned up only once its report exists and its done passed, and its nested repositories are removed first.
U-06: at cleanup EDI records the task's cost, the workers' transcript usage plus the pipeline's spend, never used for routing (I-17).
ADR-090 refused the clean-up of a task whose latest worker still works, so a task whose request the captain merged while its worker waited idle for the merge stayed in flight until that worker reported a done (ADR-156).

**Decision:** `CleanUpTask` refuses a task that is not in flight or is held, and a scout without an accepted done and its `report.md`.
It refuses a task whose latest worker still works unless the task's recorded delivery is merged: a merge the forge shows hands the worker's work over, as an accepted done does.
It removes a scout's nested repositories, then runs the landed-work test (ADR-089); a refusal records `CleanupRefused` with the findings and says that nothing is discarded without the captain's word.
A landed task is retired in this order: its cost is counted, every unretired worker's window is closed, the copy is removed with `git worktree remove`, the branch is deleted, and each worker's state directory, which holds its settings file and token, is deleted; then, in one transaction, its workers are retired, the task is done, and `TaskCostRecorded` and `TaskCleanedUp` are recorded.
`TaskCosts` counts each worker transcript once, from the earliest launch that used it, so a resumed session is not counted twice, and adds the pipeline's spend or keeps it unknown (ADR-081).
A closed task's inbox no longer waits for receipt, and its delivery is no longer watched, because both look only at tasks in flight.
Board listeners are retired as ADR-111 decided, and dropping or discarding work follows ADR-117.

**Motivation:** A finished task leaves nothing behind but its records, a refused cleanup leaves everything exactly as it was, and a worker whose work the captain merged no longer holds the task in flight.

**Alternatives considered:**
- Removing the copy with `--force` - untracked work would vanish without the landed-work test seeing it.
- Deleting the inbox rows - they are part of the task's history.
- Waiting for the worker's done after a merge - a worker idle until the merge never reports one by itself, so the task would wait for a steer.

**Consequences:**
- Good: `edi task show` shows the task's cost, its draft and its pipeline run, and a merge ends its task however busy its worker looked.
- Bad: a worker still running when its request is merged is closed by the clean-up; only the landed-work test keeps any work it had not committed or landed.

**Revisit when:** A worker should be told before it is closed after its merge.

**Sources:** EDI rewrite specification, section 11.8, I-12, I-17, U-06, U-12 and U-16; ADR-081, ADR-089, ADR-090, ADR-111, ADR-117, ADR-156; `application.delivery.CleanUpTask`, `CopyRetirement.requireNoWorkerAtWork`, `TaskCosts`, `domain.delivery.Delivery.isMerged`, `domain.delivery.TaskCost`, `domain.task.Task.cleanedUp`, `adapter.store.TaskCostStore`, `InboxStore.awaitingReceipt`, `web.delivery.DeliveryController`, `edi-cli` `CleanupCommand`.

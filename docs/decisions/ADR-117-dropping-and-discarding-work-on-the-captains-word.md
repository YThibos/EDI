# ADR-117: A task is dropped, and unlanded work discarded, only on the captain's word

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-12: cleanup refuses while any commit or file is not provably landed, and only the captain's explicit word discards; I-06 and AT-06 demand that word in the current exchange for `edi task drop --discard-work`, `edi worktree discard` and deleting an unlanded branch.
Cleanup retires a landed task's copy, branch, workers and boards (ADR-090) after the landed-work test (ADR-089).
`Task.dropped` already demands the captain's word.

**Decision:** `DropTask` needs the captain's word on the task (ADR-115), refuses while a decision waits on it, and for a dispatched task refuses while its worker still works.
It runs the landed-work test on the task's copy: landed work is retired as at cleanup; unlanded work is refused with its findings unless the drop says `--discard-work`, which removes the copy with `git worktree remove --force` and deletes the branch.
The task is closed as dropped, its workers and boards are retired, and `TaskDropped` records the captain's words and what was discarded.
`DiscardWork` (`edi worktree discard`) needs the same word, removes the task's copy and branch whatever they hold, retires its workers and records `WorkDiscarded`, but keeps the task in flight, so a later dispatch starts a fresh copy from the origin base; a branch left without a copy is deleted the same way.
`CopyRetirement` holds the retirement steps that cleanup, dropping and discarding share: closing worker windows, removing the copy, deleting the branch and the workers' state, retiring workers and boards.

**Motivation:** Discarding is the one irreversible act on a task's work, so it happens only on the captain's own words and leaves a record of exactly what went.

**Alternatives considered:**
- A `--force` on cleanup - would make the irreversible act look like routine cleanup.
- Dropping a task without touching its copy - would leave copies and branches no task owns.

**Consequences:**
- Good: AT-06 exercises each path, and the timeline shows what was discarded on which words.
- Bad: a drop of a task with a running worker waits for `edi worker stop`.

**Revisit when:** The console offers dropping or discarding.

**Sources:** EDI rewrite specification, sections 11.8 and 16, I-06, I-12 (AT-06); ADR-089, ADR-090, ADR-115; `application.delivery.DropTask`, `DroppedTask`, `DiscardWork`, `DiscardedWork`, `CopyRetirement`, `CleanUpTask`, `LandedWorkCheck`, `adapter.git.Git`, `domain.event.DomainEvent.TaskDropped`, `WorkDiscarded`, `web.task.TaskController`, `web.delivery.DeliveryController`, `edi-cli` `TaskDropCommand`, `WorktreeDiscardCommand`.

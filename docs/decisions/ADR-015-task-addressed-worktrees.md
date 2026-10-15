# ADR-015: EDI-owned, task-addressed worktrees

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The old harness drew workers' copies from a shared pool of numbered slots, and relaunches more than once landed a worker in another task's copy.
Workers must never touch the captain's own checkouts or the project clone's primary directory (I-07), and must branch from the freshly fetched origin base (I-13).

**Decision:** Each task gets its own git worktree at `$EDI_HOME/worktrees/<project>/<task-id>`, created with a fetch and `git worktree add -b <branch> <path> origin/<base>`.
A relaunch reuses the same path by construction.
No pool is kept; the shared Maven and npm caches keep repeat builds fast.
A worktree is removed only by cleanup after the landed-work test passes, or by `edi worktree discard` on the captain's word.

**Motivation:** An address derived from the task cannot be reassigned, so the slot-reassignment incidents cannot recur.

**Alternatives considered:**
- A pool of pre-created copies - faster first start, at the cost of the reassignment failure class.

**Consequences:**
- Good: a task's copy is findable from its id alone.
- Bad: the first build in a new worktree starts from cold build directories.

**Revisit when:** Worktree creation becomes a measurable bottleneck.

**Sources:** EDI rewrite specification, sections 11.3 and 11.9 (decision E5, I-07, I-13, I-25).

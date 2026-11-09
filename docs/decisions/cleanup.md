# Cleanup retires a task only once its work has provably landed, and records what the task cost

**Kind:** Design

## Context

Cleanup refuses while any commit or file is not provably landed, and only the captain's explicit word discards (I-12).
The pipeline rebuilds or rebases commits, and the forge squash-merges, so ancestry alone would call landed work unlanded.
A scout is cleaned up only once its report exists and its done passed, and its nested repositories go first.
At cleanup EDI records the task's cost, the workers' transcript usage plus the pipeline's spend, and never routes by it (I-17).

## Decision

`LandedWorkCheck` fetches the clone's origin and gathers the facts with git; `LandedWork` in the domain decides.
Work is landed exactly when the copy has nothing uncommitted or untracked beyond EDI's own state, holds no nested repository, every commit `git cherry` lists is in `origin/<base>` by ancestry, by an equal patch, or by content (when `merge-tree` of the base and the head gives the base's tree), and, for `local-branch`, the captain's checkout holds the head.
When the copy is gone the branch in the clone stands in for it; each failure is a finding naming the path, the repository or the commit.
`CleanUpTask` refuses a task that is not in flight or is held, a scout without an accepted done and its `report.md`, and a task whose latest worker still works unless its recorded delivery is merged, since a merge the forge shows hands the work over as an accepted done does.
A refusal records `CleanupRefused` with the findings and says that nothing is discarded without the captain's word.
A landed task is retired by `CopyRetirement`: its cost is counted (`TaskCosts`, each transcript once from its earliest launch, plus the pipeline's spend or unknown), its worker windows are closed, the copy is removed with `git worktree remove`, the branch is deleted, the workers' state directories with their settings and tokens are deleted, and in one transaction the workers and boards are retired and the task is done, with `TaskCostRecorded` and `TaskCleanedUp`.
Dropping a task and discarding work share the same retirement steps on the captain's word ([captains-word](captains-word.md)).
`edi task recount-costs --since <date>` recounts the pipeline's spend of every task cost counted since that day (`RecountTaskCosts`): it reads the spend of each task's branch again, keeps what the workers spent and when the cost was counted, and saves each cost it changes with `TaskCostRecounted`, the cost before and after; a spend the pipeline no longer knows leaves the recorded one.

## Why

The rule that protects unlanded work is a few lines of domain code tested in milliseconds, while git gathers the facts it judges.
A finished task leaves nothing behind but its records, a refused cleanup leaves everything as it was, and a merged request no longer holds its task in flight.
A recount through the core keeps the core the only writer of the store and the event log the record of what changed, and it serves whenever the pipeline's reading of its spend is corrected.

## Alternatives rejected

- Ancestry alone - every squash merge and pipeline rebuild would look unlanded.
- Asking the forge whether the request merged - a merged request can still leave unpushed commits in the copy.
- Correcting recorded costs at a start of the core, in a migration or with a script against the store - one-off code that lingers, a migration reaching into the pipeline's store, or a write outside the core with no event.
- Removing the copy with `--force`, or deleting the inbox rows - untracked work would vanish unseen, and the rows are the task's history.
- Waiting for the worker's done after a merge - a worker idle until the merge never reports one by itself.

## Consequences

- Good: AT-12 runs every case on real repositories, and `edi task show` shows the task's cost, request and pipeline run.
- Bad: a branch whose change the base later reverted counts as landed by its patch, and a worker still running when its request merges is closed, keeping only what the landed-work test protects.

## When to revisit

When a delivery mode lands work without its commits or content reaching the base, or a worker should be told before it is closed after its merge.

## How we got here

- 16/10/2026: slice S5 built the landed-work test and cleanup with the task's cost.
- 17/10/2026: slice S7 retired board listeners at cleanup; slice S8 shared the retirement with dropping and discarding.
- 21/10/2026: a merge the forge shows started to hand a busy worker's work over (EDI-23).
- 09/11/2026: the pipeline's spend had been read with its cache reads and writes counted again as input since 2 November 2026, so `edi task recount-costs` recounts the costs recorded since a day (EDI-235).

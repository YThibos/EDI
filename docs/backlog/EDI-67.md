# EDI-67 The base moved: open requests stay mergeable

| Field | Value |
|---|---|
| Key | EDI-67 |
| Kind | ship, a change to the repository, on branch `feature/EDI-67` |
| Wave and order | wave 4, order 14 |
| Source | EDI-74 plan, step 16, with EDI-52 slice 16 |
| Size | S |
| Blocked by | [EDI-86](EDI-86.md) |
| Status | open |
| Cloud session | ok |

## Goal

When a merge lands on a project's base, every other delivered or ready task of that project moves to CHANGING and receives one steer to merge the base and re-run its pipeline, and a closed request leaves the review zone.

## Context

Parallel EDI pull requests conflicted with each other during the first week, and the captain asked twice for all of them to be put on one branch with no merge conflicts.
The primary sent "main has moved, merge origin/main" steers by hand.
This item makes the core do it, on the lifecycle's base-moved trigger.
It takes over EDI-52 slice 16 (open requests stay mergeable).
The research is in [the EDI-74 plan](research/EDI-74-plan.md), step 16, and [the EDI-52 plan](research/EDI-52-plan.md), finding F4.

## Acceptance criteria

- After a followed merge, each other task of the project that is DELIVERED or READY moves to CHANGING and receives exactly one steer naming the merged request, telling it to merge the base and re-run its pipeline.
- A closed request moves its task to CHANGING and leaves the review zone at once.
- A task whose request was closed no longer appears under "needs your decision".
- No code path rebases: the worker merges the base with a merge commit.

## Requirements from decisions and answers

- Q7 A (EDI-74 round 1): slice 16 is rebuilt on the stages.
- The captain asked for merges, never rebases, of a worker's commits.
- A steer counts once acknowledged (I-27); this item sends the steer through the existing steering path.
- One steer per task per merge: two merges in a row send two steers, never a duplicate for one.

## Research and measurements

- On 23 and 26 October the captain asked twice for all of the open EDI pull requests to be put on one branch with no merge conflicts, because one conflicted and the others probably would too.
- The closed pull requests 35, 37 and 38 kept showing as waiting on the captain.

## Read first

- [merge-following](../decisions/merge-following.md), [steering](../decisions/steering.md), [task-lifecycle](../decisions/task-lifecycle.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/delivery/FollowMerges.java`, `application/worker/SteerWorker.java`, `domain/task/ChangeCause.java`.

## How to verify

- `./mvnw verify`.
- A follow-merge test with three tasks on one project: each other READY task gets exactly one steer, the merged task none, a task on another project none.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

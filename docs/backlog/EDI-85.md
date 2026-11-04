# EDI-85 The stage is stored

| Field | Value |
|---|---|
| Key | EDI-85 |
| Kind | ship, a change to the repository, on branch `feature/EDI-85` |
| Wave and order | wave 2, order 5 |
| Source | EDI-74 plan, step 11 |
| Size | M |
| Blocked by | nothing |
| Status | open |
| Issue | [#122](https://github.com/YThibos/EDI/issues/122) |
| Cloud session | ok |

## Goal

A task's stage is stored on the task and changes only through the domain's transition rules, so every surface that asks "where is this task" reads one stored answer.

## Context

The domain already holds the lifecycle (EDI-84): `TaskStage`, `StageTransitions`, `StageTrigger`, `StageChange` and `Holds`, with the transition table and the four defects as tests, and the record [task-lifecycle](../decisions/task-lifecycle.md) states the decision.
Nothing calls them yet: the application services still read `TaskState`, `CaptainHold` and the done check.
This item stores the stage and makes every service go through it; it unlocks most of what follows.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 6.

## Acceptance criteria

- A Flyway migration adds `task.stage` and derives it once for every open task.
- Every application service that changes a task's state goes through `StageTransitions`, and each change records a `StageChanged` event with the stage before and after and the reason.
- `edi task show` prints the task's stage and its history.
- `ObservePipelines` and `FollowMerges` keep observing a delivered task's pipeline runs, request head and draft flag after the done.
- A pipeline run started after a done is recorded.
- After the migration, a task merged with a commit after the merged head reads MERGED with that commit named as not landed, and a finished scout with an answered decision reads DELIVERED.
- The stow-list's idea of "landed" (EDI-81) changes from DONE to the stage LANDED or RETIRED in one place.
- The core's tests that pinned the old derivation are rewritten as transition tests, not deleted.

## Requirements from decisions and answers

- D43 A (captain, 26 October 2026): one stored stage per task, the reasons it waits as a set beside it, and the worker's condition as its own derived machine.
- D44 A: ship the whole EDI-74 plan in one go, steps in the plan's order and waits.
- Q6 A, widened on 27 October 2026: any merge with commits after the merged head, whatever the stage before it, raises one decision naming them, offering a follow-up request from the same branch or discarding them, the latter carrying the `discard-work` authorisation.
- The domain stays free of Spring, input, output and processes; `ArchitectureRulesTest` is not to be bent.
- The `Clock` port is the only source of time.
- The migration must be safe to run on a store with tasks in every state; test it on a fixture store, not only on an empty one.
- A step that touches Spring configuration, resources or the command also passes the native build; this one adds a migration resource, so run `./mvnw -Pnative,quick-build verify` or state plainly why the sandbox could not.

## Research and measurements

- Before this change the stage was inferred from four enums, two hold records, about twelve timestamps across nine tables and a derived worker health.
- Four defects of the first week came from that inference: a merge of a head without the last steer's commit, a finished scout back under Ongoing after its decision was answered, a squash merge whose cleanup depended on the base not changing the same lines again, and a request shown ready while its pipeline still ran.
  Each is a transition test already in the domain; this item makes the services honour them.
- Cost accepted by the captain: the core keeps observing runs and heads after a done, one forge call per delivered task per round.

## Read first

- [task-lifecycle](../decisions/task-lifecycle.md), [store](../decisions/store.md), [fleet-snapshot](../decisions/fleet-snapshot.md), [delivery](../decisions/delivery.md), [cleanup](../decisions/cleanup.md).
- `edi-core/src/main/java/io/github/ythibos/edi/domain/task/` (`TaskStage`, `StageTransitions`, `StageTrigger`, `StageChange`, `Holds`, `Task`, `TaskState`, `CaptainHold`).
- `edi-core/src/main/java/io/github/ythibos/edi/application/delivery/` (`DoneClaims`, `CleanUpTask`, `FollowMerges`, `ObservePipelines`), `application/worker/RecordStatus.java`, `application/fleet/FleetSnapshotQuery.java`, `application/task/TaskDetailQuery.java`.
- The Flyway migrations under `edi-core/src/main/resources/`, and `adapter/store/`.

## How to verify

- `./mvnw verify`, then the native quick build.
- A migration test over a store holding a queued, a working, a delivered, a merged and a dropped task.
- Transition tests through the services: `RecordStatus`, `SteerWorker`, `ObservePipelines`, `FollowMerges`, `CleanUpTask`.
- Check `edi task show` output on a fixture task with several stage changes.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

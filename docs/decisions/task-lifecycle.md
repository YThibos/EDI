# A task moves through one stored stage at a time, the reasons it waits are a set beside the stage, and its worker's condition is a machine of its own

**Kind:** Design

## Context

A task's place in its life is worked out each time from four enums, two hold records, about twelve timestamps across nine tables and a derived worker health ([store](store.md), [fleet-snapshot](fleet-snapshot.md)).
`TaskState` knows only queued, in flight, done and dropped, and done means cleaned up, so everything between a dispatch and a cleanup is inferred from the latest worker's done check, the delivery row, the latest pipeline run and the hold.
Four defects in one week came from checks that each read one of those and ignored the rest: a done accepted while a steer waited unread and a pipeline ran, after which the captain merged a head without the steer's commit; an answer to a finished scout's decision that put it back under Ongoing; a squash merge whose cleanup depended on the base not changing the same lines again; and a request shown ready for review while its pipeline still ran.
A task can wait on the captain, be parked for the night and have a dead worker at the same time, so these are three questions, not one.

## Decision

`domain/task/TaskStage` holds where a task is in its life: QUEUED, WORKING, DELIVERED, CHANGING, READY, MERGED, LANDED, RETIRED and DROPPED, stored in `task.stage`.
`domain/task/StageTransitions` is the only place a stage changes: `move` takes the stage and a `StageTrigger` carrying the facts its guard reads, and either refuses, naming every gap, or returns the `StageChange` a `StageChanged` event is to record, with the stage before and after and the reason.

| From | To | Trigger | Guard |
|---|---|---|---|
| QUEUED | WORKING | `edi dispatch`, or a plan dispatching its next slice (`Dispatch`) | not a captain item, no hold, every wait met, the project's and the plan's capacity free |
| WORKING, CHANGING | DELIVERED | the worker's `edi status done` (`DoneReported`) | the copy is clean, no SNAPSHOT is added, no message waits unacknowledged, no pipeline run is running; a ship's request is open at the copy's head; a scout's `report.md` exists |
| DELIVERED | READY | the core, observing the forge (`ForgeObserved`) | a ship's request, out of draft, its latest run passed, and its head is the delivered head |
| DELIVERED, READY | CHANGING | the core: a steer is sent, a pipeline run starts, the worker reports working, the request's head or the base moves, or the request is closed (`Change`, `ChangeCause`) | none |
| WORKING, DELIVERED, CHANGING, READY | MERGED | the core following the forge, or `edi merged` (`RequestMerged`) | the forge reports the request merged; the merged head is recorded, with the commits after it |
| MERGED | LANDED | the core (`LandingObserved`) | the commit the merge landed is in the base and no commit follows the merged head; a commit after it keeps the task MERGED and is named |
| LANDED | RETIRED | the core's cleanup, retried each delivery round until it holds (`CleanUp`) | the copy, branch, workers and boards are retired (`Leftover`) |
| DELIVERED (a scout) | RETIRED | `edi cleanup` (`CleanUp`) | the report exists, and the copy, branch, workers and boards are retired |
| QUEUED (a captain item) | RETIRED | the answer to its decision (`CaptainItemAnswered`) | nothing else holds it |
| any but RETIRED and DROPPED | DROPPED | `edi task drop` (`Drop`) | the captain's word, and the work landed unless discarding it is part of that word |

Dependants and plan slices wait on a named stage of another task: MERGED or LANDED.
`domain/task/Holds` is the set of reasons a task waits, beside its stage and never changing it: each open decision (`Hold.OpenDecision`), a deferral, an approval, an outage entry and, once parking for the night exists, a night's park.
A hold added never replaces another, a hold of each kind is held once (a decision once per number), an answer removes only its own decision, and a decision is released only by its answer.
`WorkerHealth` stays the worker's own condition, derived from the worker row as today; a stage transition may read it, but it is never a stage.
`FleetSnapshotQuery` is to read the stage and the holds instead of combining the done check, the delivery and the run: Ongoing is WORKING and CHANGING (`TaskStage.isOngoing`), ready for review is READY, "report ready" is a delivered scout, and a MERGED task with a commit not landed shows in red with the commit named.
A merge with commits after the merged head, whatever the stage before it (`StageChange.raisesAFollowUpDecision`), raises one decision on the task naming those commits: deliver them as a follow-up request from the same branch, or discard them, the option that authorises `discard-work`.
The stored stage is the one answer: `TaskState` (queued, in flight, done, dropped) is only its coarse projection (`TaskStage.state`) for the readers that still ask it.
Every application service that changes a task's stage moves it through `StageTransitions` and `TaskStages`, which records a `StageChanged` event with the stage before and after, the reason and any commit not landed, and `edi task show` prints the stages a task reached.
`V26__the_stage_is_stored.sql` derived the stage of every task once from the facts stored before it, with a `StageDerived` event for each task in flight: merged when its request was merged, naming as not landed the heads its pipeline runs reached after the merged head; delivered when its latest worker's done stands; changing when an earlier done no longer does; working otherwise.
A ship cleaned up without a followed merge, such as one delivered to the captain's checkout, is recorded merged at the branch head that landed before it becomes landed.
Until the delivery guard, the triggers into CHANGING and READY arrive (EDI-86), a done the claims accept moves an ongoing task to DELIVERED and only its worker reporting working moves it to CHANGING; `CaptainHold` stays the one hold the services read until `Holds` replaces it (EDI-87).

## Why

Each question has one answer in one place: the stage says where the work is, the holds say why it waits, the worker's health says how its process is.
A guard that reads the stage and the facts its trigger carries cannot forget a field it was never told to read, and each of the four defects becomes one transition test.
Recording every transition as an event gives the timeline, active time and the report's "since you last looked" one source.

## Alternatives rejected

- One flat list of states that includes held, parked, stopped and dead - it needs a state for every combination, such as delivered and parked with a dead worker.
- Keeping today's fields and fixing each defect where it appeared - every new zone or guard would again combine the fields in its own way.
- Computing the stage on demand from today's fields - transitions would still have no single owner, and nothing would record when the stage changed.

## Consequences

- Good: the sitrep page, the boxed report, the digest and `edi task show` agree, because they read one stored stage.
- Good: a merge can never be presented as safe while the work is changing.
- Bad: every application service that changed a task's state now goes through `StageTransitions`, and a migration derives the stage of every open task once.
- Bad: the core keeps observing pipeline runs and request heads after a done, which costs one forge call per delivered task per round.

## When to revisit

When a delivery mode appears whose work is neither merged nor reported, or when a task needs two deliveries at once.

## How we got here

- 26/10/2026: the EDI-74 scout traced four defects of week one to state inferred from several fields, and proposed the stages, the holds as a set and the worker's own machine; the captain chose it as D43.
- 27/10/2026: `TaskStage`, `StageTransitions` and `Holds` entered the domain with the transition table and the four defects as tests, and a captain item's answer became its own transition to RETIRED, since a captain item never gets a worker (EDI-84).
- 27/10/2026: the captain widened Q6 A so a merge while the task was still working also asks: any merge with commits after the merged head raises the follow-up decision, whatever the stage before it (EDI-84).
- 12/11/2026: `task.stage` replaced `task.state`, derived once for every task; every service moved it through `StageTransitions` with a `StageChanged` event, and `ObservePipelines` and `FollowMerges` kept observing a delivered task's runs, head and draft flag (EDI-85).

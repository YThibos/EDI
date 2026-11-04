# EDI-86 Delivered, changing, ready

| Field | Value |
|---|---|
| Key | EDI-86 |
| Kind | ship, a change to the repository, on branch `feature/EDI-86` |
| Wave and order | wave 3, order 8 |
| Source | EDI-74 plan, step 12 |
| Size | M |
| Blocked by | [EDI-85](EDI-85.md) |
| Status | open |
| Issue | [#138](https://github.com/YThibos/EDI/issues/138) |
| Cloud session | ok |

## Goal

A task is DELIVERED only when its work is truly delivered, becomes CHANGING when anything moves under it, and is READY only from the forge's own view, so a request is never shown as safe to merge while it is still changing.

## Context

On the first week's merge of a request, the captain merged a head that lacked the last steer's commit: a done had been accepted while a steer waited unread and a pipeline ran.
EDI-52 and EDI-53 (the first two console slices) landed an interim rule: a done is refused while a message waits unacknowledged, and a request counts as ready only when no run is running and no steer waits.
With the stage stored (EDI-85), this item re-homes both rules as transition guards and replaces the interim computation.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), sections 6.2 (defects a and d) and 6.3.

## Acceptance criteria

- The guard on WORKING and CHANGING to DELIVERED: the copy is clean, no SNAPSHOT is added, no message waits unacknowledged, no pipeline run is running, a ship's request is open at the copy's head, and a scout's `report.md` exists.
- The triggers into CHANGING: a steer is sent, a pipeline run starts, the worker reports working, the request's head or the base moves, the request is closed.
- READY is entered only from the forge's view: the request is out of draft, its latest run passed, and its head is the delivered head.
- Defect tests a and d pass end to end through `RecordStatus`, `SteerWorker`, `ObservePipelines` and `FleetSnapshotQuery`:
  - a DELIVERED task and a steer sent gives CHANGING; `edi status done` is refused naming the message; after `edi inbox ack` and the run's end it is DELIVERED again;
  - DELIVERED with a pipeline run running is not READY; the run passes and the draft flag drops, then it is READY; a new run starts, then it is CHANGING.
- The interim rules and their acceptance tests from the two console slices become transition tests; none is deleted.

## Requirements from decisions and answers

- D43 A and Q6 A as in [task-lifecycle](../decisions/task-lifecycle.md): the stage says where the work is; a merge while CHANGING leaves the task MERGED, names the commits after the merged head and raises one decision (that part is EDI-70).
- The worker's status words carry what they promise: a scout's done needs its report, needs-decision and blocked need a key, paused needs an until (these refusals already exist; keep them).
- Every stage change is recorded as an event through the one place that changes stages.

## Research and measurements

- The first-week timeline: the steer was sent mid-turn at 16:27, "steer not received" fired at 16:32, the done was accepted at 16:33:50, the stop hook delivered the steer at 16:33:56, the worker committed at 16:36:18, the request was merged at its old head at 16:38:37.
- A busy worker receives a steer only when its turn ends, so a done reported inside that turn runs ahead of the steer; this is why the guard must read the inbox and the run.
- After a done, `ObservePipelines` stopped observing the task and `FollowMerges` saved only closed or merged changes, so an open request's head and draft flag went stale; EDI-85 keeps observing, this item uses it.

## Read first

- [task-lifecycle](../decisions/task-lifecycle.md), [fleet-snapshot](../decisions/fleet-snapshot.md), [delivery](../decisions/delivery.md), [steering](../decisions/steering.md).
- `edi-core/src/main/java/io/github/ythibos/edi/domain/task/StageTransitions.java`, `application/delivery/DoneClaims.java`, `application/worker/RecordStatus.java`, `application/worker/SteerWorker.java`, `application/delivery/ObservePipelines.java`, `application/fleet/FleetSnapshotQuery.java`, `application/fleet/ChangingRequest.java`.

## How to verify

- `./mvnw verify`; the native quick build only if resources or Spring configuration change.
- The two defect tests above, plus the old acceptance tests of the interim rule, now driven through the stage.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

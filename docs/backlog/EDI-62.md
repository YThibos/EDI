# EDI-62 The Week view

| Field | Value |
|---|---|
| Key | EDI-62 |
| Kind | ship, a change to the repository, on branch `feature/EDI-62` |
| Wave and order | wave 5, order 25 |
| Source | EDI-52 plan, slice 11 |
| Size | S |
| Blocked by | [EDI-58](EDI-58.md) |
| Status | open |
| Issue | [#159](https://github.com/YThibos/EDI/issues/159) |
| Cloud session | ok |

## Goal

A Week view shows active and wall-clock time per task per day, with the work key and a control that copies a task's line for the timesheet tool.

## Context

EDI-54 (EDI-52 slice 3) landed active minutes per task, shown by `edi task show` and written to the fleet ledger at cleanup.
Time booked on tickets used to include waiting on the captain; the Week view puts the honest numbers on a page.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), finding F3 and slice 11.

## Acceptance criteria

- The view lists active and wall-clock time per task per day, with the work key.
- The figures equal `edi task show` for the same task and day.
- Time on an EDI key shows against the evergreen tooling ticket the ledger books it on.
- A control copies a task's line for the timesheet tool.

## Requirements from decisions and answers

- Q7 c and Q8 A: a Week view for active time per task.
- Active time is the sum of the stretches in working (and resolved), each ending at the next non-working status, a decision raised, a hold or the worker's session end.
- Times shown to the captain are local ([time-zones](../decisions/time-zones.md)).

## Research and measurements

- The prototype over one day's event log gave, for one scout, 139 active of 191 wall-clock minutes, against the worker's own estimate of 2 hours 40 minutes.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/domain/task/WorkedTime.java`, `application/task/WorkedTimes.java`, `application/ledger/`, `web/console/`.
- [trackers-and-time-booking](../decisions/trackers-and-time-booking.md), [time-zones](../decisions/time-zones.md).

## How to verify

- `./mvnw verify`, then the native quick build.
- A test comparing the view's figures with `WorkedTimes` for a fixture timeline across a midnight.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

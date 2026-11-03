# EDI-61 The Fleet view

| Field | Value |
|---|---|
| Key | EDI-61 |
| Kind | ship, a change to the repository, on branch `feature/EDI-61` |
| Wave and order | wave 5, order 24 |
| Source | EDI-52 plan, slice 10 |
| Size | M |
| Blocked by | [EDI-58](EDI-58.md), [EDI-60](EDI-60.md) |
| Status | open |
| Cloud session | ok |

## Goal

A Fleet view shows each worker's tile and a health strip with the primary's liveness and the usage meters.

## Context

The captain asked for a view on top of the sitrep for workers, the primary's health and usage, so the main dashboard stays clean.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), slice 10, Q7 c and e.

## Acceptance criteria

- Worker tiles show state, model, effort, context and the last lines of the pane.
- A health strip shows whether the primary session is live and whether its `edi wait` runs, and the five-hour and weekly use with their resets, taken from the latest status-line reading the core already parses (`StatusLinePayload.sessionIn`, `rate_limits.five_hour` and `seven_day`), which the core starts keeping.
- A worker above 80% context shows its context in amber.
- With no `edi wait` live while a task is in flight, the strip says so in red.
- The usage meters show the reading's time and turn grey when it is older than ten minutes.

## Requirements from decisions and answers

- Q7 c and e, Q8 A: a Fleet view for workers, the primary's health and usage meters.
- The page's own health (whether the core answers) is part of the same strip's idea; see EDI-110 for the header placeholders when the core does not answer.
- The page never writes to a worker.

## Research and measurements

- Whether the primary's wake stream is live is read by `WakeStreams.liveOf` from the stream's own process, which the stop hook and, through `PrimaryWaitQuery`, `edi health` share since EDI-158; the Fleet strip reads the same source.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/application/statusline/`, `domain/statusline/`, `application/worker/WorkerListQuery.java`, `application/primary/PrimaryWaitQuery.java`, `web/console/`, `edi-console/src/app/`.
- [console](../decisions/console.md), [console-look](../decisions/console-look.md), [supervision](../decisions/supervision.md).

## How to verify

- `./mvnw verify`, then the native quick build.
- Tests for the amber, red and grey states with the recording clock; a browser check.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

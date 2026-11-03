# EDI-59 The task panel

| Field | Value |
|---|---|
| Key | EDI-59 |
| Kind | ship, a change to the repository, on branch `feature/EDI-59` |
| Wave and order | wave 5, order 22 |
| Source | EDI-52 plan, slice 8 |
| Size | M |
| Blocked by | [EDI-57](EDI-57.md), [EDI-58](EDI-58.md) |
| Status | open |
| Cloud session | ok |

## Goal

A task opens in a panel over the current view at `/task/<id>`, with its facts and chips and tabs for its timeline, decisions, brief, report and board rounds, and no control that changes the fleet.

## Context

A row's chips take the captain to the tracker and the forge, but the task's own record, brief and report are in files and commands only.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), slice 8 and section 6.

## Acceptance criteria

- `/task/<id>` opens a panel over the current view with the task's facts and chips and tabs for timeline, decisions, brief, report and board rounds; Esc and a close control return to the view.
- The board rounds tab reads the read-only endpoint behind `edi board show` ([EDI-66](EDI-66.md)), so opening it consumes nothing.
- A new `GET /api/tasks/<id>` returns the detail that `edi task show --json` already assembles.
- Clicking a row's title opens its panel; the address is shareable within the console session and survives a reload.
- The decisions column stays visible beside the panel from 1200 pixels wide; below that the panel takes the page.
- The panel has no control that changes the fleet.

## Requirements from decisions and answers

- D41 A (captain, 26 October 2026): a one-glance sitrep plus a task panel that opens over the right-hand columns at `/task/<id>`.
- The brief is shown only inside the panel.
- The route is open only to the captain's console session and the primary.

## Research and measurements

- The store keeps the worker's status history, its inbox, its decisions and its timeline; `edi task show` prints them as text.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/application/task/TaskDetailQuery.java`, `TaskDetail.java`, `web/console/`, `web/access/`, `edi-console/src/app/`.
- [console](../decisions/console.md), [api-access](../decisions/api-access.md), [console-look](../decisions/console-look.md).

## How to verify

- `./mvnw verify`, then the native quick build.
- API tests for the detail route, including the caller restriction; console tests for open, close and reload; a browser check at both widths.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

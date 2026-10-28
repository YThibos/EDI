# EDI-58 View buttons in the header

| Field | Value |
|---|---|
| Key | EDI-58 |
| Kind | ship, a change to the repository, on branch `feature/EDI-58` |
| Wave and order | wave 5, order 21 |
| Source | EDI-52 plan, slice 7 |
| Size | S |
| Blocked by | [EDI-89](EDI-89.md) |
| Status | open |
| Cloud session | ok |

## Goal

The page header gains buttons that work as tabs for three views, Sitrep, Fleet and Week, each with its own route, so the main dashboard stays clean.

## Context

The captain asked that the main dashboard not be polluted and that buttons on top open different views.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), slice 7, with the views themselves in EDI-61 (Fleet) and EDI-62 (Week).

## Acceptance criteria

- Each view is reachable by its address and by its button, and the browser's back button returns to the previous view.
- The Sitrep button carries the count of what waits on the captain, and shows it from any view.
- The sitrep page itself is unchanged.
- Fleet and Week open as empty views until their items fill them.

## Requirements from decisions and answers

- Q7 note and Q8 A (captain, 26 October 2026): three views on top, Sitrep, Fleet (workers, the primary's health, usage) and Week (active time per task); "try not to pollute the main dashboard too much".
- The page fits 1920 by 1080 at 100% and 125%.
- No control on the page changes the fleet except those the console already allows.

## Research and measurements

- The console may do only three things as the captain: answer a decision, ask for a re-check, and set present or demo ([api-access](../decisions/api-access.md)).

## Read first

- `edi-console/src/app/app.routes.ts`, `app.ts`, `edi-console/src/app/console/`, `edi-console/src/app/sitrep/`, [console](../decisions/console.md), [console-look](../decisions/console-look.md).

## How to verify

- `./mvnw verify` (the console builds and tests run in it).
- Component tests for the routes and the count; open the page in a browser and use the back button.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

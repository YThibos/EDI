# EDI-57 Link chips on every row

| Field | Value |
|---|---|
| Key | EDI-57 |
| Kind | ship, a change to the repository, on branch `feature/EDI-57` |
| Wave and order | wave 5, order 20 |
| Source | EDI-52 plan, slice 6 |
| Size | M |
| Blocked by | nothing |
| Status | open |
| Issue | [#148](https://github.com/YThibos/EDI/issues/148) |
| Cloud session | ok |

## Goal

Every row of the sitrep page carries link chips: the work key to the tracker, the request with its state and pipeline step, the branch, the board kept after its round, the report and the decisions.

## Context

The page already holds almost everything for one-click navigation; it simply does not render it.
The work key is printed but never linked, the forge and the tracker's base address are in the registry and the machine profile, a board's link shows only while a round waits, and a task's report has no route.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), section 5 and slice 6.

## Acceptance criteria

- An application service `TaskLinks` builds, per task, the tracker link through the `Tracker` port (the JIRA adapter adds `/browse/<KEY>`, the EDI-native and no-tracker adapters give none), the request with its state and pipeline step, the branch on the forge (from the registry's `forge`), the board link kept after its round, the report and the decisions.
- `ConsoleSitrep` carries them on decision cards, waiting, ongoing, charted and landed rows.
- A read-only route renders `report.md` and `brief.md` as HTML under the console session.
- A JIRA-tracked task's key links to the tracker's browse address; an EDI-keyed row shows its key as plain text.
- A GitLab project's branch chip opens `https://<host>/<path>/-/tree/<branch>`, a GitHub project's `https://github.com/<path>/tree/<branch>`.
- A board's chip remains after its round is answered; the report chip renders the task's `report.md`, and a task without one shows no chip.
- The page still fits 1920 by 1080 at 100% and 125%.

## Requirements from decisions and answers

- The captain (3 November 2026) lifted this slice's wait on EDI-89: it needs neither plans nor stages, so it may start on its own.
- D41 A and Q2 (captain, 26 October 2026): links a to g on every row; the brief only inside the task panel (EDI-59).
- The tracker's browse path is a JIRA convention and belongs in the JIRA adapter, not the console, so a future tracker supplies its own.
- Landed rows gain the same chips, which keeps a merged request and its ticket reachable for the seven days the list covers.
- The route is read-only and open only to the captain's console session and the primary.
- The presenter builds one `TaskLinks` value per row, so the text sitrep can print the same links later.
- Tracked tests and docs use invented hosts and keys.

## Research and measurements

| Link | Source | Address | Per machine |
|---|---|---|---|
| Work key | `Intake.workKey`, the project's `tracker.kind`, the profile's `tracker.baseUrl` | `<baseUrl>/browse/<KEY>` for JIRA; none for EDI-native and none | the laptop's JIRA only |
| Merge or pull request | `Delivery.address`, `Delivery.state`, `PipelineRun.activeStep` | the delivery's own address | both |
| Branch | the task's branch, the registry's `forge.kind`, `forge.host`, `forge.path` | the forge's tree address | both |
| Board | `board.link` | the Lavish session address | while the Lavish server runs |
| Report and brief | `<EDI home>/data/tasks/<task>/report.md` and `brief.md` | a new read-only route | both |
| Decisions | the decisions raised on the task | the task panel's tab or the decision card | both |

## Read first

- [console](../decisions/console.md), [console-look](../decisions/console-look.md), [api-access](../decisions/api-access.md), [trackers-and-time-booking](../decisions/trackers-and-time-booking.md).
- `edi-core/src/main/java/io/github/ythibos/edi/web/console/` (`SitrepConsolePresenter`, `ConsoleSitrep`), `application/fleet/`, `edi-console/src/app/sitrep/`, `edi-console/src/app/console/`.

## How to verify

- `./mvnw verify` builds the console too; then the native quick build (a route and console resources change).
- Presenter tests for each chip and each missing-chip case; a console component test.
- Look at the page in a browser at 1920 by 1080 at 100% and 125% and at phone width, and describe it in the pull request.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

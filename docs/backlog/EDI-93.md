# EDI-93 Plans on the page and in the report

| Field | Value |
|---|---|
| Key | EDI-93 |
| Kind | ship, a change to the repository, on branch `feature/EDI-93` |
| Wave and order | wave 5, order 33 |
| Source | EDI-74 plan, step 23 |
| Size | S |
| Blocked by | [EDI-89](EDI-89.md), [EDI-91](EDI-91.md), [EDI-59](EDI-59.md) |
| Status | open |
| Cloud session | ok |

## Goal

A plan appears as one row under Ongoing with counts by stage, a strip of its slices and the next slice with its wait, and the report groups plans on its WORKING line.

## Context

Plans exist as stored objects (EDI-88) that dispatch themselves (EDI-89), but the page and the report still show their slices as separate tasks.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), sections 7.2 and 8 and step 23.

## Acceptance criteria

- The plan shows as one row under Ongoing with its key, title, counts by stage, a strip of its slices and the next slice with its wait; a click opens the plan's slices, which the task panel then serves.
- The report's WORKING line groups plans, for example "EDI-74 plan 3 of 24 landed, slices 4 to 6 working".
- The EDI-74 plan shows as one row with its counts and the report reads "EDI-74 plan n of 24".

## Requirements from decisions and answers

- Plans are grouped on the one working line (Q11 A and the report's layout in EDI-91).
- The page fits 1920 by 1080 at 100% and 125%; no new row pollutes the main dashboard ([console-look](../decisions/console-look.md)).
- Text in the page speaks as EDI in the first person.

## Research and measurements

- At the research time two plans were open: the console plan of twenty slices and the lifecycle plan of twenty-four, each titled by hand.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/application/fleet/FleetSnapshotQuery.java`, `web/console/SitrepConsolePresenter.java`, `web/report/`, `edi-console/src/app/sitrep/`, and the `plans` decision record that EDI-88 wrote.

## How to verify

- `./mvnw verify`, then the native quick build.
- Snapshot and golden tests for a plan with slices in several stages; look at the page in a browser if one is available, otherwise say so.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

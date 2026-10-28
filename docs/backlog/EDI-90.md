# EDI-90 The captain's last look and the delta

| Field | Value |
|---|---|
| Key | EDI-90 |
| Kind | ship, a change to the repository, on branch `feature/EDI-90` |
| Wave and order | wave 5, order 15 |
| Source | EDI-74 plan, step 20 |
| Size | S |
| Blocked by | [EDI-65](EDI-65.md) |
| Status | open |
| Cloud session | ok |

## Goal

EDI records the captain's last look and can list what changed in a captain-visible way since it, which the report and the stop hook then use.

## Context

The boxed report repeats itself: in the first week, 95 of 206 decision zones were identical to the report before, and the stop hook demanded reports whose content had not changed.
The report is to become what changed since the captain last looked and what waits on him.
This item is the foundation: the record of the look and the query of the delta.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 8 and step 20.

## Acceptance criteria

- `CaptainsLook` is recorded when a report is shown at the end of a turn the captain typed, and when the console sitrep page is opened or regains focus (the page reports it).
- A query lists the captain-visible changes since the last look from the event log: a landing, a request becoming ready, a failure, a block, a task changing with its reason, a decision answered elsewhere.
- With a look at 18:15, a landing at 18:30 and a worker's status at 18:40, the delta holds the landing only.

## Requirements from decisions and answers

- Q11 A and Q12 A (EDI-74 round 1): the report shows what changed since the captain last looked and what waits on him; it is printed only for news the captain has not seen.
- The captain's last look is the latest of a report shown at the end of a typed turn and the console page visible in his browser.
- Time comes only from the `Clock` port.
- A worker's routine status is not captain-visible news.

## Research and measurements

- 309 turns in the first week: 191 pasted a boxed report, 200 reports in all, from 235 report calls and 12 sitrep calls.
- The decision zone was byte-identical to the previous report's in 93 of 199 consecutive pairs.
- Reports ran 18 to 97 lines, median 34.
- Baselines for the review a week after the change are in [the EDI-74 plan](research/EDI-74-plan.md), "The review after a week".

## Read first

- [captain-report](../decisions/captain-report.md), [wakes-and-turn-end](../decisions/wakes-and-turn-end.md), [events-and-followers](../decisions/events-and-followers.md), [presence](../decisions/presence.md).
- `edi-core/src/main/java/io/github/ythibos/edi/web/report/`, `domain/event/`, `domain/primary/PrimaryTurn.java`, and `edi-console/src/app/sitrep/`.

## How to verify

- `./mvnw verify`, then the native quick build (a store table and a console route).
- The 18:15 delta test above with the recording clock.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

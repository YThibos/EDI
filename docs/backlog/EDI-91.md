# EDI-91 The report's new layout

| Field | Value |
|---|---|
| Key | EDI-91 |
| Kind | ship, a change to the repository, on branch `feature/EDI-91` |
| Wave and order | wave 5, order 16 |
| Source | EDI-74 plan, step 21 |
| Size | M |
| Blocked by | [EDI-90](EDI-90.md) |
| Status | open |
| Cloud session | ok |

## Goal

The boxed report takes its new layout: a header, what waits on the captain, what changed since he last looked, what this turn did, what comes next, one line for the work in progress, and the page at the foot.

## Context

The report was built to reproduce the previous harness byte for byte, and its purpose is stated that way in the decision record.
The console sitrep page now holds the full picture, so the chat report can answer two questions only: what changed since the captain last looked, and what waits on him.
In the first week the report was 30.2% of the primary's output tokens, run five times as often per day as in the previous harness.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 8, and [the EDI-75 measurements](research/EDI-75-measurements.md), section 7.

## What EDI-133 already changed

EDI-133 split the report in two, and this item builds on that split rather than on the boxed report alone ([turn-report](../decisions/turn-report.md)).

- `edi report` prints the turn report (`TurnReport`, `TurnReportPresenter`): the calendar notice, the decision zone when something waits, and DONE THIS TURN and NEXT when the turn has items; nothing else.
- `edi sitrep` keeps the boxed `CaptainReport` and its golden files unchanged, so AT-42 still pins the sitrep, not the turn report.
- A decision card is drawn in full until a report of an earlier turn of the session showed it, then as one line with its number, title and recommended option (`DecisionZone.Reminder`); `PrimaryTurn.previousReportAt` carries the moment of that report, which is the "last look" this item's header and SINCE section can read.
- Ongoing, parked and queued work, the presence and the `Builds:` line left the turn report; the WORKING line and the presence in the header are therefore additions to the turn report, not trims of it.
- A turn report with nothing in it is one line, which this item's 12-line limit for a report with nothing waiting already allows.

## Acceptance criteria

- The report shows, in order:
  1. a header line with the local time, the presence when not plainly present, and "since you looked at HH:MM";
  2. WAITS ON YOU: a decision card in full the first time it is shown, then one line with "shown HH:MM" until answered; boards and their bound decisions as today; ready requests with their full URL;
  3. SINCE HH:MM: what changed for the captain since the last look, one line each;
  4. DONE THIS TURN and NEXT, from the turn's items, as today;
  5. one WORKING line: the count, plans grouped, parked tasks counted;
  6. the page at the foot.
- A decision shown in full in one report is one line in the next, with "shown HH:MM"; the console page still shows it in full.
- With nothing new since the look, the SINCE section is absent and the report is at most 12 lines when nothing waits.
- The golden report cases (AT-42) are rewritten on purpose; I-19 is reworded in the anchor and the catalogue to "Every open decision is in every report: in full the first time, then one line until it is answered".
- `docs/decisions/captain-report.md` is rewritten to the new purpose with a history line.

## Requirements from decisions and answers

- Q11 A: the report shows what changed since the captain last looked and what waits on him.
- Captain-facing words are plain: no internal labels (I-22), no EDI in the third person.
- Quiet and away keep their rule: no text, notifications and the return brief.
- The report is 120 columns wide, or the pane's width minus two, as today.

## Research and measurements

- Reports ran 18 to 97 lines, median 34; Ongoing held 0 to 12 tasks, median 7; the parked block appeared in 193 of 200.
- Decisions D22 to D24 filled five reports running (53 to 66 of 78 to 92 lines).
- What only the page shows: recent answers, the landed list, queued tasks, each task's doing text, answering in place, live updates.
- What only the report shows: this turn's work and next steps, the primary's own wording, the calendar notice, and being in the chat where the captain types.
- Targets for the review after a week: report length median under 20 and longest under 40 lines; no decision zone identical to the one before.

## Read first

- [captain-report](../decisions/captain-report.md), [captain-facing-voice](../decisions/captain-facing-voice.md), [console](../decisions/console.md).
- [turn-report](../decisions/turn-report.md).
- `edi-core/src/main/java/io/github/ythibos/edi/web/report/` (`TurnReportPresenter`, `TurnReport`, `CaptainReportPresenter`, `CaptainReport`, `DecisionZone`), `invariant/ReportGoldenCases.java` in the tests, `claude/primary/CLAUDE.md`.

## How to verify

- `./mvnw verify`, then the native quick build.
- Golden cases for: nothing new, a new decision, the same decision a second time, a landing, a plan grouped on the working line.
- Print a real report in a terminal at 120 columns and read it.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

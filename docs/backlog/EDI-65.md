# EDI-65 The page, the report and the digest on stages: report ready, not ongoing

| Field | Value |
|---|---|
| Key | EDI-65 |
| Kind | ship, a change to the repository, on branch `feature/EDI-65` |
| Wave and order | wave 4, order 13 |
| Source | EDI-74 plan, step 15, with EDI-52 slice 14 |
| Size | M |
| Blocked by | [EDI-86](EDI-86.md), [EDI-70](EDI-70.md) |
| Status | open |
| Cloud session | ok |

## Goal

The page, the report and the digest read stages and holds: Ongoing means working or changing, ready for review means READY, a delivered scout is "report ready", and a changing or unlanded task shows in red with its reason.

## Context

Before the stored stage, a finished scout came back under Ongoing after its decision was answered, and three of nine tasks listed as ongoing at one moment were not: two finished scouts and a merged request whose cleanup was refused.
The fleet snapshot combined the done check, the delivery and the run for each zone.
With EDI-85, EDI-86 and EDI-70 in place, this item makes the presenters read the stage.
It takes over EDI-52 slice 14 (report ready, not ongoing, and the refused cleanup shown).
The research is in [the EDI-74 plan](research/EDI-74-plan.md), step 15, and [the EDI-52 plan](research/EDI-52-plan.md), finding F7.

## Acceptance criteria

- `FleetSnapshotQuery` reads stages and holds: Ongoing is WORKING and CHANGING; ready for review is READY; a delivered scout is "report ready"; CHANGING shows in red with its reason; MERGED with a commit not landed shows in red with the commit named.
- A scout with an accepted done and a later answered decision has no Ongoing entry and one "report ready" entry (defect test b on the snapshot).
- No task appears both under Ongoing and as ready for review.
- After a refused cleanup the row names the unlanded commits, as `edi cleanup` prints them.
- The console, `edi sitrep` and the digest list the same tasks in the same zones.

## Requirements from decisions and answers

- Q7 A and D44 A: the EDI-52 items 14 and 16, 18b and 18c are folded into the lifecycle plan; this task keeps its key.
- Captain-facing words are plain and use the captain's nouns, never internal labels (I-22); the persona rule applies to the text.
- The console page keeps fitting 1920 by 1080 at 100% and 125% ([console-look](../decisions/console-look.md)).

## Research and measurements

- At the research time `edi sitrep` listed nine tasks under Ongoing, of which three were not ongoing.
- A task could be ready for review and ongoing at once, because Ongoing was every unheld in-flight task whatever its worker said.

## Read first

- [fleet-snapshot](../decisions/fleet-snapshot.md), [task-lifecycle](../decisions/task-lifecycle.md), [console](../decisions/console.md), [console-look](../decisions/console-look.md), [captain-report](../decisions/captain-report.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/fleet/` (`FleetSnapshotQuery`, `OngoingTask`, `ReadyForReview`, `ChangingRequest`), `web/console/SitrepConsolePresenter.java`, `web/report/CaptainReportPresenter.java`, `application/primary/SessionStartDigestQuery.java`, and `edi-console/src/app/sitrep/`.

## How to verify

- `./mvnw verify`, then the native quick build.
- Snapshot tests for each zone; the golden report cases (AT-42) rewritten on purpose where the layout changes.
- Look at the console in a browser at desktop and phone widths and describe what you saw in the pull request; a sandbox without a browser says so plainly.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

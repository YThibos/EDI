# EDI-92 The stop hook's new rule

| Field | Value |
|---|---|
| Key | EDI-92 |
| Kind | ship, a change to the repository, on branch `feature/EDI-92` |
| Wave and order | wave 5, order 17 |
| Source | EDI-74 plan, step 22 |
| Size | S |
| Blocked by | [EDI-90](EDI-90.md) |
| Status | open |
| Issue | [#157](https://github.com/YThibos/EDI/issues/157) |
| Cloud session | ok |

## Goal

The stop hook asks for the report only when something changed for the captain since the last report shown, never for a report whose content would be identical.

## Context

The stop hook blocked the primary's stop with "render the captain report" whenever any event had been recorded since the prompt, from any actor, including a worker's status.
It forced 22 reports in the first week, of which 13 were identical to the one before; in four a report had already been pasted in that turn and a message the captain queued mid-turn had reset the turn.
It also blocked 23 stops for "no edi wait is live".
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 8.1 and step 22.

## Acceptance criteria

- `TurnCircumstances.needsTheReport` asks whether a captain-visible change happened since the last report shown, not whether any event was recorded since the prompt.
- A render counts across prompts until such a change.
- A report identical to the last shown is never demanded.
- I-21 is reworded to "A turn that brings the captain news ends with the turn report".
- A turn in which only a worker's status was recorded ends without a demand.
- A message queued mid-turn after a report does not cause a second demand when nothing new for the captain happened.
- AT-21 still holds for a new decision.
- `docs/decisions/wakes-and-turn-end.md` gains a history line.

## Requirements from decisions and answers

- Q12 A (EDI-74 round 1): the report is printed only for news the captain has not seen, and the stop hook asks only then.
- A notification turn in present mode prints the report only for a new decision, a request becoming ready, a failure or a landing.
- The hook gives up after three blocks, as today.
- The hook's reason text speaks to the primary as "you".

## Research and measurements

- The stop hook demanded 25 reports in the first week, 13 of the 22 analysed unchanged.
- "Rendered" meant `reportedAt >= startedAt` and every prompt started a new turn, including a notification.

## Read first

- [wakes-and-turn-end](../decisions/wakes-and-turn-end.md), [captain-report](../decisions/captain-report.md).
- `edi-core/src/main/java/io/github/ythibos/edi/domain/primary/TurnCircumstances.java`, `TurnEnd.java`, `PrimaryTurn.java`, `application/primary/PrimaryHooks.java`, `EventLog` and the delta query of EDI-90.

## How to verify

- `./mvnw verify`.
- Hook tests for the four cases in the criteria, driven through `PrimaryHooks` with the recording clock.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

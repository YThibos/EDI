# EDI-87 The reasons a task waits, as a set

| Field | Value |
|---|---|
| Key | EDI-87 |
| Kind | ship, a change to the repository, on branch `feature/EDI-87` |
| Wave and order | wave 3, order 10 |
| Source | EDI-74 plan, step 14 |
| Size | M |
| Blocked by | [EDI-85](EDI-85.md) |
| Status | open |
| Cloud session | ok |

## Goal

The reasons a task waits become a set, so a task can wait on several decisions, a deferral and an outage entry at once, and an answer removes only its own decision.

## Context

A task holds a single row today, so raising a second decision overwrites the first, and an answer clears every hold: a deferred task that gets a decision loses its deferral when it is answered.
The domain already has `Holds` (EDI-84); this item replaces the single `captain_hold` row with it in the store and the services.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 6.1 and step 14.

## Acceptance criteria

- `Holds` replaces the single `captain_hold` row: several open decisions per task, a decision never replacing a deferral or an outage entry, an answer removing only its own decision.
- `RaiseDecision.decisionOn` raises a second open decision instead of rewriting the first.
- Two decisions on one task have two numbers and both show; answering one leaves the other.
- A deferred task that gets a decision is still deferred after the answer; this inverts today's `RaiseAndAnswerDecisionTest` case, so rewrite that test on purpose.
- EDI-68's night's park, once merged, becomes one of the holds.
- The outage hold is no longer recognised by its reason text: it is its own kind of hold.

## Requirements from decisions and answers

- D43 A: the reasons a task waits are a set beside the stage and never move it.
- Decision numbers are assigned once and never reused (I-18).
- Raising a card again on a task updates its open decision under the same number; once answered, a new card is a new decision ([captain-decisions](../decisions/captain-decisions.md)).
- Preserve the migration of existing hold rows: a store with held tasks must read correctly after the change.

## Research and measurements

- `RaiseDecision` overwrote any other hold through `Task.heldForDecision`, and the answer cleared it, so a deferral was lost; `OutageHoldList` skipped a held task.
- "Waits on the primary" had three definitions (`StatusReport.isForThePrimary`, `SupervisionRules.AWAITING_THE_PRIMARY`, the outage list text); the holds as a set is a precondition for one definition.

## Read first

- [task-lifecycle](../decisions/task-lifecycle.md), [captain-decisions](../decisions/captain-decisions.md), [store](../decisions/store.md), [recovery](../decisions/recovery.md).
- `edi-core/src/main/java/io/github/ythibos/edi/domain/task/Holds.java`, `Hold.java`, `CaptainHold.java`, `application/decision/RaiseDecision.java`, `AnswerDecision.java`, `application/task/HoldTask.java`, `ReleaseTask.java`, `application/supervision/OutageHoldList.java`.

## How to verify

- `./mvnw verify`, then the native quick build (a migration is a resource).
- The two decisions test and the deferred-task test above, through the services and the command.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

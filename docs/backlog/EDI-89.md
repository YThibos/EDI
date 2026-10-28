# EDI-89 The plan dispatches its slices

| Field | Value |
|---|---|
| Key | EDI-89 |
| Kind | ship, a change to the repository, on branch `feature/EDI-89` |
| Wave and order | wave 4, order 11 |
| Source | EDI-74 plan, step 18 |
| Size | M |
| Blocked by | [EDI-88](EDI-88.md) |
| Status | open |
| Cloud session | ok |

## Goal

The core dispatches each plan slice itself, in its supervision round, once the slice's wait is met and the plan and the project have room, so the primary no longer dispatches plans by hand.

## Context

With plans and waits stored (EDI-88), the next slice of a plan is eligible the moment its wait is met.
Until this item, the primary dispatches each by hand, in the right order, from its own memory of the plan.
From this item on, a plan dispatches itself, which is why the remaining waves can be queued as plans.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 7.2 and steps 18 and 19.

## Acceptance criteria

- The supervision round dispatches every eligible slice, through the same `DispatchTask`, while the plan's capacity, the project's capacity and the captain's presence allow it.
- It tells the primary of each dispatch with one notification.
- With capacity 2 and three eligible slices, two are dispatched in one round and the third when one of them is delivered.
- A held slice is not dispatched.
- A slice whose wait is not met is not dispatched.
- The captain's go on the plan covers every slice; a plan without that go dispatches nothing.

## Requirements from decisions and answers

- Q9 A (EDI-74 round 1): the core dispatches the next slice once its wait is met and the plan has room.
- Dispatch stays serialised, so two slices never choose the same branch number at once ([work-keys-and-branches](../decisions/work-keys-and-branches.md)).
- The effort ceiling stays high; the plan's effort word is EDI-71.
- The notification speaks to the primary as "you", per the persona rule.
- When this item merges, the captain is told it is the moment for EDI-105 (revise the validation pipeline research on the new task record and plans, then put decision D47 again).
  That is the primary's act on the captain's machine and is not part of this item.

## Research and measurements

- The first-week plans were dispatched by hand, and no record said which slice waited for which: the first three slices of the console plan were dispatched together.
- Plans needed one effort word per slice and one dispatch per slice, each one a captain prompt and a primary turn.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/application/supervision/SupervisionRound.java`, `application/dispatch/DispatchTask.java`, `application/presence/`.
- [dispatch-routing](../decisions/dispatch-routing.md), [supervision](../decisions/supervision.md), and the `plans` decision record that EDI-88 wrote.

## How to verify

- `./mvnw verify`.
- The capacity test above with a recording `Clock` and fixture projects.
- A test that a held slice and an unmet wait dispatch nothing.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

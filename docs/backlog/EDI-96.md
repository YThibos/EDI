# EDI-96 Ship-raised decisions batched into console rounds

| Field | Value |
|---|---|
| Key | EDI-96 |
| Kind | ship, a change to the repository, on branch `feature/EDI-96` |
| Wave and order | wave 5, order 34 |
| Source | EDI-75 change 2 |
| Size | M |
| Blocked by | [EDI-59](EDI-59.md) |
| Status | open |
| Cloud session | ok |

## Goal

A decision a running ship raises waits in the console's decision zone and reaches the captain as one round with the others open at that moment, instead of one chat question each.

## Context

Of 42 answered decisions in the first week, 33 went through chat, and 28 of those 33 rounds carried one decision, each costing a captain prompt and a primary turn (0.17M weighted tokens per captain turn).
The console was the fastest channel (4.0 minutes median against 9.8 for chat), and a board round carries a median of six answers.
The research is in [the EDI-75 measurements](research/EDI-75-measurements.md), section 10, change 2.

## Acceptance criteria

- A decision raised by a ship waits in the console's decision zone and is not announced in chat on its own.
- The primary's notification to the captain bundles every decision open at that moment into one round, one message that points at the console, instead of one chat question per decision.
- A decision that would block the captain's current work still reaches him at once; only the batching rule changes, never the existence of a decision.
- The measured return is checkable: the share of decisions answered through chat in one-decision rounds falls, and the console's share rises.

## Requirements from decisions and answers

- D46 A (captain, 26 October 2026): ship changes 1 to 3 of the EDI-75 report; this is change 2, with the console slices.
- A decision's number is assigned once and never reused; nothing here changes how a decision is raised or answered.
- Quiet and away rules are unchanged: nothing speaks to the captain in those modes.
- The notification speaks to the captain as EDI in the first person.

## Open questions to settle on the issue before code

- The report names the mechanism only in one paragraph.
  Before writing code, put these on the item's issue with lettered options and a recommendation, and stop until the captain answers:
  - A. Batch by time window (for example everything raised within a short window goes out as one round), recommended because it needs no new state and is easy to measure.
  - B. Batch until the captain next looks at the console (the "last look" of EDI-90).
  - C. Batch by an explicit "round" the primary closes.
- Which decisions count as urgent enough to skip batching (recommendation: none by default, one explicit flag on the card).

## Research and measurements

- 33 of 42 decisions went through chat; 28 of 33 chat answer rounds carried one decision; the console answered fastest.
- Each chat answer avoided saves a captain prompt and a primary turn.

## Read first

- [captain-decisions](../decisions/captain-decisions.md), [wakes-and-turn-end](../decisions/wakes-and-turn-end.md), [console](../decisions/console.md), [presence](../decisions/presence.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/decision/`, `application/wake/`, `web/decision/`, `web/console/`.

## How to verify

- `./mvnw verify`, then the native quick build.
- Wake tests with the recording clock: three decisions raised within the window produce one round; a fourth later produces its own.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

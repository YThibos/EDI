# EDI-55 Quiet delivers the primary's own notifications at once

| Field | Value |
|---|---|
| Key | EDI-55 |
| Kind | ship, a change to the repository, on branch `feature/EDI-55` |
| Wave and order | wave 5, order 18 |
| Source | EDI-52 plan, slice 4 |
| Size | S |
| Blocked by | nothing |
| Status | open |
| Cloud session | ok |

## Goal

In quiet and away presence, the wakes the primary settles without the captain are delivered at once, and only the wakes that would speak to the captain keep the 30-minute batch.

## Context

In quiet and away, every routine wake is batched for `presence.quietBatch` (30 minutes), including the pipeline gates and worker questions the primary settles alone.
Quiet starts by itself after 15 minutes without a typed prompt, so this applied most of a working afternoon.
In the first week 150 worker wakes waited 1,187 minutes in all between raise and delivery; 28 of them waited 20 to 31 minutes each, the batch's signature, 761 minutes together.
One task parked three times in one morning, 26 to 30 minutes each.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), finding F2 and slice 4.

## Acceptance criteria

- With presence quiet, a pipeline-gate wake reaches a parked `edi wait` within its long poll.
- The digest of such a wake tells the primary to end its turn without chat text.
- A wake that would speak to the captain, such as a decision raised for him, still waits for the batch.
- AT-46 still holds.
- `docs/decisions/presence.md` gains a history line.

## Requirements from decisions and answers

- The captain (3 November 2026) lifted this slice's wait on EDI-89: it needs neither plans nor stages, so it may start on its own.
- D42 A (captain, 26 October 2026): ship the whole console plan in order.
- Wakes the primary settles without the captain: pipeline gates, a worker's question for the primary, a done to accept.
- Quiet and away keep their rule: no text to the captain.

## Research and measurements

- 150 worker wakes (needs-decision, done, blocked) waited 1,187 minutes in total; 28 waited 20 to 31 minutes each (761 minutes together).
- Measured from the `wake` table's `raised_at` to `delivered_at`.

## Read first

- [presence](../decisions/presence.md), [wakes-and-turn-end](../decisions/wakes-and-turn-end.md), [supervision](../decisions/supervision.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/wake/` (`WaitForWakes`), `domain/wake/`, `domain/presence/`.

## How to verify

- `./mvnw verify`.
- Wake tests with presence quiet: a gate wake delivered at once, a decision wake held for the batch; use the `Clock` port, never `Thread.sleep`.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

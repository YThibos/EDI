# EDI-69 A standing CI note per project

| Field | Value |
|---|---|
| Key | EDI-69 |
| Kind | ship, a change to the repository, on branch `feature/EDI-69` |
| Wave and order | wave 5, order 30 |
| Source | EDI-52 plan, slice 18a |
| Size | S |
| Blocked by | [EDI-89](EDI-89.md) |
| Status | open |
| Cloud session | ok |

## Goal

The captain can record, per project, that CI is unavailable and local verification counts, and the pipeline's CI gate reads that note instead of asking each run.

## Context

GitHub Actions has refused every job on the EDI repository since 22 October 2026 because of a billing limit.
The captain accepted local verification instead (decision D13 B), yet each pipeline run waited at its CI gate and ended "passed with override".
In the research, 40 of the 67 CI steps on EDI carried findings naming the billing block, each parking six `ask-user` findings the worker had to approve.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), finding F10 and slice 18a, and [EDI-105](research/EDI-105-validation-pipeline.md), failure mode 3.

## Acceptance criteria

- `edi captain said` can record, per project, a standing note that CI is unavailable and local verification counts, and lift it.
- With the note set for EDI, a pipeline's CI gate passes naming the note and wakes nobody.
- Once the note is lifted, the gate asks again.

## Requirements from decisions and answers

- D13 B: local verification counts while CI is unavailable; a pull request's body carries the local results.
- Set and lifted only on the captain's recorded word, as every standing note ([captains-word](../decisions/captains-word.md)).
- The current pipeline skips its rebase and CI steps on projects whose CI does not run (EDI-104 landed); this item replaces the per-run answer with the stored note.

## Research and measurements

- A pipeline's CI step diagnosed jobs that never started and spent 8.8M weighted tokens over 67 calls.

## Read first

- [pipeline-steps](../decisions/pipeline-steps.md), [captains-word](../decisions/captains-word.md), [delivery](../decisions/delivery.md), [no-mistakes verification notes](../verification/no-mistakes.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/authority/`, `adapter/pipeline/`, `application/worker/GuardToolCall.java`.

## How to verify

- `./mvnw verify`, then the native quick build (a command option).
- A gate test: note set means the gate passes naming it; lifted means it asks again.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

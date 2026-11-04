# EDI-66 edi board show

| Field | Value |
|---|---|
| Key | EDI-66 |
| Kind | ship, a change to the repository, on branch `feature/EDI-66` |
| Wave and order | wave 5, order 28 |
| Source | EDI-52 plan, slice 15 |
| Size | S |
| Blocked by | [EDI-59](EDI-59.md) |
| Status | delivered |
| Issue | [#162](https://github.com/YThibos/EDI/issues/162) |
| Cloud session | ok |

## Goal

`edi board show` prints a research board's rounds read-only, so the primary can read what the captain answered without consuming it.

## Context

A round's full text exists only as an inbox message to the scout.
Reading it through the worker's inbox marks it received, and polling the board tool takes over EDI's listener, so two steers existed only to ask whether every answer had arrived.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), finding F8 and slice 15.

## Acceptance criteria

- A read-only primary command and endpoint print each round's prompts and the bound answers with their settlement.
- After a round arrives, `edi board show <task>` prints every prompt; the message's `received_at` stays empty and no `SteerReceived` is recorded; the scout's `edi inbox` still delivers it.
- The task panel's board tab shows the same.

## Requirements from decisions and answers

- F8 (captain, 26 October 2026, all thirteen findings approved): `edi board show <task> [--round N]`, read-only.
- Never mark an inbox message received from the primary's side.
- The caller is the primary or the captain's console session; no worker may call it.

## Research and measurements

- `board_answer` keeps only answers to bound decisions and the `BoardAnswered` event only counts; the full prompts live in inbox messages.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/application/board/ReceiveBoardFeedback.java`, `application/worker/WorkerInbox.java`, `adapter/board/`, `web/board/`.
- [research-boards](../decisions/research-boards.md), [console-and-boards](../console-and-boards.md).

## How to verify

- `./mvnw verify`, then the native quick build (a command and a route).
- The round test above: after the board shows, the scout's inbox still delivers the round with `received_at` empty beforehand.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

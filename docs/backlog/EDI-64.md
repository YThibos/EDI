# EDI-64 Quiet and away buttons

| Field | Value |
|---|---|
| Key | EDI-64 |
| Kind | ship, a change to the repository, on branch `feature/EDI-64` |
| Wave and order | wave 5, order 27 |
| Source | EDI-52 plan, slice 13 |
| Size | S |
| Blocked by | [EDI-89](EDI-89.md) |
| Status | open |
| Cloud session | ok |

## Goal

The page's presence control gains Quiet and Away, with Away asking for its mandate in a small form.

## Context

Quiet and away were deliberately chat-only; the captain now wants them on the page.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), slice 13, Q7 b.

## Acceptance criteria

- Quiet from the console sets quiet with the captain as cause.
- Away requires a mandate text and records it.
- The return brief still comes at the captain's next typed prompt.
- `docs/decisions/presence.md` records that the console may now set them.

## Requirements from decisions and answers

- Q7 b: quiet and away buttons.
- The console may do only what `Caller.Captain` allows; extend that list for these two acts and no others ([api-access](../decisions/api-access.md)).
- Away needs a mandate; never set it without one.

## Research and measurements

- The console could previously set present or demo only.

## Read first

- [presence](../decisions/presence.md), [api-access](../decisions/api-access.md).
- `edi-core/src/main/java/io/github/ythibos/edi/web/access/Caller.java`, `web/presence/`, `application/presence/`, `edi-console/src/app/`.

## How to verify

- `./mvnw verify`, then the native quick build.
- API tests for each caller: the captain's session may set quiet and away, every other caller is refused as before.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

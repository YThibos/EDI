# EDI-63 Tab title and browser notification

| Field | Value |
|---|---|
| Key | EDI-63 |
| Kind | ship, a change to the repository, on branch `feature/EDI-63` |
| Wave and order | wave 5, order 26 |
| Source | EDI-52 plan, slice 12 |
| Size | S |
| Blocked by | [EDI-89](EDI-89.md) |
| Status | delivered |
| Issue | [#150](https://github.com/YThibos/EDI/issues/150) |
| Cloud session | ok |

## Goal

The browser tab's title shows how many things wait on the captain, and a browser notification announces a new decision or review while the page is open.

## Context

The captain keeps the sitrep page open as a main panel and wants to notice news without watching it.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), slice 12, Q7 a.

## Acceptance criteria

- The document title reads "(N) SITREP · EDI" while N things wait on the captain.
- A new decision raises one browser notification and the title count rises; answering it lowers the count.
- Without the browser's permission, nothing is asked twice and nothing fails.

## Requirements from decisions and answers

- Q7 a (captain, 26 October 2026): tab title and notification.
- Notification text speaks as EDI in the first person.
- The title also states a page health state when the core does not answer, if EDI-110 has landed; otherwise leave that to EDI-110.

## Research and measurements

- The browser's Notification permission is requested once, on a user action.

## Read first

- `edi-console/src/app/sitrep/`, `edi-console/src/app/console/`, `edi-core/src/main/java/io/github/ythibos/edi/web/console/ConsoleStream.java`, [console](../decisions/console.md).

## How to verify

- `./mvnw verify`; component tests with a stubbed `Notification`.
- A browser check where one exists; otherwise say so.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

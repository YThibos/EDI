# EDI-56 autoQuietAfter accepts PT0S

| Field | Value |
|---|---|
| Key | EDI-56 |
| Kind | ship, a change to the repository, on branch `feature/EDI-56` |
| Wave and order | wave 5, order 19 |
| Source | EDI-52 plan, slice 5 |
| Size | XS |
| Blocked by | nothing |
| Status | delivered |
| Issue | [#147](https://github.com/YThibos/EDI/issues/147) |
| Cloud session | ok |

## Goal

`autoQuietAfter` accepts `PT0S` as "never quiet on silence", and a meeting still turns presence quiet.

## Context

`PresenceSettings` rejects a zero or negative `autoQuietAfter`, so the captain cannot switch off quiet on silence; a demo holds present for three hours at most.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), finding F9 and slice 5.

## Acceptance criteria

- With `autoQuietAfter: PT0S`, presence stays present after eight hours without a typed prompt.
- A meeting in progress still turns it quiet.
- `PresenceSettings` accepts zero, and `FollowThePresence` skips the silence rule then.
- `docs/configuration.md` and `docs/decisions/presence.md` describe it.

## Requirements from decisions and answers

- The captain (3 November 2026) lifted this slice's wait on EDI-89: it needs neither plans nor stages, so it may start on its own.
- D42 A: ship the whole console plan in order.
- A negative value is still refused.
- Time comes from the `Clock` port.

## Research and measurements

- Quiet starts by itself after 15 minutes without a typed prompt, which batches wakes for 30 minutes (see EDI-55).

## Read first

- [presence](../decisions/presence.md), [configuration](../configuration.md).
- `edi-core/src/main/java/io/github/ythibos/edi/adapter/home/PresenceSettings.java` and the presence services under `application/presence/`.

## How to verify

- `./mvnw verify`.
- Presence tests with the recording clock: eight hours of silence stays present; a meeting turns quiet.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

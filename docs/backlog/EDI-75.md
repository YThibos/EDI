# EDI-75 Spec first for features with several slices

| Field | Value |
|---|---|
| Key | EDI-75 |
| Kind | ship, a change to the repository, on branch `feature/EDI-75` |
| Wave and order | wave 1, order 2 |
| Source | EDI-75 change 1 |
| Size | S |
| Blocked by | nothing |
| Status | delivered |
| Issue | [#119](https://github.com/YThibos/EDI/issues/119) |
| Cloud session | ok |

## Goal

Any feature that will take more than one pull request goes through a scout's research board before implementation, and the `edi-ship` and `edi-scout` skills say so.

## Context

The first measured comparison of EDI against the previous harness found that the two features specified through a board first reached a sliced plan within a working day.
A registered project's large integration feature took 7 hours 1 minute from request to a sliced plan and the console sitrep work took 1 hour 27 minutes, against 18 hours 27 minutes for a comparable feature in the previous harness.
Their scouts cost 25.3M and 5.6M weighted tokens, between 1% and 5% of a week.
Specified slices then ran with few steers: that feature's slices took 0 to 2 steers each.
This item makes that practice the default.
[The EDI-75 measurements](research/EDI-75-measurements.md) hold the full comparison.

## Acceptance criteria

- `claude/skills/edi-ship/SKILL.md` tells the primary that a request which will take more than one merge request is not dispatched as a ship: it queues a scout on the request first, and the plan from the scout's board is what gets promoted into slices.
- `claude/skills/edi-scout/SKILL.md` says that such a scout puts its plan and its open questions on a research board, with acceptance criteria per slice, and that the captain's go on the board is what authorises promotion.
- Both skills cite the measured return in one line each, not the whole table.
- A request that takes one merge request is unchanged: it stays a ship.
- `ClaudeLayerTest` and the anchor budget test still pass, and no skill restates an anchor rule's text.

## Requirements from decisions and answers

- D46 A (captain, 26 October 2026): ship changes 1 to 3 of the EDI-75 report, of which this is change 1: spec first for multi-slice features, in the `edi-ship` and `edi-scout` skills.
- The rule lives in the skills, as the EDI-74 plan decides where rules live; the anchor only indexes rules and triggers.
  If EDI-83 (the anchor as an index) has not landed, do not add rule text to the anchor.
- The captain's stated direction is spec driven development: a spec is written and refined before the code, and the board is where it is settled.
- The Cucumber specification pilot for the Java projects is a separate task outside EDI and is not part of this item.

## Research and measurements

- Request to a sliced plan: a registered project's large integration feature 7h01, the console sitrep plan 1h27, the previous harness's requester identity feature 18h27.
- A board round carries a median of six answers (mean 9.2) where a chat message carries 1.1.
- The scouts' cost: 25.3M and 5.6M weighted tokens.

## Read first

- `claude/skills/edi-ship/SKILL.md` and `claude/skills/edi-scout/SKILL.md`.
- [research-first](../decisions/research-first.md), [research-boards](../decisions/research-boards.md), [primary-anchor-and-skills](../decisions/primary-anchor-and-skills.md).
- `edi-core/src/test/java/io/github/ythibos/edi/claude/ClaudeLayerTest.java`.

## How to verify

- `./mvnw verify`; the skills are read by tests, so a change that breaks their shape fails the build.
- Read both skills end to end after the edit: the new step must be one numbered step, one sentence per line, and must not repeat an anchor rule.
- A native build is not needed: skills are not native-image inputs; say so in the pull request.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

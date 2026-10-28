# EDI-76 Introspect again after a week of the EDI-52 and EDI-74 changes

| Field | Value |
|---|---|
| Key | EDI-76 |
| Kind | scout, research that ends in a report, on branch `research/EDI-76` |
| Wave and order | wave 6, order 36 |
| Source | The captain's word of 26 October 2026 |
| Size | M |
| Blocked by | [EDI-93](EDI-93.md) |
| Status | needs-captain-machine: the measurement reads the primary's transcripts and EDI's store, which exist only on the captain's machine. |
| Cloud session | not yet: see the status |

## Goal

Introspect again after the EDI-52 and EDI-74 changes have run for a week, and measure them against the first week's baselines.

## Context

The captain asked to do the upgrade in one go and then "revise and introspect again after another week of the patches".
A scout reruns the first comparison and the EDI-74 measures a week after the last step of the lifecycle plan lands.
It is a research task, not a ship; the deliverable is a report with the comparison and a board for the captain.

## Acceptance criteria

- A report compares each measure of [the EDI-74 plan](research/EDI-74-plan.md), "The review after a week", with its baseline and expected value:
  reports pasted per working day; reports the stop hook demanded; decision zones identical to the one before; report length median and longest; primary messages naming EDI in the third person; tasks under Ongoing that are not ongoing; merges of a task still changing without a question; merged tasks waiting on cleanup longer than a delivery round; minutes from a slice's wait met to its dispatch; effort words asked per plan; learnings expired or unticketed and rules with more than one home; refusals by each new guard, each read for a false refusal; a done accepted while a steer waited unread.
- It repeats the first comparison's tables for the new window against the same previous-harness window ([the EDI-75 measurements](research/EDI-75-measurements.md)).
- It adds the two questions the first comparison could not answer: post-merge defects, and what the higher effort bought once EDI-97 records it.

## Requirements from decisions and answers

- Captain (26 October 2026): do the whole upgrade in one go, then revise and introspect after a week of the patches.
- The review starts a week after the last lifecycle report step (EDI-93) lands, not on a fixed date.
- Research only: the report authorises nothing; questions to the captain go through a research board.
- The report is delivered in the repository as `docs/backlog/research/EDI-76-introspection.md`.

## Research and measurements

- The first comparison's scripts, which read the primary's transcripts and EDI's store, were kept in a research branch on the captain's machine.
  They are not in this repository.

## Not for the cloud session

The measurement reads the primary's Claude Code transcripts and EDI's store, which exist only on the captain's machine, and uses scripts that live there.
The item is listed as needs-captain-machine.

## Read first

- [The EDI-74 plan](research/EDI-74-plan.md), [the EDI-75 measurements](research/EDI-75-measurements.md), [research-first](../decisions/research-first.md), [research-boards](../decisions/research-boards.md).

## How to verify

- Every figure in the report names the query or script that produced it, as the first comparison did.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

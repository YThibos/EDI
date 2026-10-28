# EDI-81 Learnings name their ticket

| Field | Value |
|---|---|
| Key | EDI-81 |
| Kind | ship, a change to the repository, on branch `feature/EDI-81` |
| Wave and order | wave 1, order 1 |
| Source | EDI-74 plan, step 7 |
| Size | S |
| Blocked by | nothing |
| Status | open |
| Cloud session | ok |

## Goal

A learning in the captain's memory either is a dated fact or names the ticket that removes it, and EDI itself says when such a ticket has landed or does not exist, so the primary no longer notices expired workarounds by chance.

## Context

The primary's memory holds `learnings.md`, a file of operational facts and workarounds.
In the first week, eight of its 22 entries were workarounds for a defect in EDI itself, and ten were expired, duplicated or already enforced by the core.
Nothing linked a workaround to the change that removes it, so an entry outlived its cause.
`edi memory stow-list` already lists repeated headings, undated entries and learnings unconfirmed for 90 days; it knows nothing about tickets.
The EDI-74 plan fixes the format and the flagging in step 7; the research behind it is in [the EDI-74 plan](research/EDI-74-plan.md), sections 5.1 and 5.2.

## Acceptance criteria

- A learning is either a fact, dated with the day it was last confirmed as today, or a workaround, which ends with "until EDI-n", naming the key of the ticket that removes it.
- `StowList` reads the key at the end of each workaround and resolves it: an EDI-native key through the task store, a JIRA-style key through the `Tracker` port's status.
- With a learning "until EDI-69" and EDI-69 done, `edi memory stow-list` lists it as expired, with the instruction to delete it.
- With no task keyed EDI-99, a learning "until EDI-99" is listed as unticketed.
- A dated fact is listed as neither.
- The session-start digest's memory line names the count of expired and the count of unticketed workarounds.
- The `stow` skill says what to do with each: delete an expired workaround, give an unticketed one a ticket or turn it into a dated fact.
- Until the lifecycle stages are stored (EDI-85), "landed" for an EDI-native key means the task is DONE; once they are stored it means the stage LANDED or RETIRED.
  Write the check as one predicate so EDI-85 changes it in one place.

## Requirements from decisions and answers

- Q4 A (EDI-74 round 1): a workaround ends with "until EDI-n", and stow-list and the digest flag it as expired or unticketed.
- The memory files live in the captain's EDI home and are private; this item changes the code, the digest wording and the `stow` skill only, never a memory file.
- The primary writes the keys on today's workarounds (EDI-74 step 1, done by the primary on the captain's machine); this item does not edit memory.
- The digest and every new message template follow the persona rule: a text the primary reads says "you", a text that reaches the captain says "I", and EDI in the third person names only the project.
  The persona test in the invariant package scans every message template and fails the build on a breach.

## Research and measurements

- Every workaround found in the first week named a defect with a fix: a missing worklog booking, no plans, a merge order nobody recorded, CI that does not run, parallel requests that conflict, one open decision per task, and no report of whether the primary's wait is live.
- The classification of the 22 learnings is in [the EDI-74 plan](research/EDI-74-plan.md), section 5.1: ten could go at once, eight were workarounds, two were facts, one belonged in another file, one was a procedure.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/domain/memory/StowList.java`, `StowFinding.java`, `MemoryFile.java`, `MemoryEntry.java`.
- `edi-core/src/main/java/io/github/ythibos/edi/application/memory/MemoryQuery.java`.
- `edi-core/src/main/java/io/github/ythibos/edi/application/primary/SessionStartDigestQuery.java` and `domain/primary/SessionStart.java`.
- `claude/skills/stow/SKILL.md`.
- [primary-startup-context](../decisions/primary-startup-context.md), [memory-and-presence](../memory-and-presence.md), [trackers-and-time-booking](../decisions/trackers-and-time-booking.md).

## How to verify

- `./mvnw test -Pfast` while working, then `./mvnw verify` before the pull request.
- Unit tests over `StowList` with a memory fixture: an expired workaround, an unticketed one, a dated fact, and a workaround ticketed with a JIRA-style key against a stub `Tracker`.
- A digest test that pins the memory line with both counts.
- The native build is needed only if the change touches Spring configuration or resources; say in the pull request why it was not run otherwise.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

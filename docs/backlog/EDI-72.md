# EDI-72 Spike: a steer at the next tool call

| Field | Value |
|---|---|
| Key | EDI-72 |
| Kind | scout, research that ends in a report, on branch `research/EDI-72` |
| Wave and order | wave 5, order 31 |
| Source | EDI-52 plan, slice 19 |
| Size | S |
| Blocked by | nothing |
| Status | needs-captain-machine: the spike needs a real Claude Code worker session in a private tmux socket and a real long turn, which only the captain's machine gives. |
| Cloud session | not yet: see the status |

## Goal

Find out whether a steer can reach a worker at its next tool call instead of at the end of its turn, and report whether the steering record should change.

## Context

A busy worker receives a steer only when its turn ends, because the worker hooks answer only session start, prompt, stop, stop failure, notification and session end, with no tool-call hook.
Outside the core outages already fixed, steers took 46, 69, 50 and 96 minutes to arrive in the first week, and 16 "steer not received" alarms fired.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), finding F6 and slice 19.

## Acceptance criteria

- On a throwaway branch, a worker hook on `PostToolUse` returns a waiting steer as added context and records its receipt.
- In a real Claude Code session on a private tmux socket, a steer sent during a long turn is received within one tool call.
- The spike changes no production behaviour; the report says whether Claude Code acts on the added context mid-turn and whether the steering decision should change.
- A follow-up slice is proposed only if the spike works.
- The spike's report is delivered in the repository as `docs/backlog/research/EDI-72-steer-spike.md`.

## Requirements from decisions and answers

- D42 A: slice 19 is a spike, with its report as the deliverable; it authorises no production change.
- tmux only with a named socket, never EDI's own server (I-31).
- The deliverable is `research/EDI-72` with a report under the task's reporting rules; the branch is never merged into main.

## Research and measurements

- Steers took 46 minutes (one task's steer 2), 69 minutes (steer 4), 50 minutes (another's steer 1) and 96 minutes (a third task's steer 3) to arrive.
- `WorkerHooks` is the class that answers the six hook events today.

## Read first

- [steering](../decisions/steering.md), [worker-sessions](../decisions/worker-sessions.md), [claude-code verification notes](../verification/claude-code.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/worker/WorkerHooks.java`, `InboxNotice.java`, `NoticeOverdueSteers.java`, `claude/worker/settings.json`.

## Not for the cloud session

This spike needs a real Claude Code worker session, started with EDI's hooks, on a private tmux socket, in a turn long enough to send a steer into.
Only the captain's machine gives that.
The item stays out of the cloud session's pickup order and is listed as needs-captain-machine.
A cloud session that picks it up by mistake stops and says so on the issue.

## How to verify

- The spike's evidence is a recording of the session: the time the steer was sent, the tool call that followed, and the time the receipt was recorded.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

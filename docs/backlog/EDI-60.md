# EDI-60 Live check-in on a worker

| Field | Value |
|---|---|
| Key | EDI-60 |
| Kind | ship, a change to the repository, on branch `feature/EDI-60` |
| Wave and order | wave 5, order 23 |
| Source | EDI-52 plan, slice 9 |
| Size | M |
| Blocked by | [EDI-59](EDI-59.md) |
| Status | open |
| Cloud session | ok |

## Goal

The task panel's Live tab mirrors a worker's terminal pane read-only, about once a second, beside the worker's model, effort, context, tokens, active time and any waiting steer.

## Context

`edi worker peek <task>` prints the visible lines of a worker's pane through `Tmux.visibleLines` on EDI's own socket, behind the `WorkerRuntime.peek` port.
The pane's status line already shows model, effort, context used, session tokens and usage.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), section 6 and slice 9.

## Acceptance criteria

- `WorkerRuntime` gains a capture with colours (`capture-pane -p -e`).
- A server-sent event stream opened only while a panel's Live tab is open sends the pane about once a second and stops when the tab closes; the console renders it read-only.
- The Live tab shows the same lines as `edi worker peek <task>`, with colours, within two seconds of a change in the pane.
- Closing the tab ends the capture; no capture runs with no panel open.
- The page has no input that reaches a worker's pane, and the stream refuses any caller but the captain's console session and the primary.

## Requirements from decisions and answers

- Q3 A: the read-only terminal mirror first, next to the worker's status, model, effort, context and active time.
- Q4 and Q5 E: no command line in the page for now; input stays in Claude Code.
- A worker hears the captain only through `edi steer` and its inbox ([steering](../decisions/steering.md)).
- A pseudo-terminal attach was rejected: it needs a native pseudo-terminal library in the native image for the same picture.
- tmux is only ever used with a named socket (`-L` or `-S`); never touch another server (I-31).

## Research and measurements

- The existing server-sent event pattern (`web/console/ConsoleStream.java`) fits: one port method, one endpoint and one renderer.
- Rejected alternative: a conversation view of the worker's transcript.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/adapter/runtime/Tmux.java`, `application/port/WorkerRuntime.java`, `application/worker/PeekWorker.java`, `web/console/ConsoleStream.java`.
- [steering](../decisions/steering.md), [worker-sessions](../decisions/worker-sessions.md), [api-access](../decisions/api-access.md).

## How to verify

- `./mvnw verify`, then the native quick build.
- Adapter tests against a private tmux socket (skip with a reason when tmux is missing); a stream test that closing the tab ends the capture.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

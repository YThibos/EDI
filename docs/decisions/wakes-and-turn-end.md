# Wakes are rows written with their event, edi wait long-polls for them, and the primary's stop waits for the report and a live wait

**Kind:** Design

## Context

The primary keeps one background `edi wait`, which long-polls the core and exits with a digest when something needs it; wakes must survive a crash between printing and handling, and routine events never wake the primary.
Every state-changing turn ends with the turn report (I-21), and a turn must not end while work is in flight and no `edi wait` is live.
In quiet and away a routine wake turn ends with no chat text at all, and wakes are batched ([presence](presence.md)).
Claude Code opens every turn with `UserPromptSubmit`, lists the session's background jobs on `Stop`, and keeps calling the Stop hook while it blocks.

## Decision

`EventLog.record` asks `WakeRule.wakeFor` about every event, in one exhaustive switch per event type, and inserts a `wake` row in the same transaction for the events of the specification's wake list and the health rules; nothing the primary itself caused wakes it.
`WakeSignal` hears `WakeQueued` after commit and wakes every parked `edi wait`.
`POST /api/wait` answers only the session holding the primary role: it acknowledges every wake it delivered to that session before, then delivers every unacknowledged wake, or parks for up to 25 seconds and answers 204; `edi wait` asks again after a 204 and exits printing the digest.
A delivery whose long poll expired is withdrawn, and a wake delivered to a session that crashed stays unacknowledged for the next primary.
While the captain keeps the chat quiet, `WaitForWakes` delivers nothing until the oldest pending wake has waited `presence.quietBatch`, then delivers them all in one batch, and `WakeDigestText` tells the primary to end the turn without text.
Each prompt starts a turn and records the event log's latest sequence as its mark, and a report, the turn report or the sitrep, rendered with the session's header records its time on the turn; the next turn keeps the latest such time as its previous report, which decides which decision cards the turn report draws in full ([turn-report](turn-report.md)).
`TurnEnd.blockingReason` blocks the primary's stop when the turn changed state, the captain is present and no report was rendered, naming `edi report --turn -`, and when a task is in flight and no background job runs `edi wait`, which `BackgroundJob` finds with the guard's shell reader.
A turn's stop is blocked at most three times, and a standby session's stop is never held.
The stop that ends a turn records on it when it ended and whether a background job ran `edi wait` then; `PrimaryWait` reads that from the role holder's last turn: live, missing, unchecked while a turn runs, or missing because no session holds the role.
A session start other than a compaction starts a new Claude process whose background jobs are gone, so it ends the turn it finds without a wait.
A compaction keeps the Claude process and its background jobs, so it keeps the turn as it was.
A failed stop ends the turn without a wait, and a session with no turn yet reads as missing since it started.
`GET /api/primary/wait` answers it to any caller, `edi health` prints it below the core's own line without changing its exit code, and the status line adds "Wait: none" in dark red while a task is in flight and the wait is missing.

## Why

A wake can never exist without its event, and the primary acknowledges a wake by asking for the next one, which it does only after handling the last.
The turn-end rules read only the store, the presence and what Claude Code reports, so they hold whatever the primary remembers, and an unanswerable block cannot burn the captain's usage.

## Alternatives rejected

- Choosing wakes when `edi wait` reads the event log - payloads would be read back into typed events on every poll.
- Acknowledging a wake as it is printed - a primary that crashed before handling it would lose it.
- Requiring the report in quiet too - every routine wake would print into a chat nobody reads.
- Asking the core whether a wait is polling - racy at the end of the very turn that starts it.
- Checking for a live wait with `pgrep -f` - the pattern matches the checking shell itself.
- Failing `edi health` when the wait is missing - scripts read its exit code as the core's health alone.

## Consequences

- Good: a new event type must decide whether it wakes the primary, and a burst of events in quiet becomes one turn.
- Bad: in quiet a worker's question waits up to `presence.quietBatch` for the primary, while a decision reaches the captain at once as a notification.
- Bad: the wait's answer is what the last turn's stop saw, so it says nothing certain while a turn runs.

## When to revisit

When Claude Code stops listing background jobs on `Stop`, or a wake proves too urgent for the batch.

## How we got here

- 16/10/2026: slice S4 built durable wakes, `edi wait` and the turn-end rules.
- 17/10/2026: slice S8 batched wakes in quiet and let a quiet turn end without a report.
- 27/10/2026: EDI-95 let `edi health` and the status line say whether the wait is live, from what the turn's last stop saw.
- 28/10/2026: the turn began carrying the previous turn's report, and the Stop hook began naming the turn report (EDI-133).

# Wakes are rows written with their event, edi wait long-polls for them, and the primary's stop waits for the report and a live wait

**Kind:** Design

## Context

The primary keeps one background `edi wait`, which long-polls the core and exits with a digest when something needs it; wakes must survive a crash between printing and handling, and routine events never wake the primary.
Every state-changing turn ends with the boxed captain report (I-21), and a turn must not end while work is in flight and no `edi wait` is live.
In quiet and away a routine wake turn ends with no chat text at all, and wakes are batched ([presence](presence.md)).
Claude Code opens every turn with `UserPromptSubmit`, lists the session's background jobs on `Stop`, and keeps calling the Stop hook while it blocks.

## Decision

`EventLog.record` asks `WakeRule.wakeFor` about every event, in one exhaustive switch per event type, and inserts a `wake` row in the same transaction for the events of the specification's wake list and the health rules; nothing the primary itself caused wakes it.
`WakeSignal` hears `WakeQueued` after commit and wakes every parked `edi wait`.
`POST /api/wait` answers only the session holding the primary role: it acknowledges every wake it delivered to that session before, then delivers every unacknowledged wake, or parks for up to 25 seconds and answers 204; `edi wait` asks again after a 204 and exits printing the digest.
A delivery whose long poll expired is withdrawn, and a wake delivered to a session that crashed stays unacknowledged for the next primary.
While the captain keeps the chat quiet, `WaitForWakes` delivers nothing until the oldest pending wake has waited `presence.quietBatch`, then delivers them all in one batch, and `WakeDigestText` tells the primary to end the turn without text.
Each prompt starts a turn and records the event log's latest sequence as its mark, and a captain report rendered with the session's header records its time on the turn.
`TurnEnd.blockingReason` blocks the primary's stop when the turn changed state, the captain is present and no report was rendered, and when a task is in flight and no background job runs `edi wait`, which `BackgroundJob` finds with the guard's shell reader.
A turn's stop is blocked at most three times, and a standby session's stop is never held.

## Why

A wake can never exist without its event, and the primary acknowledges a wake by asking for the next one, which it does only after handling the last.
The turn-end rules read only the store, the presence and what Claude Code reports, so they hold whatever the primary remembers, and an unanswerable block cannot burn the captain's usage.

## Alternatives rejected

- Choosing wakes when `edi wait` reads the event log - payloads would be read back into typed events on every poll.
- Acknowledging a wake as it is printed - a primary that crashed before handling it would lose it.
- Requiring the report in quiet too - every routine wake would print into a chat nobody reads.
- Asking the core whether a wait is polling - racy at the end of the very turn that starts it.

## Consequences

- Good: a new event type must decide whether it wakes the primary, and a burst of events in quiet becomes one turn.
- Bad: in quiet a worker's question waits up to `presence.quietBatch` for the primary, while a decision reaches the captain at once as a notification.

## When to revisit

When Claude Code stops listing background jobs on `Stop`, or a wake proves too urgent for the batch.

## How we got here

- 16/10/2026: slice S4 built durable wakes, `edi wait` and the turn-end rules.
- 17/10/2026: slice S8 batched wakes in quiet and let a quiet turn end without a report.

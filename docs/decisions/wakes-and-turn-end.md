# Wakes are rows written with their event, the primary hears them through one wake stream, and its stop waits for the report and a live stream

**Kind:** Design

## Context

Wakes must survive a crash between printing and handling, and routine events never wake the primary.
Every state-changing turn ends with the turn report (I-21), and a turn must not end while work is in flight and nothing would tell the primary what needs it.
In quiet and away a routine wake turn ends with no chat text at all, and wakes are batched ([presence](presence.md)).
Claude Code opens every turn with `UserPromptSubmit` and keeps calling the Stop hook while it blocks.
Its Monitor tool runs a command in the background and turns each line the command prints into a notification for the session, until the command ends or the monitor's timeout kills it.
In the week of 26 October 2026, 502 of the primary's 2,484 tool calls went on restarting a one-shot `edi wait` after every wake and reading its output file.
On 2 November 2026 a second `edi wait`, started with `&` and its output sent to `/dev/null`, took a worker's question, printed it nowhere, and the question waited an hour.

## Decision

`EventLog.record` asks `WakeRule.wakeFor` about every event, in one exhaustive switch per event type, and inserts a `wake` row in the same transaction for the events of the specification's wake list and the health rules; nothing the primary itself caused wakes it.
`WakeSignal` hears `WakeQueued` after commit and wakes every parked wait.
The primary hears its wakes through one wake stream: `edi wait --follow`, started alone with the Monitor tool, which prints each wake the moment it is raised and keeps running; where Monitor is unavailable, `edi wait` alone in the background is a stream that ends after one delivery.
Every call of either names its stream, a fresh id, and its process; `WakeStreams` records the stream in `wake_stream` with the process's start tick, and a stream is live while it has not ended and its process runs.
`POST /api/wait` answers only the session holding the primary role, and `WakeStreams.admit` refuses a stream of that session while another of its streams is live, naming the live one's command and process; a stream whose process is gone, or that belongs to a session no longer primary, is retired instead, so a crash never locks the primary out.
A stream that has ended, cleanly or by retirement, is refused if it calls again.
`edi wait` refuses to start when its standard output is `/dev/null` or closed, which `StandardOutput` reads from `/proc/self/fd/1`, before it calls the core.
The guard refuses `edi wait` chained, piped, redirected or sent to the background in the same Bash or Monitor call ([pre-tool-guard](pre-tool-guard.md)).
A call offers the stream every wake not yet acknowledged and not yet delivered to this session, or parks for up to 25 seconds and answers 204; it answers the wakes' numbers in the `EDI-Wakes` header, and the stream's next call names the numbers it printed.
A wake counts delivered once its stream says it printed it; an offer a stream never confirms goes back when the stream is retired, and an offer whose long poll expired is withdrawn.
A wake is acknowledged when the primary records an event on its task after its delivery, or when a later wake for its task is printed; starting another wait never acknowledges anything.
A wake delivered to a session that crashed stays unacknowledged, so the next primary session is offered it again; the same session is never offered a wake it printed.
A wait registers with `WakeSignal` before it looks for wakes, so a wake committed in between is delivered by that first look or by the fresh look the wake starts; the looks of one call run one at a time and stop once it is answered, and a stream that has ended is offered nothing.
`edi wait` keeps its stream across a restart of the core: it asks again every two seconds for up to a minute, naming what it printed, and the restarted core finds the stream in the store.
Whenever the stream ends, by a refusal, a minute without the core or the session leaving the primary role, it says why on its standard output, so the Monitor's notification carries the reason; a stream that cannot print exits at once.
While the captain keeps the chat quiet, `WaitForWakes` delivers nothing until the oldest pending wake has waited `presence.quietBatch`, then delivers them all in one batch, and `WakeDigestText` tells the primary to end the turn without text.
Each prompt starts a turn and records the event log's latest sequence as its mark, and a report, the turn report or the sitrep, rendered with the session's header records its time on the turn; the next turn keeps the latest such time as its previous report, which decides which decision cards the turn report draws in full ([turn-report](turn-report.md)).
`TurnEnd.blockingReason` blocks the primary's stop when the turn changed state, the captain is present and no report was rendered, naming `edi report --turn -`, and when a task is in flight and the core knows no live wake stream of the session, naming the Monitor tool and `edi wait --follow`.
A turn's stop is blocked at most three times, and a standby session's stop is never held.
`PrimaryWait` reads the role holder's live stream at the moment it is asked: following, waiting for one wake, missing, or missing because no session holds the role.
`GET /api/primary/wait` answers it to any caller, `edi health` prints it below the core's own line without changing its exit code, and the status line adds "Wait: none" in dark red while a task is in flight and no stream is live.

## Why

A wake can never exist without its event, and a wake printed into a stream nobody reads cannot happen: the stream that would print it is refused before it starts.
Counting a wake delivered only once its stream printed it, and acknowledged only once the primary acted on its task or a newer wake for that task reached it, keeps a crash between printing and acting from losing it.
One stream per session turns the wake loop's three tool calls per wake into one notification per wake and a restart when the monitor's timeout ends it.
Liveness read from the stream's own process, rather than from what a turn's last stop saw, holds between turns and is the same answer for the stop hook, `edi health` and the status line.
The turn-end rules read only the store, the presence and the process table, so they hold whatever the primary remembers, and an unanswerable block cannot burn the captain's usage.

## Alternatives rejected

- Choosing wakes when `edi wait` reads the event log - payloads would be read back into typed events on every poll.
- Acknowledging a wake as it is printed - a primary that crashed before handling it would lose it.
- Acknowledging every wake a session was given when it next waits - a stray second wait acknowledged a question nobody saw.
- Looking for wakes before registering the wait - a wake committed in between would sit until the next wake or the 25-second expiry.
- A server-sent stream held open by the core - a write into the socket says nothing about the stream having printed it, and a core restart would end it.
- Reading the session's background jobs from the Stop hook's `background_tasks` - they say a command runs, not that it prints anywhere, and say nothing between turns.
- Requiring the report in quiet too - every routine wake would print into a chat nobody reads.
- Checking for a live wait with `pgrep -f` - the pattern matches the checking shell itself.
- Failing `edi health` when the wait is missing - scripts read its exit code as the core's health alone.

## Consequences

- Good: a new event type must decide whether it wakes the primary, and a burst of events in quiet becomes one turn.
- Good: a second stream, a wait sent to `/dev/null` and a wait chained onto another command are refused, each naming the one correct way to start the stream.
- Good: a dead stream reads as missing at once, in the stop hook, `edi health` and the status line, and the next stream replaces it and is offered what it never printed.
- Bad: in quiet a worker's question waits up to `presence.quietBatch` for the primary, while a decision reaches the captain at once as a notification.
- Bad: the Monitor tool ends a stream at its timeout, so the primary starts it again then; the notification of its end is what tells it.
- Bad: a wake whose task the primary never acts on stays unacknowledged until a newer wake for that task arrives, and the next session is offered it again.
- Bad: a closed standard output is read from `/proc`, so off Linux `edi wait` cannot tell and starts.

## When to revisit

When Claude Code's Monitor tool changes how it delivers lines or drops its timeout, or a wake proves too urgent for the batch.

## How we got here

- 16/10/2026: slice S4 built durable wakes, `edi wait` and the turn-end rules.
- 17/10/2026: slice S8 batched wakes in quiet and let a quiet turn end without a report.
- 27/10/2026: EDI-95 let `edi health` and the status line say whether the wait is live, from what the turn's last stop saw.
- 28/10/2026: the turn began carrying the previous turn's report, and the Stop hook began naming the turn report (EDI-133).
- 28/10/2026: EDI-120 recorded that a wait registers before it looks for wakes, after a test assumed the wake always reached a parked wait.
- 02/11/2026: EDI-158 gave the primary one wake stream, `edi wait --follow` under the Monitor tool, counted a wake delivered once printed and acknowledged once acted on, refused a second stream and a wait that prints nowhere, and read the wait's liveness from the stream's process; it carries EDI-152's hard rules.

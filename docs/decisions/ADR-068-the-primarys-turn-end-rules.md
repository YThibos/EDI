# ADR-068: The primary's stop waits for the captain report and for a live edi wait

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-21: every state-changing turn ends with the boxed captain report; AT-21: the primary's Stop hook detects a state change in the turn, an event in the store since the turn started, and blocks the stop until `edi report` ran in that turn.
Section 12.3: the Stop hook refuses to end a turn while work is in flight and no `edi wait` is live.
On Claude Code 2.1.301 every turn opens with `UserPromptSubmit`, a typed prompt and a background job's notice alike, and the `Stop` payload lists the session's in-flight background jobs in `background_tasks` with each shell job's command.
Claude Code keeps calling the Stop hook while it blocks, so a rule that cannot be met would leave a session that never ends.

**Decision:** Each prompt starts a turn and records the event log's latest sequence as the turn's mark; a captain report rendered through `GET` or `POST /api/report` with the session's header records the time on the turn.
`TurnEnd.blockingReason` blocks the primary's stop, in this order: when an event was recorded after the mark and no report was rendered since the turn started, with "render the captain report"; and when a task is in flight and no background shell job that runs `edi wait` is running or pending, with "start edi wait in the background".
`BackgroundJob` reads each job's command with the guard's shell reader, so `edi wait` is found inside `cd ... && edi wait 2>&1` and not inside `echo edi wait`.
A turn's stop is blocked at most three times, and a standby session's stop is never held.
A stop that proceeds ends the turn and clears a usage limit the session had stopped on.

**Motivation:** The rules read only the store and what Claude Code itself reports, so they hold whatever the primary remembers, and an unanswerable block cannot burn the captain's usage.

**Alternatives considered:**
- Counting only the primary's own commands as a state change - closer to "the turn changed nothing", but AT-21 defines the change as any event since the turn began.
- Asking the core whether a wait is polling - racy at the end of the very turn that starts the wait, before its first request arrives.
- Comparing event times with the turn's time - stored instants are text of varying width, so a sequence mark is exact where a time is not.

**Consequences:**
- Good: AT-21 and the live-wait rule are tested in milliseconds and end to end against the native core.
- Bad: while workers report often, most turns end with a report; slice S8's quiet mode changes what the primary prints, not this rule.

**Revisit when:** Slice S8's presence modes need quiet turns to end without a report, or Claude Code stops listing background jobs on `Stop`.

**Sources:** EDI rewrite specification, sections 12.3, 15.1 and I-21; `domain.primary.TurnEnd`, `PrimaryTurn`, `BackgroundJob`, `application.primary.PrimaryHooks`, `web.report.ReportController`, `adapter.store.EventLog`.

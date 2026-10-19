# ADR-123: The primary's stop waits for the captain report while the captain is present, and for a live edi wait always

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-21: every state-changing turn ends with the boxed captain report; AT-21: the primary's Stop hook detects a state change in the turn, an event in the store since the turn started, and blocks the stop until `edi report` ran in that turn.
Section 12.3: the Stop hook refuses to end a turn while work is in flight and no `edi wait` is live.
Section 12.7: in quiet and away modes a routine wake turn ends with no chat text at all; AT-46: a routine wake in quiet produces no chat text.
On Claude Code every turn opens with `UserPromptSubmit`, and the `Stop` payload lists the session's in-flight background jobs in `background_tasks` with each shell job's command.
Claude Code keeps calling the Stop hook while it blocks, so a rule that cannot be met would leave a session that never ends.

**Decision:** Each prompt starts a turn and records the event log's latest sequence as the turn's mark; a captain report rendered through `GET` or `POST /api/report` with the session's header records the time on the turn.
`TurnEnd.blockingReason` weighs the turn's `TurnCircumstances`: whether an event was recorded after the mark, whether the captain keeps the chat quiet (quiet or away), whether a task is in flight and whether an `edi wait` is live.
It blocks the stop, in this order: when the turn changed state, the captain is present and no report was rendered since the turn started, with "render the captain report"; and when a task is in flight and no background shell job that runs `edi wait` is running or pending, with "start edi wait in the background".
`BackgroundJob` reads each job's command with the guard's shell reader, so `edi wait` is found inside `cd ... && edi wait 2>&1` and not inside `echo edi wait`.
A turn's stop is blocked at most three times, and a standby session's stop is never held.
A stop that proceeds ends the turn and clears a usage limit the session had stopped on.

**Motivation:** The rules read only the store, the presence and what Claude Code itself reports, so they hold whatever the primary remembers; a quiet turn may end silently, while supervision never ends blind.

**Alternatives considered:**
- Requiring the report in quiet too - every routine wake would print a report into a chat the captain is not reading, the spam presence exists to stop.
- Asking the core whether a wait is polling - racy at the end of the very turn that starts the wait.

**Consequences:**
- Good: AT-21 and AT-46's silent wake turn are tested in milliseconds.
- Bad: what changed during quiet reaches the captain through notifications and the return brief, not through reports.

**Revisit when:** Claude Code stops listing background jobs on `Stop`.

**Sources:** EDI rewrite specification, sections 12.3, 12.7, 15.1 and I-21 (AT-21, AT-46); ADR-068, ADR-122; `domain.primary.TurnEnd`, `TurnCircumstances`, `PrimaryTurn`, `BackgroundJob`, `application.primary.PrimaryHooks`, `application.presence.PresenceQuery`, `web.report.ReportController`, `adapter.store.EventLog`.

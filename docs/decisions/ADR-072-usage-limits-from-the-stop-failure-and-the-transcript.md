# ADR-072: A usage limit is confirmed in the transcript, and its reset is met with one typed line

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.5: a usage limit is detected from the `StopFailure` hook's error, confirmed against the transcript's last record with the old harness's classification ported to Java; a live worker parked on a limit is resumed at the reset by one typed instruction, staggered one worker per minute; EDI never presses a key into a limit dialog; credits stay disabled.
On Claude Code 2.1.301 `StopFailure` names an error such as `rate_limit` or `billing_error`, and the transcript's usage-limit record is an assistant record with `isApiErrorMessage`, a text opening with "You've hit your" or "You've reached your", and the reset as epoch seconds in `quotaLimits.resetsAt` or `apiErrorParams.rate_limit_info.resetsAt`.
A resumed session appends to the transcript of the session it resumes, and a tool result can quote a limit message.
Claude Code itself may continue after the reset.

**Decision:** `ClaudeTranscript` reads the last 256 KB of a transcript, takes its last `user` or `assistant` record, and finds a limit stop only in an assistant API error whose text opens like a limit message and whose timestamp is at or after the worker's launch; it names the limit from the text and the reset from the record's own fields.
An unreadable, truncated or timestamp-less tail finds no stop, as in the old harness.
`WorkerHooks.stopFailed` parks the worker on the limit it finds, or records `WorkerStoppedOnAnError`; the worker's next prompt or normal stop lifts the park.
`ResumeAfterUsageLimits`, once per supervision round, takes the first parked worker whose limit has reset, at most one a minute, and types `EDI_OP: Continue the task from where you stopped; run edi inbox first` only at an idle prompt; a busy pane has continued on its own, and any other pane is reported once as `LimitResumeHeldBack`.
A limit without a reset, such as the monthly spend limit, is never resumed by EDI.
The primary's own limit stop is kept on its session and told to the next session (ADR-076).

**Motivation:** The transcript is the session's own account of why it stopped, read with the same care that kept the old harness from mistaking a quoted message for a stopped session.

**Alternatives considered:**
- Reading the reset time from the text, such as "resets 2:10pm (Europe/Brussels)" - the record carries the instant itself, without time zones or day rollover.
- Typing into the reset dialog's prompt - I-28 forbids keys in a dialog.

**Consequences:**
- Good: the fixtures in `edi-core/src/test/resources/claude-transcript/` keep the shapes of the old harness's cases and of real 2.1.299 records.
- Bad: a limit-stopped session's real dialog has not been captured live; the record shape comes from transcripts written by earlier stops.

**Revisit when:** Claude Code changes its limit records, or a limit stop is observed in a live EDI worker.

**Sources:** EDI rewrite specification, sections 12.5 and I-17, I-28; the old harness's `bin/fm-transcript-limit-stop.mjs` and its tests; `adapter.runtime.ClaudeTranscript`, `TmuxClaudeRuntime`, `domain.worker.UsageLimit`, `LimitPark`, `application.worker.WorkerHooks`, `application.supervision.ResumeAfterUsageLimits`; [docs/verification/claude-code.md](../verification/claude-code.md).

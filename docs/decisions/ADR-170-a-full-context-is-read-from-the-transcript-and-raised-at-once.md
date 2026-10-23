# ADR-170: A full context is read from the transcript every supervision round and raised at once

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** On 22/10/2026 a worker's conversation filled up while auto-compact was off: Claude Code showed "Context limit reached · /compact or /clear to continue · auto-compact is off", the worker sat idle for two hours while EDI still read it as busy, and only the three-hour busy ceiling raised it.
A live check on Claude Code 2.1.308 reproduced it: when the context is full, Claude Code fires neither `Stop` nor `StopFailure`, and no `Notification` arrived in five minutes, though the incident's session sent one `idle_prompt` a minute later.
Every prompt typed into such a session fires `UserPromptSubmit` and fails the same way at once, and `--resume` reopens the same full conversation.
The transcript records each failure as an assistant record with `isApiErrorMessage: true`, `error: "invalid_request"` and the text "Prompt is too long", decided by Claude Code itself without an API call.
ADR-072 already reads the transcript's last conversational record to confirm a usage limit, and ADR-057 keeps the pane probe out of health decisions.

**Decision:** `ClaudeTranscript.contextLimitStop` finds a full context only when the transcript's last `user` or `assistant` record is an assistant API error whose text opens with "Prompt is too long" and whose timestamp is at or after the worker's launch; `WorkerRuntime.contextLimitStopIn` gives the moment it was written.
`WorkerHooks` records the transcript's path from `UserPromptSubmit` as well as from `SessionStart`, so a worker whose session start was missed can still be read.
`NoticeFullContexts`, once per supervision round and before the workers are judged, reads the transcript of every busy or idle worker, and for a full context records the worker's turn as ended at that moment with `Worker.contextFilled`, kept in `worker.context_full_since`, and records `ContextLimitReached`.
Only a worker still in the turn that was read is marked, so a prompt that arrived meanwhile wins.
`SupervisionRules` reads such a worker as `WorkerHealth.ContextFull`, which raises no stale or busy notice; a prompt that gets through lifts it, and a stop or the session's end keeps it, so a relaunch still knows.
`ContextLimitReached` wakes the primary with `context-full`, whose line names the stop and the fresh relaunch of ADR-171.
`InboxNotice` types nothing into a full context: a steer waits in the inbox for the fresh session, as `waiting-for-a-fresh-session`.

**Motivation:** The transcript is the only account Claude Code reliably gives of a full context, and reading it each round raises the worker within thirty seconds instead of three hours, without waiting on a hook that does not come.

**Alternatives considered:**
- Waiting for `StopFailure` with `invalid_request` - it did not fire on 2.1.308.
- Acting on the `idle_prompt` notification - it came in the incident but not in the reproduction, so it cannot be relied on.
- Reading the pane for "Context limit reached" - ADR-057 keeps the pane out of health decisions, and the line scrolls away.
- Typing `/compact` into the pane - it loses the worker's reasoning without a plan for what follows, and EDI types only plain lines into an idle prompt.

**Consequences:**
- Good: `edi worker list` shows `context full since <time>`, and the fixtures `context-full.jsonl` and `quoted-context-limit.jsonl` in `edi-core/src/test/resources/claude-transcript/` keep the record's shape and a quote that is not a stop.
- Bad: each round reads up to 256 KB of every busy or idle worker's transcript.

**Revisit when:** Claude Code fires a hook when the context fills, changes the record it writes, or EDI turns auto-compact on for its workers.

**Sources:** the JUSTMD-53 incident of 22/10/2026; ADR-057, ADR-072, ADR-091, ADR-171; [docs/verification/claude-code.md](../verification/claude-code.md); `adapter.runtime.ClaudeTranscript`, `TmuxClaudeRuntime`, `application.port.WorkerRuntime`, `application.worker.NoticeFullContexts`, `WorkerHooks`, `InboxNotice`, `SteerDelivery`, `application.supervision.SupervisionRound`, `domain.worker.Worker`, `WorkerHealth`, `SupervisionRules`, `domain.event.DomainEvent.ContextLimitReached`, `domain.wake.WakeRule`, `WakeKind`, `adapter.store.WorkerStore`, `V10__a_full_context.sql`, `web.hook.HookController`, `web.wake.WakeDigestText`, `web.worker.HealthWords`.

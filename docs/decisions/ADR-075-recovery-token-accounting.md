# ADR-075: Every recovery relaunch records the tokens it spent

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 11.9: every relaunch and fresh restart records its token cost when its session ends, from the transcript's usage records (input, cache read, cache write and output), so the captain sees how often recovery hinders work and what it costs.
A resumed session writes into the transcript of the session it resumes, and Claude Code 2.1.301 repeats one message's usage on every record of that message, one per content block.
A worker lost in an outage sends no `SessionEnd`.

**Decision:** When a worker launched as a resume or a fresh start ends, by its `SessionEnd` hook or when the supervision round finds it dead, `CountRecoveryCost` asks `WorkerRuntime.tokensSpentIn` for the usage of the assistant records written since that incarnation's launch, each message counted once by its id, and stores it once in `recovery_cost` with a `RecoveryCostRecorded` event.
`edi recover` ends with the recoveries counted so far, resumed and fresh, and their tokens.

**Motivation:** The incarnation's launch time separates its own spending from the session it resumed, and counting at death as well as at the end covers the sessions an outage killed.

**Alternatives considered:**
- Claude Code's `cost-state` records - cumulative per process and undocumented.
- Counting every worker - slice S5 records each task's whole cost at cleanup (U-06); this record answers the cost of recovery alone.

**Consequences:**
- Good: the count needs no network and no model.
- Bad: a recovery that never ends and never dies is counted only when its task is cleaned up in a later slice.

**Revisit when:** Slice S5 adds the task cost record at cleanup, or the console shows recovery costs.

**Sources:** EDI rewrite specification, sections 11.9 and 21.3 (U-06); `application.worker.CountRecoveryCost`, `WorkerHooks`, `application.supervision.JudgeWorkers`, `adapter.runtime.ClaudeTranscript`, `adapter.store.RecoveryCostStore`, `domain.worker.TokenUsage`.

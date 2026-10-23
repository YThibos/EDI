# One supervision round keeps every time-based rule, and worker health is derived from hooks, tmux, the boot and the pipeline

**Kind:** Design

## Context

Several rules follow the clock rather than a commit: an unreceived steer, a launch that sent no hook, deferred dispatches, worker health, usage-limit resets, holds whose date arrived, presence and the calendar.
Workers are judged by structured state, never by shell liveness or their last status line (I-26); twice the old harness reported a fleet "alive" after the machine had restarted.
A worker that drives the validation pipeline idles while the pipeline reviews and tests, often for longer than twenty minutes.
The validation pipeline (no-mistakes) is an external tool the worker drives, and EDI only observes it.

## Decision

`SupervisionRound` runs every `edi.supervision.round` (30 seconds), each part guarded so one failing part does not stop the others: overdue steers, missed launches, judging workers, usage-limit resets ([recovery](recovery.md)), due holds, deferred dispatches, mechanical checks, presence and the calendar ([presence](presence.md)).
Each rule is a use case that tests call directly with a controllable clock.
`NoticeMissedLaunches` records `WorkerLaunchFailed` once for a worker with no `SessionStart` or `UserPromptSubmit` hook two minutes after launch, with what the pane probe reads.
`SupervisionRules` derives `WorkerHealth` afresh from the worker row, `WorkerRuntime.check` and the current `BootId`: stopped, failed to launch, finished, dead because the machine restarted, dead because its tmux server is gone or changed or its window closed, parked on a usage limit, starting, busy, idle, or waiting until a declared pause ends.
`MachineBoot` reads the kernel's boot id once at the core's start, every launch records it, and a worker launched in another boot is dead before tmux is asked at all.
`TmuxClaudeRuntime.check` reads the server's pid and start time from tmux and tells `SERVER_GONE`, `SERVER_CHANGED`, `WINDOW_CLOSED` and `OPEN` apart.
`noticeDue` names the one condition worth a wake, keyed by the moment it began: stale after `supervision.staleAfter` idle (PT20M), busy past three hours, a pause past its `until`, or died; `worker_notice` records each once, and a new activity or status starts a new condition.
A worker waiting on the primary, on a message it has not received, or on its task's running pipeline is never stale (`Awaited`).
`ObservePipelines` reads, every `edi.delivery.pipelines` (PT60S), the latest run of each `draft-review` ship whose work is not handed over, through `NoMistakesPipeline.latestRunOf`, which runs `no-mistakes axi status` in the copy and reads its `run:` block (`AxiStatus`); `PipelineRunStore` keeps the latest reading, and every status but the finished ones counts as running.
`NoMistakesPipeline.tokensSpentOn` opens the pipeline's `state.sqlite` read-only and sums each invocation's token deltas for the branch, or keeps the spend unknown ([cleanup](cleanup.md)).

## Why

Rules kept as state in the store make a restart only a delay, and one small round replaces the old harness's watcher.
Health computed from tmux, the kernel and the hooks cannot outlive the machine the way a stored "alive" flag can, and the boot id is the one fact a restart always changes.
A worker waiting on its own pipeline is doing its job, and the reading comes from the pipeline, not from the worker's word.

## Alternatives rejected

- A timer per rule, per message or per launch - lost on a restart, and more threads with no shared order.
- A health column the round updates - the old harness's false "alive".
- Reading the pane to tell busy from idle - the pane probe stays out of every health decision ([steering](steering.md)).
- `axi status --run <id>`, or the pipeline's logs - only the worker knows the run id, and the database already holds the counts.

## Consequences

- Good: a dead worker is noticed within one round, and `edi worker list` shows each worker's health in words, such as `dead: its tmux server changed`.
- Bad: every rule reacts within 30 seconds of its time, every round asks tmux about every serving worker, and a worker in a permission dialog counts as busy until the ceiling.
- Bad: the spend read depends on the pipeline's internal tables; a schema change makes it unknown, never wrong.

## When to revisit

When the round's tmux calls show up in its time, Claude Code reports a pending permission prompt as a state of its own, or the pipeline reports progress or spend through `axi`.

## How we got here

- 16/10/2026: slice S3 started the round for steers, launches, deferrals and mechanical checks; slice S4 added worker health, the boot id, outages, limit resets and due holds; slice S5 added the pipeline as a reason not to be stale.
- 17/10/2026 to 22/10/2026: presence, ticket settlement and the calendar joined the round.

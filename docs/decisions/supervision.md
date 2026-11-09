# One supervision round keeps every time-based rule, and worker health is derived from hooks, tmux, the boot and the pipeline

**Kind:** Design

## Context

Several rules follow the clock rather than a commit: an unreceived steer, a launch that sent no hook, deferred dispatches, worker health, usage-limit resets, holds whose date arrived, presence and the calendar.
Workers are judged by structured state, never by shell liveness or their last status line (I-26); twice the old harness reported a fleet "alive" after the machine had restarted.
A worker that drives the validation pipeline idles while the pipeline reviews and tests, often for longer than twenty minutes.
The validation pipeline (no-mistakes) is an external tool the worker drives; EDI observes its runs and sets nothing but the route its agent runs on ([pipeline-agent](pipeline-agent.md)), the work key in front of its commits and titles ([pipeline-commit-keys](pipeline-commit-keys.md)), and the steps every run skips ([pipeline-steps](pipeline-steps.md)).
A worker's hooks fail while the core is down: Claude Code ends the turn anyway, writes the failure into the transcript as a `stop_hook_summary` record with `hookErrors` followed by a `turn_duration` record, and fires no hook again until something is typed ([docs/verification/claude-code.md](../verification/claude-code.md)).
When a worker's conversation fills up with auto-compact off, Claude Code fires no `Stop`, `StopFailure` or reliable `Notification`, while its transcript records each failed prompt as an assistant API error opening with "Prompt is too long" ([docs/verification/claude-code.md](../verification/claude-code.md)).

## Decision

`SupervisionRound` runs every `edi.supervision.round` (30 seconds), each part guarded so one failing part does not stop the others: overdue steers, missed launches, full contexts, missed stops, judging workers, unanswered worker questions, usage-limit resets ([recovery](recovery.md)), due holds, deferred dispatches, mechanical checks, presence, the calendar ([presence](presence.md)) and heavy runs at once ([local-native-builds](local-native-builds.md)).
Each rule is a use case that tests call directly with a controllable clock.
`NoticeMissedLaunches` records `WorkerLaunchFailed` once for a worker with no `SessionStart` or `UserPromptSubmit` hook two minutes after launch, with what the pane probe reads.
`SupervisionRules` derives `WorkerHealth` afresh from the worker row, `WorkerRuntime.check` and the current `BootId`: stopped, failed to launch, finished, dead because the machine restarted, dead because its tmux server is gone or changed or its window closed, parked on a usage limit, starting, busy, idle, or waiting until a declared pause ends.
`MachineBoot` reads the kernel's boot id once at the core's start, every launch records it, and a worker launched in another boot is dead before tmux is asked at all.
`TmuxClaudeRuntime.check` reads the server's pid and start time from tmux and tells `SERVER_GONE`, `SERVER_CHANGED`, `WINDOW_CLOSED` and `OPEN` apart.
`noticeDue` names the one condition worth a wake, keyed by the moment it began: stale after `supervision.staleAfter` idle (PT20M), busy past three hours, a pause past its `until`, or died; `worker_notice` records each once, and a new activity or status starts a new condition.
A worker waiting on the primary, on a message it has not received, or on its task's running pipeline is never stale (`Awaited`).
`RemindOfUnansweredQuestions`, after the workers are judged, records `WorkerQuestionUnanswered` for each serving worker whose last status is a `needs-decision` or `blocked` once `supervision.questionReminder` (PT10M) has passed since the question or since its last reminder, read from the event log, so a restart loses none; it wakes the primary with the worker, the key and the question, and the wake says how long it has waited.
The reminders stop once a steer that resolves the key is recorded, even before the worker acknowledges it, and once the worker reports another state; while the captain is away they pause, and the next round after the return raises the one that is due, while quiet still reminds, since the primary answers workers in quiet.
`edi health` lists each open question, with its worker, key and age, from `GET /api/workers/questions` (`OpenWorkerQuestionsQuery`), which answers the EDI home's own token and refuses a worker's, until the resolving steer is acknowledged.
`StatusReport.fromWorker` refuses a `needs-decision` or `blocked` without its `--key`, which the steer that answers it settles ([steering](steering.md)), and a `paused` without its `--until`, the moment its notice is due.
`NoticeFullContexts`, each round before workers are judged, reads every busy or idle worker's transcript through `ClaudeTranscript.contextLimitStop`, which finds a full context only in a last record of that shape written since the launch; it ends the worker's turn at that moment (`Worker.contextFilled`, `worker.context_full_since`) unless a newer prompt arrived, and records `ContextLimitReached`, which wakes the primary.
`SupervisionRules` reads such a worker as `WorkerHealth.ContextFull`, which raises no stale or busy notice; a prompt that gets through lifts it, a stop keeps it so a relaunch starts afresh ([recovery](recovery.md)), and `InboxNotice` types nothing into it, so a steer waits for the fresh session.
`NoticeMissedStops`, after `NoticeFullContexts` in the round, reads every busy worker's transcript through `ClaudeTranscript.missedStop`, which finds a stop only when the last turn record is a `turn_duration` that follows a `stop_hook_summary` with `hookErrors`, written since the worker's turn began; it ends that turn at that moment (`Worker.turnEnded`) and records `WorkerTurnEnded` dated then, unless a newer prompt arrived, and steers the worker with a message from EDI to retry what it refused and carry on, so its notice is typed into the idle prompt and the worker reads every message that waited ([steering](steering.md)); `WorkerStopMissed` then wakes the primary with the message's number and whether the pane took its notice.
`NoticeMissedStops` also reads each busy worker's transcript through `ClaudeTranscript.usageLimitStop` since its turn began, as a usage limit hit while the core was down fails the `StopFailure` hook; it finds a `LimitStop`, the limit with the time of the record that names it, parks such a worker on its limit (`Worker.parkedOn`) unless a newer prompt arrived, records `UsageLimitReached` as the hook would have but dated when the limit stopped the turn, and sends it no message, so `ResumeAfterUsageLimits` resumes it at the reset ([recovery](recovery.md)).
It then reads, through `ClaudeTranscript.errorStop`, a turn whose last turn records are an API error followed by `turn_duration`, and counts it only once `NoticeMissedStops.HOOK_GRACE` (two minutes) has passed since the turn ended, so it never races a live `StopFailure` hook; it ends the turn at that moment unless a newer prompt arrived, records `WorkerStoppedOnAnError` as the hook would have but dated when the turn ended, and wakes the worker with the same message and `WorkerStopMissed` as a failed stop hook.
`WorkerHooks` records a session's transcript path from `UserPromptSubmit` as well as `SessionStart`, so a worker whose session start was missed can still be read.
`ObservePipelines` reads, every `edi.delivery.pipelines` (PT60S), the latest run of each `draft-review` ship whose work is not handed over or whose stored latest run is still running or still changes the request ([fleet-snapshot](fleet-snapshot.md)), through `NoMistakesPipeline.latestRunOf`, which runs `no-mistakes axi status` in the copy and reads its `run:` block (`AxiStatus`), then reads from the pipeline's `state.sqlite` whether the run records its CI checks as passed (`runs.ci_ready_at`, `PassedChecks`), which the status never prints; `PipelineRunStore` keeps the latest reading, and every status but the finished ones (completed, failed, cancelled, aborted, checks_passed, handed_off and ci_monitor_interrupted) counts as running.
`NoMistakesPipeline.tokensSpentOn` opens the pipeline's `state.sqlite` read-only and sums each invocation's token deltas for the branch, or keeps the spend unknown ([cleanup](cleanup.md)).
An invocation whose input is at least its cache reads and writes together counts them in its input, as the pipeline has since 2 November 2026; its uncached input is then its fresh input, the input less its cache reads, less its cache writes, so each token lands in one column of the task's cost (`PipelineSpend`).

## Why

Rules kept as state in the store make a restart only a delay, and one small round replaces the old harness's watcher.
Health computed from tmux, the kernel and the hooks cannot outlive the machine the way a stored "alive" flag can, and the boot id is the one fact a restart always changes.
A worker waiting on its own pipeline is doing its job, and the reading comes from the pipeline, not from the worker's word.

## Alternatives rejected

- A timer per rule, per message or per launch - lost on a restart, and more threads with no shared order.
- A health column the round updates - the old harness's false "alive".
- Reading the pane to tell busy from idle, or for "Context limit reached" - the pane probe stays out of every health decision ([steering](steering.md)).
- Keeping a worker's failed hooks in a file for the core to replay - the CLI would hold state the store should, and a replay could not wake the worker.
- Ending the turn at every unheard `turn_duration` - an interrupt by the captain ends a turn with no stop hook, and must not be answered with a message.
- Typing a resume line straight into the pane, as after a usage limit - nothing would prove it arrived, while a steer is proved by the worker's own hook and raised when overdue.
- Waiting for `StopFailure` or the `idle_prompt` notification on a full context - neither came reliably on Claude Code 2.1.308.
- Typing `/compact` into a full context - loses the worker's reasoning without a plan, and EDI types only plain lines into an idle prompt.
- `axi status --run <id>`, or the pipeline's logs - only the worker knows the run id, and the database already holds the counts.

## Consequences

- Good: a dead worker is noticed within one round, and `edi worker list` shows each worker's health in words, such as `dead: its tmux server changed`.
- Bad: every rule reacts within 30 seconds of its time, every round asks tmux about every serving worker, and a worker in a permission dialog counts as busy until the ceiling.
- Good: a worker that stopped while the core was down is idle again within one round of the core's return, its steers reach it and the primary hears of it.
- Good: a full context is raised within one round instead of at the three-hour busy ceiling, and `edi worker list` shows `context full since <time>`.
- Bad: the spend read depends on the pipeline's internal tables; a schema change makes it unknown, never wrong.
- Bad: each round reads up to 256 KB of every busy or idle worker's transcript, a busy one four times.

## When to revisit

When the round's tmux calls show up in its time, Claude Code reports a pending permission prompt as a state of its own, the pipeline reports progress or spend through `axi`, or Claude Code fires a hook when the context fills or retries a hook that failed.

## How we got here

- 16/10/2026: slice S3 started the round for steers, launches, deferrals and mechanical checks; slice S4 added worker health, the boot id, outages, limit resets and due holds; slice S5 added the pipeline as a reason not to be stale.
- 17/10/2026 to 22/10/2026: presence, ticket settlement and the calendar joined the round.
- 22/10/2026: a worker whose context had filled sat idle for two hours while EDI read it as busy, so the round reads each transcript for a full context (EDI-35).
- 23/10/2026: EDI began setting the route of the pipeline's agent, its one write to the pipeline ([pipeline-agent](pipeline-agent.md), EDI-46).
- 23/10/2026: EDI began writing each delivered repository's commit keys into the pipeline's configuration too ([pipeline-commit-keys](pipeline-commit-keys.md), EDI-21).
- 23/10/2026: workers whose stop hooks failed during a core outage stayed busy in EDI for hours with steers undelivered, so the round reads each busy transcript for a stop EDI missed and wakes the worker through its inbox, or parks it on the usage limit that stopped it, and wakes a worker whose turn ended on another API error once the hook has had its time (EDI-38).
- 26/10/2026: a handed-over task's stored run stayed running after its pipeline finished, so the round keeps reading a handed-over ship's pipeline while its stored run is running (EDI-53).
- 26/10/2026: the EDI-74 review found that the brief asked for a key and an end that `edi status` never required, so it refuses a status without them (EDI-77).
- 27/10/2026: a stop the round notices late is dated at the time the transcript shows it, so the measure of a task's active time ends the stretch there ([trackers-and-time-booking](trackers-and-time-booking.md), EDI-54).
- 27/10/2026: the round began noticing native builds and test runs that several workers run at once ([local-native-builds](local-native-builds.md), EDI-99).
- 27/10/2026: EDI began naming the steps every pipeline run skips in the brief's pipeline call ([pipeline-steps](pipeline-steps.md), EDI-104).
- 03/11/2026: a needs-decision wake was taken by a stray `edi wait` and the worker waited an hour on a question nothing raised again, so an unanswered question comes back to the primary every ten minutes until a resolving steer is sent (EDI-153).
- 09/11/2026: the pipeline began counting cache reads and writes in its input on 2 November 2026, which EDI took as uncached input and so recorded every ship's pipeline cost several times too high, so an invocation's input is read less the cache tokens it holds (EDI-235).
- 09/11/2026: a CI watch on passed checks read as a running pipeline, and a handed-off or interrupted run as still running, so EDI began reading the pipeline's recorded checks verdict beside the status and counts those two as finished ([fleet-snapshot](fleet-snapshot.md), EDI-238).

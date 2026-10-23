# A relaunch resumes the recorded session in the task's own copy, several dead workers wait on a numbered outage list, and usage limits are met at their reset

**Kind:** Design

## Context

A relaunch resumes the same worktree and Claude session with `--resume`, never another task's copy (I-25), and resolves its model and effort from the category as a fresh dispatch does (I-16).
EDI kills only process ids it recorded (I-30).
When more than one worker is dead, EDI relaunches none and acts only on the numbers the captain names, one at a time (I-24), so no restart storm follows a lost machine.
A usage limit is detected from the `StopFailure` hook and the transcript, and a live worker parked on one is resumed at its reset by one typed line; EDI never presses a key into a limit dialog, and credits stay disabled.
A resumed session appends to the transcript of the session it resumes, and a tool result can quote a limit message.

## Decision

A worker is launched as a `dispatch`, a `resume` or a `fresh` start (`LaunchKind`); `LaunchWorker.relaunch` launches the next incarnation in `EdiHome.worktreeOf` with the route the rules give now and a captain's override when one is given.
A resume passes `--resume` with the latest session id and a first line from `ResumeNotes` naming the branch, the head, the last status and the answers since; a fresh start draws a new session id and writes the brief again.
`RelaunchWorker` refuses a worker that still runs and a held task, and a copy in which `ProcessTable.workingIn` finds a process.
`StopWorker` checks the pane's process is still a child of the recorded server, then `ProcessTable.stopTree` sends SIGTERM to it and its descendants and SIGKILL to whatever outlives ten seconds.
When a round finds two or more dead workers, `OutageHoldList` lists each in `outage_entry`, numbered after the highest open number, holds its task for the captain and records `LostInOutage`; one dead worker alone records `WorkerDied`.
`edi recover` prints the list, with each copy verified by git (`OutageListQuery`), and `edi recover <n> resume|fresh [--model M] [--effort E]` or `drop [--discard-work]` answers one entry (`RecoverFromOutage`, `DropFromOutage`); a relaunch is refused while a usage limit still holds.
`ClaudeTranscript` reads a transcript's last 256 KB and finds a limit stop only in its last record, an assistant API error whose text opens like a limit message, dated after the incarnation's launch, with the reset from the record's own fields.
`WorkerHooks.stopFailed` parks the worker on that limit, and `ResumeAfterUsageLimits` types `EDI_OP: Continue the task from where you stopped; run edi inbox first` into one reset worker a minute, at an idle prompt only; a limit without a reset is never resumed by EDI.
When a resume or fresh start ends or dies, `CountRecoveryCost` stores the tokens its records spent since its launch, each message once, in `recovery_cost`, and `edi recover` reports the total.

## Why

The worker's context and uncommitted work both survive a lost server, and nothing EDI did not start is ever signalled.
An outage is answered entry by entry with what the captain needs to choose on the list itself.
The transcript is the session's own account of why it stopped, read with the care that keeps a quoted message from passing for a stop.

## Alternatives rejected

- Relaunching fresh by default - the worker would lose its reasoning; fresh stays an explicit choice.
- Stopping through `tmux kill-window` - SIGHUP without the grace period.
- A global outage number sequence - numbers in the hundreds for a list of three.
- Reading the reset time from the message text - the record carries the instant itself.

## Consequences

- Good: the stand-in in the native test records `--resume` with the first session's id after a simulated restart, and recovery's cost is known.
- Bad: reading every process's working directory scans `/proc`, which is Linux only, and a limit dialog has not been captured live.

## When to revisit

When Claude Code changes `--resume` or its limit records, or a worker needs a copy of another project.

## How we got here

- 16/10/2026: slice S4 built relaunch, stop, the outage list, usage limits and recovery cost.
- 17/10/2026: slice S8 let the outage list drop a task on the captain's word ([captains-word](captains-word.md)).

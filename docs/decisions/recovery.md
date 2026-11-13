# A relaunch resumes the recorded session in the task's own copy or, for a full context or a session without a transcript, starts afresh with a handover; several dead workers wait on a numbered outage list, and usage limits are met at their reset

**Kind:** Design

## Context

A relaunch resumes the same worktree and Claude session with `--resume`, never another task's copy (I-25), and resolves its model and effort from the category as a fresh dispatch does (I-16).
EDI kills only process ids it recorded (I-30).
When more than one worker is dead, EDI relaunches none and acts only on the numbers the captain names, one at a time (I-24), so no restart storm follows a lost machine.
A usage limit is detected from the `StopFailure` hook and the transcript, and a live worker parked on one is resumed at its reset by one typed line; EDI never presses a key into a limit dialog, and credits stay disabled.
A resumed session appends to the transcript of the session it resumes, and a tool result can quote a limit message.
A worker whose context is full cannot be resumed: `--resume` reopens the same full conversation ([supervision](supervision.md)), while its work lives on in the copy, the store and the task directory.
A session stopped before Claude Code saved its transcript cannot be resumed either: `--resume` prints "No conversation found with session ID" and exits at once, so the worker's window closes ([docs/verification/claude-code.md](../verification/claude-code.md)).
A session's transcript lives only under the configuration directory of the Claude login that ran it, and `--resume` from another login's directory answers the same way ([claude-logins](claude-logins.md)).

## Decision

A worker is launched as a `dispatch`, a `resume` or a `fresh` start (`LaunchKind`); `LaunchWorker.relaunch` launches the next incarnation in `EdiHome.worktreeOf` with the route the rules give now and a captain's override when one is given.
A resume passes `--resume` with the latest session id and a first line from `ResumeNotes` naming the branch, the head, the last status and the answers since; a fresh start draws a new session id and writes the brief again.
`RelaunchWorker` refuses a worker that still runs and a held task, and a copy in which `ProcessTable.workingIn` finds a process; a stopped or dead worker whose context is full is relaunched as a `fresh` start, `edi worker relaunch --fresh` starts one afresh on the captain's word, and `LaunchWorker` refuses to resume a full context whichever command asks.
Every launch records its Claude login on the worker by name (`WorkerLaunch.login`, the store's `worker.claude_login`), and a worker launched before the login was recorded reads as `default`.
A resume runs on the login the earlier worker recorded, a session adopted from the old harness on `default`, and a dispatch and a fresh start on the login the project's registry entry names at that moment ([claude-logins](claude-logins.md)).
Before any resume `LaunchWorker` asks `WorkerRuntime.hasATranscriptOf` whether the session left a transcript in the sessions folder of the login it resumes on (`ClaudeSessions`); when it did not, the relaunch is a `fresh` start on the project's login instead, its reason and the handover name the session that left no transcript, and the confirmation says so.
A resume whose recorded login the machine profile no longer names is refused, naming the login and the two ways out: restoring the login under `claude.logins`, or a fresh start with `edi worker relaunch <task> --fresh`, or `edi recover <n> fresh` for an entry of the outage list.
A worker's first hook proves its login: when the transcript path `SessionStart` reports lies outside the configuration directory of the recorded login (`ClaudeLoginFiles`), `WorkerHooks` records `WorkerRanOnAnotherLogin` with the directory the session ran in and the one it should have, which wakes the primary, and stops the worker through `StopWorker`.
Every fresh start writes `handover.md` beside the brief, and its first prompt asks the worker to read it: `Handovers` writes, from the store and the copy, who takes over from whom and why, the branch, its head and the copy, the commits beyond `origin/<base>`, the uncommitted changes, the latest pipeline run, the brief and any scout's report, the last status, the decisions answered since, and how to carry on.
`StopWorker` checks the pane's process is still a child of the recorded server, then `ProcessTable.stopTree` sends SIGTERM to it and its descendants and SIGKILL to whatever outlives ten seconds.
It then ends, with the same timing and by process id alone (`ProcessTable.stopEach`), every process whose working directory or executable lies inside the task's copy (`ProcessTable.heldIn`), which a worker's process tree no longer holds once a server it started was re-parented; it spares the core and its ancestors, every serving worker's tmux server and every other worker's pane tree, and the confirmation names each process it ended, so a relaunch finds the copy free and still refuses one something else holds.
When a round finds two or more dead workers, `OutageHoldList` lists each in `outage_entry`, numbered after the highest open number, holds its task for the captain and records `LostInOutage`; one dead worker alone records `WorkerDied`.
The list holds only work worth resuming: a task whose work is merged, landed, done or dropped has nothing left to resume (`Task.hasWorkToResume`), so a dead worker of such a task is not listed, and `LeaveTheOutageList` closes a task's entry and releases its outage hold when `TaskStages` moves it that far and, for an entry listed before, each time `OutageHoldList` builds the list and each time `edi recover <n>` answers a number, which it then refuses as no longer on the list.
The other entries keep their numbers.
`edi recover` prints the list, with each copy verified by git (`OutageListQuery`), and `edi recover <n> resume|fresh [--model M] [--effort E]` or `drop [--discard-work]` answers one entry (`RecoverFromOutage`, `DropFromOutage`); a relaunch is refused while a usage limit still holds.
`ClaudeTranscript` reads a transcript's last 256 KB and finds a limit stop only in its last record, an assistant API error whose text opens like a limit message, dated after the incarnation's launch, with the reset from the record's own fields.
`WorkerHooks.stopFailed` parks the worker on that limit unless it is already parked on it (`Worker.isParkedOn`), as the round does when the core missed that hook ([supervision](supervision.md)), and `ResumeAfterUsageLimits` types `EDI_OP: Continue the task from where you stopped; run edi inbox first` into one reset worker a minute, at an idle prompt only; a limit without a reset is never resumed by EDI.
When a resume or fresh start ends or dies, `CountRecoveryCost` stores the tokens its records spent since its launch, each message once, in `recovery_cost`, and `edi recover` reports the total.

## Why

The worker's context and uncommitted work both survive a lost server, and nothing EDI did not start is ever signalled.
An outage is answered entry by entry with what the captain needs to choose on the list itself.
The transcript is the session's own account of why it stopped, read with the care that keeps a quoted message from passing for a stop.

## Alternatives rejected

- Relaunching fresh by default - the worker would lose its reasoning; fresh stays an explicit choice, or the only way on for a full context.
- Putting a full context on the outage hold list - the list suits two or more dead workers, not one whose only way on is a fresh start.
- Every relaunch on the project's current login - a resume after the project moved to another login would find no transcript and lose its session.
- Recording the login's directory on the worker - a directory is a fact of the machine, so the store keeps the login's name and the machine profile its directory.
- Trusting the launch to have set the login - a worker that ran on another directory would spend another subscription and write its transcript where no resume finds it, so its first hook proves the login.
- Writing the handover into the brief - the brief stays the same for every incarnation and within its line budget, while the handover changes with every start.
- Stopping through `tmux kill-window` - SIGHUP without the grace period.
- A global outage number sequence - numbers in the hundreds for a list of three.
- Reading the reset time from the message text - the record carries the instant itself.

## Consequences

- Good: the stand-in in the native test records `--resume` with the first session's id after a simulated restart, refuses to resume a session without a transcript as Claude Code does, and recovery's cost is known.
- Good: a full context costs the primary two commands, `edi worker stop` and `edi worker relaunch`, and `edi recover <n> fresh` gets the same handover.
- Good: a worker on any Claude login keeps its session across relaunches, and a project moved to another login carries its next fresh start there.
- Bad: a login taken out of the machine profile strands the sessions of the workers that ran on it until it is restored or they start afresh.
- Bad: reading every process's working directory scans `/proc`, which is Linux only, and a limit dialog has not been captured live.
- Bad: the handover reads the copy's git state at launch, so a copy git cannot read gives a thinner one.

## When to revisit

When Claude Code changes `--resume` or its limit records, can compact or clear a session from the command line, or a worker needs a copy of another project.

## How we got here

- 16/10/2026: slice S4 built relaunch, stop, the outage list, usage limits and recovery cost.
- 17/10/2026: slice S8 let the outage list drop a task on the captain's word ([captains-word](captains-word.md)).
- 22/10/2026: a worker whose context had filled could not be resumed, and the primary wrote its successor a handover by hand, so a full context now starts afresh with a handover EDI writes (EDI-35).
- 23/10/2026: the round parks a worker whose usage limit the core missed during an outage, so the hook parks a worker only once on the same limit (EDI-38).
- 27/10/2026: a worker stopped within seconds of its start left no transcript, and two relaunches resumed its session and closed at once, so a resume without a transcript starts afresh with a handover (EDI-107).
- 03/11/2026: a dev server a worker started outlived the worker's own process tree for 90 minutes and the relaunch was refused until the primary ended it by hand, so a stop also ends what the worker left running in its copy (EDI-151).
- 10/11/2026: a project's workers can run on another Claude login, whose sessions a resume does not yet find, so a resume on such a login starts afresh with a handover (EDI-253).
- 10/11/2026: a relaunch of a worker on another login than `default` lost its session, so every launch records its login, a resume runs on the recorded login, a fresh start on the project's current one, and the first hook proves the login (EDI-254).
- 13/11/2026: a task released and recorded as merged stayed number 1 on the outage list eleven days later, inviting a pointless restart, so a task leaves the list once nothing is left to resume (EDI-242).

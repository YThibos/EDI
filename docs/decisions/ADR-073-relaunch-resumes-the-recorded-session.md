# ADR-073: A relaunch resumes the recorded session in the task's own copy, and a stop signals only what EDI recorded

**Date:** 16/10/2026

**Status:** Superseded by ADR-171

**Kind:** Design

**Context and constraints:** I-25: a relaunch resumes the same worktree and the same Claude session id with `--resume`, never another task's copy; AT-25: a relaunch after a lost server lands in the recorded path with the recorded session id.
I-16: a relaunched worker's model and effort are resolved from its category as for a fresh dispatch; an explicit captain instruction outranks both.
Section 11.9: `edi worker relaunch` checks the recorded server and window and that no process holds the worktree, and adds a one-line resume note naming the branch, the head, the last status and the answered decisions; `edi worker stop` signals the worker's recorded processes, SIGTERM and then SIGKILL after ten seconds, never `/exit`.
I-30: EDI kills only process ids it recorded itself.
On Claude Code 2.1.301 `--resume <id>` keeps the session id, opens the same transcript, and accepts `--name`, `--settings` and a first prompt after `--`.

**Decision:** A worker is launched as a `dispatch`, a `resume` or a `fresh` start, recorded as its `launch_kind`; `LaunchWorker.relaunch` takes the task's own dispatch, applies a captain's override when one is given, and launches the next incarnation in `EdiHome.worktreeOf` with the route the dispatch rules give now.
A resume passes `--resume` with the latest incarnation's session id and, as its first prompt, `ResumeNotes`' line `resumed after <why>: your copy is on <branch> at <head>; your last status was ...; answered since you started: ...; run edi inbox first, then carry on with the task`; a fresh start draws a new session id and writes the brief again.
`worker.session_id` is no longer unique, because a resume shares its session with the incarnation it resumes.
`RelaunchWorker` refuses a worker that still runs and a held task; `LaunchWorker.relaunch`, which `edi worker relaunch` and `edi recover` share, refuses a copy in which `ProcessTable.workingIn` finds a process.
`StopWorker` checks that the pane's process is still a child of the recorded tmux server, then `ProcessTable.stopTree` sends SIGTERM to that process and its descendants and SIGKILL to whatever outlives ten seconds; the worker is marked stopped, so it is not counted as dead and can be relaunched.

**Motivation:** The worker's context and its uncommitted work both survive a lost server, and nothing EDI did not start is ever signalled.

**Alternatives considered:**
- Relaunching with a fresh session by default - the worker would re-read its brief and lose its reasoning; `fresh` stays an explicit choice.
- Stopping through `tmux kill-window` - the pane would get SIGHUP without the grace period, and a background-task picker could still interfere.

**Consequences:**
- Good: the stand-in in the native test records `--resume` with the first launch's session id after a simulated restart.
- Bad: reading every process's working directory scans `/proc`, which is Linux only, as both machines are.

**Revisit when:** Claude Code changes how `--resume` treats a session's settings, or a worker needs a copy of another project.

**Sources:** EDI rewrite specification, sections 11.3, 11.9 and I-16, I-25, I-30; ADR-052, ADR-054; `application.dispatch.LaunchWorker`, `ResumeNotes`, `application.worker.RelaunchWorker`, `StopWorker`, `adapter.process.ProcessTable`, `adapter.runtime.ClaudeCommandLine`, `domain.worker.LaunchKind`, `WorkerLaunch`; [docs/verification/claude-code.md](../verification/claude-code.md).

# ADR-070: Worker health is read from hooks, the tmux server and the boot, and each condition is noticed once

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.2 sets the health rules: busy from `UserPromptSubmit` to `Stop`, idle after; a declared pause is healthy until its `until` and then resurfaces once; an idle worker with no declared wait and no pending message raises one "waiting too long" wake after `supervision.staleAfter`, and repeats only after something changed; a busy worker is healthy up to a ceiling of three hours; a worker whose window is gone, whose server identity changed or whose boot is old is dead.
I-26: workers are judged by structured state, never by shell liveness or the last status line; AT-26: a worker whose last status is `working` but whose server identity changed is dead.
Section 8.1: a worker's state is derived, never stored twice.

**Decision:** `SupervisionRules` derives `WorkerHealth` from the worker row, `WorkerRuntime.check` and the current `BootId`: stopped by EDI, dead because the machine restarted, dead because its server is gone or changed or its window closed, finished when its window closed after its done was accepted or refused, parked on a usage limit, starting, busy, idle, or waiting until a declared pause ends.
`TmuxClaudeRuntime.check` reads the server's pid and start time from `tmux list-sessions` and tells `SERVER_GONE`, `SERVER_CHANGED`, `WINDOW_CLOSED` and `OPEN` apart.
`SupervisionRules.noticeDue` names the one condition worth a wake, keyed by the moment it began: `stale` since the later of the last activity change and the last status, `busy-too-long` since the turn began, `wait-ended` since the pause was reported, and `died`.
A worker that waits on the primary, by `done`, `needs-decision`, `blocked`, `failed` or `paused`, is never stale, nor is one with a message it has not received.
`worker_notice` records each condition once; a new activity or status starts a new condition, which may be noticed again.
`supervision.staleAfter` and `supervision.pauseResurface` come from the machine profile, PT20M and PT4H by default.

**Motivation:** Health is computed afresh from what tmux, the kernel and the hooks say, so no stored "alive" flag can outlive the machine, and a notice carries the moment its condition began, so suppression ends exactly when something changes.

**Alternatives considered:**
- Storing a health column updated by the round - the stale flag would be the old harness's false "alive".
- Reading the pane to tell busy from idle - ADR-057 keeps the pane probe out of every health decision.

**Consequences:**
- Good: `edi worker list` shows each worker's health in words, such as `dead: its tmux server changed`.
- Bad: a worker waiting in a permission dialog counts as busy until the ceiling, because the dialog arrives inside its turn.

**Revisit when:** Claude Code reports a pending permission prompt as a state of its own, or slice S5's pipeline runs join the stale rule.

**Sources:** EDI rewrite specification, sections 8.1, 12.2 and I-26; ADR-057; `domain.worker.SupervisionRules`, `WorkerHealth`, `WindowCheck`, `DeathCause`, `WorkerNotice`, `NoticeKind`, `application.worker.WorkerListQuery`, `application.supervision.JudgeWorkers`, `adapter.runtime.TmuxClaudeRuntime`, `Tmux`, `adapter.store.NoticeStore`, `adapter.home.MachineProfile`.

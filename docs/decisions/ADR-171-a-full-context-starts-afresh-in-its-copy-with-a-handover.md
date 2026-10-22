# ADR-171: A worker whose context is full starts afresh in its own copy, briefed with a handover

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-073 has `edi worker relaunch` resume the recorded session and keeps a fresh session an explicit choice of `edi recover`, which lists only dead workers.
A worker whose context is full cannot be resumed: `--resume` reopens the same full conversation and its first prompt fails again (ADR-170).
On 22/10/2026 the primary therefore stopped such a worker, dispatched the task again and wrote its successor a handover by hand: the branch, the commits so far, the uncommitted files and where the plan was.
A fresh start through `edi recover <n> fresh` wrote the brief again and nothing more, so its worker had to rediscover how far the work had come.

**Decision:** This record supersedes ADR-073: everything it decided stands, with three changes.
`RelaunchWorker` relaunches a stopped or dead worker whose context is full as a `fresh` start, because "its context filled up", and refuses one whose session still runs with a message that names the stop first; every other worker is resumed as before.
`LaunchWorker` refuses to resume a session whose context is full, whichever command asked, since the resumed session would stop on it again.
Every `fresh` start writes the brief again and `handover.md` beside it in the task directory, and its first prompt is `fresh start after <why>: read the handover <path> and carry on from where it leaves off`.
`Handovers` writes the handover from the store and the copy: who takes over from whom and why; the branch, its head and the copy; the commits beyond `origin/<base>`, oldest first; the uncommitted changes; the latest pipeline run with its step and merge request; the brief's path and, for a ship planned by a scout, the scout's report; the earlier worker's full last status and the decisions answered since; and how to carry on, starting with `edi inbox`.
A part EDI cannot read from git says so and names the command that shows it.

**Motivation:** A full context loses only the conversation, while the work lives in the copy, the store and the task directory, so EDI can write the handover the primary had to write by hand, and the captain decides only whether to stop the worker.

**Alternatives considered:**
- Putting full-context workers on the outage hold list for `edi recover <n> fresh` - the list holds the tasks of an outage until the captain answers, which suits two or more dead workers, not one worker whose only way on is a fresh start.
- An `edi worker relaunch --fresh` flag for every worker - nothing yet asks for a fresh start of a worker that could resume, and ADR-073's reason to resume by default still holds.
- Relaunching a full context without the stop - stopping a worker stays a separate act the primary takes, as ADR-073 decided.
- Writing the handover into the brief - the brief stays the same for every incarnation and under its line budget, while the handover changes with every start.

**Consequences:**
- Good: a full context costs the primary two commands, `edi worker stop` and `edi worker relaunch`, and `edi recover <n> fresh` gets the same handover.
- Bad: the handover reads the copy's git state at launch, so a copy git cannot read gives a thinner handover.

**Revisit when:** Claude Code can compact or clear a session from the command line, or the captain wants fresh starts for workers that could resume.

**Sources:** the JUSTMD-53 incident of 22/10/2026; ADR-073, ADR-074, ADR-170; `application.worker.RelaunchWorker`, `application.dispatch.LaunchWorker`, `Handovers`, `ResumeNotes`, `ResearchBeforeImplementation`, `application.supervision.RecoverFromOutage`, `web.ConfirmationText`, `web.WorkerSentences`, `edi-cli` `WorkerRelaunchCommand`; [docs/workers.md](../workers.md).

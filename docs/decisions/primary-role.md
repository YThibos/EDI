# The session the captain typed in last is the primary, and any other EDI session stands by

**Kind:** Design

## Context

A session in which the captain types a prompt claims the primary role, and any other primary becomes standby and read-only (I-02).
Every primary holds the home token, so two primary sessions need another name ([api-access](api-access.md)).
Not every prompt is the captain's: a finished background job submits a `<task-notification>` prompt, and EDI's own injected lines carry the operational marker.
A primary that crashed sends no `SessionEnd`, and a pid can be reused.
The JDK computes a process's start instant from `/proc/stat` `btime`, which the kernel derives from the wall clock, and WSL2 steps that clock in whole seconds as it resyncs with the host.

## Decision

`primary_session` keeps every session that runs EDI's primary hooks, with its Claude process's pid and start instant, when it claimed the role and its current turn.
`PrimaryRole` gives the role to the session that claimed it last, among sessions that have not ended and whose Claude process still runs, or whose process is unknown.
A session claims the role at its start when no live session holds it, and at every prompt `Prompt.isTypedByTheCaptain` accepts, which is neither a background job's notice nor marked by `OperationalText`; a claim takes the role from every other session in the same transaction.
`ApiTokenFilter` turns the home token with an `EDI-Session` header into `Caller.Standby` when another session holds the role, and `Caller.requirePrimary` refuses a standby caller with one sentence saying how to become primary again; a call without a session header, such as the captain's shell, is a primary caller.
`SessionEnd` releases the role, and a crashed holder loses it as soon as its process is gone.
`SessionProcess.isTheOneStartedAt` treats a running process as the recorded one when its start lies within two seconds of the recorded start, and `ProcessTable.isRunning` is the only place that compares a recomputed start; a tmux server's identity keeps its exact comparison, since tmux stores its own start time.

## Why

Two sessions can never both change the fleet, and the role moves only on the captain's own typing, never on a notice that arrived while they were elsewhere.
Two seconds absorb the one-second clock steps observed, while a reused pid would need the kernel to wrap its whole pid space within them.

## Alternatives rejected

- A lock file held by the primary - the old harness's fleet lock, which outlived crashed sessions and needed takeover rules.
- Claiming on every prompt - a background job's notice would hand the role to an idle session.
- One token per primary session - a second secret per session, while the role, not the secret, is what two primaries contend for.
- Comparing start ticks from `/proc/<pid>/stat`, or a tolerance of a minute - Linux-only reading outside `ProcessHandle`, or a wider window for a reused pid.

## Consequences

- Good: a session restarted after a crash takes the role at once, because its predecessor's process is gone.
- Bad: a session whose process EDI could not identify is assumed live until it ends or another claims, and a clock step above two seconds still reads a live session as gone.

## When to revisit

When a clock step larger than two seconds drops a live session, or Claude Code stops naming its session and process to hooks.

## How we got here

- 16/10/2026: slice S4 gave the role to the session the captain typed in last.
- 17/10/2026: slice S8 tied the captain's word to the same typed prompts ([captains-word](captains-word.md)).
- 22/10/2026: a WSL2 clock resync made a live primary read as gone after a core restart, so a process's start is compared within two seconds.

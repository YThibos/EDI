# ADR-164: A process is the recorded one when its start lies within two seconds of the recorded start

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** EDI tells a live Claude process from a pid the kernel reused by recording the process's start instant with its pid (ADR-115) and comparing it when it asks whether the process still runs.
On Linux the JDK computes that instant from `/proc/stat` `btime` plus the process's start ticks, and the kernel derives `btime` from the wall clock, so it moves whenever the clock is stepped.
Under WSL2 the clock is stepped as it resyncs with the host: on 22/10/2026 a primary session's recorded start and the instant read an hour later differed by exactly one second.
An exact comparison then read the live primary as gone, so after a core restart the role stood vacant, a claim by the same session still answered not-primary, and `edi wait` refused it.
`btime` is a whole number of seconds, so the drift arrives in whole-second steps.

**Decision:** `SessionProcess.isTheOneStartedAt` treats a running process as the recorded one when its start instant lies within two seconds of the recorded start, in either direction; a process whose start EDI could not read on either side still counts as the recorded one.
`ProcessTable.isRunning` asks that rule, and it is the only place that compares a recomputed process start instant.
A tmux server's identity (`Tmux.ServerIdentity`) keeps its exact comparison: `#{start_time}` is a time tmux stored when the server started, not one recomputed from `btime`.

**Motivation:** Two seconds absorbs the one-second steps observed, with one second of margin, while a reused pid would need the kernel to wrap its whole pid space within those seconds.

**Alternatives considered:**
- Comparing start ticks read from `/proc/<pid>/stat` - exact and drift-free, but Linux-only reading of the process table outside `ProcessHandle`, and every stored start instant would change meaning.
- A tolerance of a minute - absorbs larger clock steps, but widens the window in which a reused pid passes for the recorded process.

**Consequences:**
- Good: a live primary session keeps its standing across the clock resyncs WSL2 makes.
- Bad: a clock step larger than two seconds, such as one after a long host sleep, still reads a live process as gone.

**Revisit when:** A clock step larger than two seconds is seen to drop a live session.

**Sources:** `domain.primary.SessionProcess`, `adapter.process.ProcessTable`, `adapter.runtime.Tmux`, `application.primary.PrimarySessions`; ADR-115.

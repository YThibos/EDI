# ADR-002: One local core, one thin command and a Claude Code layer

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** Supervision is made of timers, long polls, child processes and transactional state.
In the old harness each of these was a script with its own watcher, wake queue and lock files, and hooks paid the start-up cost of a script on every turn.
Everything has to run on the developer's own machine, with no hosted service.

**Decision:** EDI has three parts.
`edi-core` is one Spring Boot application, the only owner of state and the only component that starts, observes and stops workers; it serves a JSON API and the EDI console on loopback.
`edi` is a thin picocli command, with no Spring, that every actor uses: the captain, the primary session, workers and Claude Code hooks; it talks to the core over loopback HTTP.
The Claude Code layer is the primary's anchor, a few skills, the hook definitions that call `edi hook <event>`, and the worker brief template, kept in `claude/`.

**Motivation:** One long-running process owns timers, children and transactions, which removes the watcher, wake-queue and lock machinery entirely.
One command for every actor keeps a single interface and simple permission rules.
Spring Boot plus Angular is the captain's standing stack for personal tools, the stack he reviews best, and it already runs on his machines.

**Alternatives considered:**
- Scripts around a shared state directory - recreates the old harness's locking and liveness problems.
- A plain transaction-script application calling processes from services - its rules could only be tested by starting tmux, Claude Code and git (ADR-007).

**Consequences:**
- Good: one process to start, health-check and restart; the command stays tiny and fast.
- Bad: the core is a long-running service that must be installed and kept alive (`edi up`, an optional systemd user unit).

**Revisit when:** EDI needs to serve more than one machine from one process, or a second agent interface (ADR-021) is added.

**Sources:** EDI rewrite specification, sections 5.1 and 5.2 (decision E2); `edi-core/src/main/java/io/github/ythibos/edi/EdiCoreApplication.java`, `edi-cli/src/main/java/io/github/ythibos/edi/cli/Edi.java`.

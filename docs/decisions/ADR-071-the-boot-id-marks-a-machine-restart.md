# ADR-071: Every worker launched in an earlier boot is dead

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 11.9: the core records the boot id at start, and a changed boot id means every worker from the previous boot is dead, whatever its status line says; twice the old harness reported a fleet "alive" after the machine had restarted.
A test must be able to simulate a restart without restarting the machine.

**Decision:** `MachineBoot` reads the kernel's boot id once, at the core's start, from the file `edi.machine.boot-id` names, `/proc/sys/kernel/random/boot_id` by default.
Every launch records that boot on the worker, and `SupervisionRules` judges a worker launched in another boot dead with the cause "the machine restarted", before it looks at tmux at all.
A worker recorded before slice S4 carries no boot and is judged by its server and window alone.

**Motivation:** After a restart a tmux server can be running again on the same socket, and pids are reused; the boot id is the one fact a restart always changes.

**Alternatives considered:**
- The machine's uptime - it moves backwards when the clock is set, and says nothing about which workers are older.
- Judging by the tmux server alone - it misses a restart that EDI's own core followed by starting a new server.

**Consequences:**
- Good: a simulated machine restart is a new boot id in a file, so the outage hold list is tested end to end on the native core.
- Bad: a core that cannot read the boot id does not start.

**Revisit when:** EDI runs where the kernel offers no boot id.

**Sources:** EDI rewrite specification, section 11.9; `adapter.machine.MachineBoot`, `domain.worker.BootId`, `WorkerLaunch`, `SupervisionRules`, `application.dispatch.LaunchWorker`, `edi-core` `application.yaml`.

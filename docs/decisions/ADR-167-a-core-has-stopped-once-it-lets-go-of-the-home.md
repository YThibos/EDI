# ADR-167: A core has stopped once it lets go of the home, and edi update starts the new core only then

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A core holds an exclusive lock on `state/core.pid` from before it opens the store until it exits, and a second core on the same home refuses to start (ADR-129).
`edi down` and `edi update` asked the core to stop and then waited until it no longer answered its health endpoint; `edi update` started the new core right after that wait (ADR-130).
A stopping core stops answering as soon as Tomcat's graceful shutdown begins, but it keeps the lock while it lets in-flight requests and scheduled rounds finish: up to 30 seconds for each shutdown phase, and up to 30 more for a scheduled round (`spring.task.scheduling.shutdown.await-termination-period`).
The primary keeps an `edi wait` request open on the core at all times, so a stop routinely takes as long as that request's 25 seconds.
In daily use `edi update` started the new core 0.24 seconds after the old one began to stop; the new core refused because the old one still held `state/core.pid`, the old one exited nine seconds later, and the machine was left without a core until the captain ran `edi up --core-only`.
`edi down` followed by `edi up` raced the same way.

**Decision:** The command counts a core as stopped once it has let go of `state/core.pid`, not once it stops answering.
`CorePidFile.isHeld` tries the same lock the core takes, through `FileChannel.tryLock`, and releases it at once; a missing file is not held.
`LocalCore.awaitItsStop` looks every 250 milliseconds until the file is free, for at most 90 seconds, which covers the core's own bounded shutdown with room to spare; when the patience runs out it fails, naming the process the file records and the file it still holds.
`edi down` uses that wait, so it returns only once a new core could start.
`edi update`, once the install has succeeded, asks a running core to stop, waits for it to let go of the home, starts the new core and waits up to a minute for it to answer its health endpoint.
When either wait fails, the command says so and adds that the new build is installed but no core runs it, and that `edi up --core-only` starts it once the old core has exited.
`CoreRestartTest` reproduces the race with a stand-in core, a separate process that holds `state/core.pid` the way the core does and takes a while to stop after it is asked to.

**Motivation:** The lock is the very thing the new core checks before it starts, so waiting on it removes the race instead of narrowing it, and it needs no process id that a reboot could hand to another program (ADR-165).

**Alternatives considered:**
- Waiting until the process recorded in `state/core.pid` has exited - the file keeps the last core's id after it exits, and after a restart of the machine another program can carry that id.
- Retrying the start of the new core until one stays up - each refused start writes a stack trace to the core's log, and the command would still not know why a start failed.
- Letting the core release the lock before its graceful shutdown - work still under way would then write to a store that a new core has already opened.

**Consequences:**
- Good: `edi update` and `edi down` followed by `edi up` never leave the home without a core because of a core that is still stopping, and a core that does not stop is named with the way back.
- Bad: a restart takes as long as the old core's shutdown, which an open `edi wait` stretches to about 25 seconds.

**Revisit when:** The core answers open `edi wait` requests as it begins to stop, or something other than the command starts and stops the core.

**Sources:** EDI-36; ADR-129, ADR-130, ADR-165; `edi-cli` `CorePidFile`, `LocalCore`, `UpdateCommand`, `DownCommand`, `CoreRestartTest`, `StandInCore`; `CoreInstanceLock`, `web.wake.WaitController`, `application.yaml`.

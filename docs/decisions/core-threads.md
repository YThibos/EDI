# The core serves requests and runs its scheduled rounds on virtual threads

**Kind:** Design

## Context

The core's own executors already ran on virtual threads: `ProcessTable`, `BoardListeners`, `EventFollowers`, `SupervisionRound`, `WakeSignal` and `ConsoleStream` each use `Executors.newVirtualThreadPerTaskExecutor()`.
Spring's own threads did not: Tomcat handled every request, from each `edi` command to the console, on its pool of platform threads, and every `@Scheduled` round, such as `DeliveryRound`, `DailyMemoryCheck`, `SupervisionRound`, `EventFollowers` and `BoardListeners`, ran on Spring's single platform scheduler thread.
Most of the core's work blocks: on a child process such as git, tmux or gh, on the SQLite store, or on a forge's HTTP API.

## Decision

`spring.threads.virtual.enabled: true` in `edi-core/src/main/resources/application.yaml` puts Spring's threads on virtual threads too.
Tomcat handles each request on its own virtual thread, named `tomcat-handler-<n>`.
Spring Boot replaces the platform `ThreadPoolTaskScheduler` with a `SimpleAsyncTaskScheduler` on virtual threads, which runs every fixed-delay round on its one fixed-delay thread, so rounds still run one at a time and never overlap, as before.
A stopping core still waits for a running round ([core-lifecycle](core-lifecycle.md)): the `SimpleAsyncTaskScheduler`'s lifecycle stop completes only once the round on its fixed-delay thread ends, and Spring waits for it up to `spring.lifecycle.timeout-per-shutdown-phase`, 30 seconds, before closing the scheduler interrupts the round.
`spring.task.scheduling.shutdown` no longer bounds that wait, as it only reaches the scheduler's trigger, fixed-rate and one-off tasks, which the core does not use, so `application.yaml` sets the shutdown phase timeout instead.
The setting lives in `application.yaml` because ahead-of-time processing fixes the conditional beans at build time, so the native image carries it, and it is no machine difference ([machine-and-person-specifics](machine-and-person-specifics.md)).
`edi wait` holds no thread while it waits: `WaitController` returns a `DeferredResult`, which a worker's wake completes from `WakeSignal`.
`CoreExit` keeps its own single-thread scheduler: the setting does not reach it, and it runs one delayed `System.exit` per core life, after the answer to `edi down` is sent, so a virtual thread would change nothing there.
`EdiCoreApplicationTest` pins that a request and a scheduled job run on a virtual thread, and `WaitControllerTest` pins that an `edi wait` held open is answered once a worker wakes the primary.

## Why

One thread model runs the whole core, so a request or a round blocked on a child process, the store or a forge parks a virtual thread instead of holding a platform thread.
Java 25 no longer pins a virtual thread to its carrier inside `synchronized` (JEP 491), so the core's synchronized services, such as `LaunchWorker` and `DispatchTask`, need no change.
Spring Boot's own setting configures both Tomcat and the scheduler, and keeps them in step with each Spring Boot upgrade.

## Alternatives rejected

- Keeping Spring's platform threads - two thread models in one core, and a request blocked on git or tmux holds one thread of a fixed pool.
- Hand-written executor and scheduler beans - they repeat what the setting configures and must follow every Spring Boot change by hand.
- Running each round on its own virtual thread, through a fixed rate or a target executor for the scheduler - rounds that never overlapped would start to, a change of behaviour the move to virtual threads does not need.

## Consequences

- Good: blocking work costs a parked virtual thread, and a thread dump shows one kind of request and round thread.
- Bad: a native call, such as SQLite's, holds its carrier for as long as it runs.
- Bad: Tomcat's thread pool no longer bounds how many requests run at once; only its connection limit does.

## When to revisit

When a library the core uses holds a carrier for long, or the core needs its rounds to run side by side.

## How we got here

- 28/10/2026: at the captain's request the core's requests and scheduled rounds moved onto virtual threads, the way its own executors already ran (EDI-115).

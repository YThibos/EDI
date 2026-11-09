# The core measures its own health with Micrometer, keeps a week of it in the store, and runs a bounded flight recording

**Kind:** Architecture

## Context

The captain asked that the core itself be measured: how fast it answers, how long a wake takes to reach the primary and to be acted on, how long the store, the supervision round and a worker's launch take, and how much memory it holds, with JDK profiling on demand.
The core restarts on every update, so what it holds in memory alone never covers a week.
Telemetry must stay on the machine, cost no model tokens, and hold nothing secret or personal, while a flight recording's default settings record the environment, the system properties, the command line and every file and socket path.
The core ships as a native image, where the JDK Flight Recorder exists only when the image is built with it.

## Decision

`CoreMeters` names the core's own Micrometer timers: `edi.store.transaction` around every `TransactionTemplate` call through `MeasuredTransactions`, the wait for the write lock included, `edi.supervision.round` around `SupervisionRound.run`, `edi.worker.launch` around `LaunchWorker`'s launch, `edi.wake.printed` from a wake's raising to its printing and `edi.wake.acknowledged` from its printing to the primary acting on it, both read by `WakeStore` from the rows it updates, and the gauge `edi.memory.used`; Spring Boot's `http.server.requests` times each endpoint.
`CoreMeasures` reads those meters, and `RecordCoreHealth` writes what each counted since its last sample to the `health_sample` table every `edi.health.sample-every` (PT10M), forgetting samples older than `edi.health.kept` (P35D); Micrometer's maximum decays over the same period, so each sample's maximum is the maximum of its own period.
`edi health --metrics` prints, from `GET /api/telemetry` and `CoreHealthQuery`, each measure's count, mean and maximum over `--days` (7 by default, at most 35) with what was counted since the last sample, the memory in use on average and at its peak, and the state of the flight recording; `/api/metrics`, the actuator's endpoint behind the API token, shows the live meters.
`FlightRecording` starts one continuous flight recording at start, at most `edi.health.recording.max-size` (64MB) and `edi.health.recording.max-age` (P1D), with only CPU samples, garbage collections, the heap, CPU load, contended monitors, parked and pinned threads slower than 20 ms, and sampled allocations, every other event type registered at start switched off, a library's own events included, so it holds class and method names and no environment, property, argument, process, file or network address.
`edi health --dump-recording` writes it out through `POST /api/telemetry/recordings` to `data/recordings` in the EDI home, keeping the five latest, for JDK Mission Control or `jfr`.
The native image of the core is built with `--enable-monitoring=jfr`; a core without a flight recorder says so, and refuses a dump under `core.flight-recording` ([rule-firings](rule-firings.md)).

## Why

Micrometer is already on the classpath through the actuator, and timers named in the domain's language make the summary readable without a dashboard.
Samples in the store survive restarts at the cost of a few rows per measure an hour, and summing them answers any period up to their keeping.
A recording with only these events is safe to keep on the machine and to open with standard tools, and it is there when a slowdown needs profiling, without a restart.

## Alternatives rejected

- A metrics backend such as Prometheus, or OpenTelemetry export - a server to run, or data sent off the machine.
- The default flight recording settings - they record the environment, system properties, the command line, and file and socket paths.
- Timing each store query through a proxy around the data source - reflection the native image must be told about, while one transaction per use case already bounds every query.
- Keeping the measures in memory only - every restart would lose the week.

## Consequences

- Good: the gardening and the captain read the core's latencies and memory for any recent period with one command and no model tokens.
- Good: a recording is a command away, with no restart and nothing personal in it.
- Bad: a timer joined by a nested transaction counts it twice, and a sample's maximum is only as fine as its period.
- Bad: the flight recorder adds a little to every core's CPU and disk, and to the native image's size.

## When to revisit

When the core runs on more than one machine, or the gardening needs percentiles rather than means and maxima.

## How we got here

- 09/11/2026: the captain asked for the core to be measured, with JDK profiling and telemetry; `CoreMeters`, `health_sample`, `edi health --metrics` and the flight recording were built (EDI-164).

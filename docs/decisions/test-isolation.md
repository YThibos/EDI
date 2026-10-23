# Tests stop every process they start, run git without the developer's configuration, and stay within their timeout under load

**Kind:** Design

## Context

The old harness's suites leaked watcher children that wedged the runner, and a leaked process must fail the build (AT-40).
Test classes run concurrently in one JVM, one thread per processor, and every test must finish within the 30-second default timeout.
A tmux server daemonises, so it is neither a descendant of the JVM nor of the client that started it.
The developer's global git configuration, such as `insteadOf` rewrites, `core.hooksPath` or an include on a slow mounted file system, can change what a test proves and how long it takes, and git 2.47 and later leave a background maintenance process behind ordinary commands.
On a busy laptop the full suite ran its longest tests at three to five times their solo time, and a recording showed the optimising Graal compiler, a Flyway migration per test home and repeated YAML mapping taking most of the processors.

## Decision

`TestProcesses`, shared through `edi-core`'s test jar, is a JUnit extension a test class registers: each process a test starts is handed to it, and after each test, or after the class for the class's own, it kills every registered process with its descendants that is still alive, waits with a hard timeout, and fails naming their pids and commands.
Attribution is by registration only, and `PrivateTmuxServer` registers its server's pid and waits for the server and its panes to exit.
A program a helper runs to completion, such as `FixtureRepository`'s git, is waited for with a hard timeout.
The root `pom.xml` sets `GIT_CONFIG_GLOBAL=/dev/null` and `GIT_CONFIG_NOSYSTEM=1` for every test JVM, and `FixtureRepository` also clears the `GIT_CONFIG_*` variables and runs every git with `-c maintenance.auto=false`.
Every test JVM compiles with C1 only (`-XX:TieredStopAtLevel=1`); `MigratedStore` migrates one store per JVM and `TestHome` starts each home from a copy; `TestHome` holds one `MachineProfile`, and `InstalledPrograms` asks each program its version once.
A test that needs overlapping processes starts them before the work they overlap, as AT-38 starts its four pre-push hooks before its deliveries, whose guard JVMs start with C1 and the serial collector.
A test that needs an external tool skips with a reason when the tool is missing, time comes only from the `Clock` port, and nothing sleeps except a helper waiting for a real child with a hard timeout.

## Why

A per-test ledger is exact under concurrency, and failing the test keeps a missing stop visible instead of hiding it behind the extension.
Tests prove the same thing on every machine, and a git call costs what git costs, not what a developer's configuration costs.
Most test code runs a few times and is thrown away, so C1 alone nearly halved the processor time, and copying a migrated store takes a millisecond where migrating took a hundred.

## Alternatives rejected

- Diffing the JVM's descendants before and after each test - concurrent classes would blame each other, and a daemonised server is never a descendant.
- Isolating git in `ExternalPrograms` - EDI itself must read the captain's configuration for proxies and credentials.
- Fewer test threads, a longer timeout, or the slowest classes run alone - slower everywhere, a hidden cost that keeps growing, or the suite stopped while they run.

## Consequences

- Good: a leaking test fails with its pid and command, and the longest tests keep about half their timeout in hand.
- Bad: a process started without registration is outside the extension's reach, hot code runs slower without the optimising compiler, and a test of a migration migrates a store of its own.

## When to revisit

When JUnit offers process tracking, a test needs the developer's git configuration or long CPU-bound code, or the pinned toolchain changes compiler.

## How we got here

- 16/10/2026: slice S3 built `TestProcesses` and failed the build on a leak.
- 19/10/2026: AT-38 overran its timeout in the parallel suite, so its pushes overlap its deliveries and test git stopped reading the developer's configuration; a fixture's background maintenance broke AT-47 on CI, so fixtures turn it off.
- 20/10/2026: AT-38 and AT-22 still overran on a loaded laptop, so test JVMs compile with C1 only and homes start from a migrated store.

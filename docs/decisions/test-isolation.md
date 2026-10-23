# Tests stop every process they start, run git without the developer's configuration, and stay within their timeout under load

**Kind:** Design

## Context

The old harness's suites leaked watcher children that wedged the runner, and a leaked process must fail the build (AT-40).
Test classes run concurrently in one JVM, one thread per processor, and every test must finish within the 30-second default timeout.
A tmux server daemonises, so it is neither a descendant of the JVM nor of the client that started it.
The developer's global git configuration, such as `insteadOf` rewrites, `core.hooksPath` or an include on a slow mounted file system, can change what a test proves and how long it takes, and git 2.47 and later leave a background maintenance process behind ordinary commands.
On a busy laptop the full suite ran its longest tests at three to five times their solo time, and a recording showed the optimising Graal compiler, a Flyway migration per test home and repeated YAML mapping taking most of the processors.
With several workers building at once, a recording of a test that overran showed most of its time in SQLite's native calls: `StoreFile` opens a connection for every statement and leaves `synchronous` at its default, `FULL`, so every commit and every checkpoint waits for the disk, and a test home writes hundreds of commits, one for each event a follower handles.
At a load average of about 20, one write and one read through the store took 27 to 59 ms, and 1 to 3 ms with `synchronous` off.

## Decision

`TestProcesses`, shared through `edi-core`'s test jar, is a JUnit extension a test class registers: each process a test starts is handed to it, and after each test, or after the class for the class's own, it kills every registered process with its descendants that is still alive, waits with a hard timeout, and fails naming their pids and commands.
Attribution is by registration only, and `PrivateTmuxServer` registers its server's pid and waits for the server and its panes to exit.
A program a helper runs to completion, such as `FixtureRepository`'s git, is waited for with a hard timeout.
The root `pom.xml` sets `GIT_CONFIG_GLOBAL=/dev/null` and `GIT_CONFIG_NOSYSTEM=1` for every test JVM, and `FixtureRepository` also clears the `GIT_CONFIG_*` variables and runs every git with `-c maintenance.auto=false`.
Every test JVM compiles with C1 only (`-XX:TieredStopAtLevel=1`); `MigratedStore` migrates one store per JVM and `TestHome` starts each home from a copy; `TestHome` holds one `MachineProfile`, and `InstalledPrograms` asks each program its version once.
`MigratedStore`, and so every `TestHome`, opens its stores with `StoreFile.disposableAt`, the core's settings with `synchronous` set to `OFF`; the store adapters' own tests, such as `TaskStoreTest` and `EventLogTest`, build their stores through `TestHome` too, and none of those tests checks what survives a crash of the machine.
Only the core's `StoreConfiguration` and the migration tests `SupervisionMigrationTest` and `DecisionsMigrationTest` keep `StoreFile.openedAt`.
A test that needs overlapping processes starts them before the work they overlap, as each AT-38 case starts its pre-push hook before its delivery, whose guard JVM starts with C1 and the serial collector.
A test whose cases each start child processes checks each case in a home of its own, as `deliveryAndPushRefuseEachKindOfLeak` is a parameterised test over `TwoMachinesTest.Leak` and `deliveryAndPushPassAClearBranch` checks the clean branch apart.
A test that builds many homes checks each in a case of its own, as AT-22's `captainFacingStringsCarryNoInternalLabels` is a parameterised test over every kind of string `CaptainFacingStrings` generates, each golden report a kind of its own and the fleet's views sharing one home.
A test that needs an external tool skips with a reason when the tool is missing, time comes only from the `Clock` port, and nothing sleeps except a helper waiting for a real child with a hard timeout.

## Why

A per-test ledger is exact under concurrency, and failing the test keeps a missing stop visible instead of hiding it behind the extension.
Tests prove the same thing on every machine, and a git call costs what git costs, not what a developer's configuration costs.
Most test code runs a few times and is thrown away, so C1 alone nearly halved the processor time, and copying a migrated store takes a millisecond where migrating took a hundred.
A test home lives in a temporary directory thrown away when its test ends and no test checks what survives a power cut, so its commits need not wait for a disk other builds keep busy; the research-first pilot's test went from 6.7 seconds to 1.9 at a load average of about 20, and it still runs every statement, transaction and file the core does.
Under load a test's time follows the processor work it and its children ask for, so each AT-38 case gets the whole timeout for one branch, takes 5 to 14 seconds at a load average of about 41 where the four branches together took 30, and a failing case names the kind of leak it checks.
AT-22 rendered sixteen golden reports and built three more homes in one test, about 24 seconds of work at a load average of 39 to 54, where none of its cases now takes more than 9 seconds and a failing case names the kind of string that carries the label.

## Alternatives rejected

- Diffing the JVM's descendants before and after each test - concurrent classes would blame each other, and a daemonised server is never a descendant.
- Isolating git in `ExternalPrograms` - EDI itself must read the captain's configuration for proxies and credentials.
- Fewer test threads, a longer timeout, or the slowest classes run alone - slower everywhere, a hidden cost that keeps growing, or the suite stopped while they run.
- Pooled connections in the test homes - a home is never closed, so every pool would keep its connections and threads until the JVM ends.
- A store in memory - the adapters and AT-32 read the store's files on disk.
- `synchronous` set to `NORMAL` in the core as well - a change to the core's durability that slowness in the tests does not call for.
- AT-38's four branches kept in one test with a lighter guard - the guard's JVM starts in about 0.4 seconds alone, and most of the work is gitleaks and git, which the check needs.
- A class-data-sharing archive for the guard's JVM - the JVM refuses to archive classes loaded from directories, and the tests run from `target/classes`.

## Consequences

- Good: a leaking test fails with its pid and command, and the longest tests keep about half their timeout in hand.
- Bad: a process started without registration is outside the extension's reach, hot code runs slower without the optimising compiler, and a test of a migration migrates a store of its own.
- Bad: a crash of the machine could leave a test home's store corrupt, and the Spring tests that start the core keep the core's waits for the disk.
- Bad: each AT-38 case pays for its own home and clone of the project.

## When to revisit

When JUnit offers process tracking, a test needs the developer's git configuration or long CPU-bound code, a test checks what the store keeps across a crash of the machine, or the pinned toolchain changes compiler.

## How we got here

- 16/10/2026: slice S3 built `TestProcesses` and failed the build on a leak.
- 19/10/2026: AT-38 overran its timeout in the parallel suite, so its pushes overlap its deliveries and test git stopped reading the developer's configuration; a fixture's background maintenance broke AT-47 on CI, so fixtures turn it off.
- 20/10/2026: AT-38 and AT-22 still overran on a loaded laptop, so test JVMs compile with C1 only and homes start from a migrated store.
- 23/10/2026: AT-38, AT-32, AT-22 and the research-first pilot overran with several workers building at once while they waited on the disk, so a test home's store commits without waiting for it (EDI-45).
- 23/10/2026: AT-38's leak test still overran in a loaded full build, so each kind of leak is checked in a case of its own (EDI-45).
- 23/10/2026: AT-22 still overran at a load average of 39, so each kind of captain-facing string is checked in a case of its own (EDI-45).

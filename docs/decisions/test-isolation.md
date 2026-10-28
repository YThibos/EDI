# Tests stop every process they start, never open the captain's browser, run git without the developer's configuration, and stay within their timeout under load

**Kind:** Design

## Context

The old harness's suites leaked watcher children that wedged the runner, and a leaked process must fail the build (AT-40).
Test classes run concurrently in one JVM, one thread per processor, and JUnit fails a test that outlives the default timeout.
A tmux server daemonises, so it is neither a descendant of the JVM nor of the client that started it.
The developer's global git configuration, such as `insteadOf` rewrites, `core.hooksPath` or an include on a slow mounted file system, can change what a test proves and how long it takes, and git 2.47 and later leave a background maintenance process behind ordinary commands.
On a busy laptop the full suite ran its longest tests at three to five times their solo time, and a recording showed the optimising Graal compiler, a Flyway migration per test home and repeated YAML mapping taking most of the processors.
With several workers building at once, a recording of a test that overran showed most of its time in SQLite's native calls: `StoreFile` opens a connection for every statement and leaves `synchronous` at its default, `FULL`, so every commit and every checkpoint waits for the disk, and a test home writes hundreds of commits, one for each event a follower handles.
At a load average of about 20, one write and one read through the store took 27 to 59 ms, and 1 to 3 ms with `synchronous` off.
After all of that, `edi update` still failed while several workers built at once, when AT-32's canary test and AT-47's import outlived a 30-second timeout; alone each takes about 2 seconds.
With 80 busy loops and six disk writers beside two builds of edi-core on 16 processors, at a load average of 114 to 127, 28 tests of the suite as it was timed out at 30 seconds over two runs, these two among them, while beside them every test passed once the timeout was generous, the slowest in 37 seconds.
A Spring test core opened the sitrep in the captain's real browser at every primary session start unless its test named a stand-in browser, and `WaitControllerTest` named none.

## Decision

`TestProcesses`, shared through `edi-core`'s test jar, is a JUnit extension a test class registers: each process a test starts is handed to it, and after each test, or after the class for the class's own, it kills every registered process with its descendants that is still alive, waits with a hard timeout, and fails naming their pids and commands.
Attribution is by registration only, and `PrivateTmuxServer` registers its server's pid and waits for the server and its panes to exit.
A program a helper runs to completion, such as `FixtureRepository`'s git, is waited for with a hard timeout.
The root `pom.xml` sets `GIT_CONFIG_GLOBAL=/dev/null` and `GIT_CONFIG_NOSYSTEM=1` for every test JVM, and `FixtureRepository` also clears the `GIT_CONFIG_*` variables and runs every git with `-c maintenance.auto=false`.
Every test JVM compiles with C1 only (`-XX:TieredStopAtLevel=1`); `MigratedStore` migrates one store per JVM and `TestHome` starts each home from a copy; `TestHome` holds one `MachineProfile`, and `InstalledPrograms` asks each program its version once.
`MigratedStore`, and so every `TestHome`, opens its stores with `StoreFile.disposableAt`, the core's settings with `synchronous` set to `OFF`; the store adapters' own tests, such as `TaskStoreTest` and `EventLogTest`, build their stores through `TestHome` too, and none of those tests checks what survives a crash of the machine.
Only the core's `StoreConfiguration` and the migration tests `SupervisionMigrationTest` and `DecisionsMigrationTest` keep `StoreFile.openedAt`.
`MigratedStore` keeps each test home's store behind a HikariCP pool of at most 16 connections, which opens a connection only when one is asked for, closes one idle for 10 seconds and shares one housekeeping thread with every other pool, so a statement reuses a connection instead of opening the file again.
`edi-core`'s `logback-test.xml` keeps Spring Boot's console format, quiets the lines HikariCP writes for each pool it starts and sets the INFO level, at which every test logs except while `CapturedLogs` captures, which lowers the root logger to DEBUG.
`edi-core`'s test JVMs set `org.springframework.boot.logging.LoggingSystem` to `none`, so logback configures itself once from `logback-test.xml`, and a Spring test core that starts no longer resets the logger context while concurrent test classes log, which printed "No appenders present" warnings.
A test that needs overlapping processes starts them before the work they overlap, as each AT-38 case starts its pre-push hook before its delivery, whose guard JVM starts with C1 and the serial collector.
A test whose cases each start child processes checks each case in a home of its own, as `deliveryAndPushRefuseEachKindOfLeak` is a parameterised test over `TwoMachinesTest.Leak` and `deliveryAndPushPassAClearBranch` checks the clean branch apart.
A test that builds many homes checks each in a case of its own, as AT-22's `captainFacingStringsCarryNoInternalLabels` is a parameterised test over every kind of string `CaptainFacingStrings` generates, each golden report a kind of its own and the fleet's views sharing one home.
A `TestHome` helper that reads and writes several rows does so in one transaction, as `markDone` and `putInFlight` do, and a test that stores several rows for one step of its story stores them through `TestHome.inOneTransaction`, as the research-first pilot's test stores each finished ship, so one step of the test's story is one transaction, as one command is in the core.
A test that follows the log several times holds one `EventFollowers` for its home, as the core holds one, instead of building the followers and their trackers again for every round.
A test that needs an external tool skips with a reason when the tool is missing, time comes only from the `Clock` port, and nothing sleeps except a helper waiting for a real child with a hard timeout.
Every Spring test context holds a `RecordingBrowser` in place of `CaptainsBrowser`: `EveryTestCoreRecordsItsBrowser`, a context customiser registered in `edi-core`'s test `META-INF/spring.factories`, replaces the bean before the core starts, so no test core opens the captain's browser and a test reads the pages it would have opened from the recorder; `EveryTestCoreRecordsItsBrowserTest` pins it.
The root `pom.xml` sets JUnit's default timeout to 3 minutes, which catches a test that hangs, while a test's speed is judged by its time on an idle machine, a few seconds of work at most.
Every helper that waits for real work, a child process, a tmux pane, a listener thread or an event stream, waits up to `Patience.FOR_REAL_WORK`, one minute, so a stuck child fails with its own message before the test's timeout ends it; among them are `TestProcesses`, `StandInScripts`, `FixtureRepository`, `PrePushHook`, `PrivateTmuxServer`, `FakeBoardHost` and `InstalledPrograms`.
A test asserts what an event shows, not how soon it came, as `ProcessTableTest` reads a process's start between the starts of two processes started around it.
`NativeSmokeIT` and `NativeRecoveryIT` wait up to `Patience.FOR_REAL_WORK` for each native command, start-up of the core, poll and event stream, and run under the default timeout.
`NativeRecoveryIT`'s own-work limit and AT-39's round-trip budget measure the native executables' speed and stay as [native-smoke-test](native-smoke-test.md) sets them.

## Why

A per-test ledger is exact under concurrency, and failing the test keeps a missing stop visible instead of hiding it behind the extension.
Tests prove the same thing on every machine, and a git call costs what git costs, not what a developer's configuration costs.
Most test code runs a few times and is thrown away, so C1 alone nearly halved the processor time, and copying a migrated store takes a millisecond where migrating took a hundred.
A test home lives in a temporary directory thrown away when its test ends and no test checks what survives a power cut, so its commits need not wait for a disk other builds keep busy; the research-first pilot's test went from 6.7 seconds to 1.9 at a load average of about 20, and it still runs every statement, transaction and file the core does.
Under load a test's time follows the processor work it and its children ask for, so each AT-38 case gets the whole timeout for one branch, takes 5 to 14 seconds at a load average of about 41 where the four branches together took 30, and a failing case names the kind of leak it checks.
AT-22 rendered sixteen golden reports and built three more homes in one test, about 24 seconds of work at a load average of 39 to 54, where none of its cases now takes more than 9 seconds and a failing case names the kind of string that carries the label.
Opening a connection took about 11 ms at a load average of about 20, and the research-first pilot's `raisesTheComparisonOnlyOnce` opened 408, seven in each call of `markDone`, about nine for each finished ship and four in each follow round only to find the log empty, and built its four followers again for each of its eleven rounds; under the same synthetic load, at a load average of 30 to 45, it went from 14 to 21 seconds to 5 to 10.
At a load average of about 46, opening a connection and writing a row took 1.9 to 5.6 ms, holding one connection open beside the store 0.6 to 1.5 ms, and a pooled connection 0.06 to 0.3 ms; `EventFollowersTest`'s test of 101 events went from 4 to 5 seconds alone, and 22 in a loaded full build, to 1, and the full build's slowest test stayed at 22 seconds at a load average of 43 to 64.
A test's time on a busy machine follows the work every other build asks for, which no test controls, so any limit near the slowest test's loaded time fails a build for a reason outside the change, and only a limit far above it tells a hang from a crowd.

## Alternatives rejected

- Diffing the JVM's descendants before and after each test - concurrent classes would blame each other, and a daemonised server is never a descendant.
- Isolating git in `ExternalPrograms` - EDI itself must read the captain's configuration for proxies and credentials.
- Spring Boot's logging system in the test JVMs - each Spring test core resets logback's shared context as it starts, and a concurrent test that logs in that moment finds no appender.
- A stand-in browser named in each Spring test's properties - a test that forgets it opens the captain's browser, as `WaitControllerTest` did.
- Fewer test threads, or the slowest classes run alone - slower everywhere, or the suite stopped while they run.
- A long timeout only for the tests tagged `adapter` - application tests on a test home and the architecture scans overran as often, and they read and write the disk too.
- A timeout scaled by the machine's load average - the limit would still depend on what other builds do, measured a minute late.
- Making the slowest tests faster once more - every round so far left a test that the next busier machine pushed past the limit.
- One connection held open beside each test home's store - three times cheaper than opening one for every statement, but never closed, so every home would keep its files open until the JVM ends.
- A pool in the core as well - the core's store is the store record's concern, and its `StoreConfiguration` stays as it is.
- A store in memory - the adapters and AT-32 read the store's files on disk.
- `synchronous` set to `NORMAL` in the core as well - a change to the core's durability that slowness in the tests does not call for.
- AT-38's four branches kept in one test with a lighter guard - the guard's JVM starts in about 0.4 seconds alone, and most of the work is gitleaks and git, which the check needs.
- A class-data-sharing archive for the guard's JVM - the JVM refuses to archive classes loaded from directories, and the tests run from `target/classes`.

## Consequences

- Good: a leaking test fails with its pid and command, and a test fails on a busy machine only when it hangs.
- Bad: a hung test holds its thread for up to 3 minutes, and a test that grows slow passes until someone reads its time in the reports.
- Bad: a process started without registration is outside the extension's reach, hot code runs slower without the optimising compiler, and a test of a migration migrates a store of its own.
- Bad: a crash of the machine could leave a test home's store corrupt, and the Spring tests that start the core keep the core's waits for the disk.
- Bad: each AT-38 case pays for its own home and clone of the project.
- Bad: a test home's connections stay open for up to 40 seconds after its test, the 10 idle seconds and HikariCP's 30-second housekeeping round, and every pool stays registered with the housekeeping thread until the JVM ends.

## When to revisit

When JUnit offers process tracking, a test needs the developer's git configuration or long CPU-bound code, a test checks what the store keeps across a crash of the machine, the pinned toolchain changes compiler, or a test that hangs costs the suite more than the limit saves.

## How we got here

- 16/10/2026: slice S3 built `TestProcesses` and failed the build on a leak.
- 19/10/2026: AT-38 overran its timeout in the parallel suite, so its pushes overlap its deliveries and test git stopped reading the developer's configuration; a fixture's background maintenance broke AT-47 on CI, so fixtures turn it off.
- 20/10/2026: AT-38 and AT-22 still overran on a loaded laptop, so test JVMs compile with C1 only and homes start from a migrated store.
- 23/10/2026: AT-38, AT-32, AT-22 and the research-first pilot overran with several workers building at once while they waited on the disk, so a test home's store commits without waiting for it (EDI-45).
- 23/10/2026: AT-38's leak test still overran in a loaded full build, so each kind of leak is checked in a case of its own (EDI-45).
- 23/10/2026: AT-22 still overran at a load average of 39, so each kind of captain-facing string is checked in a case of its own (EDI-45).
- 23/10/2026: the research-first pilot's test took 24 seconds at a load average of 30 to 46, so `TestHome` helpers and each finished ship use one transaction, the test holds one `EventFollowers`, a follower's run ends at a short batch and the machine profile is parsed again only when it changed (EDI-45).
- 23/10/2026: the follower test written for the short batch took 22 seconds in a loaded full build, nearly all of it opening connections, so a test home's store keeps its connections in a pool (EDI-45).
- 27/10/2026: AT-32's canary test and AT-47's import still outlived 30 seconds with several workers building at once, so the default timeout is 3 minutes, helpers and the native tests wait a minute for real work and `ProcessTableTest` no longer reads a start against a fixed window (EDI-106).
- 28/10/2026: `WaitControllerTest`'s runs opened the sitrep in the captain's browser, so every Spring test core records its browser instead (EDI-120).
- 28/10/2026: a full build warned that loggers had no appenders while a Spring test core reset logback, so the test JVMs leave logback to configure itself (EDI-120).

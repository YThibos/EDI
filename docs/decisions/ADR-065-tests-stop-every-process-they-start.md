# ADR-065: Tests stop every process they start, and a leaked one fails the build

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 17.3 asks every test that starts a process to register it with a JUnit extension that kills its whole process tree on any exit path, and a leaked process fails the build (AT-40).
The fork's suites leaked watcher children that wedged the runner for minutes, and its cleanup was registered in the wrong scope.
Test classes run concurrently in one JVM, each test within a 30-second default timeout, so the JVM's descendants at the end of one test include processes another test has just started.
A tmux server daemonises: it is not a descendant of the JVM or of the client that started it, and the panes it runs are its own descendants.

**Decision:** `TestProcesses`, in `edi-core`'s test package `testing` and shared with `edi-cli` through the test jar, is a JUnit extension that a test class registers with `@RegisterExtension`.
A test hands it each process it starts, as a `Process` or as a pid; a process registered inside a test belongs to that test, and one registered in `@BeforeAll` belongs to the class.
After each test, and after the class for the class's own processes, it takes every registered process with its descendants, kills those still alive, waits for them with a hard timeout, and fails with their pids and commands.
Attribution is by registration only, never by diffing the JVM's descendants, so concurrent classes never see each other's processes.
`PrivateTmuxServer` starts its server itself, with a `standby` session, and registers the server's pid; closing it kills the server and waits for the server and its panes to exit.
`TmuxPaneTest` registers its private server and every tmux client it runs, and `NativeSmokeIT` registers the native core, every command it runs, and the worker tmux server the core starts, whose pid it reads from tmux after the dispatch.
AT-40 runs a fixture test, `BeyondTheInvariantsTest.LeakingTest`, through JUnit's `EngineTestKit`; the fixture starts a shell that forks a child and returns without stopping it, and AT-40 asserts that the fixture fails for the leak and that both processes are gone.
A program a helper runs to completion, such as `FixtureRepository`'s git or `InstalledPrograms`' version check, is waited for with a hard timeout and destroyed by that helper when it overruns.

**Motivation:** A per-test ledger is exact under concurrency, and registering the server pid is the only way to reach a daemonised tmux server and the stand-ins in its panes.
Failing the test, not only cleaning up, keeps a missing stop visible instead of hiding it behind the extension.
Waiting for the tmux server to exit after `kill-server` keeps a server that is still shutting down from being counted as a leak.

**Alternatives considered:**
- Diffing the JVM's descendants before and after each test - concurrent classes would blame each other, and a daemonised tmux server is never a descendant.
- Only killing leftovers without failing - a test that forgets to stop its process would pass for ever.
- Running test classes one at a time - it would slow the build and still miss daemonised servers.

**Consequences:**
- Good: a test that leaves a process running fails with its pid and command, and the process is gone before the next test starts.
- Bad: a test that starts a process must register it; a process started without registration is outside the extension's reach.

**Revisit when:** A test needs a process that outlives its class, or JUnit offers process tracking of its own.

**Sources:** EDI rewrite specification, section 17.3 and AT-40; ADR-064; `edi-core` `testing.TestProcesses`, `adapter.runtime.PrivateTmuxServer`, `invariant.BeyondTheInvariantsTest`; `edi-cli` `TmuxPaneTest`, `NativeSmokeIT`.

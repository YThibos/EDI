# ADR-149: The test JVMs compile with C1 only, and a new test home starts from a store migrated once

**Date:** 20/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Test classes run concurrently in one JVM at JUnit's default parallelism, one thread per processor, and every test must finish within the 30-second default timeout (AGENTS.md, ADR-065).
The full suite runs `edi-core`'s 938 tests once under each machine profile (ADR-102), and on a laptop with eight cores and sixteen threads the machine ran about 90% busy.
The longest tests then took three to five times as long as they take alone: AT-38 and AT-22 overran 30 seconds, and AT-32 and a test of the research-first pilot took 20 to 25.
A recording of one run showed where the processors went.
The pinned GraalVM Community Edition (ADR-003) compiles hot code with its optimising Graal compiler, which spent about five minutes of compile time in a run of 68 seconds, although most test code runs a few times and is thrown away.
Every `TestHome` ran the store's eight Flyway migrations on a new SQLite file (ADR-010), about 100 ms of processor time each, and Flyway was the largest library in the recorded samples.
`TestHome` built a new `MachineProfile` for every service it wired, and each one's new YAML mappers worked out the profile's types again.

**Decision:** The root `pom.xml` gives every test JVM `-XX:TieredStopAtLevel=1` in its `argLine`, so the JVMs that run the tests compile with C1 only.
`MigratedStore` migrates one store per JVM, and `TestHome` starts each new home from a copy of it; a home whose store already exists, as after a restart, is migrated as before.
`TestHome` holds one `MachineProfile` per home, as the core holds one; the profile still reads its file at every call (ADR-006).
`InstalledPrograms` asks each program for its version once per JVM.
The number of test threads and the 30-second timeout stay as they are.

**Motivation:** Measured on the same laptop in alternating runs, with other builds running beside them, C1 alone took one profile's run of `edi-core`'s tests from about 630 processor-seconds to about 350, the sum of its test times from 630-744 seconds to 394-492, and its longest test from 23-25 seconds to 14-17.
Copying the migrated store takes about a millisecond where migrating a new one took about 100.
Every test does the same work as before, and a test's time depends less on what runs beside it.

**Alternatives considered:**
- Fewer test threads - the suite would take longer on every machine, and the captain's acceptance runs at the default.
- A longer timeout - it hides the cost, and the slowest tests would keep growing with the suite.
- Running the slowest test classes in isolation - each would stop the rest of the suite while it runs, and every new slow test would need the same.
- A store in memory - the adapters and AT-32 read the store's files on disk.

**Consequences:**
- Good: the suite spends its processors on the tests, and the longest tests keep about half their timeout in hand.
- Bad: code that a test runs hot for long runs slower without the optimising compiler.
- Bad: a new `TestHome` no longer runs Flyway, so a test of a migration migrates a store of its own, as `SupervisionMigrationTest` does.

**Revisit when:** A test runs CPU-bound code long enough for the optimising compiler to pay for itself, the pinned toolchain changes compiler, or a migration needs to run in every new home.

**Sources:** AGENTS.md, "Tests"; ADR-003, ADR-006, ADR-010, ADR-065, ADR-102, ADR-145, ADR-146; root `pom.xml` `argLine`; `edi-core` `testing.MigratedStore`, `testing.TestHome`, `testing.InstalledPrograms`, `adapter.home.MachineProfile`, `adapter.store.StoreFile`.

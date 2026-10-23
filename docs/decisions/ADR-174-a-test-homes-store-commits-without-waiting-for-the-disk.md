# ADR-174: A test home's store commits without waiting for the disk

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Every test keeps the 30-second default timeout, and a longer timeout must not hide a cost (AGENTS.md, ADR-065, ADR-149).
On 23/10/2026, with several workers building at once and a load average of 23 to 42 on sixteen threads, AT-38's leak test, AT-32, AT-22 and the research-first pilot's `raisesTheComparisonOnlyOnce` overran the 30 seconds, while they pass alone in 11 to 17 seconds.
A recording of the pilot test showed few processor samples and most of its time in SQLite's native calls: the test waited, it did not compute.
`StoreFile` opens a new connection for every statement and leaves SQLite's `synchronous` setting at its default, `FULL`, so every commit waits for the disk to confirm the write, and closing the last connection checkpoints the write-ahead log, which waits for the disk again.
A test home writes hundreds of commits: each event a follower handles moves its cursor in a transaction of its own.
Measured at a load average of about 20, one write and one read through the store took 27 to 59 ms, and 1 to 3 ms with `synchronous` off; when other builds write to the same disk, every wait for it grows with them.
A test home lives in a temporary directory and is thrown away when its test ends; no test checks what survives a power cut.

**Decision:** `StoreFile.disposableAt` opens a store with the same settings as `StoreFile.openedAt`, WAL mode, foreign keys, the busy timeout and `IMMEDIATE` transactions, and `synchronous` set to `OFF`.
`MigratedStore`, and so every `TestHome`, opens its stores with `disposableAt`; the core and every test of the store's own behaviour keep `openedAt`.

**Motivation:** Measured alone at a load average of about 20, the pilot test went from 6.7 seconds to 1.9, and its own work from about 6 seconds to under 1; AT-22 took 1.7 seconds and AT-32 1.1.
The tests still run every statement, transaction, rollback and file the core does; they no longer wait for a disk that other builds keep busy.

**Alternatives considered:**
- A longer timeout - it hides the cost, and the slowest tests would keep growing with the suite.
- Pooled connections in the test homes - a home is never closed, so every pool would keep its connections and threads until the JVM ends, and without the wait for the disk a new connection costs about a millisecond.
- A store in memory - the adapters and AT-32 read the store's files on disk (ADR-149).
- `synchronous` set to `NORMAL` in the core as well - a change to the core's durability that this slowness in the tests does not call for.

**Consequences:**
- Good: a test's time depends on its own work, not on how busy the disk is.
- Good: AT-32 still reads the store and its write-ahead log on disk, since a commit still writes there.
- Bad: a test home's store could be left corrupt by a crash of the machine, which only loses a temporary directory.
- Bad: the Spring tests that start the core still open the core's store, and keep its waits for the disk.

**Revisit when:** A test checks what the store keeps across a crash of the machine, or the core's own store settings change.

**Sources:** AGENTS.md, "Tests"; ADR-010, ADR-038, ADR-065, ADR-149; SQLite documentation on `PRAGMA synchronous`; `adapter.store.StoreFile`, `testing.MigratedStore`, `testing.TestHome`, `application.follow.EventFollowers`, `application.dispatch.ResearchFirstPilotTest`.

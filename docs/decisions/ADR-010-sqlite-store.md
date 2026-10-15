# ADR-010: SQLite with Flyway is the store, written only by the core

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The old harness kept its state in Markdown and status files guarded by lock files, and concurrent writes from hooks were a recurring source of trouble.
EDI needs transactions, a decision-number sequence and an append-only event log, with no database server to run.

**Decision:** The store is one SQLite file per home, `$EDI_HOME/data/edi.db`, opened in WAL mode with foreign keys on and a busy timeout of 5,000 ms, and migrated by Flyway.
The core is the only writer; the command never opens the database.
After every committed change to tasks, holds or decisions the core rewrites a read-only `data/backlog.md`, so the backlog stays readable and greppable without the command.
A nightly `VACUUM INTO` keeps seven daily backups.
Slice S0 delivers only the store's bootstrap: the core creates the data directory, opens the store with these settings and runs Flyway, which proves the SQLite driver and Flyway inside the native image; the schema arrives with slice S1.

**Motivation:** One file, transactional, no server; decision numbers become a sequence and hook writes are serialised by the database instead of lock files.

**Alternatives considered:**
- Markdown files with locks - the old harness's failure class.
- A database server - a second process to install, secure and keep alive for one user.

**Consequences:**
- Good: a crash cannot leave a half-written state change.
- Bad: SQLite allows one writer at a time, which is fine for one core process.

**Revisit when:** EDI needs concurrent writers from more than one process.

**Sources:** EDI rewrite specification, sections 8.2 and 8.3 (decision E3); `edi-core/src/main/java/io/github/ythibos/edi/adapter/store/StoreConfiguration.java`, `EdiHome.store()`, `EdiCoreApplicationTest`.

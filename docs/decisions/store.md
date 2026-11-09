# One SQLite store, written only by the core, keeps current state in tables and history in the event log

**Kind:** Architecture

## Context

The old harness kept its state in Markdown and status files guarded by lock files, and concurrent writes from hooks were a recurring source of trouble.
EDI needs transactions, a decision-number sequence and an append-only event log, with no database server to run.
State must never be rebuilt by replaying events: the system is consistent by design.
SQLite serialises writers, and a transaction that reads before it writes must upgrade to the write lock, which SQLite refuses at once with `SQLITE_BUSY` when another writer committed in between.
The core runs as a native image, where Spring's `@Transactional` proxies are generated ahead of time, and the use cases must be testable without a Spring context.

## Decision

The store is one SQLite file per home, `$EDI_HOME/data/edi.db`, in WAL mode with foreign keys on and a busy timeout of 5,000 ms, migrated by Flyway (`StoreConfiguration`, `StoreFile`).
The core is the only writer; the command never opens the database.
Tables hold the current state and the `event` table holds the history: each event row carries its time, actor, type and a JSON payload written by `EventLog`, in the same transaction as the change ([events-and-followers](events-and-followers.md)).
A current row keeps only what is true now, such as a task's one hold or a decision's current card, and the event log keeps what came before, such as an earlier answer verbatim in its `DecisionAnswered` event.
Columns are strict SQLite types; times are ISO-8601 instants in UTC and days ISO dates, both stored as text; kinds and states are stored as the words the command uses.
Each slice adds the tables it writes in its own migration once it knows their columns, and each table has one store class that reads and writes it, such as `TaskStore`, `WorkerStore` or `DeliveryStore`.
Derived facts are not stored twice: a worker's health and a worktree's path are computed, never kept.
Every connection opens with SQLite's transaction mode `IMMEDIATE`, so each transaction takes the write lock when it begins; every use case runs inside one `TransactionTemplate` call, queries included, and there are no `@Transactional` annotations.
`TestHome` builds the same use cases over a temporary store with a `JdbcTransactionManager`, so tests exercise the real transactions and rollbacks.

## Why

One file, transactional, and no server: decision numbers become a sequence, and hook writes are serialised by the database instead of lock files.
A query reads committed truth from plain tables, the history needed for timelines and answers is kept without being needed to compute the present, and a crash cannot leave a change without its event.
Immediate transactions mean a command never fails because another committed while it was reading, and a refused command leaves nothing behind, not even a consumed number.

## Alternatives rejected

- Markdown files with locks - the old harness's failure class.
- A database server - a second process to install, secure and keep alive for one user.
- Event sourcing, or a history table per entity - replay EDI never needs, and the event log already holds every change in order.
- The full schema in one baseline - speculative columns later slices would change anyway.
- Deferred transactions with retries, or `@Transactional` - retry code in every command, or proxies generated at build time and use cases that need Spring to be tested.

## Consequences

- Good: a restarted core knows every task, worker, token, wake and delivery, each question is one indexed read, and a crash cannot leave a half-written change.
- Bad: one writer at a time, and a read waits for a running write; each lasts milliseconds.
- Bad: a question about history reads JSON payloads rather than typed columns, and SQLite cannot drop a constraint in place, so changing a table means rebuilding it, as `V3__supervision.sql` rebuilt `worker`.
- Bad: the read-only `data/backlog.md` export and the nightly `VACUUM INTO` backups the specification plans are not built yet.

## When to revisit

When EDI needs concurrent writers from more than one process, long-running readers over a very large store, or a history the event payloads cannot answer cheaply.

## How we got here

- 15/10/2026: the specification chose SQLite with Flyway; slice S0 proved the driver and Flyway in the native image, and slice S1 created the first eight tables and made every transaction immediate.
- 16/10/2026 to 19/10/2026: slices S3 to S10 added workers and inboxes, supervision state, deliveries, tracker and follower state, console sessions and boards, presence and memory, and migration and adoption, one migration each.
- 21/10/2026: `V9__waiting_on_the_captain.sql` added board links and dropped dependencies on closed tasks.
- 09/11/2026: `V24__the_stage_is_stored.sql` rebuilt `task` with `stage` in place of `state` and derived each task's stage once from the stored facts ([task-lifecycle](task-lifecycle.md), EDI-85).

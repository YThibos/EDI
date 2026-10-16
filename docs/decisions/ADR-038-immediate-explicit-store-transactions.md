# ADR-038: Store transactions are explicit and take the write lock at once

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** SQLite serialises writers.
A transaction that reads first and writes later starts as a reader and must upgrade to the write lock; when another writer committed in between, SQLite refuses the upgrade at once with `SQLITE_BUSY` instead of waiting for the busy timeout.
EDI's commands read before they write: raising a decision reads the task and its earlier decision before it takes the next number.
The core runs as a native image, where Spring's proxies for `@Transactional` are generated ahead of time, and the use cases must be testable without a Spring context.

**Decision:** `StoreFile` opens every connection with SQLite's transaction mode `IMMEDIATE`, so each transaction takes the write lock when it begins and waits up to the busy timeout of 5,000 ms for it.
Every use case runs its work inside one `TransactionTemplate` call, queries included, so a read sees one consistent state; there are no `@Transactional` annotations.
`TestHome` builds the same use cases over a temporary store with a `JdbcTransactionManager`, so tests exercise the real transactions, including the rollback of a refused command.

**Motivation:** A command never fails because another one committed while it was reading, and a refused command leaves nothing behind, not even a consumed decision number.
The transaction boundary is visible in each use case instead of hidden in a proxy.

**Alternatives considered:**
- Deferred transactions with a retry on `SQLITE_BUSY` - retry code in every command for a conflict that cannot happen otherwise.
- `@Transactional` - proxies generated at build time and use cases that need Spring to be tested.
- Reads outside transactions - a query of several statements could mix two states.

**Consequences:**
- Good: writers queue behind each other in the order they start, which suits one core serving one developer.
- Bad: a read-only query also waits for a running write; each lasts milliseconds, so the wait is not noticeable.

**Revisit when:** EDI needs concurrent long-running readers, for example a report over a very large store.

**Sources:** SQLite documentation on transactions and `BEGIN IMMEDIATE`; `adapter.store.StoreFile`, `StoreConfiguration`, the use cases in `application.task` and `application.decision`, `testing.TestHome`.

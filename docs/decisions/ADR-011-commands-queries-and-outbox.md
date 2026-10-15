# ADR-011: Commands and queries on one database, with an in-process outbox

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain asked whether an internal event bus with command-query separation would help, so that many things can be updated asynchronously, without a full message broker.
He also ruled out replaying events: "we will not replay any events to get to a certain state. The system will have to be consistent by design."

**Decision:** Commands (for example `DispatchTask`, `RaiseDecision`, `RecordMerge`) change state in one SQLite transaction and record their domain events as rows of the `event` table in the same transaction.
Queries (for example `FleetSnapshotQuery`) only read, with read models shaped for their presenters, from the same database.
After commit, Spring publishes the events in-process; listeners are `@TransactionalEventListener(phase = AFTER_COMMIT)` methods running asynchronously on virtual threads.
Each listener marks the outbox row it handled, and a small relay re-delivers unhandled rows at start-up and every 30 seconds; listeners are idempotent, keyed by the event's sequence number.

**Motivation:** A state change without its event, or an event without its state change, cannot exist, and a query always sees the committed truth.
Follow-ups such as waking the primary, live console updates, the fleet ledger and notifications run beside the command without delaying it, and a crash delays a follow-up but never loses it.

**Alternatives considered:**
- An external broker (JMS, Kafka, RabbitMQ) - a server to run and secure for one process talking to itself.
- Event sourcing - replay EDI never needs.
- Spring Modulith's event publication registry - the same idea, but without SQLite support.

**Consequences:**
- Good: consistent by design, with no second store to keep in step.
- Bad: EDI owns a small relay class instead of using a library.

**Revisit when:** A follow-up must run in another process.

**Sources:** EDI rewrite specification, section 5.8 (decision E20).

# ADR-096: Followers read the event log from their own cursor after each commit and every 30 seconds

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-011 records every command's events in the `event` table in the same transaction, publishes them after commit, and has each follow-up mark what it handled so that a relay delivers again whatever a crash interrupted; ADR-037 left the relay to the first follow-up that needs it.
Slice S6 brings three: the fleet ledger, the ticket updates and the research-first pilot, each of which must see every event once and in order.

**Decision:** Each recorded event is published as `EventRecorded` after its commit; `EventFollowers` then runs every follower on a virtual thread, and a scheduled round runs them every `edi.followers.round` (PT30S by default) and shortly after start.
A follower keeps one cursor in `event_cursor`, the sequence of the last event it handled, which is the outbox mark of ADR-011 for every row up to it: it reads the events after its cursor in batches of 100 and moves the cursor after each event it handled.
A follower runs one run at a time, so the round and an after-commit run never interleave; one that throws stops at that event, logs why, and starts again from it in its next run, while the other followers go on.
The V5 migration starts each cursor at the end of the log, so no history from before the follower existed is replayed.

**Motivation:** One cursor per follower gives in-order, at-least-once delivery with one row per follower instead of one mark per event and follower, and the same code serves the immediate run and the crash-safe round.

**Alternatives considered:**
- A handled flag per event and follower - more rows and writes for the same guarantee.
- Spring's `@TransactionalEventListener` alone - an event whose follower crashed would be lost.

**Consequences:**
- Good: a crash between a commit and its follow-up delays the follow-up by at most one round, and followers are tested by running the round directly.
- Bad: an event a follower can never handle stops that follower until the cause is fixed, which its log names every round.

**Revisit when:** A follower must run in another process, or one follower's slowness delays the others.

**Sources:** EDI rewrite specification, section 5.8 (decision E20); ADR-011, ADR-037; `application.follow.EventFollowers`, `Follower`, `adapter.store.EventLog`, `EventRecorded`, `LoggedEvent`, `EventCursorStore`, `V5__tracker_and_time.sql`.

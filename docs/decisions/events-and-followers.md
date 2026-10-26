# Commands record their events in the same transaction, and followers read the event log from their own cursor

**Kind:** Architecture

## Context

Many follow-ups of a state change should run beside the command without delaying it: waking the primary, live console updates, the fleet ledger, ticket updates and notifications.
A message broker would be a server to run for one process talking to itself, and replaying events to reach a state is ruled out.

## Decision

Commands such as `DispatchTask` or `RaiseDecision` change state in one SQLite transaction and record their domain events as rows of the `event` table in that same transaction ([store](store.md)).
Queries such as `FleetSnapshotQuery` only read, with read models shaped for their presenters.
After commit each recorded event is published in-process as `EventRecorded`.
A follow-up that must see every event once and in order is a follower of `EventFollowers`: it keeps one cursor in `event_cursor`, the sequence of the last event it handled, reads the events after it in batches of 100, and moves the cursor after each one.
A batch of fewer than 100 events ends a follower's run, and an event committed after that batch was read starts a run of its own.
Followers run on virtual threads right after a commit and again every `edi.followers.round` (PT30S by default) and shortly after start; a follower runs one run at a time, and one that throws stops at that event, logs why and starts again from it next run, while the others go on.
A new follower's cursor starts at the end of the log, so no history is replayed.
A follow-up that only needs a signal, such as the console stream ([console](console.md)), listens to `EventRecorded` directly, and wakes are rows written with their event ([wakes-and-turn-end](wakes-and-turn-end.md)).

## Why

A state change cannot exist without its event, nor an event without its change, and a query always sees committed truth.
One cursor per follower gives in-order, at-least-once delivery with one row per follower, and a crash delays a follow-up by at most one round but never loses it.
Ending a run at a short batch spares each follower a read of the log that would find it empty, a connection of its own every time, and the cursor still moves after each event.

## Alternatives rejected

- An external broker - a server to run and secure.
- Event sourcing - replay EDI never needs.
- Spring Modulith's event publication registry - the same idea, without SQLite support.
- A handled flag per event and follower, or `@TransactionalEventListener` alone - more rows for the same guarantee, or an event lost when its follower crashed.
- Moving the cursor once per batch - a follower that throws part-way through a batch would follow the events before it again, to save one connection for each event a follower handles.

## Consequences

- Good: followers are tested by running their round directly, and the timeline of a task shows each follow-up next to its cause.
- Bad: an event a follower can never handle stops that follower until the cause is fixed, which its log names every round.

## When to revisit

When a follower must run in another process, or one follower's slowness delays the others.

## How we got here

- 15/10/2026: the captain asked for an internal event bus with command-query separation and no broker, and ruled out replay; the specification chose an in-process outbox on the same database.
- 17/10/2026: slice S6 built the outbox's relay as per-follower cursors for the fleet ledger, the ticket updates and the research-first pilot.
- 23/10/2026: the research-first pilot's test neared its timeout under load, and every follow round opened a connection per follower only to find the log empty, so a run ends at a short batch (EDI-45).

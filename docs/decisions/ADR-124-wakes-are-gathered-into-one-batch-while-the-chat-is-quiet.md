# ADR-124: Wakes are queued with the event that causes them, edi wait long-polls for them, and quiet gathers them into batches

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.3: the primary keeps one background `edi wait`, which long-polls the core and exits with a digest when a wake is queued; wakes are durable rows, printed and acknowledged so that a crash between print and handling re-delivers them; routine events never wake the primary.
Section 12.7: in quiet and away modes the core batches wakes into one turn every `presence.quietBatch` (30 minutes) instead of one turn per event, and a routine wake turn ends with no chat text.
A wake is read by one consumer, the primary, which pulls it.

**Decision:** `EventLog.record` asks `WakeRule.wakeFor` about every event and, for a wake, inserts a `wake` row in the same transaction, then publishes `WakeQueued`; `WakeSignal` hears it after commit and wakes every parked `edi wait`.
`WakeRule` decides once per event type, in one exhaustive switch, and wakes only for the list of section 12.3 and the health rules of section 12.2; nothing the primary itself caused wakes it.
`POST /api/wait` answers only the session that holds the primary role: it first acknowledges every wake it delivered to that session before, then delivers every unacknowledged wake, or parks for up to 25 seconds and answers 204; `edi wait` asks again after a 204 and exits printing the digest.
While the captain keeps the chat quiet, `WaitForWakes` delivers nothing until the oldest pending wake has waited `presence.quietBatch`, then delivers every pending wake in one batch; the delivery carries the presence, and `WakeDigestText` tells the primary to handle them and end the turn with no text for the captain.
A delivery whose long poll expired before it could be answered is withdrawn, so the next poll delivers it again, and a wake delivered to a session that crashed stays unacknowledged for the next primary.

**Motivation:** A wake can never exist without its event, and in quiet the primary works through one batch at a time instead of one turn per event, while the console and the notifications carry what matters to the captain at once.

**Alternatives considered:**
- Batching on a fixed clock - would wake the primary every half hour even when nothing waits.
- Dropping routine wakes in quiet - the primary still handles every wake; only its chat stays silent.

**Consequences:**
- Good: a burst of events in quiet becomes one turn.
- Bad: in quiet a worker's question waits up to `presence.quietBatch` for the primary; a decision for the captain reaches them at once as a notification.

**Revisit when:** A wake proves too urgent for the batch, such as a usage limit that needs an answer before its reset.

**Sources:** EDI rewrite specification, sections 5.8, 12.3 and 12.7 (decision E20, AT-46); ADR-011, ADR-069, ADR-122; `domain.wake.WakeRule`, `Wake`, `WakeKind`, `adapter.store.EventLog`, `WakeStore`, `WakeQueued`, `application.wake.WaitForWakes`, `WakeDelivery`, `WakeSignal`, `web.wake.WaitController`, `WakeDigestText`, `edi-cli` `WaitCommand`.

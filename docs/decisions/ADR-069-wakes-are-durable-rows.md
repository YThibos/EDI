# ADR-069: Wakes are queued with the event that causes them, and edi wait long-polls for them

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.3: the primary keeps one background `edi wait`, which long-polls the core and exits with a digest when a wake is queued; wakes are durable rows, printed and acknowledged so that a crash between print and handling re-delivers them; routine events never wake the primary.
ADR-011 lets each follow-up of a command run after commit, with a relay re-delivering what a crash interrupted.
A wake is read by one consumer, the primary, which pulls it, so nothing has to be pushed outwards.

**Decision:** `EventLog.record` asks `WakeRule.wakeFor` about every event and, for a wake, inserts a `wake` row in the same transaction, then publishes `WakeQueued`; `WakeSignal` hears it after commit and wakes every parked `edi wait`.
`WakeRule` decides once per event type, in one exhaustive switch: a worker's `needs-decision`, `blocked`, `failed` or `done`, a dead worker, an outage entry, a stale, over-long or ended wait, a launch that failed, a steer not received, a usage limit and its held-back resume, a mechanical check's outcome, a local branch delivery, a failed recovery and a hold whose date arrived; nothing the primary itself caused wakes it.
`POST /api/wait` answers only the session that holds the primary role: it first acknowledges every wake it delivered to that session before, then delivers every unacknowledged wake, or parks for up to 25 seconds and answers 204; `edi wait` asks again after a 204, retries for a minute while the core does not answer, and exits printing the digest.
A wake delivered to a session that crashed stays unacknowledged, so the next primary receives it again.

**Motivation:** A wake can never exist without its event, nor an event that should wake without its wake, because both are one commit; and the primary acknowledges a wake by asking for the next one, which it does only after handling the last.

**Alternatives considered:**
- An after-commit listener with the relay of ADR-011 - a second copy of the same guarantee, since the rows are pulled and already durable.
- Choosing wakes when `edi wait` reads the event log - the event payloads would have to be read back into typed events on every poll.
- Acknowledging a wake as it is printed - a primary that crashed before handling it would lose it.

**Consequences:**
- Good: a switch case per event type makes a new event type decide whether it wakes the primary.
- Bad: a delivery whose answer is lost before `edi wait` prints it, in a session that carries on, is acknowledged by that session's next wait without being read.

**Revisit when:** A follow-up pushes outwards, such as the console's live updates of slice S7, which brings the relay of ADR-011; or slice S8 batches wakes in quiet mode.

**Sources:** EDI rewrite specification, sections 5.8 and 12.3 (decision E20); ADR-011; `domain.wake.WakeRule`, `Wake`, `WakeKind`, `adapter.store.EventLog`, `WakeStore`, `WakeQueued`, `application.wake.WaitForWakes`, `WakeSignal`, `web.wake.WaitController`, `WakeDigestText`, `edi-cli` `WaitCommand`.

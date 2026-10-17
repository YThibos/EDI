# ADR-095: The captain's answer books the work on a ticket or creates it, and a refused answer goes back to him

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Only the captain's answer creates a JIRA ticket, and EDI fills every field after creation, the priority through its own call (section 14.2).
A tracker can be unreachable for a while, can refuse a call, and a crash can fall between creating a ticket and recording it; none of these may create a second ticket or leave the task keyed on nothing.

**Decision:** `AnswerDecision` records the answer and then asks `TicketProposals` to settle the task's ticket outside the transaction, because settling calls the tracker:
- an existing ticket is read once more and the task is keyed on it (`TaskKeyed`);
- a new ticket is created after the attempt is recorded on the proposal, then filled; the task is keyed in the same transaction that records `TicketCreated`;
- a creation interrupted after the attempt was recorded first adopts an open ticket with exactly the drafted summary, and creates one only when there is none;
- a tracker that refuses the fields after creating the ticket keeps the task on the created ticket and records `TicketUpdateRefused`, so the captain fixes the fields in the tracker;
- a tracker that refuses the choice itself, such as a proposed ticket it no longer finds, records `TicketChoiceRefused` and raises the same decision again under the same number with the refusal in its context, which wakes the primary;
- a tracker that cannot be reached leaves the answer unsettled, and the supervision round settles every answered proposal again every 30 seconds; a key settled there wakes the primary.
The confirmation of the answer says which of these happened.

**Motivation:** Every outcome ends either with the task keyed on exactly one ticket or with the question back before the captain; nothing is retried that cannot succeed by itself.

**Alternatives considered:**
- Retrying a refused answer every round - a 404 or a forbidden field never clears by itself.
- Creating the ticket inside the database transaction - a network call would hold the store's write lock.

**Consequences:**
- Good: the first ticket EDI creates is created exactly when the captain answers, which is this slice's acceptance check.
- Bad: an answer can come back as a decision raised again, which the primary has to put before the captain once more.

**Revisit when:** A tracker offers idempotent creation keyed by EDI's task id.

**Sources:** EDI rewrite specification, sections 9.1, 14.2 and 16; ADR-038, ADR-039, ADR-094; `application.decision.AnswerDecision`, `AnsweredDecision`, `application.tracker.TicketProposals`, `application.supervision.SupervisionRound`, `domain.tracker.ProposedTicket`, `TicketChoice`, `TicketSettlement`, `adapter.store.TicketProposalStore`, `domain.wake.WakeRule`, `web.ConfirmationText`.

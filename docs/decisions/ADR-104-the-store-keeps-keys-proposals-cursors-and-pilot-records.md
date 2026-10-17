# ADR-104: The store keeps work key sequences, ticket proposals, follower cursors, unplanned ships and reviews

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** EDI-native keys come from a per-project sequence in the store (section 8.5, ADR-016); the trackers of this slice keep a decision's proposal while the captain answers it; followers keep their place in the event log (ADR-096); the pilot needs its population and each ship's review (ADR-100).
Current state lives in tables and history in the event log (ADR-037).

**Decision:** `V5__tracker_and_time.sql` adds six tables:
- `work_key_sequence`, the last number issued per key prefix; the next key is one above the larger of that number and the highest number any task already carries under the prefix, so keys given by hand or migrated are never issued again;
- `ticket_proposal` and `ticket_candidate`, the drafted ticket, the matches offered in their order, and the progress of the answer: when creation was tried, the created key, when it was filled, the settled key, and the latest refusal;
- `event_cursor`, one row per follower, started at the end of the log;
- `unplanned_ship`, each ship dispatched with unplanned work, with its research task when research came first;
- `task_review`, each cleaned-up task's review rounds and first-round findings, unknown values kept unknown.
`WorkKeySequenceStore`, `TicketProposalStore`, `EventCursorStore`, `UnplannedShipStore` and `PipelineReviewStore` are the only code that reads and writes them, and `TaskStore` now also writes a task's key and its blockers when a task changes.

**Motivation:** Each table holds what a use case needs to resume after a restart, and every change to it is told by an event in the same transaction.

**Alternatives considered:**
- Keeping the proposal only in the decision card - the card's text cannot tell an interrupted creation from a new one.
- A `project` table for the key sequence, as section 8.2 lists - the registry stays the only place a project is described (ADR-041), and the sequence belongs to the prefix, which may change for future tasks.

**Consequences:**
- Good: changing a project's prefix starts its own sequence and leaves existing keys alone.
- Bad: two projects sharing a prefix share its sequence.

**Revisit when:** The EDI work board needs its own item records.

**Sources:** EDI rewrite specification, sections 8.2, 8.5 and 8.6a; ADR-016, ADR-037, ADR-041, ADR-096, ADR-100; `V5__tracker_and_time.sql`, `adapter.store.WorkKeySequenceStore`, `TicketProposalStore`, `EventCursorStore`, `UnplannedShipStore`, `PipelineReviewStore`, `TaskStore`.

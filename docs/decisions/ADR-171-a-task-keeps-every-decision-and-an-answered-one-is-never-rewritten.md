# ADR-171: A task keeps every decision, and a card raised after an answer is a new decision with a new number

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-18 numbers decisions durably, and I-20 records the captain's answer verbatim.
The store held one decision per task (`decision.task_id` was unique), and `RaiseDecision` raised a card on a task that already had a decision by rewriting that decision under its number, answered or not.
In the shadow run the primary raised a card on a task whose decision the captain had already answered, and the answered card was silently replaced and its number reopened, so the captain's answer was gone from the decision and the same number stood for two questions.
ADR-095 raised a refused ticket choice again under the same number on purpose, which rewrote the captain's answer the same way.
A captain-word act on a task counts any decision on it that the captain answered by choosing the option that names the act (ADR-115).

**Decision:** A task keeps every decision raised on it, and its latest decision is its decision (`DecisionStore.ofTask`); `DecisionStore.everyOneOfTask` lists them all.
Raising a card on a task whose latest decision is open updates that card under the same number, as before.
Raising a card on a task whose latest decision is answered raises a new decision with the next number, and the answered decision keeps its card and its answer; `Decision.reraised` refuses an answered decision, so nothing rewrites one.
A card raised by a decision's number updates that decision while it is open, and otherwise the task's open decision, so a task holds at most one open decision and its decision hold names it; a new number is raised only when the task has none open.
A refused ticket choice is therefore asked again as a new decision with the refusal in its context, replacing the same-number re-raise of ADR-095.
`CaptainsWordEvidence` weighs every decision of the task, so an earlier answer that authorised an act still counts after a later decision is raised.
Migration `V11__a_task_keeps_every_decision.sql` rebuilds the `decision` table without the unique task column, with foreign keys deferred so that the options, board bindings and board answers keep their decision, and indexes it by task and number.

**Motivation:** The captain's answer is the record of his word and stays exactly as given, and a number names one question for good, which is what AT-18's strictly increasing numbers ask of assigning again.

**Alternatives considered:**
- Refusing to raise on a task whose decision is answered - the primary would have no way to ask a follow-up on the task, and a refused ticket choice could not be asked again.
- Keeping one row per task and moving the old answer into the event history only - the decision table, the console and the authority check would lose the answer the captain gave.

**Consequences:**
- Good: an answered decision is never reopened or rewritten, whoever raises the next card.
- Good: a follow-up question on a task shows under its own number in the report and the console.
- Bad: a refused ticket choice comes back under a new number, so the captain sees two numbers for one ticket question.

**Revisit when:** A task needs more than one open decision at a time.

**Sources:** EDI rewrite specification, I-18 and I-20 (AT-18, AT-20); ADR-095, ADR-115, ADR-170; `application.decision.RaiseDecision`, `domain.decision.Decision`, `adapter.store.DecisionStore`, `application.authority.CaptainsWordEvidence`, `application.tracker.TicketProposals`, `V11__a_task_keeps_every_decision.sql`, `edi-cli` `DecisionRaiseCommand`.

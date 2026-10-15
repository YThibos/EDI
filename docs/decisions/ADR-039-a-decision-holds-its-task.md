# ADR-039: A decision holds its task, and its number never changes

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A decision always belongs to a held task, as in the old harness's single hold primitive (section 8.1).
Its D number is assigned once, never reused, and continues the old harness's sequence (I-18); the D number is an alias of its task wherever a task id is accepted (section 8.3).
The captain's answer is recorded verbatim with its interpretation, and the decision then leaves the report (I-20).

**Decision:** The task and its decision move together, through `RaiseDecision` and `AnswerDecision`:
- `edi decision raise <task> --card -` gives a task without a decision the next number, and gives a task that already has one the same number again, with the new card and no answer; either way the decision holds its task until the captain's word, replacing a deferral or approval the task carried;
- a task has at most one decision (`decision.task_id` is unique), so a D number names exactly one task, and `TaskReference` reads `D58` as that task;
- `edi decision answer <D|task> --text "<verbatim>" [--option X] [--interpretation "<text>"]` records the words exactly as given, the option only when the card offers it, the interpretation, the time and the channel (`chat` from the command); the answer releases the task, and a captain item, whose only purpose was the decision, is done;
- `edi hold --kind decision` is refused, because a decision hold without its card would be a decision without a number; `edi hold` takes `deferral` and `approval` only;
- `edi release <task>` releases a deferral or an approval; a decision hold is released only by its answer.
The open decisions are the decisions without an answer, read from the store by `DecisionListQuery`; `edi decision list --open` shows them, and every report surface draws its decision zone from the same rows, so a decision appears until it is answered whatever a turn says (I-19).

**Motivation:** One primitive cannot drift into two states: no held task waits on a decision that does not exist, and no decision is open on a task that is free.
A refused raise rolls back its transaction, so a number is consumed only by a decision that exists (ADR-038).

**Alternatives considered:**
- Decisions as their own entity, with holds set separately - two records that can disagree.
- A new number for every raise - breaks "assigned once" and the alias from D number to task.
- Answering with an option key only - loses the captain's words, which I-20 requires verbatim.

**Consequences:**
- Good: AT-18, AT-19 and AT-20 test the whole rule in milliseconds against a real store.
- Bad: a task that needs a second, unrelated decision after the first was answered reuses the first number; raising it again is the one way to ask the captain about that task.

**Revisit when:** A task genuinely needs two open decisions at once.

**Sources:** EDI rewrite specification, sections 4.4, 8.1 and 8.3, and AT-18 to AT-20; `domain.decision.Decision`, `domain.task.Task`, `application.decision.RaiseDecision`, `AnswerDecision`, `DecisionListQuery`, `invariant.DecisionsAndReportingTest`.

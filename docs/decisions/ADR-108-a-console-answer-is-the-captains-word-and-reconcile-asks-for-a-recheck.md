# ADR-108: A console answer is the captain's word through the chat answer's use case, and Reconcile asks for a re-check

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 15.3: an answer posts to `POST /api/decisions/{D}/answer` under the captain's console session, resolves exactly that decision through the same use case as a chat answer (I-20) and wakes the primary; a "Reconcile" option means "re-check reality", never an answer.
Section 9.1 counts an answer given in the console as the captain's word for exactly the decision it answers.
`WakeRule` wakes the primary for nothing the primary did itself (ADR-069).

**Decision:** The answer endpoint takes its channel from the caller: the captain's console session answers through `AnswerChannel.CONSOLE` as `Actor.CAPTAIN`, the primary through `CHAT` as before (`Caller.answeringChannel`), and both go to `AnswerDecision`.
`DecisionAnswered` now wakes the primary (`WakeKind.DECISION_ANSWERED`) whenever the answer came from anyone but the primary, with the decision, the channel and the verbatim text.
The console records exactly what the captain typed as the verbatim answer, with the option chosen; an option chosen without typed words is recorded as the option's key and its text as the card showed them, such as `A: The simple way`.
Each card offers Reconcile after its options; choosing it posts `POST /api/decisions/{D}/recheck` with any typed words as a note, which only the console session may call: `AskForARecheck` records `RecheckAsked` and wakes the primary (`WakeKind.RECHECK_ASKED`), and the decision stays open.
A refused answer, such as one to a decision answered meanwhile, comes back as the sentence the core refused it with and is shown on the card.

**Motivation:** One use case for every channel keeps the verbatim record, the option check, the ticket choice and the task release identical whichever way the captain answers.

**Alternatives considered:**
- A console-only answer use case - two places would enforce I-20.
- Recording a bare option as its key alone - the verbatim answer would no longer say what the captain chose without the card.
- Treating Reconcile as an answer with a special option - an answer closes the decision, and a re-check must leave it open.

**Consequences:**
- Good: the primary hears of a console answer at once and carries it to the work, as with a chat answer.
- Bad: the "re-check asked" marker on a card is the page's own and is gone after a reload, while the wake that carries the request is durable.

**Revisit when:** The console offers more than answering and re-checking, or the primary handles re-checks without the captain.

**Sources:** EDI rewrite specification, sections 9.1, 12.3 and 15.3 (I-20, AT-20); ADR-069, ADR-105; `web.decision.DecisionController`, `AnswerRequest`, `RecheckRequest`, `web.access.Caller`, `application.decision.AnswerDecision`, `AskForARecheck`, `domain.wake.WakeRule`, `edi-console` `DecisionCardView`, `invariant.DecisionsAndReportingTest` (AT-20).

# A decision holds its task, keeps its number and its answer, and is answered through one use case whatever the channel

**Kind:** Design

## Context

A decision always belongs to a task it holds, as in the old harness's single hold primitive.
Its D number is assigned once, never reused, continues the old harness's sequence (I-18), and is an alias of its task wherever a task id is accepted.
The captain's answer is recorded verbatim with its interpretation (I-20), and every open decision appears in every report until it is answered (I-19).
The captain answers in the chat, in the console and on research boards, and the console offers "Reconcile" to have reality re-checked rather than to answer.

## Decision

A task keeps every decision raised on it (`DecisionStore.everyOneOfTask`), and whoever needs one of them finds it among the task's open decisions or by its card, never by taking the latest; migration `V12__a_task_keeps_every_decision.sql` rebuilt the `decision` table without its unique task column, with foreign keys deferred so options, board bindings and board answers keep their decision.
`RaiseDecision` raises a card again under the number of the open decision it names, or of the task's open decision that asks the same (`DecisionCard.asksTheSameAs`, the same title), and otherwise gives the task a new decision with the next number from `decision_seq`, so a task holds as many open decisions as it has questions and an answered decision keeps its card and its answer for good; `Decision.reraised` refuses an answered decision.
A board raises its card again under the number of the open decision it binds to that question.
A decision named by its number resolves to exactly that decision (`DecisionStore.namedBy`, `TaskReference` reading `D58`) when it is answered, re-checked or raised again; named by its task, it resolves to the task's open decision, or to its latest one when none is open, and an answer or a re-check by the task's id is refused while the task waits on two or more decisions, naming them.
Raising by the number of an answered decision raises a new decision on its task, unless an open one already asks the same.
Either way the decision holds its task until answered, beside every other reason the task waits ([task-lifecycle](task-lifecycle.md)).
An answer releases only its own decision: a deferral, an approval, an outage entry or another open decision keeps holding the task.
A card may be urgent; a running ship's decision whose card is not reaches the captain in a round of decisions rather than on its own ([decision-rounds](decision-rounds.md)).
`AnswerDecision` records the words exactly as given, the option only when the card offers it, the interpretation, the time and the channel; the answer releases the task from that decision, and a captain item, whose only purpose was its decisions, is done once its question is answered and nothing else holds it, whichever comes last: the answer, or the `edi release` that leaves an answered captain item holding nothing, retires it.
When the task's worker waits on a question, its last status a `needs-decision` or `blocked` with a key, the same transaction stores the answer as a steer to it (`SteerWorker.carryTheAnswerOf`), and the worker is told of it once the answer is committed; the steer carries the decision's title, the chosen option, the captain's words and the interpretation.
The steer resolves that question only when the decision is the first of its task raised no earlier than the worker asked it, each decision judged by its first `DecisionRaised` event so that raising it again moves nothing (`DecisionsOfATask.answers`); a question asked afterwards is not the one the decision was raised for, and a later decision of the same task was raised for something else, so either answer reaches the worker as a plain steer and the question stays open.
A worker that waits on no question, such as a scout whose board round already carries the captain's words, a worker that is still at work or one whose done was accepted, gets nothing, and the primary carries the answer where it is needed.
A turn of the primary cannot end while an answered decision leaves the question it was raised for without a steer that resolves its key ([wakes-and-turn-end](wakes-and-turn-end.md)).
`edi hold` takes `deferral` and `approval` only, because a decision hold without its card would be a decision without a number, and `edi release` never releases a decision.
The open decisions are the decisions without an answer, and every report surface draws its decision zone from those rows ([fleet-snapshot](fleet-snapshot.md)).
Every channel answers through `AnswerDecision`: the primary as `CHAT`, the captain's console session as `CONSOLE` and a bound board as `board`, both as `Actor.CAPTAIN` (`Caller.answeringChannel`); an answer from anyone but the primary wakes the primary with the verbatim text.
An option chosen in the console without typed words is recorded as the option's key and text, such as `A: The simple way`.
Reconcile posts `/api/decisions/{D}/recheck`, which only the console session may call: `AskForARecheck` records `RecheckAsked` and wakes the primary, and the decision stays open.
An answer from the chat must have been typed by the captain in the current exchange ([captains-word](captains-word.md)), and a decision that proposes a ticket books it on the answer ([trackers-and-time-booking](trackers-and-time-booking.md)).

## Why

One primitive cannot drift into two states: no held task waits on a decision that does not exist, and no decision is open on a free task.
A refused raise rolls back its transaction, so a number is consumed only by a decision that exists ([store](store.md)).
One use case for every channel keeps the verbatim record, the option check, the ticket choice and the release identical however the captain answers.
An answer that reaches the waiting worker in the answer's own call cannot be forgotten, as one was on 4 November 2026, when a worker sat idle for over an hour and a half on an answer recorded but never sent.

## Alternatives rejected

- Decisions as their own entity, with holds set separately - two records that can disagree.
- A new number for every raise - a typo fixed on an open card would ask the captain a second question.
- One open decision per task, a second card rewriting the first - the first question vanished unanswered, and an answer cleared a deferral raised before it.
- Rewriting the task's decision under its number after an answer, as raising did until 22/10/2026 - the captain's answer vanished and one number stood for two questions.
- Refusing to raise on a task whose decision is answered - no follow-up question could be asked, and a refused ticket choice could not be asked again.
- Answering with an option key only - loses the captain's words.
- Linking each decision to the key of the worker question it was raised for - it needs a store migration, while a question the worker asked no later than the decision was raised is the one the decision settles.
- Resolving whichever question the worker has open when the answer comes - an unrelated question asked after the decision was raised, such as a pipeline gate, would be closed by an answer that does not answer it.
- A console-only answer use case, or Reconcile as a special answer - two places enforcing I-20, or a re-check that closes the decision.

## Consequences

- Good: AT-18 to AT-20 test the whole rule in milliseconds against a real store.
- Good: an answered decision is never reopened or rewritten, and a follow-up question shows under its own number.
- Good: the primary no longer carries each answer by hand with a separate steer naming the gate's key.
- Bad: a question the worker asked after the decision was raised stays open even when the answer settles it, so the primary resolves it with its own steer.
- Bad: a question answered by a later decision of its task than the first raised after it stays open until the primary resolves it with its own steer; the rule errs towards a question left open, never one closed by the wrong answer.
- Bad: a refused ticket choice comes back under a new number, so the captain sees two numbers for one ticket question.

## When to revisit

When a card's title is no longer enough to tell a question raised again from a new one.

## How we got here

- 15/10/2026: slice S1 tied each decision to its held task and made answers verbatim.
- 17/10/2026: slice S7 answered through the same use case from the console and boards, and added Reconcile.
- 22/10/2026: raising on a task whose decision was answered had silently replaced the answered card and reopened its number, so a task keeps every decision, a card raised after an answer gets a new number, and a decision named by number resolves to exactly that one (EDI-40).
- 09/11/2026: a card gained its urgent flag, and a running ship's decision without it waits for its round (EDI-96).
- 13/11/2026: an answer started to reach the task's worker waiting on a question as a steer in the answer's own call, resolving the worker's question only when it was asked no later than the decision was raised, after the primary carried about fifteen answers by hand on 9 November 2026 (EDI-241).
- 24/11/2026: a task waits on several decisions at once, each under its own number: a card with a new title is a new decision, the same title raises the open one again, and an answer releases only its own decision, so a deferred task stays deferred after it; a task's decision is found among its open decisions rather than taken as its latest, and the release that frees an answered captain item retires it (EDI-87).
- 24/11/2026: with several open decisions on a task, a worker's question is resolved only by the first decision of its task raised after it was asked, so the answer to an unrelated later card no longer closes it (EDI-87).

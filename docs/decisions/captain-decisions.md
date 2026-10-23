# A decision holds its task, keeps its number, and is answered through one use case whatever the channel

**Kind:** Design

## Context

A decision always belongs to a held task, as in the old harness's single hold primitive.
Its D number is assigned once, never reused, continues the old harness's sequence (I-18), and is an alias of its task wherever a task id is accepted.
The captain's answer is recorded verbatim with its interpretation (I-20), and every open decision appears in every report until it is answered (I-19).
The captain answers in the chat, in the console and on research boards, and the console offers "Reconcile" to have reality re-checked rather than to answer.

## Decision

`RaiseDecision` gives a task without a decision the next number from `decision_seq`, and a task that has one the same number again with the new card and no answer; either way the decision holds its task until answered, and a task has at most one decision (`decision.task_id` is unique, `TaskReference` reads `D58` as that task).
`AnswerDecision` records the words exactly as given, the option only when the card offers it, the interpretation, the time and the channel; the answer releases the task, and a captain item, whose only purpose was the decision, is done.
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

## Alternatives rejected

- Decisions as their own entity, with holds set separately - two records that can disagree.
- A new number for every raise - breaks "assigned once" and the alias from number to task.
- Answering with an option key only - loses the captain's words.
- A console-only answer use case, or Reconcile as a special answer - two places enforcing I-20, or a re-check that closes the decision.

## Consequences

- Good: AT-18 to AT-20 test the whole rule in milliseconds against a real store.
- Bad: a task that needs a second, unrelated decision after the first was answered reuses the first number.

## When to revisit

When a task genuinely needs two open decisions at once.

## How we got here

- 15/10/2026: slice S1 tied each decision to its held task and made answers verbatim.
- 17/10/2026: slice S7 answered through the same use case from the console and boards, and added Reconcile.

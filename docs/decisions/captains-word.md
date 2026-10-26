# The captain's word is what the captain typed in the current exchange, and only it promotes, drops, discards, lifts one task above the effort ceiling or touches secrets

**Kind:** Design

## Context

A ship from a scout needs the captain's go recorded on the task (I-05), and destructive, irreversible and security-sensitive acts need the captain's word in the current exchange (I-06).
The core cannot judge what the captain meant, but it can prove where words came from.
An answer given in the console or on a bound research board is the captain's word for exactly the decision it answers.
Discarding unlanded work is the one irreversible act on a task's work (I-12).

## Decision

A prompt the captain typed is kept as SHA-256 digests of its passages in `typed_passage`: `Passage.allTypedIn` takes the whole prompt and each non-blank line, compared without case, runs of spacing or curly quotes.
The exchange a call belongs to is its own session, or for a call that names none the session holding the primary role (`CaptainsWordEvidence.currentExchange`); without a primary session there is no exchange and the act is refused.
`edi captain said --text <words> [--task <task> [--for <act>]]` records an `Authorisation` in `captain_word` only when the words were typed in that exchange, with the one act it is for, or none for a general word.
An act on a task (promoting a scout, dropping a task, discarding work) needs a general word on that task from the exchange, or any of its decisions the captain answered, through any channel, since the session opened (`CaptainsWordEvidence` weighs every decision of the task), by choosing an option that names exactly that act with `authorises` (`promote`, `drop` or `discard-work`, `CaptainsAuthority.onTask`).
Running one task above `high` effort needs a word recorded on that task for that act with `edi captain said --task <task> --for effort-above-high` in the exchange; a general word never lifts effort, a word for it authorises nothing else, and no decision option authorises it (`CaptainWordAct.EFFORT_ABOVE_HIGH`, `CaptainsAuthority.saidForActOnTask`, `CaptainsWordEvidence.requireSaidForActOnTask`, [dispatch-routing](dispatch-routing.md)).
An act that concerns no single task (touching a secret, deleting an unlanded branch, stopping running workers) needs a word on the home (`CaptainsAuthority.onTheHome`).
`edi decision answer` from the chat is refused unless its verbatim text was typed in the exchange.
`PromoteScout` queues a new ship through `AddTask`, keeping the scout's projects, key, base and delivery, with the scout's notes, the captain's verbatim go and the report's path as the ship's notes.
`DropTask` refuses while a decision waits on the task or its worker still works, runs the landed-work test, retires landed work as cleanup does, refuses unlanded work unless `--discard-work` is given, and records `TaskDropped` with the captain's words and what was discarded.
`DiscardWork` (`edi worktree discard`) removes the task's copy and branch whatever they hold and keeps the task in flight, so a later dispatch starts a fresh copy.

## Why

Proving the words came from the captain's keyboard in this conversation separates "the captain said so" from a notification, a worker's report or a tool's output.
Digests keep a pasted secret out of the store, and an exchange that ends with its session keeps yesterday's approval from authorising today's act.
A word on one task or one act authorises nothing else.

## Alternatives rejected

- A call without a session header as the captain at their own terminal - a session that dropped its header would act with the captain's word.
- Storing prompts in full - keeps whatever the captain typed, secrets included.
- One authorisation for the whole session, or any answered decision authorising every act on its task - an approval to promote would also discard another task's work.
- Changing a scout into a ship in place - its research branch, brief and worker would carry into delivery.
- A `--force` on cleanup - would make the irreversible act look routine.

## Consequences

- Good: AT-05 and AT-06 hold mechanically, and the primary learns from one refusal sentence what to record.
- Bad: the primary must record the captain's whole message or one whole line of it, and a decision meant to authorise an act must say so on the option.
- Bad: from their own shell outside a primary session the captain gives their word in the console or the primary session.

## When to revisit

When the console gains its own promote, drop or discard actions, or the captain wants their shell to carry their word.

## How we got here

- 17/10/2026: slice S8 built the evidence from typed prompts, promotion, dropping and discarding.
- 22/10/2026: a task keeps every decision, so an earlier answer that authorised an act still counts after a later decision is raised, and a scout's board card answered on the board is the captain's go (EDI-40).
- 26/10/2026: the captain's word recorded on one task lifts that task alone above the effort ceiling, the way their standing rule allows (EDI-49).
- 26/10/2026: the lift now needs a word recorded on the task for that act, `--for effort-above-high`, so a word recorded to discard or drop a task never lifts its effort (EDI-49).

# ADR-115: The captain's word is what the captain typed in the current exchange, and the session the captain typed in last is the primary

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-02: a session in which the captain types a prompt claims the primary role; any other primary becomes standby and read-only.
I-05 and I-06: a ship from a scout needs the captain's go recorded on the task, and destructive, irreversible and security-sensitive acts need the captain's word in the current exchange; AT-05 refuses `edi task promote` without a recorded authorisation on that task, and AT-06 refuses discarding work, deleting an unlanded branch and touching a secret without the captain's word in the current session.
Section 9.1: the primary records the captain's authorisation with `edi captain said --text "<verbatim>"` in the same turn as the captain's message, and the core accepts it only when its text appears in a typed prompt of the current session; an answer in the console or on a bound research board is the captain's word for exactly the decision it answers.
Not every prompt is the captain's: a finished background job submits a `<task-notification>` prompt, and EDI's own injected lines carry the operational marker.
A primary that crashed sends no `SessionEnd`.

**Decision:** `primary_session` keeps every session that runs EDI's primary hooks, with its Claude process (pid and start time from the process table), when it claimed the role and its current turn.
`PrimaryRole` gives the role to the session that claimed it last, among the sessions that have not ended and whose Claude process still runs, or whose process is unknown.
A session claims the role at its start when no live session holds it, and at every prompt `Prompt.isTypedByTheCaptain` accepts; a claim takes the role from every other session in the same transaction.
`ApiTokenFilter` turns the home token with an `EDI-Session` header into `Caller.Standby` when another session holds the role, and `Caller.requirePrimary` refuses a standby caller; a call without a session header is a primary caller.
`SessionEnd` releases the role, and a crashed holder loses it as soon as its process is gone.
A typed prompt is kept as SHA-256 digests of its passages in `typed_passage`: `Passage.allTypedIn` takes the whole prompt and each non-blank line, compared without case, runs of spacing or curly quotes; migration V7 replaces the earlier `typed_prompt` table.
The exchange a call belongs to is its own session, or for a call that names none the session holding the primary role (`Caller.callingSession`, `CaptainsWordEvidence.currentExchange`), so no caller escapes the check by dropping its session header; without a primary session there is no exchange and the act is refused.
`edi captain said --text <words> [--task <task>]` records an `Authorisation` in `captain_word`, and `CaptainSaid` on the task, only when the words were typed in that exchange.
A captain-word act on a task (promoting a scout, dropping a task, discarding work) needs a word on that task from the exchange, or a decision on it that the captain answered, through any channel, since the session opened, by choosing an option that names exactly that act (`CaptainsAuthority.onTask`).
A decision card's option names the act it authorises with `authorises` (`promote`, `drop` or `discard-work`, `CaptainWordAct`), kept in `decision_option`; an answer that chooses another option, or a decision whose options name another act or none, authorises nothing.
An act that concerns no single task (touching a secret, deleting a branch that has not landed, stopping the running workers) needs a word on the home (`CaptainsAuthority.onTheHome`).
`edi decision answer` from the chat is refused unless its verbatim text was typed in the exchange; an answer from the console or a board is the captain's word already.

**Motivation:** The core cannot judge what the captain meant, but it can prove that the words came from the captain's keyboard in this conversation, which separates "the captain said so" from a notification, a worker's report or a tool's output.
Digests keep a pasted secret out of the store, and an exchange that ends with its session keeps yesterday's approval from authorising today's act.

**Alternatives considered:**
- Treating a call without a session header as the captain at their own terminal - a session that removed its own header would act with the captain's word.
- Storing the captain's prompts in full - substring matching for less ceremony, at the cost of keeping whatever the captain typed, secrets included.
- One authorisation for the whole session - an approval to promote a scout would also discard another task's work.
- Any answered decision on the task authorising every act on it - "later" to a question about rebasing would discard the work, where section 9.1 makes an answer the captain's word for exactly the decision it answers.

**Consequences:**
- Good: AT-05 and AT-06 hold mechanically, and the primary learns from one refusal sentence what to record.
- Bad: the primary records the captain's words exactly, their whole message or one whole line of it; a sentence cut from a longer line is refused.
- Bad: from their own shell outside a primary session the captain gives their word in the console or in the primary session, not in the command.
- Bad: a decision meant to authorise an act must say so on the option that does it; the refusal names the act to put there.

**Revisit when:** The console gains its own promote, drop or discard actions, or the captain wants their shell to carry their word.

**Sources:** EDI rewrite specification, sections 9.1, 10.2 and 16, I-02, I-05, I-06 (AT-02, AT-05, AT-06); ADR-067; `domain.authority.Passage`, `CallingSession`, `domain.decision.Decision`, `DecisionCard`, `DecisionOption`, `Exchange`, `Authorisation`, `CaptainWordAct`, `CaptainsAuthority`, `domain.primary.PrimaryRole`, `Prompt`, `application.authority.CaptainsWordEvidence`, `application.primary.PrimaryHooks`, `PrimarySessions`, `adapter.store.CaptainWordStore`, `PrimarySessionStore`, `web.authority.CaptainController`, `web.decision.DecisionController`, `web.access.Caller`, `ApiTokenFilter`, `edi-cli` `CaptainSaidCommand`, `V7__claude_layer_memory_and_presence.sql`.

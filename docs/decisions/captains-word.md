# The captain's word is a message the captain sent in the current exchange, stored as it arrives, and only it promotes, drops, discards, lifts one task above the effort ceiling or touches secrets

**Kind:** Design

## Context

A ship from a scout needs the captain's go recorded on the task (I-05), and destructive, irreversible and security-sensitive acts need the captain's word in the current exchange (I-06).
The core cannot judge what the captain meant, but it can prove where words came from.
An answer given in the console or on a bound research board is the captain's word for exactly the decision it answers.
Discarding unlanded work is the one irreversible act on a task's work (I-12).
In the week of 26 October 2026 the primary retyped the captain's words in 139 calls, 12 of them were refused because the retyped text differed from what the captain typed, and a choice the captain made in a Claude Code dialog could not count as their word, so they typed it again.

## Decision

The primary's `UserPromptSubmit` hook stores every prompt the captain types, verbatim, as a numbered `TypedMessage` of its session with the time it arrived (`PrimaryHooks.promptSubmitted`, `CaptainMessageStore`, table `captain_message`); a prompt opening with `<task-notification>` or EDI's operational mark is not the captain's and is not stored.
Its `PostToolUse` hook on `AskUserQuestion` stores each answered question as a numbered `DialogAnswer`: the question, the option the captain chose or the text they gave for "Other", and their notes (`PrimaryHooks.dialogAnswered`, `DialogAnswers` in the hook controller).
Numbers count from 1 in each session, over both kinds; `edi captain messages` lists them compactly (`CaptainMessagesText`).
A worker's hooks never reach the store, whatever session id they carry.
Claude Code marks pasted text as `<pasted_content id="...">` up to `</pasted_content id="...">`; a stored message keeps that text and its marks, and `TypedMessage.lines` marks every line from an opening tag up to the closing tag with the same id as pasted.
A closing tag inside a paste with another id, or none, is pasted text and ends nothing, and a paste that never closes runs to the end of the message.
A `Citation` names the captain's words: a whole message by its number (`--message <n>`), one line of it (`--line <k>`), or a text (`--text`) that equals a stored message or one of its lines, compared without case, runs of spacing or curly quotes (`Passage`).
`CaptainMessages.cite` refuses a message that holds pasted content as a whole, a pasted or blank line, a number the session never had, and a text no stored message holds; a text that a typed message and a dialog answer both hold is cited from the typed message.
The exchange a call belongs to is its own session, or for a call that names none the session holding the primary role (`CaptainsWordEvidence.currentExchange`); without a primary session there is no exchange and the act is refused.
`edi captain said --message <n> [--line <k>] [--task <task> [--for <act>]]` records an `Authorisation` in `captain_word` from a typed message of that exchange, with the one act it is for, or none for a general word; a dialog answer is never recorded this way.
`edi decision answer` from the chat records the cited words, from a typed message or a dialog answer, as the captain's verbatim answer.
A dialog answer is the captain's word by itself for one act, when its chosen option opens with the act's word as a whole word and, for an act on a task, names the task's id as a whole word, such as `drop fit-lamp` or `effort-above-high fit-lamp at xhigh` (`DialogAnswer.authorises`).
An act on a task (promoting a scout, dropping a task, discarding work) needs a general word on that task from the exchange, a dialog answer for that act on it, or any of its decisions the captain answered, through any channel, since the session opened (`CaptainsWordEvidence` weighs every decision of the task), by choosing an option that names exactly that act with `authorises` (`promote`, `drop` or `discard-work`, `CaptainsAuthority.onTask`).
Running one task above `high` effort needs a word recorded on that task for that act with `edi captain said --task <task> --for effort-above-high` in the exchange, or a dialog answer for it; a general word never lifts effort, a word for it authorises nothing else, and no decision option authorises it (`CaptainWordAct.EFFORT_ABOVE_HIGH`, `CaptainsAuthority.saidForActOnTask`, `CaptainsWordEvidence.requireSaidForActOnTask`, [dispatch-routing](dispatch-routing.md)).
An act that concerns no single task (touching a secret, deleting an unlanded branch, stopping running workers) needs a word on the home, or a dialog answer for that act (`CaptainsAuthority.onTheHome`).
The guard refuses the primary an `AskUserQuestion` call whose answers or notes are already filled in, so only the captain answers a dialog ([pre-tool-guard](pre-tool-guard.md)).
`PromoteScout` queues a new ship through `AddTask`, keeping the scout's projects, key, base and delivery, with the scout's notes, the captain's verbatim go and the report's path as the ship's notes.
`DropTask` refuses while a decision waits on the task or its worker still works, runs the landed-work test, retires landed work as cleanup does, refuses unlanded work unless `--discard-work` is given, and records `TaskDropped` with the captain's words and what was discarded.
`DiscardWork` (`edi worktree discard`) removes the task's copy and branch whatever they hold and keeps the task in flight, so a later dispatch starts a fresh copy.

## Why

Storing the words as they arrive separates "the captain said so" from a notification, a worker's report or a tool's output by where they came from, and lets the primary cite them instead of retyping them, so a word is never refused for a slip in the copy.
A dialog answer reaches the core from Claude Code's own record of the captain's choice, so it is as much the captain's as a typed prompt.
Requiring the option to open with the act and name the task keeps an option such as "do not drop fit-lamp" from authorising the drop, and keeps a choice about one task from reaching another.
Keeping pasted text marked keeps a worker's report or a document the captain pastes from passing as their word.
An exchange that ends with its session keeps yesterday's approval from authorising today's act, and a word on one task or one act authorises nothing else.

## Alternatives rejected

- SHA-256 digests of each typed passage, which kept a pasted secret out of the store - the primary had to retype the words exactly, which failed 12 times in a week, and no message could be cited or listed; the store holds no more than the session's transcript already does.
- Treating a whole message with pasted content as the captain's word - a pasted instruction would authorise an act.
- Any dialog answer as a general word on a task, or an option that merely mentions the act - "keep it, do not drop it" would drop the task.
- Recording a dialog answer again with `edi captain said` - a second step for a choice the captain already made, which is the retyping this removes.
- A call without a session header as the captain at their own terminal - a session that dropped its header would act with the captain's word.
- One authorisation for the whole session, or any answered decision authorising every act on its task - an approval to promote would also discard another task's work.
- Changing a scout into a ship in place - its research branch, brief and worker would carry into delivery.
- A `--force` on cleanup - would make the irreversible act look routine.
- A second channel for each act, such as a confirmation in the console or a one-time code typed back - a step on every promote, drop and discard, while a process running as the captain's own user could reach that channel as well; it is worth its cost once workers run as another user.

## Consequences

- Good: AT-05 and AT-06 hold mechanically, the primary cites a number instead of copying words, and a dialog the captain answers can authorise the act it names.
- Good: the primary learns from one refusal sentence what to cite or what option to offer.
- Bad: the store keeps whatever the captain types in the primary, a pasted secret included, as the session's transcript does; it lives in the EDI home with the rest of the store.
- Bad: a dialog option meant to authorise an act must open with the act's word and name the task, and a decision option must say so with `authorises`.
- Bad: from their own shell outside a primary session the captain gives their word in the console or the primary session.
- Bad: a process of the captain's own user that the guard does not see, such as a script a worker writes and then runs, can still type into the primary's pane on the captain's tmux server, and that text would count as typed.
- Bad: the hooks rely on Claude Code's `AskUserQuestion` payload and its paste marks, which a release may change ([claude-code](../verification/claude-code.md)).

## When to revisit

When the console gains its own promote, drop or discard actions, the captain wants their shell to carry their word, workers run as another user, or Claude Code changes how it reports dialog answers or pasted text.

## How we got here

- 17/10/2026: slice S8 built the evidence from typed prompts, promotion, dropping and discarding.
- 22/10/2026: a task keeps every decision, so an earlier answer that authorised an act still counts after a later decision is raised, and a scout's board card answered on the board is the captain's go (EDI-40).
- 26/10/2026: the captain's word recorded on one task lifts that task alone above the effort ceiling, the way their standing rule allows (EDI-49).
- 26/10/2026: the lift now needs a word recorded on the task for that act, `--for effort-above-high`, so a word recorded to discard or drop a task never lifts its effort (EDI-49).
- 28/10/2026: a typed prompt stays the only evidence, and the guard keeps a worker's shell from typing into any tmux pane, after the security review found a worker could type into the primary through the captain's default server (EDI-123).
- 02/11/2026: the hooks store the captain's messages and dialog answers verbatim and numbered, in place of digests, so the primary cites them by number, and a dialog answer that opens with an act and names its task is the captain's word for that act (EDI-159).

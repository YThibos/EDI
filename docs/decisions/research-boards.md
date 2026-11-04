# Research boards are armed through EDI with their decision cards, listened to by the core, and their rounds reach the worker and the bound decisions

**Kind:** Architecture

## Context

Scouts iterate on research with the captain on Lavish boards, and on the old harness a board's listener died with its scout's session, losing the captain's answers.
`lavish-axi poll <file>` long-polls until the captain sends feedback or leaves, takes `--owner` and `--takeover`, and prints its result as TOON, whose prompts come either as a table or as a list of items with attachments; the old reader counted a list-form round as empty.
An answer on a bound board is the captain's word for exactly the decision it answers.
Only the scout knows the board's link and how many questions a round asks, and a board that bound no open decision never showed the captain anything waited.
`edi decision raise` is the primary's to run, so a scout could not raise the decision card its board puts before the captain, and the board's form sends Lavish's own answer data, `question` and `answer`, never a D number the scout could not know before the decision existed.

## Decision

A scout runs `edi board arm <html> --task <id> --link <url> --questions <n> [--card <card.json>] [--bind D..]`; `ArmBoard` refuses a file outside the task's directory, another task's worker, a decision that is not open, and a round with neither a question, a card nor a bound decision; arming a listening board again posts a new round.
`--card` is the decision card the round puts before the captain, in the card format of `edi decision raise` plus `question`, the board question that answers it; `ArmBoard` raises it on the board's own task as the arming worker through `RaiseDecision`, in the transaction that arms the round, checks it in the captain's voice, and binds the decision under that question (`BoardCard`, `BoardRound.withItsCardRaisedAs`, `BoardBinding`, `board_decision.question` from migration `V11__board_questions.sql`).
A card is refused without a question, when an answered decision of its task already carries its title (`DecisionCard.asksTheSameAs`), whatever else the card changes, or when it differs from its task's open decision and that decision is not bound to this board under that question, so a scout never asks an answered question again or replaces a card the primary raised; each refusal names its fix.
Arming a listening board again keeps every binding of a still-open decision it already holds, under its question, even when the round leaves `--card` out or binds that decision again by number (`BoardRound.carryingForward`).
The primary may post a new round on a listening board with `edi board post` (`PostBoardRound`), keeping its bindings.
`LavishBoardHost`, behind the `ResearchBoardHost` port, owns the program `LAVISH` and runs `lavish-axi poll <file> --owner edi:<task>#<board> --takeover` through `ExternalPrograms.runUntilItEnds`.
`BoardListeners` runs one listener per listening board on a virtual thread, polling again after an answered round; a round every `edi.boards.round` (PT30S) restarts any missing listener and stops those of boards that no longer listen; cleanup retires a task's boards.
`ToonReader` reads TOON by its grammar into a JSON tree, refusing any malformed or short read with its line, and `LavishResult` maps it to a `BoardFeedback`: answered, closed empty, disconnected or failed.
`ReceiveBoardFeedback` records an answered round in one transaction: the round counted, its answers to bound decisions saved as `board_answer` rows, one inbox message with every prompt for the task's worker, and `BoardAnswered`, which wakes the primary.
`edi board show <task> [--round N]` reads every answered round back read-only, from `GET /api/tasks/<task>/board` (`BoardRoundsQuery`), for the primary and the captain's console session alone: each round's message, found as the `SteerSent` the round's `BoardAnswered` follows, with how each bound answer settled; it never marks the message received and records nothing, so the scout's `edi inbox` still delivers it.
A prompt answers a bound decision when its context names it as `decision`, or names the binding's question as `question`, or when its words begin with the decision's number as in the chat (`D58 B, simpler`); the option is the context's `option` or `answer` when it is a single capital letter, or the capital letter that follows the number when the card offers it; `SettleBoardAnswers` then answers each through `AnswerDecision` on the `board` channel as the captain, with the prompt's words verbatim, once, even across a crash, so it authorises exactly the act its chosen option names ([captain-decisions](captain-decisions.md), [captains-word](captains-word.md)).
Settlement is the only judge of a board answer: the round's message only says which decisions its prompts answer, and whenever settlement refuses one, for any reason, it records `BoardAnswerRefused`, which wakes the primary, and sends the board's task worker one inbox message naming the decision, the reason and the captain's words.
A board waits on the captain while it listens and holds open questions or binds an open decision, which puts it in the decision zone with its link and each open decision it binds as one line beneath it ([fleet-snapshot](fleet-snapshot.md)).

## Why

The port keeps every rule about boards testable with a fake host, and `--takeover` lets a restarted core take its boards back.
Reading TOON by its grammar makes both prompt forms read the same way, and pending rows written with the round make the answers as durable as the round.
The scout states what it asks at the moment it asks it, so the zone shows the board exactly while a round waits.
The core raising the card makes raising and binding one step a scout can take, so the card the captain answers on the board is always a decision EDI holds, and matching on the question Lavish already sends lets a form built the way Lavish's input playbook shows answer it unchanged.

## Alternatives rejected

- A listener the scout runs itself - its answers die with its session.
- A TOON library for Java, or a line reader - one more native-image dependency, or the reader that lost list-form rounds.
- Answering decisions inside the round's transaction - the decision's use case may call a tracker.
- Taking any capital letter after the number as the option - `D58 A lot hinges on cost` would choose option A.
- Counting questions in the board's HTML, or asking Lavish on every read - no element marks a question, and a program per page load.
- `edi board arm` refusing a board whose round carries an unraised card - the core cannot see a card in the scout's own markup, and a scout cannot raise one.
- Letting a worker run `edi decision raise` - binding would still be a second step, and a worker could raise cards on any task.
- Having the scout write the D number into the board after arming - the board is open in the captain's browser by then, and a forgotten edit loses the answer.
- Applying a board answer to a card the primary raises afterwards - the captain chose among the options the board showed, which need not be the card's.
- Foreseeing settlement's refusals when the round arrives - a second copy of the rules that drifts from the one that decides.
- Taking only an identical card as already answered - a scout that added evidence to an answered card's context raised it again as a new decision.
- Taking an answered binding under the same board question as already answered - a scout may reuse a question name for a new question in a later round, and a binding is gone once a round arms without it.

## Consequences

- Good: a board answer reaches its decision even when the scout is dead, and a board with questions reaches the captain with its link.
- Good: a choice the captain makes on a scout's board is his word for the card it answers, promote included, and he never retypes it in chat.
- Bad: one `lavish-axi poll` runs per listening board, a message starting with a bound decision's number answers it, and a scout that forgets to arm a new round leaves it out of the zone.
- Bad: a follow-up question on a scout's task needs a title of its own.
- Bad: a task has one open decision at a time, so a board raises at most one open card of its own task, and a decision drawn on a board without `--card` is a question EDI does not hold.

## When to revisit

When Lavish offers JSON output, reports a board's unanswered prompts itself or marks a question's options, a task needs more than one open decision, or boards answer something other than decisions.

## How we got here

- 17/10/2026: slice S7 built arming, listening, the TOON reader and round delivery.
- 21/10/2026: a shadow run showed two boards waiting while the report said nothing was open, so a round names its link and question count, and the option letter must stand alone.
- 22/10/2026: a verification scout's close-out card, never raised or bound, made the captain retype his board choice in chat, so `edi board arm --card` raises and binds the card, the board's question answers it, and settlement tells the scout of every refused answer (EDI-40).
- 26/10/2026: a scout re-armed its board with an answered card whose context it had extended, which raised a duplicate decision, so a card is answered when an answered decision of its task carries its title (EDI-51).
- 03/11/2026: two steers had existed only to ask a scout whether every answer had arrived, since a round's full text lived only in its inbox, so `edi board show` reads the rounds without consuming them (EDI-66).

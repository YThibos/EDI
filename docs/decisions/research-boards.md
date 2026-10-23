# Research boards are armed through EDI, listened to by the core, and their rounds reach the worker and the bound decisions

**Kind:** Architecture

## Context

Scouts iterate on research with the captain on Lavish boards, and on the old harness a board's listener died with its scout's session, losing the captain's answers.
`lavish-axi poll <file>` long-polls until the captain sends feedback or leaves, takes `--owner` and `--takeover`, and prints its result as TOON, whose prompts come either as a table or as a list of items with attachments; the old reader counted a list-form round as empty.
An answer on a bound board is the captain's word for exactly the decision it answers.
Only the scout knows the board's link and how many questions a round asks, and a board that bound no open decision never showed the captain anything waited.

## Decision

A scout runs `edi board arm <html> --task <id> --link <url> --questions <n> [--bind D..]`; `ArmBoard` refuses a file outside the task's directory, another task's worker, a decision that is not open, and a round with neither a question nor a bound decision; arming a listening board again posts a new round.
The primary may post a new round on a listening board with `edi board post` (`PostBoardRound`), keeping its bindings.
`LavishBoardHost`, behind the `ResearchBoardHost` port, owns the program `LAVISH` and runs `lavish-axi poll <file> --owner edi:<task>#<board> --takeover` through `ExternalPrograms.runUntilItEnds`.
`BoardListeners` runs one listener per listening board on a virtual thread, polling again after an answered round; a round every `edi.boards.round` (PT30S) restarts any missing listener and stops those of boards that no longer listen; cleanup retires a task's boards.
`ToonReader` reads TOON by its grammar into a JSON tree, refusing any malformed or short read with its line, and `LavishResult` maps it to a `BoardFeedback`: answered, closed empty, disconnected or failed.
`ReceiveBoardFeedback` records an answered round in one transaction: the round counted, its answers to bound decisions saved as `board_answer` rows, one inbox message with every prompt for the task's worker, and `BoardAnswered`, which wakes the primary.
A prompt answers a bound decision when its context names it, or when its words begin with the decision's number as in the chat (`D58 B, simpler`), the option being the capital letter that follows when the card offers it; `SettleBoardAnswers` then answers each through `AnswerDecision` on the `board` channel, once, even across a crash ([captain-decisions](captain-decisions.md)).
A board waits on the captain while it listens and holds open questions, which puts it in the decision zone with its link ([fleet-snapshot](fleet-snapshot.md)).

## Why

The port keeps every rule about boards testable with a fake host, and `--takeover` lets a restarted core take its boards back.
Reading TOON by its grammar makes both prompt forms read the same way, and pending rows written with the round make the answers as durable as the round.
The scout states what it asks at the moment it asks it, so the zone shows the board exactly while a round waits.

## Alternatives rejected

- A listener the scout runs itself - its answers die with its session.
- A TOON library for Java, or a line reader - one more native-image dependency, or the reader that lost list-form rounds.
- Answering decisions inside the round's transaction - the decision's use case may call a tracker.
- Taking any capital letter after the number as the option - `D58 A lot hinges on cost` would choose option A.
- Counting questions in the board's HTML, or asking Lavish on every read - no element marks a question, and a program per page load.

## Consequences

- Good: a board answer reaches its decision even when the scout is dead, and a board with questions reaches the captain with its link.
- Bad: one `lavish-axi poll` runs per listening board, a message starting with a bound decision's number answers it, and a scout that forgets to arm a new round leaves it out of the zone.

## When to revisit

When Lavish offers JSON output or reports a board's unanswered prompts itself, or boards answer something other than decisions.

## How we got here

- 17/10/2026: slice S7 built arming, listening, the TOON reader and round delivery.
- 21/10/2026: a shadow run showed two boards waiting while the report said nothing was open, so a round names its link and question count, and the option letter must stand alone.

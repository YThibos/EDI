# An idea is caught as a spark in one step, with its context, and reaches the backlog only through the weekly gardening

**Kind:** Design

## Context

The captain wants a place for random idea sparks beside the measured weekly gardening.
Before sparks, an idea either went straight to the backlog half-formed, or was lost in a session.
Ideas go through the gardening before the backlog, and catching one must not interrupt the work in hand.
The primary's `UserPromptSubmit` hook already stores every message the captain types ([captains-word](captains-word.md)).
EDI has no merge capability of any kind, and `NameAMergeOperation` refuses any class, method or path that names one (I-03).

## Decision

A `Spark` holds its number, counted from 1 across the home, its text, its `SparkAuthor` (the captain or the primary), the Claude Code session it came from, its `Focus` (a task, a project, or neither), the time it was caught, and once sorted its `SparkSorting`; table `spark` holds them (`SparkStore`).
`edi spark "<idea>"`, or `edi spark -` reading the idea from standard input, catches a spark through `CatchSpark.caught`; a call from a Claude Code session is the primary's spark, and a call under the home's token without a session is the captain's, from their own terminal; a worker, the console and a standby session are refused.
`--task` or `--project` names the focus; without either, the focus is the task the primary or the captain acted on last, the latest event in the event log whose actor is one of them (`EventLog.latestTaskActedOnBy`), or no focus when there is none.
The primary's `UserPromptSubmit` hook catches a message the captain typed that opens with `Spark:` or `spark:` as the captain's spark, with the rest of the message as its text, the session and the same focus (`SparkMessage.ideaIn`, `CatchSpark.fromTheCaptainsMessage`); the message is stored as the captain's message as well, and the hook's additional context tells the primary the spark's number and focus and to acknowledge it in one line and carry on (`SparkSentences`).
A notification, EDI's own operational text and a message with nothing after the opening are no spark.
`edi spark list` lists the open sparks, oldest first, with number, time, author, focus and text, and `--json` gives the whole record for the gardening board to read.
The gardening sorts each open spark once: `edi spark backlog <n> <key>` when it became a backlog item, `edi spark fold <n> <key>` when an existing item already covers it, and `edi spark drop <n> --reason <why>` when it goes no further (`SortSpark`); a sorted spark leaves the list and keeps its outcome.
The `edi-upkeep` skill sorts the open sparks in the weekly round with the captain, and the `edi-backlog-item` skill sends a half-formed idea to `edi spark` instead of the backlog.

## Why

One command or one message prefix catches an idea in a second, and the context that makes it worth reading a week later is gathered by the core, not typed.
The task the primary or the captain acted on last is what the session is about, while a worker's events follow its own task and say nothing about the captain's attention.
Letting the captain's prompt through, with a one-line acknowledgement, keeps their message in the session and the turn going.
Sorting with an item key ties every spark that reached the backlog to its item, so the gardening can see where its ideas went.

## Alternatives rejected

- Ideas straight into the backlog through `edi-backlog-item` - half-formed items reach the cloud session's queue, which is what the gardening is for.
- Blocking the captain's spark message at the hook so the primary never sees it - Claude Code then erases the prompt from the session, so the primary could never be asked about the idea in that session, and the captain sees only the hook's reason.
- The focus from the session's working directory - the primary always runs in the same directory, so it names no task or project.
- The focus from the latest event of any actor - a worker's status on its own task would decide what the captain's idea is about.
- Calling the second outcome "merged" as the item says - EDI names no merge operation anywhere (I-03), and a merged spark reads like a merged pull request; "folded into" says the same without the ambiguity.

## Consequences

- Good: an idea costs the captain one prefix and the primary one command, and the gardening sees every idea with its time, author, session and focus.
- Good: the backlog receives only ideas the captain sorted in, each traceable to its spark.
- Bad: the focus is a guess from the last act, so a spark about something else takes a wrong focus unless `--task` or `--project` names it; a message spark cannot name its focus.
- Bad: until the gardening board of EDI-165 exists, the weekly round sorts the sparks in the chat, through `edi-upkeep`.

## When to revisit

When the gardening board lands and reads the sparks itself, when the console gains a way to sort them, or when the captain wants a message spark to name its own focus.

## How we got here

- 09/11/2026: sparks caught by `edi spark` and by the captain's `Spark:` message, listed and sorted by backlogging, folding into an existing item or dropping with a reason (EDI-166).

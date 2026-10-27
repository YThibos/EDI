# edi up opens the primary waiting for the captain, and the captain's first prompt clears the sitrep page's session-start mark

**Kind:** Design

## Context

`edi up` opens the primary in a new tmux session running `claude --settings ... --append-system-prompt-file ...` ([core-lifecycle](core-lifecycle.md), [primary-startup-context](primary-startup-context.md)).
The captain asked for the session to open with no prompt submitted, so that it waits for the captain to type.
A new primary session already opens the sitrep page at its start ([console](console.md)), and a `/sitrep board` runs `edi sitrep --board`, which asks the core to open the console through `POST /api/console/open`.
With no opening prompt, the captain's first prompt may come long after the start, when the captain has closed that page.
A call made inside a Claude Code session names that session to the core, and the store keeps each primary session's state ([primary-role](primary-role.md)).

## Decision

`PrimaryLaunch.claude()` ends the primary's command line with the system prompt file and passes no prompt, so the session waits for the captain to type; the captain's first prompt starts the opening turn.
The session name `edi-<yymmdd-HHmm>` changes every minute, so tmux refuses only a second `edi up` in the same minute; a later `edi up` opens another, new primary session.
When `OpenTheSitrepAtSessionStart` opens the page, it records the time on the session as `primary_session.sitrep_opened_at` (`V13__the_sitrep_opened_at_session_start.sql`, `PrimarySessionStore`).
`OpenTheConsoleOnRequest` answers `POST /api/console/open` from a session that carries that mark with `ConsoleOpening.openedAtSessionStart`, which `ConsoleOpeningText` turns into the page's address while nothing opens, and otherwise opens the console through `OpenConsole` as before.
Every prompt clears the mark, the captain's first included (`PrimarySession.promptSubmitted`), and so do the end of a turn and a resume (`PrimarySession.turnEnded`, `PrimarySession.restarted`), so a `/sitrep board` or `edi console` from the session always opens the page, and so does a request from a terminal, which names no session.

## Why

The captain decides what the first turn asks for, and a session that waits spends nothing until the captain is there.
A typed request for the board is the captain asking to see the page, and the core cannot tell whether the page the start opened is still open, so clearing the mark at the first prompt keeps every explicit request working after the captain has closed the page.

## Alternatives rejected

- Submitting the opening prompt "Hello EDI, SITREP board, please." after `--` on the primary's command line - the captain withdrew it, wanting the session to wait for a prompt of the captain's own.
- Dropping the opening at session start - a primary started without `edi up` would lose the page.
- Telling the `sitrep` skill to skip `--board` when the digest says the page is open - nothing guarantees the model follows it, and the rule belongs to the core.
- Refusing every second opening for the whole session - a page the captain closed could not be brought back with `/sitrep board`.
- Keeping the mark through the captain's first prompt - with no opening prompt the first prompt may come long after the start, when the captain has closed the page, and nothing would open.
- Keeping the mark in the core's memory - a core restarted during the opening turn would forget it, while the store keeps all of a session's state.

## Consequences

- Good: every new primary waits for the captain's first prompt, and a typed `/sitrep board` always opens the page.
- Bad: EDI does not speak first; the captain types before the first sitrep arrives.
- Bad: a second `edi up` a minute or more after the first opens a second primary beside the running one.
- Bad: a `/sitrep board` typed right after a fresh `edi up` opens a second sitrep page beside the one the start opened, which the captain accepted.
- Bad: one more column on `primary_session` and one more branch in answering a console opening.

## When to revisit

When the captain wants EDI to speak first, wants `edi up` to switch to a primary that is already running, or the session start stops opening the page.

## How we got here

- 23/10/2026: at the captain's request `edi up` opened the primary with the opening prompt, and the opening turn stopped opening a second sitrep page (EDI-43).
- 27/10/2026: at the captain's request `edi up` opens the primary with no prompt submitted, so the session waits for the captain to type, and the captain's first typed prompt clears the session-start mark (EDI-101).

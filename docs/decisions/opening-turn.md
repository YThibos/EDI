# edi up opens the primary with the captain's opening prompt, and its opening turn names the sitrep page the session's start opened

**Kind:** Design

## Context

`edi up` opened the primary in a new tmux session running `claude --settings ... --append-system-prompt-file ...` ([core-lifecycle](core-lifecycle.md), [primary-startup-context](primary-startup-context.md)), and the session then waited for the captain to type, so EDI never spoke first.
The captain asked for the primary to open with the prompt "Hello EDI, SITREP board, please." already submitted, so that the first turn is EDI's greeting and the board sitrep, and asked for no output style and no change to the global Claude Code memory loading.
On Claude Code 2.1.308 a prompt after `--` in an interactive session is submitted at once: `SessionStart` fires with `source: "startup"` first, then `UserPromptSubmit` with the prompt, and that hook's `additionalContext` reaches the model ([docs/verification/claude-code.md](../verification/claude-code.md)).
A new primary session already opens the sitrep page at its start ([console](console.md)), and the opening turn's `edi sitrep --board` asks the core to open the console through `POST /api/console/open`, so a fresh `edi up` would open the page twice; the captain asked for exactly one.
A call made inside a Claude Code session names that session to the core, and the store keeps each primary session's state ([primary-role](primary-role.md)).

## Decision

`PrimaryLaunch.claude()` ends the primary's command line with `--` and the opening prompt "Hello EDI, SITREP board, please.", exactly as the captain wrote it.
The prompt travels only in the command that `tmux new-session` starts (`PrimaryWindow`), so a running primary is never prompted again: switching to its session carries no prompt.
The session name `edi-<yymmdd-HHmm>` changes every minute, so tmux refuses only a second `edi up` in the same minute; a later `edi up` opens another, new primary session, which is greeted like any other.
The prompt reaches the core like any prompt the captain types: it claims the primary role, and when the captain was away the return brief comes with it ([presence](presence.md)).
When `OpenTheSitrepAtSessionStart` opens the page, it records the time on the session as `primary_session.sitrep_opened_at` (`V13__the_sitrep_opened_at_session_start.sql`, `PrimarySessionStore`).
`OpenTheConsoleOnRequest` answers `POST /api/console/open` from a session that carries that mark with `ConsoleOpening.openedAtSessionStart`, which `ConsoleOpeningText` turns into the page's address while nothing opens, and otherwise opens the console through `OpenConsole` as before.
The end of a turn clears the mark (`PrimarySession.turnEnded`), and so do the next prompt and a resume (`PrimarySession.promptSubmitted`, `PrimarySession.restarted`), so it lasts only for the session's opening turn even when the captain interrupts that turn: a later `/sitrep board` or `edi console` opens the page again, and so does a request from a terminal, which names no session.
A start whose browser could not open the page records nothing, so the opening turn's request opens it then.

## Why

The captain's own words make the first turn without any new mechanism, and a positional prompt is the one way Claude Code takes a first turn at launch.
Passing it after `--` matches the worker launch, so a prompt can never be read as a flag ([worker-sessions](worker-sessions.md)).
The core knows which session opened the page and when that session's turn ends, so the one-page rule holds whatever the primary types, with no instruction to the model to remember, and limiting it to the opening turn keeps a later explicit request working after the captain has closed the page.

## Alternatives rejected

- Typing the prompt into the pane once the pane probe finds it idle ([steering](steering.md)) - it would wait for the start-up screens to settle, and only the core's adapters drive a pane, while a positional prompt needs nothing beyond the command line.
- Opening the sitrep from the session-start digest alone - the digest is context, not a turn, so EDI would still not speak first.
- An output style or a `CLAUDE.md` in the EDI home - the captain declined both.
- Dropping the opening at session start - a primary started without `edi up` would lose the page.
- Telling the `sitrep` skill to skip `--board` when the digest says the page is open - nothing guarantees the model follows it, and the rule belongs to the core.
- Refusing every second opening for the whole session - a page the captain closed could not be brought back with `/sitrep board`.
- Keeping the mark in the core's memory - a core restarted during the opening turn would forget it, while the store keeps all of a session's state.

## Consequences

- Good: every new primary opens with EDI greeting the captain and showing the board sitrep without a key pressed, and leaves one sitrep page open.
- Good: a resumed, cleared or compacted primary is not greeted again, because none of them starts through `edi up`.
- Bad: a second `edi up` a minute or more after the first opens a second primary beside the running one, and that one is greeted too.
- Bad: one more column on `primary_session` and one more branch in answering a console opening.

## When to revisit

When the captain changes the opening words, wants `edi up` to switch to a primary that is already running, the session start stops opening the page, or Claude Code stops submitting a positional prompt in an interactive session.

## How we got here

- 23/10/2026: at the captain's request `edi up` opened the primary with the opening prompt, and the opening turn stopped opening a second sitrep page (EDI-43).

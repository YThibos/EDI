# ADR-171: The sitrep page a new session's start opened serves its opening turn

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A new primary session opens the console's sitrep page in the captain's browser at its start (ADR-151), and `edi up` now opens the primary with the prompt "Hello EDI, SITREP board, please." (ADR-170).
That opening turn runs `edi sitrep --board`, which asks the core to open the console through `POST /api/console/open`, so a fresh `edi up` would open the page twice.
The captain asked that a fresh `edi up` leave exactly one sitrep page open, with the opening turn printing the report and the page's address without opening the browser again.
A call made inside a Claude Code session names that session to the core (ADR-067), and the core keeps each primary session's state in the store (ADR-078).

**Decision:** When `OpenTheSitrepAtSessionStart` opens the page, it records the time on the session as `primary_session.sitrep_opened_at`.
`OpenTheConsoleOnRequest` answers `POST /api/console/open` from a session that carries that mark with `ConsoleOpening.openedAtSessionStart`, which names the page's address and opens nothing, and otherwise opens the console as before.
The end of a turn clears the mark (`PrimarySession.turnEnded`), and so do the next prompt and a resume (`PrimarySession.promptSubmitted`, `PrimarySession.restarted`), so it only lasts for the session's opening turn even when the captain interrupts that turn: a later `/sitrep board` or `edi console` opens the page again, and so does a request from a terminal, which names no session.
A start whose browser could not open the page records nothing, so the opening turn's request opens it then.

**Motivation:** The core knows which session opened the page and when that session's turn ends, so the rule holds whatever the primary types, with no instruction to the model to remember.
Limiting it to the opening turn keeps a later explicit request working after the captain has closed the page.

**Alternatives considered:**
- Dropping the opening at session start - a primary started without `edi up` would lose the page ADR-151 gives it.
- Telling the `sitrep` skill to skip `--board` when the digest says the page is open - nothing guarantees the model follows it, and the rule belongs to the core.
- Refusing every second opening for the whole session - a page the captain closed could not be brought back with `/sitrep board`.
- Keeping the mark in the core's memory - a core restarted during the opening turn would forget it, while the store keeps all of a session's state.

**Consequences:**
- Good: a fresh `edi up` leaves one sitrep page open, and the opening turn still prints the report and the page's address.
- Good: the same holds for `edi console` run in the opening turn.
- Bad: one more column on `primary_session` and one more branch in answering a console opening.

**Revisit when:** The session start stops opening the page, or the captain wants a second request in the opening turn to open a fresh page.

**Sources:** The captain's acceptance criterion of 23/10/2026 (EDI-43); ADR-067, ADR-078, ADR-151, ADR-170; `application.primary.OpenTheSitrepAtSessionStart`, `OpenTheConsoleOnRequest`, `domain.primary.PrimarySession`, `adapter.store.PrimarySessionStore`, `V10__the_sitrep_opened_at_session_start.sql`, `application.access.OpenConsole`, `web.console.ConsoleController`, `ConsoleOpeningText`, `web.hook.HookController`.

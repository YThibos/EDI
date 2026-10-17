# ADR-114: The store keeps console sign-ins and sessions, boards and board answers

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 8.2 lists `board` and `board_answer` among the store's tables; ADR-078 left the console's own sessions to this slice; current state lives in tables and history in the event log (ADR-037).

**Decision:** `V6__console_and_boards.sql` adds five tables:
- `console_sign_in`, the digest of each sign-in link with when it was issued, when it expires and when it was used;
- `console_session`, the digest of each console session cookie with when it opened and when it expires;
- `board`, each armed board with its task, its file, its count of answered rounds, and when it was armed, ended and retired, with at most one listening board per file;
- `board_decision`, the decisions a board answers, in the order they were bound;
- `board_answer`, each answer a round gave a bound decision, with its verbatim words and option, when it arrived, and when it was settled or why it was refused.
`ConsoleAccessStore` and `BoardStore` are the only code that reads and writes them.

**Motivation:** Each table holds what a use case needs to carry on after a restart: an open console stays signed in, a listening board is listened to again, and an unsettled board answer is settled.

**Alternatives considered:**
- Keeping the bound decisions as a list in one column - a decision could not be referenced by key.
- Recording board answers only as events - an unsettled answer would have to be found by replaying the log, which ADR-037 rules out.

**Consequences:**
- Good: no secret reaches the store, only digests.
- Bad: expired sign-ins and sessions stay until the next link is issued.

**Revisit when:** The console keeps more per-session state than its expiry.

**Sources:** EDI rewrite specification, section 8.2; ADR-037, ADR-078, ADR-105, ADR-111, ADR-113; `V6__console_and_boards.sql`, `adapter.store.ConsoleAccessStore`, `BoardStore`.

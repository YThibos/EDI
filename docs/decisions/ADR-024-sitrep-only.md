# ADR-024: `/sitrep` is the only overview command

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The old harness had two overview commands; the captain: "We will only use sitrep, no longer bearings."
With the sitrep board moving into the EDI console (ADR-018), the word he had chosen for the board argument named a tool no longer involved.

**Decision:** `/sitrep` prints the boxed captain report; `/sitrep board` prints the same report and then opens the console's live sitrep page in the captain's browser itself.
Only the literal argument selects the board; any other argument is named as unknown and plain `/sitrep` runs.
`/sitrep` is read-only.

**Motivation:** The word says what it does, it spares the captain hunting for a link in the terminal, and nothing misleading lingers below version 1.0.0.

**Alternatives considered:**
- Keep the old argument word - names a tool the board no longer uses.
- No argument, a link on every report - the link needs a modified click in the captain's terminal setup.

**Consequences:**
- Good: one command for every overview.
- Bad: none identified when the decision was made.

**Revisit when:** A second overview need appears.

**Sources:** EDI rewrite specification, sections 15.1 to 15.3 (decisions D-overview, E15, E16).

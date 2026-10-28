---
name: sitrep
description: >-
  Owns the situation report, as boxed text or as the console's sitrep page.
  Load it when the captain says /sitrep or /sitrep board, or asks for an overview or a status report.
user-invocable: true
---

# sitrep

The situation report is read-only: it never dispatches, steers, answers, cleans up or edits the backlog.
It is the overview: every open decision in full, what runs, what is parked and, when nothing runs, the backlog.
The turn report, `edi report --turn -`, is the chat's attention list instead: what needs the captain now, and this turn's done and next.

## Plain `/sitrep`

1. Run `edi sitrep`, also when this turn changed state; the overview the captain asked for replaces the turn report as the turn's single report.
2. Answer with one plain line to the captain, then the output verbatim in one fenced block.

## `/sitrep board`

1. Run `edi sitrep --board`: it prints the same report, opens the console's sitrep page in the captain's browser and prints its address.
2. Answer with one plain line, the report verbatim in one fenced block, then the page's address on its own line.
3. If the page could not be opened, say so and give the sign-in address it printed.

## The argument

- Only the literal word `board` selects the page.
- Natural language never selects it.
- Any other argument is named as unknown in one line, and plain `/sitrep` runs.

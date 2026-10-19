---
name: edi-board
description: >-
  Owns research boards: arming a Lavish board through EDI and routing its answers to their decisions and to the scout.
  Load it before arming a research board or when handling a board's answer.
---

# edi-board

Research and design scouts talk with the captain on a Lavish board in the EDI skin; the sitrep page lives in the EDI console instead.

## Arming

1. The scout writes its board in its own task directory, `$EDI_HOME/data/tasks/<task>/`, and opens it with `lavish-axi`.
2. The scout runs `edi board arm <board.html> --task <task> --bind D58,D59`, binding every open decision the board puts before the captain.
3. Raise the decisions first with `edi decision raise <task> --card -`, so the board can bind their numbers.
4. EDI's core listens to the board and restarts its listener if it stops; nobody else polls it.

## Answers

- Each round of feedback reaches the scout's inbox as one message.
- A prompt that names a bound decision, by its D number or in the board's decision field, answers that decision as the captain's own word.
- A board answer arrives as a notification through `edi wait`; carry it to the work, and never answer the same decision again in chat.
- An answer EDI could not apply, such as one to a decision already answered, comes back as a refusal to put before the captain once more.
- An empty close of the board is not news; a round with content is.

## When the scout's worker is dead

The answers are not lost: they stay in the store and appear on the outage list's entry for the scout.
Resume or relaunch the scout before acting on them.

## Ending

The board stops listening when the captain ends its session or when its task is cleaned up.

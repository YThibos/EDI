---
name: edi-board
description: >-
  Owns research boards: arming a Lavish board through EDI and routing its answers to their decisions and to the scout.
  Load it before arming a research board or when handling a board's answer.
---

# edi-board

Research and design scouts talk with the captain on a Lavish board in the EDI skin; the sitrep page lives in the EDI console instead.

## Arming

1. The scout writes its board in its own task directory, `$EDI_HOME/data/tasks/<task>/`, and opens it with `lavish-axi`, which prints the board's link.
2. A decision the board puts before the captain is a card, raised and bound when the board is armed, never after; a scout cannot run `edi decision raise`.
   The scout writes the card as `edi decision raise` takes it, adding `"question": "<key>"`, and gives the board's form for it `data-lavish-question="<key>"` and the answer data `{question: "<key>", answer: "<option letter>"}`.
   An option that ships, drops or discards carries `"authorises": "promote"`, `"drop"` or `"discard-work"`; no other option does.
3. The scout runs `edi board arm <board.html> --task <task> --link <url> --questions <n> --card <card.json>`, naming the link, how many other questions the round puts before the captain (`0` when it asks only the card), and the card.
   EDI raises the card on the scout's task, binds it to the board under its question, and prints its D number.
4. `--bind D58,D59` binds decisions the primary already raised, on the scout's task or another, that the board also answers; the board names them by number.
5. EDI's core listens to the board and restarts its listener if it stops; nobody else polls it.
6. The board waits in the captain's decision zone with its link and question count until a round of his answers arrives, and its card waits there as its own decision.
7. After posting a new round of questions on the board, arm it again with the new count, so the board waits on the captain once more; leave `--card` out once the card is answered.
8. When the scout has stopped, or its board was armed before boards counted their questions, the primary puts the round before the captain with `edi board post <board.html> --link <url> --questions <n>`.

## Answers

- Each round of feedback reaches the scout's inbox as one message.
- A prompt that names a bound decision, by its D number, in the board's `decision` field or by its card's `question`, answers that decision as the captain's own word, verbatim.
- That answer authorises exactly the act its chosen option names, as an answer in the console does; nobody records it again with `edi captain said`.
- A board answer arrives as a notification through `edi wait`; carry it to the work, and never answer the same decision again in chat.
- An answer EDI could not apply, such as one to a decision already answered, comes back as a refusal to put before the captain once more.
- An empty close of the board is not news; a round with content is.

## When the scout's worker is dead

The answers are not lost: they stay in the store and appear on the outage list's entry for the scout.
Resume or relaunch the scout before acting on them.

## Ending

The board stops listening when the captain ends its session or when its task is cleaned up.

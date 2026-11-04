---
name: edi-scout
description: >-
  Owns scouts: research and design tasks, their board, their completion and their promotion to a ship.
  Load it before dispatching a scout or when acting on a scout's report.
---

# edi-scout

A scout delivers knowledge in a report, never a pull request.
Its report, diagnosis or recommendation authorises nothing (I-05).

## Dispatching

1. Add it with `edi task add --kind scout --project <project> --title "<title>" --body -`, with the captain's words verbatim in the body.
2. Dispatch it with `edi dispatch <task> --category research --spec -`; research runs on Opus 5.5 at high.
3. A research or design scout hosts a Lavish board as its main conversation with the captain, in the EDI skin, until the design settles (load `edi-board`).
   Any decision the board asks goes in as a card on `edi board arm --card`, raised and bound before the captain sees the board.
4. A scout that works in text only needs the captain's word for that.
5. A scout on a request that will take more than one merge request puts its plan and its open questions on a research board: the slices, each with its own acceptance criteria, and a card whose promote option ships the plan.
   The captain's go on that board is what authorises promotion; promote one ship per slice, each with its slice's acceptance criteria in the spec.
   Measured: a board round carries a median of six answers where a chat message carries 1.1, and its slices then ran with 0 to 2 steers each.

## Completion

- The scout writes `$EDI_HOME/data/tasks/<task>/report.md`: self-contained, one sentence per line, United Kingdom English, evidence with commands and file references.
- Its done counts once the report exists and every decision it exposed is raised or answered.
- Relay its findings as findings, in outcomes, never as a bare completion notice.
- Clean it up with `edi cleanup <task>` once its done was accepted.

## Promotion to a ship

1. Only the captain's go starts implementation.
2. When the captain's message says go, record it first by its number: `edi captain said --task <scout> --message <n>`, as `edi captain messages` lists it; a dialog option they chose that opens with `promote` and names the scout and no other task needs no recording.
3. A decision on the scout counts as their go when the captain chooses, since this session opened, an option that names `"authorises": "promote"`, in the chat, in the console or on the scout's board.
   The scout's board card carries it (load `edi-board`); otherwise raise such a card with `edi decision raise <scout> --card -`, putting `"authorises": "promote"` on the option that ships the plan and on no other.
   Never ask the captain to retype a choice made on the console or the board.
4. Run `edi task promote <scout> [--title "<ship title>"]`: you queue a ship with the scout's projects, key, base and notes, and the captain's words.
5. Dispatch the ship as usual (load `edi-ship`), with the report's plan in the spec.

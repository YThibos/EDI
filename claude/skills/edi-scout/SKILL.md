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
4. A scout that works in text only needs the captain's word for that.

## Completion

- The scout writes `$EDI_HOME/data/tasks/<task>/report.md`: self-contained, one sentence per line, United Kingdom English, evidence with commands and file references.
- Its done counts once the report exists and every decision it exposed is raised or answered.
- Relay its findings as findings, in outcomes, never as a bare completion notice.
- Clean it up with `edi cleanup <task>` once its done was accepted.

## Promotion to a ship

1. Only the captain's go starts implementation.
2. When his message says go, record his words first: `edi captain said --task <scout> --text "<his words>"`.
3. A decision on the scout that he answers in this session also counts as his word.
4. Run `edi task promote <scout> [--title "<ship title>"]`: EDI queues a ship with the scout's projects, key, base and notes, and the captain's words.
5. Dispatch the ship as usual (load `edi-ship`), with the report's plan in the spec.

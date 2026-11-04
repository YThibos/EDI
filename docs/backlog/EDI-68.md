# EDI-68 edi park and edi resume

| Field | Value |
|---|---|
| Key | EDI-68 |
| Kind | ship, a change to the repository, on branch `feature/EDI-68` |
| Wave and order | wave 5, order 29 |
| Source | EDI-52 plan, slice 17 |
| Size | M |
| Blocked by | [EDI-87](EDI-87.md), [EDI-89](EDI-89.md) |
| Status | open |
| Issue | [#160](https://github.com/YThibos/EDI/issues/160) |
| Cloud session | ok |

## Goal

`edi park` stops every running worker cleanly and records the set; `edi resume` relaunches exactly that set, and the sitrep shows the parked block until it is resumed.

## Context

Halting workers for the night and restarting them in the morning was a daily by-hand ritual.
Seven workers stopped one evening were resumed over four minutes the next morning, and a worker that died on a Friday afternoon was noticed on the Monday.
Resumed incarnations spent up to 63.6M weighted tokens in the first week, 13% of it, an upper bound that includes the work they went on to do.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), finding F5 and slice 17, and [the EDI-75 measurements](research/EDI-75-measurements.md), section 6.

## Acceptance criteria

- After `edi park`, `edi worker list` shows each worker stopped by EDI and the sitrep shows the parked block.
- `edi resume` relaunches each of them with its recorded session, clears the block, and leaves a worker that was already stopped before the park untouched.
- The night's park is one of a task's holds ([task-lifecycle](../decisions/task-lifecycle.md)).
- `docs/decisions/recovery.md` gains a history line.

## Requirements from decisions and answers

- F5 (captain, 26 October 2026): `edi park` and `edi resume` replace the by-hand ritual of halting every worker at night and restarting them in the morning.
- Stopping uses the existing stop; never signal a process EDI did not record (I-30).
- A relaunch keeps its own copy and session (I-25) and its routing (I-16).
- The captain's word is needed for destructive acts; parking is not destructive, and it discards nothing.

## Research and measurements

- Nine of ten resumes in the first week followed a night or a restart; the return of this slice can be measured against 63.6M.

## Read first

- [recovery](../decisions/recovery.md), [worker-sessions](../decisions/worker-sessions.md), [supervision](../decisions/supervision.md), [task-lifecycle](../decisions/task-lifecycle.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/worker/StopWorker.java`, `RelaunchWorker.java`, `application/supervision/RecoverFromOutage.java`, `OutageList.java`, `domain/recovery/`.

## How to verify

- `./mvnw verify`, then the native quick build.
- Tests with fixture workers: park three, resume three; a worker stopped before the park stays stopped.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.

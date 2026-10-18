# ADR-118: More than one dead worker go on a numbered outage hold list, answered one number at a time, which can also drop a task on the captain's word

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-24: when more than one worker is dead, EDI relaunches none; it records a hold per worker with its recorded model and effort, the resume recipe and the outcome so far, presents one numbered list, and acts only on the numbers the captain names, one at a time; AT-24: three dead workers give three holds in a stable numbered list, and "2 resume" relaunches only task 2.
Section 11.9: each entry shows the project, the outcome so far, what it waits on, the recorded model and effort, those its category gives now, and whether its branch has unpushed work; the captain replies with numbers and actions such as "2 resume, 4 fresh at medium, 5 drop"; EDI re-checks the remaining budget between relaunches.
Dropping a task needs the captain's word in the current exchange (ADR-115, ADR-117).

**Decision:** When the supervision round finds two or more dead workers, `OutageHoldList` lists every one not yet listed in `outage_entry`, numbered after the highest open number, notices its death, holds its task for the captain's approval until the captain's word unless something else already holds it, and records `LostInOutage`, which wakes the primary; one dead worker alone records `WorkerDied` instead.
`edi recover` lists every dead worker, however many, and prints the list; `edi recover <n> resume|fresh [--model M] [--effort E]` answers one entry.
`OutageListQuery` fills each entry from the store and verifies the copy with git: commits on no remote and uncommitted changes, or "not verified" when git cannot read it.
`RecoverFromOutage` refuses while a live worker or the primary is parked on a usage limit that still holds, then relaunches with the entry and its hold in place; the transaction that records the relaunched worker releases the hold and closes the entry, and a relaunch that fails leaves both and records `RecoveryFailed`.
A model or effort the captain names becomes the dispatch's captain override, with the captain's answer as its reason.
`edi recover <n> drop [--discard-work]` goes through `DropFromOutage`, which drops the entry's task with `DropTask` on the captain's word, discarding its unlanded work only with `--discard-work`, and then closes the entry.

**Motivation:** An outage is answered by the captain entry by entry, with what the captain needs to choose on the list itself, so no restart storm can follow a lost machine, and a task the captain gives up on leaves the list in the same answer.

**Alternatives considered:**
- A global number sequence - numbers in the hundreds for a list of three.
- Dropping through `edi task drop` alone - the entry would stay open for a task that no longer exists.

**Consequences:**
- Good: the numbers are stable while an entry is open, and a second outage continues after them.
- Bad: a drop whose entry cannot be closed after the task was dropped leaves an entry for a dropped task until the next answer.

**Revisit when:** The captain answers the list in the console.

**Sources:** EDI rewrite specification, sections 11.9 and I-24 (AT-24); ADR-074, ADR-115, ADR-117; `domain.recovery.OutageEntry`, `RecoveryAction`, `application.supervision.OutageHoldList`, `OutageListQuery`, `OutageList`, `RecoverFromOutage`, `DropFromOutage`, `JudgeWorkers`, `application.delivery.DropTask`, `web.recovery.RecoveryController`, `OutageListText`, `edi-cli` `RecoverCommand`.

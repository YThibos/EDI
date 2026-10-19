# ADR-074: More than one dead worker go on a numbered outage hold list, answered one number at a time

**Date:** 16/10/2026

**Status:** Superseded by ADR-118

**Kind:** Design

**Context and constraints:** I-24: when more than one worker is dead, EDI relaunches none; it records a hold per worker with its recorded model and effort, the resume recipe and the outcome so far, presents one numbered list, and acts only on the numbers the captain names, one at a time; AT-24: three dead workers give three holds in a stable numbered list, and "2 resume" relaunches only task 2.
Section 11.9: each entry shows the project, the outcome so far, what it waits on, the recorded model and effort, those its category gives now, and whether its branch has unpushed work; entries that crossed the outage are verified or marked unverified; EDI re-checks the remaining budget between relaunches.
Dropping a task needs the captain's word, which arrives with slice S8.

**Decision:** When the supervision round finds two or more dead workers, `OutageHoldList` lists every one not yet listed in `outage_entry`, numbered after the highest open number, notices its death, holds its task for the captain's approval until his word unless something else already holds it, and records `LostInOutage`, which wakes the primary; one dead worker alone records `WorkerDied` instead.
`edi recover` lists every dead worker, however many, and prints the list; `edi recover <n> resume|fresh [--model M] [--effort E]` answers one entry.
`OutageListQuery` fills each entry from the store and verifies the copy with git: commits on no remote and uncommitted changes, or "not verified" when git cannot read it.
`RecoverFromOutage` refuses while a live worker or the primary is parked on a usage limit that still holds, then relaunches with the entry and its hold in place, so a supervision round meanwhile cannot list the task again; the transaction that records the relaunched worker releases the hold and closes the entry, and a relaunch that fails leaves both and records `RecoveryFailed`.
A model or effort the captain names becomes the dispatch's captain override, with his answer as its reason.

**Motivation:** An outage is answered by the captain entry by entry, with what he needs to choose on the list itself, so no restart storm can follow a lost machine.

**Alternatives considered:**
- A global number sequence - numbers in the hundreds for a list of three.
- Offering drop on the list now - dropping needs captain-word evidence, which slice S8 brings; until then the hold stays.

**Consequences:**
- Good: the numbers are stable while an entry is open, and a second outage continues after them.
- Bad: "verified" means checked against the clone's own remote-tracking refs; merge requests are checked from slice S5.

**Revisit when:** Slice S5 can check merge requests, or slice S8 adds drop through the captain's word.

**Sources:** EDI rewrite specification, sections 11.9 and I-24; `domain.recovery.OutageEntry`, `RecoveryAction`, `application.supervision.OutageHoldList`, `OutageListQuery`, `OutageList`, `RecoverFromOutage`, `JudgeWorkers`, `web.recovery.RecoveryController`, `OutageListText`, `edi-cli` `RecoverCommand`.

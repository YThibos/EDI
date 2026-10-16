# ADR-077: The supervision round also judges every worker, meets usage-limit resets and notices due holds

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-061 gave slice S3's time-based rules one supervision round every 30 seconds and named slice S4's health rules and wakes as the reason to revisit it.
Slice S4 adds rules that follow the clock and the world outside the store: worker health and outages (ADR-070, ADR-074), usage-limit resets (ADR-072) and holds whose date arrived, one of the wakes of section 12.3.
The round's rules depend on the worker rules, and the worker rules must not depend back on them.

**Decision:** This record supersedes ADR-061.
`SupervisionRound` moves to the package `application.supervision` and runs every 30 seconds (`edi.supervision.round`), each part guarded so one failing part does not stop the others, in this order: `NoticeOverdueSteers`, `NoticeMissedLaunches`, `JudgeWorkers`, `ResumeAfterUsageLimits`, `NoticeDueHolds`, `LaunchDeferredDispatches`, and the mechanical checks.
`JudgeWorkers` reads every serving worker's health once per round, notices what is due, puts two or more dead workers on the outage hold list or notices a single death, and counts the tokens of a dead recovery relaunch.
`NoticeDueHolds` notices once, by the hold's own start, a hold whose date has arrived in UTC.
Each rule is a use case that tests call directly with a controllable clock.

**Motivation:** One round keeps every time-based rule in one place, and a rule that fails waits for the next round without holding up the others.

**Alternatives considered:**
- A timer per rule - more threads to stop and no shared order.
- Judging only at session start - a worker dying during the day would wait for the next session.

**Consequences:**
- Good: a dead worker is noticed within 30 seconds of the round that sees it, and the round is the only place outages are listed on their own.
- Bad: every round asks tmux about every serving worker.

**Revisit when:** The round's tmux calls show up in the round's time, or slice S5's merge observation joins it.

**Sources:** EDI rewrite specification, sections 11.2, 11.9, 12.2, 12.3 and 12.5; ADR-061; `application.supervision.SupervisionRound`, `JudgeWorkers`, `ResumeAfterUsageLimits`, `NoticeDueHolds`, `OutageHoldList`.

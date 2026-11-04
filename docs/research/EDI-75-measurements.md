# EDI-75: EDI against the previous harness, measured

This is the first measured comparison of EDI against the harness before it, over the previous harness's last 25 days with token use and EDI's first four working days (21 to 26 October 2026).
It supplies the evidence for the three changes of EDI-75, EDI-96 and EDI-97 and the baseline for the review a week after the upgrade (EDI-76).
It was written on 26 October 2026 from data only the captain's machine holds, and it authorises nothing by itself; the figures are quoted as written, and work projects other than EDI are named generically.
Its Cucumber specification study, a separate pilot on a Java project outside EDI, is left out; the captain's answer to it is recorded at the end.
The scripts that produced every figure live on the captain's machine and are not in this repository.

## Summary

- Merged changes per working day rose from 2.5 to 10.0 (63 in 25 days against 40 in 4 days).
- Captain prompts per merged change fell from 5.8 to 3.8, while prompts per day rose from 14.6 to 38.0: the captain spends more time with EDI, and each minute delivers more.
- Weighted tokens per merged change fell from 15.5M to 12.2M, but tokens per day rose from 39.0M to 122.3M.
- The saving sits almost entirely in the primary: with the previous harness's supervision session it cost 3.35M per change, EDI's primary costs 1.07M (minus 68%), and its share of all spend fell from 21.7% to 8.8%.
- Worker cost per change did not change once the two xhigh outliers are removed (7.1M before, 7.3M now): the harness changed how work is steered, not what a change costs to build.
- The pipeline now costs more per change (1.78M to 2.57M, median 11.1 to 29.7 minutes per run) but fails far less (43% to 8% of runs), so a change needs 1.6 runs instead of 2.4.
- Board rounds bundle a median of 6 answers (mean 9.2); a chat message answers 1.1 decisions; yet 33 of EDI's 42 answered decisions still went through chat, 28 of them one at a time.
- The spec-driven flow works: a registered project's large integration feature went from request to a sliced plan in 7h01 and merged four merge requests between 15:07 and 18:49; EDI-52 went from request to 20 queued slices in 1h27.
- Three things cost more than they return: the turn-end boxed report (30.2% of the primary's output tokens), resuming workers left running overnight (63.6M weighted, 13% of the week, an upper bound), and single decisions answered in chat.
- The EDI week is four working days, 27 of its 40 merges are EDI's own code, and no post-merge defect data exists for either era; treat every ratio as a first reading, not a trend.

### Token weighting

- Raw token counts are dominated by cache reads, which cost a tenth of an input token, so every token total here is "weighted": input times 1, cache read times 0.1, cache write times 1.25, output times 5.
- Those are Anthropic's list-price ratios for every current model, so a weighted token is an input-token equivalent; no dollar figure is given because the account's plan does not bill per token.
- Raw counts were kept for anyone who prefers another weighting.

### What is not comparable

- Captain time: September's worklogs are reconstructed eight-hour days (8.0 to 9.25 hours a day), while the EDI week's worklogs are the agent-active minutes EDI logs per ticket (14.75 hours on 2026-10-22); they measure different things and are not used.
- The previous harness's per-task tokens: workers ran in pooled copies (shared copies named by project and slot) and only 25 of 154 worker transcripts name their task, so tokens per task of the previous harness or per phase (research against implementation) cannot be attributed (the measuring script).
- The previous harness's decision counts: decisions lived in chat and in boards whose answers were not stored, so only the decision references in the captain's own prompts can be counted.
- The previous harness request times before 2026-10-13: brief files were bulk-copied on 2026-07-29, so their file times do not show when a task was asked for.
- Quality after merge: neither era records reopened tickets or post-merge defects, so "rework" here means review rounds, fix rounds, redone tasks and resumed workers.

## Part 1: the measured comparison

### 1. Throughput: delivered changes

A delivered change is a merged merge or pull request, excluding Renovate branches, which run under the captain's account but are not agent work (the measuring script).

| Repository | FO-early (to 2026-09-13) | Previous harness window | EDI week |
|---|---|---|---|
| project A | 36 | 14 | 11 |
| EDI | - | 16 (the build, median 6 963 changed lines) | 27 (median 623 changed lines) |
| the previous harness's own repository | 12 | 19 | - |
| project B | 2 | 6 | 0 |
| project C | 1 | 4 | 2 |
| project D | 0 | 4 | 0 |
| **Total** | 51 | **63** | **40** |
| Per working day | - | **2.5** | **10.0** |

- 27 of the EDI week's 40 merges are EDI's own code, as 19 of the window's 63 were the previous harness's; both eras spent a large share of their output on the harness itself.
- Product merges (project A, project B, project C, project D): 28 in 25 days (1.1 a day) against 13 in 4 days (3.3 a day).
- Three more EDI-week tasks delivered to project E's local branch and are not counted; three EDI tasks merged through the combined pull request of EDI-47 and count once.

### 2. Captain interactions per delivered change

| Measure | Previous harness window | EDI week | Source |
|---|---|---|---|
| Prompts the captain typed in the primary | 366 | 152 | the measuring script, the measuring script |
| per working day | 14.6 | 38.0 | |
| per merged change | 5.8 | 3.8 | |
| Board rounds answered | not stored | 14 rounds, 129 answers | the measuring script |
| Decisions answered in the console | - | 6 | the measuring script |
| Decision references in typed prompts | 147 in 97 prompts | 24 in 22 prompts | the measuring script |
| Worker `needs-decision` reports per dispatch | 1.77 (85 for 48, ledger week 2026-10-13 to 20) | 1.35 (89 for 66) | the measuring script |
| Steers sent to workers | not recorded | 152 (3.8 per merged change) | the measuring script |

- A typed prompt is a user line whose origin is human, excluding machine-injected lines (task notifications, supervision wakes, the main-dialog mirror, continuation summaries, `!` command output); `/` commands count as typed.
- Counting the 14 board rounds and 6 console answers as interactions too, the EDI week had 172, or 4.3 per merged change.
- Review rounds by the captain on a merge request cannot be counted for either era: the project A merge requests carry no discussion notes at all (the note count is 0 on every one), because the captain reviews and merges without commenting.

### 3. Bundled questions and answer turnaround

| Channel (EDI week) | Decisions answered | Median minutes from raise to answer | p75 |
|---|---|---|---|
| Chat | 33 | 9.8 | 26.1 |
| Console | 6 | 4.0 | 6.1 |
| Board | 3 | 36.3 | 61.3 |

Source: the measuring script.

- Answers grouped by channel within three minutes form 33 answer rounds, and 28 of them answered a single decision.
- A board round carries a median of 6 answers and a mean of 9.2 (14 rounds: 1, 3, 3, 4, 5, 5, 5, 7, 8, 10, 10, 12, 19 and 37), counted from the `prompts` field of `BoardAnswered` events.
- A chat answer names 1.09 decisions (EDI) or 1.52 (the previous harness, where the captain more often answered several decisions in one message: 12 prompts named three or more).
- The board's longer turnaround is the captain reading a whole board; per answer it is the cheapest channel.
- The previous harness's boards in the window carried 37 questions on five boards; their rounds were not recorded.
- Five EDI decisions were raised twice (D4, D8, D17, D20 and D32), the re-raise defect EDI-40 fixed (the measuring script, the primary's learnings file).

### 4. Lead times

EDI week, 39 merged ships, medians (the measuring script):

| Step | Median |
|---|---|
| Request (task added) to dispatch | 0.01 h |
| Dispatch to draft merge request | 1.83 h |
| Draft to merge | 1.12 h |
| Request to merge | 10.73 h (overnight waits included) |

the previous harness ledger week, 2026-10-13 to 2026-10-20 (the measuring script): dispatch to ready pull request 4.03 h (13 tasks), ready to merged 0.16 h (7 tasks); 48 dispatches produced 13 ready pull requests and 7 recorded merges in the ledger.

Merge request open to merge, medians from the forges (the measuring script):

| Repository | Previous harness window | EDI week |
|---|---|---|
| project A | 2.0 h (14) | 0.9 h (11) |
| EDI | 2.5 h (16) | 1.5 h (27) |

Request to spec and spec to first merge are shown per feature in section 9.

### 5. Rework

| Measure | Previous harness window | EDI week | Source |
|---|---|---|---|
| Pipeline runs | 150 | 63 | the measuring script |
| failed | 64 (43%) | 5 (8%) | |
| runs per merged change | 2.4 | 1.6 | |
| review rounds per run (mean) | 1.47 | 2.02 | |
| auto-fix rounds per run (mean) | 0.99 | 1.90 | |
| review findings per run | 1.32 | 1.82 | `step_rounds.findings_json`, initial rounds |
| median minutes of pipeline steps per run | 11.1 | 29.7 | |
| Craftsmanship re-reviews run as separate tasks | 14 task directories | none (review runs inside the pipeline) | the measuring script |
| Workers relaunched (resume) | not recorded | 10; 9 of 39 merged ships needed more than one worker | the measuring script, the measuring script |
| Decisions raised twice | not recorded | 5 | the measuring script |
| Steers that did not arrive | not recorded | 16 | the measuring script |

- The previous harness's failed runs were mostly infrastructure: "daemon shutting down" 18, a push refused for want of a durably recorded review 13, a push to upstream failing 9 (see Queries used inline).
- Two role renames of other projects show the previous harness's craftsmanship loop at its worst: one took four separate craft re-review tasks and two others took three each, between 2026-09-17 and 2026-09-24.
- EDI's pipeline review rounds per task: 116 rounds and 92 findings over 41 tasks, 2.83 rounds per task (`task_review`, the measuring script).
- The ten EDI resumes came in three batches: four at 2026-10-22 06:54 to 06:58, four at 2026-10-23 06:01 and one at 2026-10-26 07:16 after a machine restart, plus one mid-day (see Queries used inline): workers left running overnight are resumed in the morning.

### 6. Tokens

Weighted tokens, millions (the measuring script):

| Role | Previous harness window | per merged change | EDI week | per merged change |
|---|---|---|---|---|
| Primary | 204.2 | 3.24 | 42.9 | 1.07 |
| Supervision session | 7.1 | 0.11 | - | - |
| Workers | 632.3 | 10.04 | 343.1 | 8.58 |
| Pipeline | 112.0 | 1.78 | 102.7 | 2.57 |
| Other (ad hoc sessions) | 20.3 | 0.32 | 0.4 | 0.01 |
| **Total** | **976.0** | **15.5** | **489.1** | **12.2** |
| Per working day | 39.0 | | 122.3 | |

- Two xhigh outliers distort the worker line: the EDI build in the window (298.3M, all of the previous harness's xhigh) and one large integration feature in the EDI week (scout 25.3M plus slices 56.7M, all of EDI's xhigh).
  Without them, worker tokens per merged change are 7.1M (334.0M over 47) against 7.3M (261.1M over 36).
- Per phase, EDI's own task costs (`task_cost`, the measuring script): a scout costs 3.0M (5 scouts), a ship 9.2M, of which 5.8M worker and 3.4M pipeline (44 ships).
  the previous harness's per-phase split cannot be attributed (see the limits).
- Model mix by weighted tokens: the previous harness Opus 5.5 70.1%, Sonnet 5.5 12.5%, Opus 5 9.1%, Sonnet 5 7.8%; EDI week Opus 5.5 83.6%, Sonnet 5.5 16.4%.
- Recovery: the ten resumed incarnations spent 63.6M weighted, 13.0% of the week (`recovery_cost`, the measuring script).
  That table counts everything a resumed incarnation spent, including the work it went on to do, so 63.6M is an upper bound on what the overnight stops cost.

### 7. The primary's own overhead

| Measure | Previous harness window | EDI week | Source |
|---|---|---|---|
| Primary turns started by a machine (wakes, notifications) | 1 624 (65 a day) | 148 (37 a day) | the measuring script |
| their share of the primary's weighted tokens | 64.6% (131.9M) | 42.1% (18.4M) | |
| Weighted tokens per captain turn | 0.20M | 0.17M | |
| Report commands run (`captain-report`, `edi report`, `edi sitrep`) | 303 (12 a day) | 253 (63 a day) | the measuring script |
| Median report size | 1 463 characters | 1 822 characters | |
| Replies that render a boxed report | 389 (16 a day) | 208 (52 a day) | the measuring script |
| their share of the primary's output tokens | 12.5% | 30.2% | |

- The supervision session the previous harness ran beside its primary cost 7.1M, 3.4% of the primary line; EDI has none.
- The previous harness's dominant overhead was being woken: two thirds of its primary spend went to turns no human started.
- EDI's dominant overhead is the turn-end report: it is run five times as often per day as the previous harness's, and replies carrying it hold almost a third of the primary's output tokens (an upper bound, since those replies carry other text too).
- EDI-74's point 5 owns the report's future; these figures are its baseline, and this report proposes no design for it.

### 8. Effort: xhigh against high

Share of weighted worker tokens by effort (the measuring script):

| Effort | the previous harness window workers | EDI week workers |
|---|---|---|
| xhigh | 47.2% (all the EDI build) | 23.5% (all of that feature) |
| high | 38.4% | 76.0% |
| medium | 10.8% | 0.4% |
| low | 3.6% | 0.1% |

- Pipelines ran at medium 78.4% and low 21.6% under the previous harness, and at medium 53.7% and high 46.3% in the EDI week.
- The primary ran at medium and low under the previous harness (51.9% and 48.1%) and at medium only under EDI.
- EDI's workers: 32 implementation workers on Opus 5.5 high, 11 on Sonnet 5.5 high, 4 on Opus 5.5 xhigh, 8 research on Opus 5.5 high, 1 on xhigh (the measuring script).
- No measured return for xhigh exists yet: the two merged xhigh slices of that feature took 1 and 3 review rounds (0 and 1 findings), the Sonnet high project A ships 2 to 5 rounds (1 to 7 findings), and the samples are too small and the tasks too different to compare.

### 10. What the data says

#### What works

- The primary got cheap: 1.07M weighted per merged change against 3.35M for the previous harness's primary and supervision session together, because EDI's core does the watching that the previous harness's primary and its supervision session did through 1 624 wake-up turns.
- The pipeline stopped failing: 8% of runs fail against 43%, so a change needs 1.6 runs instead of 2.4.
- Merge requests merge faster after they open (project A 0.9 h against 2.0 h), and a request reaches a worker at once (0.01 h).
- Boards bundle: a board round carries six to nine answers where a chat message carries one, and both large features of 2026-10-26 reached a sliced plan within a working day.
- Specified slices run with little steering: that feature's slices took 0 to 2 steers each and EDI-52's first slices 0 to 1.

#### What costs more than it returns

- The turn-end boxed report: 63 report commands and 52 boxed replies a day, holding up to 30.2% of the primary's output tokens; EDI-74 point 5 is redesigning it.
- Overnight resumes: workers left running when the laptop stops are resumed in morning batches, and resumed incarnations spent up to 63.6M weighted (13% of the week).
- Single decisions in chat: 28 of 33 answer rounds carried one decision, each costing a captain prompt and a primary turn (0.17M weighted per captain turn), while the console answered fastest (4.0 minutes median).
- xhigh, so far: 23.5% of the week's worker spend with no measurable return yet; it may well pay, but nothing records whether it does.

#### Changes with the best measured return

1. **Spec first for every multi-slice feature.**
   Route any feature that will take more than one merge request through a scout's board before implementation, as that integration feature and EDI-52 did, and say so in the `edi-ship` and `edi-scout` skills.
   Measured return: a board round answers six to nine items at once, and the two board-first features of 2026-10-26 reached a plan in 7h01 and 1h27 against 18h27 for the previous harness's requester identity; the scouts cost 25.3M and 5.6M, 1% to 5% of a week.
   EDI-74 point 1 decides where the rule lives; this change only supplies the rule and its evidence.
2. **Batch ship-raised decisions into rounds.**
   A decision a running ship raises waits in the console's decision zone and reaches the captain as one round with the others open at that moment, instead of one chat question each.
   Measured return: 33 of 42 decisions went through chat, 28 of 33 rounds carried one decision, and the console was the fastest channel (4.0 against 9.8 minutes median); each chat answer avoided saves a captain prompt and a primary turn.
3. **Record what the extra effort buys.**
   Add the worker's effort and model to the task's recorded review rounds and findings (`task_review`, `task_cost`), and show cost and review yield per effort in the console's Week view (EDI-52 slice 11), so xhigh and the pipeline's high-effort review can be judged on data after a month.
   Measured return: it puts a number on 23.5% of worker spend and on the pipeline's 44% higher cost per change, which today nobody can justify or cut.
4. **Park before the laptop stops (evidence for EDI-52 slice 17, `edi park` and `edi resume`).**
   Nine of ten resumes followed a night or a restart; slice 17's return can be measured against the 63.6M baseline here.
5. **Hand the report figures to EDI-74 point 5.**
   The 30.2% output share and 63 report commands a day are the baseline its redesign should beat.

Changes 4 and 5 are evidence for planned work, not new tasks.


## The captain's answer on the Cucumber specification study

- Option C, with a note that reads as options A and C combined: each test level has its use, outer tests should be fewer than the quicker deeper ones (the testing pyramid), and the build must not exceed 10 to 15 minutes.
- The direction for a pilot on a Java project outside EDI: unit tests stay the base; most scenarios run at the application layer with in-memory adapters, no Spring and no Docker; one or two scenarios per feature run through Spring and real HTTP; one feature file serves both levels; no user-interface scenarios.
- This is a task on another project, queued separately, and is not part of the EDI backlog.

## Rerunning the comparison

- The comparison is rerun for a later EDI window against the same previous-harness window by the measuring scripts on the captain's machine; that is EDI-76.
- Questions worth adding next time, from the first run's gaps: post-merge defects (reopened tickets, fix requests that name an earlier one), and the return of the higher effort once EDI-97 records it.

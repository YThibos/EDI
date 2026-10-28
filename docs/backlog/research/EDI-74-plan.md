# EDI-74: the lifecycle, plans and report plan, carried over from the research

This is the research report behind the EDI-74 plan: one source per rule, workarounds as tickets, an explicit task lifecycle, plans, and the boxed report's role.
It was written on 26 October 2026, against the code as it stood then, and it authorises nothing by itself: each step is a backlog item with its own file, and the captain's answers it records are restated there as requirements.
Section numbers follow the original report; its section 3, the sources and method, is left out because it names files only the captain's machine holds, and file paths and line numbers are those of the code at the time, so confirm each against the current code before relying on it.

## Where the steps stand

| Step | Work key | State at the time of writing |
|---|---|---|
| 1 | none | done by the primary on the captain's machine, not a backlog item |
| 2 to 6, 8, 10, 25 | EDI-74, EDI-77 to EDI-80, EDI-82, EDI-84, EDI-95 | merged |
| 7 | EDI-81 | backlog |
| 9 | EDI-83 | backlog |
| 11 | EDI-85 | backlog |
| 12 | EDI-86 | backlog |
| 13 | EDI-70 | backlog |
| 14 | EDI-87 | backlog |
| 15 | EDI-65 | backlog |
| 16 | EDI-67 | backlog |
| 17 | EDI-88 | backlog |
| 18 | EDI-89 | backlog |
| 19 | EDI-71 | backlog |
| 20 | EDI-90 | backlog |
| 21 | EDI-91 | backlog |
| 22 | EDI-92 | backlog |
| 23 | EDI-93 | backlog |
| 24 | EDI-94 | backlog |

## 1. Summary

EDI's rules have no single home: the effort ceiling lives in seven places and the no-trailer rule in six, while about 20 of the anchor's 39 rules are already enforced by the core and one flag, `--no-verify`, slips past five of them.
Of the 22 learnings, ten can go now (expired, duplicated or enforced) and eight are workarounds for an EDI defect; each now names its fix, and a learning will carry "until <key>" so EDI itself flags it once the fix lands.
The persona slipped because the voice rules sit in a skill loaded once in 309 turns, and because the core's own messages call EDI a third party; the persona rule moves into the anchor and, by the captain's fold-in, every message template is reworded and tested.
A task's place in its life is inferred from four enums, two hold records and about twelve flags, and each of this week's four lifecycle defects came from a check reading one of them; the captain chose D43 A: one stored stage per task (queued, working, delivered, changing, ready, merged, landed, retired, dropped), the reasons it waits as a set beside it, and the worker's condition as its own machine, with the four defects as transition tests.
Plans exist only in report files and the primary's head; a plan becomes a domain object whose slices wait on merged or landed work, which the core dispatches itself, with one effort word per plan.
The boxed report repeated itself: 95 of 206 decision zones were identical to the one before, and the stop hook demanded 25 reports, 13 of the 22 analysed unchanged; the report becomes what changed since the captain last looked and what waits on him, printed only when there is such news, with the page one click away.
Section 9 orders 25 steps (step 1 by the primary, 24 pull requests), each with acceptance criteria and a wait so they land one after another; EDI-52 slices 1 and 2 land as built and four queued EDI-52 tasks are re-scoped into the plan; a review after a week measures the result against the baselines in section 9.

## 2. Decisions and answers

Round 1 on the research board, 26/10/2026, the captain's answers verbatim in the scout's inbox (message 3):

- Q1: A, the anchor becomes an index, with the hard rules as numbers and a few words each, marked where EDI refuses, held to the invariant catalogue by a test.
- Q2: A, project-specific rules live in the registry's `shipBriefRules`; memory keeps only the why.
- Q3: a to f: refuse `--no-verify`; a scout's done needs its `report.md`; needs-decision and blocked need a key and paused an until; SNAPSHOT checked at push; the brief's size checked when it is written; the Read tool guarded for secrets; not g, the em dash check in publish-check.
- Q4: A, a workaround ends with "until EDI-n", and stow-list and the digest flag it as expired or unticketed.
- Q5, decision D43: A, one stored stage per task, the reasons a task waits as a set beside it, and the worker's condition as its own derived machine.
- Q6: A, a merge while changing names the commits not landed and asks one question: a follow-up request or discard.
- Q7: A, EDI-52 slices 1 and 2 land as built; 14 and 18b fold into the lifecycle; 16 is rebuilt on the stages; 18c is replaced by the plan's effort word.
- Q8: A, each slice names its wait: after N merged (the default), after N landed, or nothing.
- Q9: A, the core dispatches the next slice once its wait is met and the plan has room.
- Q10: A, one effort word per plan, for every slice and relaunch.
- Q11: A, the report shows what changed since the captain last looked and what waits on him; I-19 becomes "in full once, then one line until answered".
- Q12: A, the report is printed only for news the captain has not seen, and the stop hook asks only then.

Two messages from the primary during round 1, acted on in this plan:

- Message 1 (19:57): the primary spoke of "EDI" in the third person beside "me"; the voice rules are treated as a case for where a rule must live (section 4.6).
- Message 2 (20:03): the captain asked to fold the voice case into the plan, calling it a big upgrade and tweak of the systems, to do everything in one go, and then to revise and introspect again after another week of the patches; folded in as plan step 8, the steps ordered to land together, and the review after a week (section 9).

Round 2:

- Q13, decision D44: A, answered in chat at 20:24 on 26/10 (message 4): ship the whole plan; if restarts of the core are recommended in between, say so, because the captain will wrap up his session and start EDI again, and make that session the main one again quickly afterwards to keep the context and cache.
- This is the captain's go for the promotion; the primary promoted the scout at 20:24: steps 2 to 25 are queued as their own tasks (EDI-74 and EDI-77 to EDI-95), steps 13, 15, 16 and 19 took over EDI-70, EDI-65, EDI-67 and EDI-71, and steps 2, 3, 4, 5 and 25 were running by 07:24 on 27/10.
- The captain's question about core restarts is answered in section 9, "When to update EDI between steps".

## 4. One source per rule

### 4.1 Where the rules live

The rules lived in the anchor, the captain's memory files, every skill, the worker brief template, the dispatch rules, the project registry and the decision log; the memory files are private to the captain's machine and are not in this repository.
The effort ceiling had seven homes and the no-trailer rule six.

### 4.2 What the core already enforces

About 20 of the 39 anchor rules are held by a guard or a refusal and pinned by an invariant test of the same number; about 15 more are partly held; the writing rules have no checker at all.
The full map, one line per rule:

| Rule | Held by the core | Where | Gap |
|---|---|---|---|
| I-01 only the primary talks to the captain | partly | `domain/status/StatusReport.java:21-26` | only a status opening with the address word is refused |
| I-02 primary and standby | yes | `web/access/Caller.java:28-36` | - |
| I-03 never merge, never push to base | yes | `domain/guard/MergingCommands.java:31`, `GitPushes.java:14,23` | - |
| I-04 drafts, ready once green | partly | `adapter/forge/GitHubForge.java:59`, `GitLabForge.java:59` | `--draft` only on `edi deliver`; "ready once green" unchecked; AT-04 tests merge following instead |
| I-05 a report authorises nothing | yes | `application/task/PromoteScout.java:54` | - |
| I-06 the captain's word for destructive acts | partly | `domain/authority/CaptainWordAct.java:7-13` | a fixed list of acts |
| I-07 workers stay in their copies | partly | `application/worker/GuardToolCall.java:134-139` | shell writes are not contained |
| I-08 branch shape | yes | `domain/branch/BranchName.java:19,77` | a branch a worker makes by hand is unchecked |
| I-09 no trailer | partly | `edi-core/src/main/resources/git-hooks/commit-msg:15-24`, `domain/delivery/DraftProposal.java:42` | `--no-verify` skips the hook |
| I-10 key first | yes, short of `--no-verify` | `commit-msg:11-14` | `--no-verify` |
| I-11 no SNAPSHOT | partly | `application/delivery/DoneClaims.java:72` | only at a ship's done, only `pom.xml`, never at push |
| I-12 no discarded unlanded work | yes | `application/delivery/DiscardWork.java:51`, `domain/guard/BranchDeletions.java:43` | - |
| I-13 fresh origin base | yes | `application/dispatch/IsolatedCopies.java:83-90` | - |
| I-14 category, effort ceiling, the word to lift it | yes | `domain/dispatch/EffortLevel.java:14-22`, `application/dispatch/DispatchTask.java:95-98` | - |
| I-15 EDI on Opus 5.5 at high | data | the project override in `dispatch-rules.json` | nothing pins the model |
| I-16 relaunch re-routes | yes | `application/dispatch/LaunchWorker.java:175` | - |
| I-18 numbers never reused | yes | `adapter/store/DecisionStore.java:45,55` | - |
| I-19 every open decision in every report | yes, for `edi report` | `web/report/CaptainReportPresenter.java:86` | - |
| I-20 answer verbatim with interpretation | partly | `web/decision/DecisionController.java:62` | a blank interpretation is accepted |
| I-21 the report ends a state-changing turn | yes | `application/primary/PrimaryHooks.java:136-151` | see section 8 |
| I-22 no internal labels | partly | `domain/voice/CaptainVoice.java:48` | chat prose is unchecked |
| I-24 the outage list | partly | `edi recover` | acting only on named numbers is prose |
| I-25 relaunch in its own copy and session | yes | `LaunchWorker.java:128-180` | - |
| I-27 a steer counts once acknowledged | yes | `application/worker/NoticeOverdueSteers.java:20,38` | a done is still accepted before it (section 6) |
| I-28 no key into a dialog | partly | `domain/worker/Keystroke.java:5-27` | - |
| I-29 refresh after a merge | yes | `application/delivery/CloneRefreshes.java:87` | - |
| I-30 no kill by pattern | yes | `domain/guard/ProcessSignals.java:29-55` | - |
| I-31 tmux sockets | yes | `domain/guard/TmuxSockets.java:26,30` | - |
| I-32 secrets | partly | `domain/guard/SecretReads.java:14-19` | the Read tool is not guarded (the worker's `PreToolUse` matcher is `Bash|Write|Edit|MultiEdit|NotebookEdit`, the primary's `Bash`) |
| I-33 memory within budget | partly | `application/memory/MemoryCheck.java:21` | warns, never blocks |
| I-34 craft rules in the intent | yes | `domain/delivery/PipelineIntent.java:18-22` | - |
| I-35 anchor and brief size | partly | `domain/primary/PrimaryPrompt.java:11,31-35` | the brief's 120 lines are test-only |
| I-36 fleet data on standard input | partly | the commands accept `-` | nothing refuses a large argument |
| I-38 nothing secret pushed | partly | `application/publish/PublishCheck.java:35`, `pre-push:18` | `--no-verify` |
| I-39 the registered forge only | partly | `application/publish/PublicationTargets.java:42` | `--no-verify`; pinned by AT-43, not AT-39 |

The brief's own rules: the state words are fixed (`StatusState`), but `--key` on needs-decision and blocked and `--until` on paused are not required (`StatusReport.java:10-18` requires a key only on resolved), and a scout's done is accepted without `report.md` (`DoneClaims.java:48` skips non-ships; only `CleanUpTask.java:99-106` checks it).

One bypass weakens five rules at once: EDI installs its `commit-msg` and `pre-push` hooks through `core.hooksPath` (`application/dispatch/WorktreeHooks.java:42`), and the shell guard does not refuse `git commit --no-verify` or `git push --no-verify`.

### 4.3 Duplicates

| Rule | Homes today |
|---|---|
| Effort never above high | `profile.md` (the whole file), anchor I-14, `learnings.md` line 15, `edi-ship` step 5 (near-verbatim), `machine.md` "EDI's own research ... at high (I-15)", `archive.md`, `dispatch-rules.json` - seven |
| No trailer | the captain's `AGENTS.md` line 16, `machine.md` "Hard rules on every project commit", anchor I-09, brief line 37, the repository's `CLAUDE.md`, the commit-msg hook - six |
| Writing: United Kingdom English, plain dash, one sentence per line | the captain's `AGENTS.md`, `craft-rules.md`, the repository's `CLAUDE.md`, `edi-decision-log`, `edi-scout`, scout specs - six |
| SNAPSHOT | `machine.md` section, anchor I-11, `BriefSections.projectRules` (hard-coded), `DoneClaims`, the repository's `CLAUDE.md` - five |
| Never merge, never push to base | anchor I-03, `machine.md` delivery flow step 3, `learnings.md` line 7, brief line 42 - four |
| The report ends a turn | `machine.md` "Reporting format" (HARD RULE), anchor I-21, `wakes-and-turn-end.md`, the stop hook |
| The outage list | `machine.md` "Interrupted work" (13 lines), anchor I-24, `edi-recover` |
| Relaunch routing and model by category | `machine.md` "Dispatch routing on a relaunch" and "Model choice comes from EDI's dispatch categories", anchors I-14 and I-16, `edi-recover` step 2 |
| Lavish for research | `machine.md` "Lavish is the STANDARD", `edi-scout` step 3, `BriefSections.researchBoard` |
| EDI's decision log scope | `machine.md`, the anchor's Memory section, `edi-decision-log`, the repository's `CLAUDE.md`, `docs/decisions/README.md` |
| GitHub Actions unavailable | `machine.md` "GitHub Actions does not run", `learnings.md` line 14, EDI-52 slice 18a |
| Base branch per project | `machine.md` "Base branch per project", `projects.yaml` `base` |
| The captain's word, recorded first | anchor lines 70-74, `edi-scout` step 2, `edi-recover` step 3, `edi-ship` step 5 |
| Skills restating anchor rules | I-13 and `edi-ship` step 6; I-29 and `edi-ship` landing; I-27 and `edi-ship` supervision; I-24, I-25, I-16, I-26 and I-28 (verbatim) in `edi-recover`; I-22 in `edi-voice`; I-33 in `stow`; the full-URL rule in the anchor, the brief, `edi-voice`, `edi-personality` and `edi-upkeep` |

### 4.4 Contradictions and stale rules

- `machine.md` "Every commit, including research and chores, must link a JIRA ticket" contradicts EDI-native keys: EDI's own work is keyed `EDI-n` on GitHub, and the desktop has no JIRA; the true rule is I-10, a work key.
- `profile.md` lines 6 and 21 point at `data/captain.md`, an old-harness path that no EDI home has.
- `machine.md` "Last curated 2026-10-15" and "`config/craft-review-projects` now lists no project": both name the old harness.
- `machine.md` "He tells the old harness when he merges something himself ... Do not build branch-watching" contradicted `FollowMerges` and `learnings.md` line 13; the primary removed it at 19:22 today while clearing firstmate-era text (the captain's point 6).
- `machine.md` records of one registered project are append-only, while EDI's own records are rewritten in place with a history line; both are right for their project, but one memory file states both, which the registry separates.
- The anchor and the invariant catalogue word four rules differently: I-04 (the catalogue tests merge following, not drafts), I-06 ("in the current exchange" against "in this session"), I-28 ("never press a key into a dialog blind" against "never"), I-32 ("secrets are read at call time and never printed" against "never read or print a secret").
- I-39 is pinned by AT-43, because AT-39 is the native smoke test.

### 4.5 The proposed homes

Each rule has one home that holds its text; every other place cites its number.

- **The core, with a test**: anything a command or hook can check at the moment of acting.
  The anchor keeps the rule's number and a few words; memory keeps the captain's words and why only where they explain a preference.
- **The brief, generated**: what a worker must know, written by the core from the same rules: the hard rules, the project's `shipBriefRules`, the craft rules.
  Workers never load the anchor, so this duplication is intended; a test holds each worker-facing invariant to a line of the template.
- **A skill**: a procedure for one trigger, which cites rule numbers instead of restating them.
- **The project registry**: everything that differs per project, including its `shipBriefRules`, which `BriefSections.projectRules` already writes into every brief (`application/brief/BriefSections.java:35`) and which no project sets today.
- **Memory**: the captain's preferences, in his words, with the why; learnings are dated facts and keyed workarounds (section 5).
- **Decision records**: the why of EDI's own design, never the captain's preferences.
- **The anchor**: identity, character, voice, the captain's word, how a turn ends, and an index of rule numbers and skill triggers.
- **The captain's `AGENTS.md`**: his personal style for every agent; Claude Code loads it into every session, workers included, so EDI never restates it.

### 4.6 A case: the voice rules, anchor or skill

On 26/10 the primary wrote "EDI hasn't been able to read your calendar ... won't switch me to quiet", speaking of EDI in the third person beside "me" in one message, which broke the persona for the captain.

- No rule says that what EDI's core does is the persona's own act: the anchor's Voice section (`claude/primary/CLAUDE.md` lines 18 to 24) sets the personality, the address word, the order of outcome and consequence, the stock phrase and the full URL, and sends everything else to `edi-voice` ("load `edi-voice` before relaying internal state").
- `edi-voice` holds the register (`claude/skills/edi-voice/SKILL.md` lines 10 to 18) and the translation table (lines 20 to 35); its trigger, "before any captain-facing message that relays internal state", fits nearly every message, so it is never the moment the model notices: across 20 to 26/10 the primary loaded `edi-voice` once in 309 turns (Skill calls in the transcripts: edi-ship 6, edi-scout 4, edi-voice 1).
- About one in eight of the primary's 421 messages that week named EDI in the third person as a subject ("EDI can", "EDI is", "EDI would"; 56 matches of a pattern that also catches some mentions of the EDI project), so the slip is a habit, not a one-off.
- The core teaches it: the texts it writes for the primary describe itself as a third party, for example "EDI raised ...; put it before the captain" and "EDI typed nothing" in the wake digest (`web/wake/WakeDigestText.java`), "stopped by EDI at ..." in the worker list (`web/worker/HealthWords.java`), and "This turn changed EDI's state" in the stop hook's reason (`domain/primary/TurnEnd.java:8`).
- `CaptainVoice` lints internal labels in the primary's turn wording and cards (`domain/voice/CaptainVoice.java:48`), but nothing looks at persona.

The general rule this case shows: a rule that governs every sentence or every turn lives in the anchor, which is always in mind; a skill holds only what one recognisable trigger needs, such as a command, a dispatch or a board's answer; and a skill whose trigger is "almost always" is an anchor rule in the wrong place.

The fix, which needs no choice from the captain:

1. The anchor's Voice section gains one line, "You are EDI, core and all: what EDI does, you did; say I, never EDI in the third person, except when naming the EDI project", and absorbs `edi-voice`'s five register lines, which are short enough for the budget; `edi-voice` keeps the translation table and its trigger narrows to "a label you are unsure how to say".
2. The core's message templates are reworded, as the captain folded into this plan on 26/10 ("Fold it, this is a big upgrade and tweak on your systems"): about 53 strings in 25 files speak of EDI in the third person; strings the primary reads (session-start digest, wake digest, hook reasons, `edi worker list`, the outage list) address it as "you", for example "You raised D43 on <task>; put it before the captain"; strings that reach the captain (report lines, the console, notifications, the return brief) speak in EDI's first person, for example "Calendar: I can't read your calendar since 14:02, so a meeting added since then won't switch me to quiet"; identifiers, packages, commands and the `edi` binary keep their names.
3. A test scans every message template and fails on "EDI" as the subject of a sentence, so the core's texts cannot teach the habit again.

## 5. Workarounds as tickets

### 5.1 Every learning, classified

`$EDI_HOME/memory/learnings.md` as read at 19:22 on 26/10, by line:

| Line | Entry | Class | What happens to it |
|---|---|---|---|
| 5 | links open with Shift and a click in tmux | fact | keep |
| 6 | a captain-side merge leaves the clone behind; EDI follows merges | duplicate | EDI's own behaviour (I-29, `docs/decisions/merge-following.md`): delete |
| 7 | the push guard; conflicts get main merged in; never merge | enforced | I-03 and the guard hold it; "merge main, never rebase" moves to `edi-ship`: delete |
| 8 | JIRA through `jira-sync.sh`; transition numbers | workaround | defect: EDI moves tickets itself (`adapter/tracker/JiraTracker.transition`, the workflow in `machine.yaml`), but never books a worklog or creates a story. Fix: book worklogs and create stories through the `Tracker` port. Expires with it. |
| 9 | the console serves only the sitrep page | duplicate | `docs/console.md` holds it, and EDI-58 (EDI-52 slice 7) changes it: delete |
| 10 | the guard refuses every Bash call while the core is down; after an outage, peek every busy worker (EDI-38) | fact and expired | keep the fail-closed fact; EDI-38 landed in pull request 44: delete that half |
| 11 | a full context until EDI-35 | expired | EDI-35 landed in pull request 33: delete |
| 12 | since EDI-23 a merge closes the task; queue the next step before the merge | workaround | defect: EDI has no plans (section 7). Expires with the plan slice. |
| 13 | read PR state from GitHub before advising a merge order | workaround | defect: no merge order is recorded, and the snapshot's request state lags the forge once a done is accepted (section 6, defect d). Expires with stages on the page and plans. |
| 14 | GitHub Actions refuses jobs; D13 B | workaround | ticket exists: EDI-69 (EDI-52 slice 18a). Expires with it. |
| 15 | effort above high: the procedure | duplicate | anchor I-14 and `edi-ship` step 5: delete |
| 16 | combining several PRs under a new key | workaround | defect: parallel requests conflict and there is no combined delivery. EDI-67 (EDI-52 slice 16) and the base-moved transition remove most of the need; expires with them. |
| 17 | `edi update` refuses a dirty clone; flaky `ProcessTableTest` | duplicate and expired | the `update` skill states the first; EDI-51 fixed the flake (commit `e38d07b`, pull request 47): delete once installed |
| 18 | a work checkout's location | machine fact | move to `machine.md` |
| 19 | presence commands until PR 32 | expired | pull request 32 merged and installed; the skills exist: delete |
| 20 | `edi decision raise` rewrites the task's card | workaround, half expired | EDI-40 stopped an answered card being rewritten (`application/decision/RaiseDecision.decisionOn` filters open decisions). Left: a task holds one open decision, so a second raise overwrites the first, and `Task.heldForDecision` overwrites any other hold. Fix: holds and decisions as a set (lifecycle slice). |
| 21 | a board answer counts only for cards raised before arming | enforced | `edi board arm --card` since EDI-40: delete |
| 22 | operations work cannot report done until EDI-41 | expired | EDI-41 landed in pull request 42: delete |
| 23 | two workers at most on one registered project | duplicate | `projects.yaml` `capacity: 2`: delete |
| 24 | never check a live `edi wait` with `pgrep -f` | workaround | defect: nothing reports whether the primary's wait is live, although the stop hook knows (`BackgroundJob.anyWaitsForEdi` in `PrimaryHooks.answeredStop`). Fix: `edi health` and the status line say it. Expires with it. |
| 25 | check standing answers before raising a decision | procedure | moves to `edi-ship`; its D13 B case expires with EDI-69 |
| 26 | the auto-mode classifier refused `jira-sync.sh worklog-add` | workaround | the same defect as line 8: expires with worklogs through the `Tracker` port |

Ten entries can go now (6, 7, 9, 11, 15, 17, 19, 21, 22, 23, and half of 10); eight are workarounds for an EDI defect (8, 12, 13, 14, 16, 20, 24, 26); two are facts (5, the first half of 10) and one belongs in `machine.md` (18); one is a procedure (25).

### 5.2 Keeping the link

The captain chose Q4 A.

- A learning is either a fact, dated with the day it was last confirmed as today, or a workaround, which ends with the key of the ticket that removes it: "until EDI-69".
- `StowList` (`domain/memory/StowList.java`, served by `application/memory/MemoryQuery.stowList`) reads the key at the end of each workaround and resolves it: an EDI-native key through the task store, a JIRA key through the `Tracker` port's status.
- A workaround whose ticket has landed is listed as "expired: delete it", one whose key no task or ticket carries as "unticketed", and the session-start digest names both counts, so `/stow` deletes or tickets the entry instead of the primary noticing by chance.
- Until the lifecycle stages exist (plan step 11), "landed" for an EDI-native key means the task is DONE; afterwards it means the stage LANDED or RETIRED.
- The entries that are workarounds today get their keys when the plan is promoted (plan step 1): line 14 "until EDI-69", line 20 the holds step, line 24 the wait step, lines 8 and 26 the tracker step, lines 12 and 13 the plan steps, line 16 the base-moved step.

## 6. An explicit task lifecycle

### 6.1 Where a task's state lives today

No field answers "where is this task in its life".
The state is spread over four enums, two hold records, about twelve nullable timestamps and flags across nine tables, and a derived worker health:

- `domain/task/TaskState.java:6`: QUEUED, IN_FLIGHT, DONE, DROPPED; DONE is reached only by cleanup (`Task.cleanedUp`, `Task.java:108`) or a captain item's answer (`Task.decisionAnswered`, `Task.java:64-71`).
- `domain/task/CaptainHold.java:5` with `HoldKind` DECISION, DEFERRAL, APPROVAL, one row per task in `captain_hold`; an outage hold is an APPROVAL hold recognised by its reason (`domain/recovery/OutageEntry.java:30,35`).
- `domain/worker/Worker.java:16`: activity (STARTING, BUSY, IDLE), the last status (`StatusState`: WORKING, NEEDS_DECISION, BLOCKED, PAUSED, DONE, FAILED, RESOLVED), the done check (`DoneCheck`: CHECKING, ACCEPTED, REFUSED), and timestamps for started, briefed, ended, stop requested, launch failed, retired, context full and limit parked; `WorkerHealth` is derived by `SupervisionRules.healthOf` (`SupervisionRules.java:26-49`).
- `domain/delivery/Delivery.java:8`: draft, head commit, landed commit, and `MergeRequestState` OPEN, MERGED, CLOSED.
- `domain/delivery/PipelineRun.java:6`: a free-text status; running means none of five end words.
- `domain/delivery/CloneRefresh.java`: verified and cleanup-tried timestamps.
- Landing is computed each time and never stored (`domain/landing/LandedWork.java:23`).
- Decisions are open while their answer is null (`domain/decision/Decision.java:42`); boards listen while not ended or retired (`domain/board/Board.java:40`).

The fleet snapshot then works out each zone from several of these at once (`application/fleet/FleetSnapshotQuery.java`): ready for review is IN_FLIGHT, the latest worker's done ACCEPTED and the request OPEN (lines 103 to 116); Ongoing is every unheld IN_FLIGHT task whatever its worker said (lines 67, 122 to 127), so a task can be ready for review and ongoing at once; Landed means a DONE task closed in the last seven days, that is, cleaned up.

The same question is answered differently in several places:

- "Waits on the primary" has three definitions: `StatusReport.isForThePrimary` (NEEDS_DECISION, BLOCKED), `SupervisionRules.AWAITING_THE_PRIMARY` (also DONE, FAILED, PAUSED) and `web/recovery/OutageListText.java:71` (NEEDS_DECISION, BLOCKED, PAUSED).
- "Handed over" has three proxies: `CleanUpTask` treats a merged delivery as handed over (lines 89 to 97), `CopyRetirement` uses `handedItsWorkOver` and the window, and capacity counts a worker until its done is accepted, so a worker working again after its done frees its slot.
- `Blocker.isResolved` counts only DONE as resolved, while `DependantTasks` also removes DROPPED blockers.
- `RaiseDecision` overwrites any other hold through `Task.heldForDecision`, and the answer then clears it, so a deferral is lost (pinned by `RaiseAndAnswerDecisionTest.holdsTheTaskWhileItsDecisionIsOpenAndReleasesItToItsStateOnTheAnswer`), while `OutageHoldList` skips a held task.
- `SteerWorker` records a `resolved` status event at send time (`SteerWorker.java:62`), while the worker row changes only on acknowledgement (`WorkerInbox.java:62`).
- FAILED and PAUSED have no effect on the task.
- After a refused cleanup the core never retries: once `clean_up_tried_at` is set, only `edi merged` or `edi cleanup` tries again (`adapter/store/CloneRefreshStore.java:60-61`).

### 6.2 This week's defects, traced

**a. A request merged without the last steer's commit** (store `event` rows 2050 to 2066, times in CET):

1. 16:27:26: steer 2, an amendment to the spec, is sent while the worker is mid-turn.
2. 16:32:50: "steer not received".
3. 16:33:50: the worker reports done; `RecordStatus.report` calls `DoneClaims.verify` (`application/delivery/DoneClaims.java:46-59`), which checks the copy, SNAPSHOT versions, the request's gaps and a report, but neither the inbox nor the running pipeline run; `Worker.reported` accepts it at once (`Worker.java:200-205`).
4. 16:33:56: the stop hook delivers steer 2; 16:34:03 the worker reports working, which resets the done check.
5. 16:36:18: the worker commits the late commit; a new pipeline run starts.
6. 16:38:37: the captain merges the request at its old head the old head; `FollowMerges.recordedAsMerged` runs `CleanUpTask`, which skips the hand-over check for a merged delivery and refuses in `requireLanded` because the late commit is not in `origin/master`.
7. The task stays IN_FLIGHT with a merged delivery, and still sits under Ongoing in `edi sitrep` at 19:30.

**b. A finished scout came back as Ongoing (a decision on it)**: the decision holds the task from 14:00 (shown only as a card); the done is accepted at 15:35 with no check for an open decision; the scout is promoted at 15:48 and stays IN_FLIGHT, because `PromoteScout` does not close it; the answer at 16:01 calls `Task.decisionAnswered`, which for a scout only clears the hold, and `FleetSnapshotQuery.ongoing` lists it again because it ignores `Worker.hadItsDoneAccepted`.
No zone exists for "finished, waiting for the next step"; it is still under Ongoing at 19:30.

**c. Cleanup refused after a squash merge**: `LandedWorkCheck.ofTheCopy` runs `git cherry -v base HEAD` (`adapter/git/Git.java:164-169`), which matches commits by patch identity, so a squash matches none; the only rescue is `holdsTheContentOf` (`Git.java:171-174`), which needs `merge-tree` of the base and the head to equal the base's tree exactly, and fails once the base changes the same lines again or the copy holds any extra commit.
AT-12 pins only a squash with an unchanged base (`WorkIsolationAndGitHygieneTest.java:264`).
This week's three refusals were right (the EDI-52 scout's F11 checked each), so this is a latent gap.

**d. A request shown ready while still changing**: `readyForReviewAmong` (`FleetSnapshotQuery.java:103-116`) reads only an accepted done and an open request, ignoring the latest pipeline run, unacknowledged steers, the draft flag and whether the request's head is the copy's; after the done, `ObservePipelines` stops observing the task (`ObservePipelines.java:59-66`), so its run row freezes at "running", and `FollowMerges.observed` saves only closed or merged changes (lines 276 to 283), so an open request's head and draft flag go stale.

### 6.3 The model

The captain chose D43 A: one stored stage per task, the reasons a task waits as a set beside it, and the worker's condition as its own derived machine.

Three questions, three machines:

1. **Where is the work?** The stage, stored on the task: QUEUED, WORKING, DELIVERED, CHANGING, READY, MERGED, LANDED, RETIRED, DROPPED.
2. **Why does it wait?** The holds, a set beside the stage that never moves it: each open decision (any number), a deferral, an approval, an outage entry, a night's park.
3. **How is its worker?** `WorkerHealth`, derived from the worker row as today: starting, busy, idle, waiting, limit parked, context full, stopped, launch failed, finished, dead, retired.

A flat list of states would need one for every combination, such as "delivered, parked for the night, with a dead worker"; three small machines each answer one question, and every surface reads the same answers.

| From | To | Trigger | Guard | Today |
|---|---|---|---|---|
| QUEUED | WORKING | the primary's `edi dispatch`, or a plan dispatching its next slice (section 7) | no hold, every wait met, the project's and the plan's capacity free | `Task.dispatched`; deferral is a separate column (`dispatch.deferred_at`) |
| WORKING, CHANGING | DELIVERED | the worker's `edi status done` | the copy is clean, no SNAPSHOT is added, no steer waits unacknowledged, no pipeline run is running; a ship's request is open at the copy's head; a scout's `report.md` exists | `DoneClaims.verify` checks neither steers nor runs, and skips a scout's report |
| DELIVERED | READY | the core, observing the forge | the request is out of draft, its latest run passed, its head is the delivered head | not stored; inferred from the done check and an open request |
| DELIVERED, READY | CHANGING | the core: a steer sent, a pipeline run started, the worker reporting working, the request's head or the base moving, the request closed | none | does not exist; pipelines stop being observed after the done |
| WORKING, DELIVERED, CHANGING, READY | MERGED | the core following the forge, or `edi merged` | the forge reports the request merged; the merged head is recorded | `FollowMerges.recordedAsMerged` |
| MERGED | LANDED | the core | every commit up to the merged head is in the base; a commit after it keeps the task MERGED and is named | computed each time; a squash is judged by content only |
| LANDED | RETIRED | the core's cleanup, retried every delivery round until it holds | copy, branch, workers and boards retired; dependants and plan slices waiting on LANDED released | `Task.cleanedUp`; a refusal is never retried by the core |
| DELIVERED (a scout) | RETIRED | `edi cleanup` | the report exists | the same cleanup |
| any but RETIRED | DROPPED | `edi task drop` | the captain's word, and the work landed unless discarding it is part of that word | `Task.dropped` |
| (no move) | (no move) | an answer to a decision | removes that decision's hold only | `Task.decisionAnswered` lifts any hold |

A merge while the task was CHANGING (Q6 A) leaves it MERGED, names the commits after the merged head ("1 commit not landed") and raises one decision on the task: deliver them as a follow-up request from the same branch, or discard them, the option carrying `"authorises": "discard-work"`.

The four defects as transition tests:

| Defect | Test |
|---|---|
| a. the merged request | a DELIVERED task and a steer sent → CHANGING; `edi status done` refused naming the message; after `edi inbox ack` and the run's end → DELIVERED; a merge of the old head while CHANGING → MERGED with "1 commit not landed" and one decision raised |
| b. the finished scout | a scout DELIVERED with an open decision; the answer → still DELIVERED, holds empty, listed as "report ready", not under Ongoing |
| c. squash | MERGED at head H by a squash; the base later rewrites the same lines → LANDED; a commit after H → stays MERGED, the commit named |
| d. ready while changing | DELIVERED with a pipeline run running → not READY; the run passes and the draft flag drops → READY; a new run starts → CHANGING |

### 6.4 The decision record

The record is in the repository: [task-lifecycle](../../decisions/task-lifecycle.md).

### 6.5 Against the EDI-52 slices

The captain chose Q7 A.

| EDI-52 slice | State at 20:10 on 26/10 | In this plan |
|---|---|---|
| 1, a done waits for its steers (EDI-52) | working since 19:05 | lands as built; step 12 re-homes its rule as the guard on WORKING → DELIVERED |
| 2, changing before ready (EDI-53) | working since 19:05 | lands as built as the interim rule; step 12 replaces its computation with the stored stage, and its acceptance tests become transition tests |
| 14, report ready and the refused cleanup in red (EDI-65) | queued | re-scoped by `edi task note` into step 15, keeping its key |
| 16, open requests stay mergeable (EDI-67) | queued | re-scoped into step 16, the base-moved trigger, keeping its key |
| 18b, squash-merge cleanup (EDI-70) | queued | re-scoped into step 13, landing by the merged head, keeping its key |
| 18c, one effort word for several tasks (EDI-71) | queued | re-scoped into step 19, the plan's effort word, keeping its key |
| 17, park and resume (EDI-68) | queued | unchanged; step 14 then folds its night's park into the set of holds |
| 3 to 13, 15, 18a, 19, 20 | working or queued | unchanged |

Re-scoping rather than dropping keeps the keys and needs no word to drop; a note rewrites each task's brief and pipeline intent (`edi-ship`, "A steer that changes a task's scope is first recorded with `edi task note`").

## 7. Plans as a first-class concept

### 7.1 What exists

- The only plan in EDI's domain is research first: one scout, one ship waiting for it, the scout's report opening the ship's brief (`docs/decisions/research-first.md`, `ResearchBeforeImplementation`, `BriefFacts.followsAPlan`).
- EDI-52's twenty slices are twenty queued or running tasks, keyed EDI-52 to EDI-73, each titled "EDI-52 slice n" by hand; the order lives in each task's notes ("see section 9, item 4") and in the captain's words "slices 1 to 19 in order"; none carries `--blocked-by` (`edi task list`, `edi task show edi-52-slice-4-quiet-delivers-the`), and slices 1, 2 and 3 were dispatched together at 19:05.
- The second plan, eleven slices of a registered project's work, kept its order in prose only: one slice said it followed once another was merged, and several depended on earlier slices without any record of it.
- `--blocked-by` waits for the blocker to be DONE, which means cleaned up (`domain/task/Blocker.java:5`); it cannot say "after it is merged", nor "stack on its branch".
- Each slice needs its own key because tasks sharing a key share a branch (`learnings.md` line 22, EDI-41).
- The captain's "For the implementer: Opus 5.5 xhigh" was said once for eleven slices of one plan, but the word is held per task, one act and the current exchange (`domain/authority/Authorisation`, `DispatchTask.java:95-99`), which EDI-52 slice 18c (EDI-71) would loosen to a repeated `--task`.
- Capacity exists per project (`projects.yaml` `capacity: 2` for one registered project; dispatch deferral in `DispatchTask.java:244-250`), not per plan.
- `learnings.md` lines 12, 13 and 16 are the primary keeping plans in its head: queue the next step before a merge, read the forge before advising a merge order, combine requests by hand.

### 7.2 The proposal

The captain chose Q8 A, Q9 A and Q10 A.

- **A plan** is a domain object with a title, the umbrella work key (EDI-52, or a registered project's umbrella key), the report it came from, the decision that gave the go, its capacity (slices working at once, within the project's own) and, when the captain gives it, its effort word.
- **A slice** is a task of its kind and category with its own key, its number in the plan, and one wait.
- **A wait** is "after N merged" (the default, on the slice before), "after N landed", or none, read against the stages of section 6; today's `--blocked-by` becomes "after landed" on the named task, which is what it means now (`Blocker.isResolved` is DONE, that is, cleaned up).
- **Input**: `edi plan add --from -` takes the plan as JSON on standard input (I-36), which the primary writes from the report's plan section: title, key, report, capacity, and per slice its number, title, category, key, body and wait; `edi plan show <plan>` prints its slices with stage and wait.
- **Dispatch (Q9 A)**: in each supervision round the core dispatches every slice whose wait is met while the plan and the project have room and the captain's presence allows, through the same `DispatchTask`, and tells the primary with one notification per dispatch; the captain's go on the plan covers every slice.
- **The effort word (Q10 A)**: `edi captain said --plan <plan> --for effort-above-high --text "..."` records one authorisation on the plan, which `DispatchTask` honours for its slices and their relaunches; the ceiling stays high everywhere else, and it replaces EDI-71's repeated `--task`.
- **On the page**: under Ongoing, one row per plan with its key, title, counts by stage, a strip of its slices and the next slice with its wait; a click opens the plan's slices (the EDI-52 task panel then serves each slice).
- **In the report**: plans are grouped on the one working line ("EDI-52 plan 3 of 20 landed, slices 4 to 6 working").
- **A record**: a decision record named `plans`, which the lifecycle record's QUEUED → WORKING transition cites.
- **What it retires**: `learnings.md` lines 12, 13 and 16, and the per-slice "see section 9, item n" notes.

## 8. The boxed report, now that the console sitrep page exists

### 8.1 How it is built and when it is forced

- `edi report [--turn -]` and `edi sitrep` call `ReportController` (`web/report/ReportController.java:32-48`); both mark the report rendered for the session (`primaryHooks.reportRendered`, line 47).
- `CaptainReportPresenter.text` (`CaptainReportPresenter.java:46-58`) prints, in order: the presence and calendar lines when not plainly present; the decision zone, every open decision as a full card (I-19), board questions and reviews as one line each; DONE THIS TURN and NEXT from the turn's JSON only; ONGOING, every unheld task in flight; PARKED; BACKLOG only when nothing waits and nothing runs.
  It is 120 columns wide, or the pane's width minus two.
- The stop hook (`PrimaryHooks.answeredStop`, lines 136 to 153; `domain/primary/TurnEnd.java:7-25`) blocks the primary's stop with "This turn changed EDI's state: render the captain report with edi report and paste it verbatim, then stop." when `TurnCircumstances.needsTheReport` holds (state changed and the chat is not kept quiet) and no report was rendered since the turn began; it gives up after three blocks.
- "State changed" is any event in the log after the turn's mark, from any actor, a worker's status included (`EventLog.anyAfter`, `EventLog.java:168-173`).
- "Rendered" is `reportedAt >= startedAt` (`PrimaryTurn.java:65-67`), and every prompt, a notification included, starts a new turn; it never checks that the report was pasted or that anything visible changed.
- `docs/decisions/captain-report.md` still states the report's purpose as reproducing the old harness's layout byte for byte against golden files (AT-42), and lists "when the captain asks for a change to the report's layout" as the time to revisit.

### 8.2 What this week's sessions show (20 to 26/10)

- 309 turns: 198 typed by the captain, 111 notification wakes; 191 of them pasted a boxed report, 200 reports in all (36 on 21/10, 84 on 22/10, 40 on 23/10, 40 on 26/10), from 235 `edi report` and 12 `edi sitrep` calls.
- The stop hook forced 22 reports (12 in wake turns, 10 in typed turns); in 13 of them the decision zone and Ongoing were identical to the report before; in 4 a report had already been pasted in that turn, each time a message the captain queued mid-turn had reset the turn (23/10 10:51 and 13:14 CEST, 26/10 16:01 and 16:06 CET); the primary said so itself at 19:09 on 26/10: "Nothing has changed since my last report; EDI's turn check asked for a fresh render anyway".
- It also blocked 23 stops for "no edi wait is live".
- The decision zone was byte-identical to the previous report's in 93 of 199 consecutive pairs; decisions D22, D23 and D24 filled five reports running (22/10 18:03 to 18:15 CEST, 53 to 66 of 78 to 92 lines); D38 and D39 four (26/10 15:50 to 16:01 CET, 46 of 63 to 66 lines).
- Reports ran 18 to 97 lines, median 34; Ongoing held 0 to 12 tasks, median 7; PARKED appeared in 193 of 200; BACKLOG once.
- At 19:30 on 26/10, `edi sitrep` lists nine tasks under Ongoing, of which three are not ongoing: two finished scouts and a merged request whose cleanup was refused.
- The captain asked that decisions on a research board not stay on the console's ascii sitrep (which became EDI-51), that the sitrep page become his main panel of operations, and that the report not simply be culled but researched; his memory rules are not to list recently landed things and to lead with what is still moving, what is blocked and what needs him.
- He opens the page at every session ("Hello EDI, SITREP board, please." is the opening prompt) and asked for `/sitrep board` five times on 22 and 23/10.

### 8.3 What only each surface shows

- Only the page: recent answers with their channel and time, the landed list, Charted next (queued and parked, always shown), each ongoing task's key, kind, worker state, what it is doing and its request, the time each card was raised, answering in place, and live updates (`SitrepConsolePresenter.java:63-81`, `edi-console/src/app/sitrep/`).
- Only the report: DONE THIS TURN and NEXT, which exist only in the primary's narration; the primary's own wording of tasks; the calendar notice; and being in the chat, where the captain types.

### 8.4 The proposal

The captain chose Q11 A and Q12 A.

**What the report is for.**
It answers two questions in the chat, where the captain types: what changed since he last looked, and what waits on him; the console sitrep page holds the full picture, one click away.
It also carries the one thing only the chat knows: what this turn did and what comes next.

**What it shows**, in order:

1. A header line: the local time, the presence when not plainly present, and "since you looked at HH:MM".
2. WAITS ON YOU: a decision card in full the first time it is shown, then one line with "shown HH:MM" until answered; boards and their bound decisions as today; requests READY with their full URL.
3. SINCE HH:MM: what changed for the captain since his last look, one line each: landed, ready, failed, blocked, changing with its reason, decisions answered elsewhere.
4. DONE THIS TURN and NEXT, from the turn's items, as today.
5. One WORKING line: the count, plans grouped, parked tasks counted.
6. The page at the foot.

I-19 becomes "Every open decision is in every report: in full the first time, then one line until it is answered"; the page always shows every card in full.

**The captain's last look** is the latest of: a report shown at the end of a turn he typed, and the console sitrep page visible in his browser (the page reports it when it is opened or regains focus).

**When it is printed, and the stop hook's rule.**

- A turn the captain typed ends with the report when something changed for him since his last look; otherwise it ends in prose.
- A notification turn in present mode prints it only for a new decision, a request becoming ready, a failure or a landing.
- The stop hook's `TurnCircumstances.needsTheReport` asks whether a captain-visible change happened since the last report shown, not whether any event was recorded since the prompt; "rendered" is kept across prompts until such a change; and it never asks for a report whose content would be identical to the last one shown.
- I-21 becomes "A turn that brings the captain news ends with the captain report".
- Quiet and away keep their rule: no text, notifications and the return brief.

**The record**: `docs/decisions/captain-report.md` is rewritten to state the report's purpose as the delta and what waits, with a history line, and the golden files (AT-42) are rewritten on purpose; `wakes-and-turn-end.md` gains the new stop rule.

The mock-up on the board (section 5) shows today's report beside the proposed one.

## 9. Plan

An ordered plan of small EDI changes, each its own task and pull request on `feature/EDI-<n>` except step 1, each shippable on its own and with its own acceptance criteria.
The captain asked for the whole plan in one go (steer, 26/10 20:03): every step is queued at the go, and each step's wait names the steps whose merge it needs, so the steps land one after another without conflicting.
Steps 2 to 7 and 25 touch separate corners of the code and start at once; step 8 rewords the message templates before the presenters change, so every later step's text is held to its test; the lifecycle, plans and report steps form one chain.
Plans do not exist until step 17, so the primary dispatches this plan by hand in this order, honouring each wait; from step 18 on, a plan dispatches itself.
Every step also passes `./mvnw verify`, and a step touching Spring configuration, resources, the hooks or the command passes `./mvnw -Pnative verify`; every step that changes a recorded decision rewrites its record with a history line.
EDI's research and implementation run on Opus 5.5 at high (I-15).
Sizes: XS under two hours, S about half a day, M about a day.

### Rules and memory

1. **Memory to one source (the primary, on the captain's go; no pull request), S. Wait: none.**
   Done by the primary on the captain's machine, in memory files that are not in this repository; it is not a backlog item.
2. **Refuse `--no-verify`, XS. Wait: none.**
   `domain/guard` gains a rule refusing `git commit`, `git push` and `git merge` with `--no-verify` or `-n` (commit), and `-c core.hooksPath=` on any git command, naming the checks it would skip; `docs/decisions/pre-tool-guard.md` gains a history line.
   - `ShellGuardTest`: each form is refused with the reason; `git commit -m "EDI-1 x"` is allowed.
   - AT-09 and AT-10 gain a case: a worker's `git commit --no-verify` with a trailer is refused before git runs.
3. **Status words carry what they promise, S. Wait: none.**
   `DoneClaims.verify` refuses a scout's done without `report.md` in its task directory; `StatusReport` refuses needs-decision and blocked without `--key`, and paused without `--until`; the brief's Reporting table is unchanged, since it already asks for them.
   - A scout's `edi status done` without a report is refused naming the path; with one, accepted.
   - `edi status needs-decision -- x` is refused naming `--key`; `edi status paused -- x` naming `--until`.
4. **SNAPSHOT at push, S. Wait: none.**
   The pre-push check (`application/publish/PublishCheck`) reads every `pom.xml` the outgoing commits touch and refuses a `-SNAPSHOT` version, as `DoneClaims` does at a ship's done (`domain/delivery/SnapshotVersions`).
   - `PublishCheckTest`: an outgoing commit adding `1.2.0-SNAPSHOT` to a pom is refused naming the file; a release version passes.
   - AT-11 gains the push case.
5. **The brief's size when it is written, XS. Wait: none.**
   `WorkerBriefs` refuses a brief over 120 lines at dispatch, naming the longest section, instead of only a test holding the template (I-35).
   - A dispatch whose spec makes the brief 121 lines is refused naming "Spec"; at 120 it dispatches.
6. **Read guarded for secrets, S. Wait: none.**
   The worker's and the primary's `PreToolUse` matchers gain `Read` (`claude/worker/settings.json`, `claude/primary/settings.json`), and `GuardToolCall` refuses a Read whose path `SecretReads` names, as it refuses `cat` of it; the captain's word lifts it for the primary as today.
   - A worker's Read of the keys file named in the machine profile is refused; a Read of a source file is allowed.
   - AT-32 gains the Read case.
7. **Learnings name their ticket, S. Wait: none.**
   As section 5.2: `StowList` resolves a workaround's "until <key>" through the task store or the `Tracker` port and lists expired and unticketed workarounds; the session-start digest names both counts; the `stow` skill says what to do with each.
   - With a learning "until EDI-69" and EDI-69 DONE, `edi memory stow-list` lists it as expired; with no task keyed EDI-99, "until EDI-99" is listed as unticketed; a dated fact is listed as neither.
   - The digest's memory line names the counts.
8. **I speak as myself, M. Wait: none.**
   As section 4.6 and the captain's fold-in: the anchor's Voice section gains the persona line and `edi-voice`'s register lines; the runtime message templates the primary reads address it as "you", and those that reach the captain (report lines, console, notifications, return brief) speak in EDI's first person; identifiers, packages, commands, the `edi` binary, worker briefs and `--help` descriptions keep their names and third person, since their readers are not EDI; a persona test, beside AT-22, scans every message template and fails on "EDI" as the subject of a sentence; `docs/decisions/captain-facing-voice.md` gains a history line.
   - The persona test fails on "EDI raised D43" in a wake template and passes on "You raised D43"; it ignores `EDI-74`, `edi report` and quoted text.
   - The calendar line reads "Calendar: I can't read your calendar since 14:02, so a meeting added since won't switch me to quiet" in the report's golden case.
   - Re-running the transcript measuring script over a day of use after the merge finds no message naming EDI in the third person outside the project's name.
9. **The anchor as an index, S. Wait: after 2 to 8 merged.**
   `claude/primary/CLAUDE.md` keeps identity, character, voice, the captain's word, turn ending and skill triggers, and lists the hard rules as numbers with a few words each, marked where EDI refuses; skills cite rule numbers instead of restating them (section 4.3's list); a test holds every anchor number to its nested class in the invariant catalogue and their wording to each other, which fixes the drift of I-04, I-06, I-28 and I-32; `docs/decisions/primary-anchor-and-skills.md` gains a history line.
   - The test fails when the anchor names a rule the catalogue lacks, or the two wordings differ.
   - The anchor is under 7,000 bytes; AT-35 holds.
   - No skill restates an anchor rule's text (a test over `claude/skills` for the rule lines).

### The task lifecycle

10. **The record and the stages, in the domain, M. Wait: after EDI-52 slices 1 and 2 (EDI-52, EDI-53) merged.**
    `docs/decisions/task-lifecycle.md` (section 6.4); `domain/task/TaskStage`, `StageTransitions` and `Holds`, pure domain with no caller yet; the transition table of section 6.3 as tests, including the four defects.
    - One test per row of the table, allowed and refused; the four defect tests of section 6.3.
    - `ArchitectureRulesTest` holds: the domain has no Spring, input or output.
11. **The stage is stored, M. Wait: after 8 and 10 merged.**
    A Flyway migration adds `task.stage` and derives it once for every open task; every application service that changes a task's state goes through `StageTransitions`; `StageChanged` events; `edi task show` prints the stage and its history; `ObservePipelines` and `FollowMerges` keep observing a delivered task's runs, head and draft flag.
    - After the migration, the task merged with a commit after the merged head reads MERGED with that commit not landed, and the finished scout reads DELIVERED.
    - A pipeline run started after a done is recorded.
12. **Delivered, changing, ready, M. Wait: after 11 merged.**
    The guard on WORKING and CHANGING → DELIVERED (step 3's scout report and EDI-52 slice 1's steer rule re-homed, plus no running run); the triggers into CHANGING; READY entered only from the forge's view (EDI-52 slice 2's rule re-homed).
    - Defect tests a and d pass end to end through `RecordStatus`, `SteerWorker`, `ObservePipelines` and `FleetSnapshotQuery`.
13. **Landing by the merged head, M. Wait: after 11 merged. Takes over EDI-70 (EDI-52 slice 18b) by task note.**
    MERGED → LANDED judges the commits up to the merged head as landed and names any after it; a merge while CHANGING raises the one decision of Q6 A; the core retries a refused cleanup every delivery round; `docs/decisions/cleanup.md` gains a history line.
    - Defect test c passes; a commit after the merged head stays named.
    - With the first week's timeline, one decision is raised naming the late commit, with a follow-up and a discard option, the latter `"authorises": "discard-work"`.
14. **The reasons a task waits, as a set, M. Wait: after 11 merged.**
    `Holds` replaces the single `captain_hold` row: several open decisions per task, a decision never replacing a deferral or an outage entry, an answer removing only its own decision; `RaiseDecision.decisionOn` raises a second open decision instead of rewriting the first; EDI-68's park, once merged, becomes one of the holds.
    - Two decisions on one task have two numbers and both show; answering one leaves the other.
    - A deferred task that gets a decision is still deferred after the answer (the inverse of today's `RaiseAndAnswerDecisionTest` case).
15. **The page, the report and the digest on stages, M. Wait: after 12 and 13 merged. Takes over EDI-65 (EDI-52 slice 14) by task note.**
    `FleetSnapshotQuery` reads stages and holds: Ongoing is WORKING and CHANGING; ready for review is READY; a delivered scout is "report ready"; CHANGING shows in red with its reason; MERGED with a commit not landed shows in red with the commit.
    - Defect test b passes on the snapshot; no task appears both under Ongoing and as ready for review.
    - The console, `edi sitrep` and the digest list the same tasks in the same zones.
16. **The base moved, S. Wait: after 12 merged. Takes over EDI-67 (EDI-52 slice 16) by task note.**
    After a followed merge, each other task of the project that is DELIVERED or READY moves to CHANGING and receives one steer to merge the base and re-run its pipeline; a closed request moves its task to CHANGING and leaves the review zone.
    - After a merge on project P, each other READY task of P receives exactly one steer naming the merged request.

### Plans

17. **Plans and their waits, M. Wait: after 11 merged.**
    As section 7.2: the plan domain and store, `edi plan add --from -`, `edi plan show`, waits on MERGED or LANDED, `--blocked-by` as "after landed", and the `plans` decision record.
    - A plan of three slices where 2 waits on 1 merged and 3 on nothing shows 1 and 3 eligible, then 2 once 1 is MERGED.
18. **The plan dispatches its slices, M. Wait: after 17 merged.**
    The supervision round dispatches eligible slices within the plan's and the project's capacity and the presence's cap, and notifies the primary of each.
    - With capacity 2 and three eligible slices, two are dispatched in one round and the third when one is delivered.
    - A held slice is not dispatched.
19. **One effort word per plan, S. Wait: after 17 merged. Takes over EDI-71 (EDI-52 slice 18c) by task note.**
    `edi captain said --plan <plan> --for effort-above-high` records one authorisation on the plan, honoured for its slices and their relaunches; `docs/decisions/captains-word.md` gains a history line.
    - Every slice of the plan dispatches at xhigh; a task outside the plan is still refused.

### The report

20. **The captain's last look and the delta, S. Wait: after 15 merged.**
    `CaptainsLook` is recorded when a report is shown at the end of a typed turn and when the console page is opened or regains focus; a query lists the captain-visible changes since it from the event log.
    - With a look at 18:15, a landing at 18:30 and a worker's status at 18:40, the delta holds the landing only.
21. **The report's new layout, M. Wait: after 20 merged.**
    As section 8.4: header, WAITS ON YOU (a card in full once, then one line), SINCE, DONE THIS TURN, NEXT, one WORKING line, the page; the golden files (AT-42) rewritten; I-19 reworded in the anchor and the catalogue; `docs/decisions/captain-report.md` rewritten to the new purpose with a history line.
    - A decision shown in full in one report is one line in the next, with "shown HH:MM"; the page still shows it in full.
    - With nothing new since the look, the SINCE section is absent and the report is at most 12 lines with nothing waiting.
22. **The stop hook's new rule, S. Wait: after 20 merged.**
    `TurnCircumstances.needsTheReport` asks for a captain-visible change since the last report shown; a render counts across prompts until such a change; a report identical to the last shown is never demanded; I-21 reworded; `docs/decisions/wakes-and-turn-end.md` gains a history line.
    - A turn in which only a worker's status was recorded ends without a demand.
    - A message queued mid-turn after a report does not cause a second demand when nothing new for the captain happened.
    - AT-21 holds for a new decision.
23. **Plans on the page and in the report, S. Wait: after 18 and 21 merged.**
    A plan's row under Ongoing with counts by stage, its slice strip and the next slice with its wait; plans grouped on the report's WORKING line.
    - The EDI-74 plan shows as one row with its counts; the report reads "EDI-74 plan n of 24".

### Workaround tickets

24. **Worklogs and stories through the tracker, M. Wait: after EDI-52 slice 3 (EDI-54) merged.**
    The `Tracker` port gains booking a worklog and creating a story; at cleanup, a JIRA-keyed task's active minutes (EDI-54) are booked on its ticket, or the evergreen ticket for EDI keys (`machine.yaml` `tracker.evergreen.tooling`); a ticket proposal answered yes creates the story; no `jira-sync.sh` call remains in EDI's procedures; `docs/decisions/trackers-and-time-booking.md` gains a history line.
    - Against a stubbed JIRA, a cleaned-up task keyed on a tracker project books its active minutes once, with the EDI key in the comment for EDI work; a second cleanup attempt books nothing.
    - `learnings.md` lines 8 and 26 expire.
25. **Whether the wait is live, XS. Wait: none.**
    `edi health` and the status line say whether the primary's `edi wait` is live, from the same `BackgroundJob.anyWaitsForEdi` the stop hook reads.
    - With work in flight and no wait, `edi health` says so and the status line marks it; `learnings.md` line 24 expires.

### When to update EDI between steps

A merged step changes the running EDI only once `/update` has built, installed it and restarted the core (`claude/skills/update/SKILL.md`); workers keep running across it, and the store makes the restart a non-event.
The anchor, the skills and the primary's settings are read when a primary session starts, so a step that changes them needs a fresh `edi up` as well.

| After | Update | Why |
|---|---|---|
| steps 2 to 6 and 25 are merged | `/update` once for the batch | the guards, the status checks and the health line take effect for every later worker |
| step 8 is merged | `/update`, then a fresh primary session (`edi up`) | the persona line is in the anchor, and the reworded templates are what the primary reads |
| step 9 is merged | `/update` and a fresh primary session | the anchor is rewritten as an index |
| step 11 is merged | `/update` when no lifecycle step is between its done and its merge | the migration derives every open task's stage once at the core's start |
| step 18 is merged | `/update`, then the primary enters the plan's remaining steps with `edi plan add` | from then on, the core dispatches each remaining step itself when its wait is met |
| step 22 is merged | `/update` and a fresh primary session | the stop hook's rule and I-21's wording change together |

Other steps can wait for the next of these updates; none needs a restart of its own.

### The review after a week

The captain asked to "revise and introspect again after another week of the patches": a scout, a week after step 23 lands, re-runs the transcript measuring script kept on the captain's machine over that week's primary transcripts, reads the store, and compares:

| Measure | Baseline, 20 to 26/10 | Expected |
|---|---|---|
| reports pasted per working day | about 50 (207 in four days) | only turns with news for the captain |
| reports the stop hook demanded | 25, of which 13 of the 22 analysed were unchanged | none unchanged |
| decision zones identical to the one before | 95 of 206 | none |
| report length, median and longest | 35 and 97 lines | under 20 and 40 |
| primary messages naming EDI in the third person | 54 of 425 | none outside the project's name |
| tasks under Ongoing that are not ongoing | 3 of 9 at 19:30 on 26/10 | none |
| merges of a task still changing, without a question | 1 | none |
| merged tasks waiting on cleanup longer than a delivery round | one task since 16:38 on 26/10 | none without a decision |
| minutes from a slice's wait met to its dispatch | by hand | within one supervision round |
| effort words asked per plan | once per slice | once |
| learnings expired or unticketed; rules with more than one home | 10, 8; about 14 | 0, 0; 0 |
| refusals by each new guard | none exist | counted from the event log, each read for a false refusal |
| a done accepted while a steer waited unread | at least 1 | none |

The transcript measures come from that script; the store measures come from the `event` table (`StageChanged`, `CleanupRefused`, the guard refusals) and `edi memory stow-list`.


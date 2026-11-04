# EDI-52: the console sitrep page and week-one quality of life, carried over from the research

This is the research report behind the console plan: the sitrep page as the captain's main panel, and the fixes found in the first week of using EDI.
It was written on 26 October 2026 against the code as it stood then.
It authorises nothing by itself: each slice is a backlog item with its own file, and the captain's answers it records are restated there as requirements.
Section numbers follow the original report; its section 3, the sources and method, is left out because it names files only the captain's machine holds.
File paths and line numbers are those of the code at the time, so confirm each against the current code before relying on it.

## Where the slices stand

| Slice | Work key | Where it is now |
|---|---|---|
| 1, 2 and 3 | EDI-52, EDI-53, EDI-54 | merged |
| 4 to 13 | EDI-55 to EDI-64 | backlog |
| 14 | EDI-65 | backlog, folded into the lifecycle plan (EDI-74 step 15) |
| 15 | EDI-66 | backlog |
| 16 | EDI-67 | backlog, folded into the lifecycle plan (EDI-74 step 16) |
| 17 | EDI-68 | backlog |
| 18a | EDI-69 | backlog |
| 18b | EDI-70 | backlog, folded into the lifecycle plan (EDI-74 step 13) |
| 18c | EDI-71 | backlog, folded into the lifecycle plan (EDI-74 step 19) |
| 19 | EDI-72 | backlog, needs the captain's machine |
| 20 | EDI-73 | backlog, needs the captain's machine |
| outside EDI | none | the test command of a registered project, not part of this repository |

## 1. Summary

The sitrep page already holds almost everything the captain needs for one-click navigation; it simply does not render it.
The work key, the forge, the tracker's base address, the board link, the delivery, the pipeline run and the worker's pane are all in the store or the profiles, so links and a read-only worker check-in cost presenter and console work, not new data.
The most valuable fix is not on the page at all: a done can be accepted while a steer waits unread, and the page then shows a request as ready for review while it is still changing, which is how a request of a registered project was merged without its last commit on 26/10.
Quiet mode batches even the wakes the primary settles alone: 28 worker wakes this week waited 20 to 31 minutes each, 761 minutes in all.
EDI computes no active time, so the time booked on JIRA includes time spent waiting on the captain.
A command line in the page is four quite different things (a palette of verbs, an `edi` line, a line to the primary, or a real terminal); the captain chose none for now and keeps his input in Claude Code.
The captain chose a one-glance sitrep with link chips and a task panel that mirrors a worker's pane read-only, every week-one fix, and further views on top so the sitrep stays clean.
Section 9 orders the work into twenty small slices, merge safety first, each with its acceptance criteria; the captain gave his go for the plan (D42 A).

## 2. Decisions and answers

Round 1 on the research board, 26/10/2026, the captain's answers verbatim in the scout's inbox (message 1):

- Q1, decision D41: A, the one-glance sitrep plus a task panel; every row carries its link chips, and a task opens its panel over the right-hand columns at `/task/<id>`.
- Q2: links a to g on every row (work key to the tracker, request with state and pipeline step, branch, board kept after its round, report, decisions, check in); the brief only inside the task panel.
- Q3: A, the read-only terminal mirror first, next to the worker's status, model, effort, context and active time.
- Q4: none: a command line in the page is not wanted.
  Input stays in Claude Code in the terminal, and the page grows as a dashboard through which EDI and its workers show the captain what he follows up on.
- Q5: E, no command line yet; ship the links and the task panel and decide after a week of using them.
- Q6: all thirteen week-one fixes (F1 to F13).
- Q7: all five page ideas (tab title and notification, quiet and away buttons, usage meters, the refused-cleanup warning, the page's own health), with the requirement not to pollute the main dashboard: introduce buttons on top that work as tabs and open different views.

Round 2, 26/10/2026 (message 2):

- Q8: A, three views on top: Sitrep, Fleet (workers, the primary's health, usage) and Week (active time per task).
- Q9, decision D42: A, "Ship the whole plan, slices 1 to 19 in order, plus a registered project's test command as its own task"; this is the captain's go for the promotion.
- On the wallpaper: the captain has two concept arts for EDI on his own machine and allowed putting them in the repository as base art; the face shot would be wanted on a black background.
- The two images, `EDI-face.jpg` and `EDI.jpg` (2752 by 1536 pixels, each about 2.8 MB), are copied into the task directory's `art/` folder for the slice that adds them (slice 20).

From the captain's Q4 note: a sci-fi wallpaper of EDI behind the WezTerm session is a `background` layer with an image file in the captain's own `.wezterm.lua`, with an opacity or tint that keeps the text readable; the `.wezterm.lua` change stays outside EDI, and slice 20 supplies the picture.

## 4. What the page knows today

- `GET /api/sitrep` returns `ConsoleSitrep`, built by `web/console/SitrepConsolePresenter.java` from `application/fleet/FleetSnapshot`.
- An Ongoing row carries the task id, the work key, the title, the kind, the projects, a state word, a "doing" text and the delivery's URL; nothing else (`edi-console/src/app/console/sitrep.ts`, `OngoingTask`).
- The work key is printed but never linked: the tracker's address lives only in the machine profile (`tracker.baseUrl: https://tracker.example.test` on the laptop), and on the desktop there is no JIRA at all.
- The forge kind, host and path of every project are in `projects.yaml`, yet `edi project list` prints only names and key patterns, and nothing builds a branch link.
- A board's Lavish link (`board.link`) shows only while a round waits on the captain (`WaitingBoard`); once answered it disappears from the page.
- "Doing" is the worker's whole status line; this afternoon one row's text was 385 characters long.
- A request is "ready for your review" when the worker's done was accepted and the request is open (`FleetSnapshotQuery.readyForReviewAmong`, lines 67 to 80); a running pipeline or an unread steer does not count.
- At 17:40 the page showed EDI-51's pull request 47 as ready for review while its Ongoing row said "pipeline running" since 16:40.
- A finished scout sat under Ongoing with the state "done" after its decision was answered (finding 7).
- The console may do only three things as the captain: answer a decision, ask for a re-check, and set present or demo (`web/access/Caller.java`, `Caller.Captain`; `docs/decisions/api-access.md`).
- Quiet and away are deliberately chat-only (`docs/decisions/presence.md`, line 22).

## 5. Links: everything one click away

Every link below is derivable from data EDI already holds; none needs a worker to report anything new.

| Link | Source | Address | Per machine |
|---|---|---|---|
| Work key | `Intake.workKey`, the project's `tracker.kind`, the profile's `tracker.baseUrl` | `<baseUrl>/browse/<KEY>` for `jira`; none for `edi` and `none` | the laptop's JIRA only; on the desktop the key stays plain text |
| Merge or pull request | `Delivery.address`, `Delivery.state`, `PipelineRun.activeStep` | the delivery's own URL | both |
| Branch | the task's branch, the registry's `forge.kind`, `forge.host` and `forge.path` | GitHub `https://github.com/<path>/tree/<branch>`, GitLab `https://<host>/<path>/-/tree/<branch>` | both; none for a project without a forge |
| Board | `board.link` | the Lavish session URL | both, while the Lavish server runs |
| Report and brief | `<EDI home>/data/tasks/<task>/report.md` and `brief.md` | a new read-only core route rendering the Markdown, for example `/api/tasks/<task>/report` | both |
| Decisions | the decisions raised on the task | the task panel's Decisions tab, or the decision card | both |
| Check in | the task's latest worker | the task panel's Live tab, `/task/<task>` | both |

- The tracker's browse path is a JIRA convention (`/browse/<KEY>`); it belongs in the JIRA adapter, not the console, so a future tracker supplies its own.
- Landed rows gain the same chips, which keeps a merged request and its ticket reachable for the seven days the list covers.
- The presenter gains a `TaskLinks` value per row, built by one application service from the registry and the machine profile, so the text sitrep can print the same links later.

## 6. Checking in on a worker

### What exists

- `edi worker peek <task>` prints the visible lines of the worker's pane through `adapter/runtime/Tmux.visibleLines` (`tmux -L <socket> capture-pane -p`), on EDI's own socket, behind the `WorkerRuntime.peek` port.
- The worker's status line in that pane already shows model, effort, context used, session tokens and the five-hour and weekly usage.
- The store keeps the worker's status history, its inbox, its decisions and its timeline; `edi task show` prints them as text.

### Options for the live view

- A, the pane mirror: a server-sent event stream, opened only while a task panel is open, sends the pane captured with `capture-pane -p -e` (with its colours) about once a second; the page renders it read-only with xterm.js or plain styled text.
- B, the conversation view: the page renders the worker's Claude Code transcript (messages and tool calls) through `adapter/runtime/ClaudeTranscript`, which scrolls back further than a 50-line pane.
- C, both, as two tabs.
- A costs one port method (capture with escapes), one endpoint and one renderer; the existing SSE pattern (`web/console/ConsoleStream.java`) fits it.
- A pseudo-terminal attach (`tmux attach -r`) was rejected: it needs a native pseudo-terminal library in the native image for the same picture.

### What the panel shows beside it

- The worker's latest structured status, its model and effort, context used and tokens, the active time (finding 3), any steer still waiting, and the timeline, decisions, brief, report and board rounds of the task.
- There is never an input: a worker hears the captain only through `edi steer` and its inbox (`docs/decisions/steering.md`).

## 7. A command line in the page

### Security model today

- The core binds `127.0.0.1` only; the console signs in with a link that works once within two minutes, then holds a twelve-hour cookie, HttpOnly and SameSite=Strict (`web/access/ConsoleSessionCookie.java`, `domain/console/ConsoleSession`).
- Every console write must carry `X-EDI-Console: 1`, which no cross-site form can add (`web/access/ApiTokenFilter.java`, `isAConsoleChangeWithoutItsHeader`).
- `Caller.Captain` may read, answer decisions, ask for re-checks and set presence; every other act is refused with "is the primary's to run" (`Caller.requirePrimary`).

### The four shapes

- A, a command palette: `Ctrl K` jumps to any task, work key or D number, and offers a few verbs as small forms (steer, hold, release, queue a task, presence); each verb is its own endpoint with its own rule under `Caller.Captain`, recorded with the captain as actor.
- B, an `edi` line: the core parses `edi` commands typed on the page and answers with the same text presenters, without starting a process; the read-only set first (`task show`, `backlog`, `decision list`, `worker list`, `worker peek`, `sitrep`), writes only as A adds them.
- C, a line to the primary: the words are recorded as the captain's through the console, a wake hands them to the primary's next `edi wait`, and the primary's short reply returns to the page; anything the captain can say in chat works, and the primary still performs every act under its existing guard rules.
- D, a real terminal: xterm.js read-write on the primary's tmux pane over a WebSocket; full power, but a browser tab becomes a shell with bypass permissions, keys typed mid-turn race with Claude Code, and a WebSocket upgrade carries no custom header, so the console's CSRF defence does not cover it.
- The cheapest version worth having is the jump half of A: read-only, one slice, and a prerequisite for every other shape, since each needs a way to name a task.

The captain chose none of them for now (Q4, Q5 E): input stays in Claude Code, and the page grows as a dashboard through which EDI and its workers show him what he follows up on; the question returns after a week of the links and the task panel.

## 8. Week one: findings, ranked by value to the captain

Each finding gives what happened, the cause in the code, the fix and its acceptance criterion; the plan in section 9 orders them.

### F1. A request looked ready for review while it was still changing (merge risk)

- On one task (CET): steer 1 was acknowledged at 16:14; the request was recorded at 16:25; steer 2 (an amendment to the spec) was sent at 16:27 while the worker was mid-turn; "steer not received" fired at 16:32; the worker's done was accepted at 16:33:50; the stop hook delivered steer 2 at 16:33:56; the worker reported working again at 16:34:03, committed the amendment at 16:36:18; the captain merged the request at its old head at 16:38:37; cleanup was refused at 16:38:41 because that commit was not in the base branch.
- Evidence: the task's timeline and the store's `event` and `inbox_message` tables.
- Cause: `application/delivery/DoneClaims.verify` (lines 46 to 59), called from `application/worker/RecordStatus.report`, checks the copy, the snapshot versions and the request, never the inbox.
- Cause: a busy worker receives a steer only when its turn ends (`InboxNotice`, `application/worker/WorkerHooks.stopping`, lines 76 to 86), so a done reported inside that turn runs ahead of the steer.
- Cause: the page's review zone asks only for an accepted done and an open request (`FleetSnapshotQuery.readyForReviewAmong`), and an Ongoing row shows only the request number and a state word; nothing says the request is still changing.
- Fix F1a: refuse a done while the task's inbox holds an unacknowledged message, naming the message numbers.
- Acceptance F1a: with steer N sent and not acknowledged, `edi status done` is refused with a refusal naming message N and the worker's status is unchanged; after `edi inbox ack N` the same done is accepted.
- Fix F1b: a request is ready for review only when the worker's done stands, no pipeline run is running and no steer waits; otherwise the snapshot marks it changing, and the console, `edi sitrep` and `edi report` show "changing, hold the merge".
- Acceptance F1b: a snapshot test with an accepted done, an open request and a running pipeline run lists the request as changing and not as ready; the same with a sent, unacknowledged steer; with neither, it is ready.

### F2. Quiet mode holds the primary's own work for half an hour at a time (throughput)

- In quiet and away, every routine wake is batched for `presence.quietBatch` (30 minutes) (`docs/decisions/presence.md`, line 20; `WaitForWakes`), including the pipeline gates and worker questions the primary settles without the captain.
- Quiet starts by itself after 15 minutes without a typed prompt (`presence.autoQuietAfter`), so this applies most of a working afternoon.
- This week 150 worker wakes (needs-decision, done, blocked) waited 1 187 minutes in all between raise and delivery; 28 of them waited 20 to 31 minutes each, the batch's signature, 761 minutes together; `captain-effort-exception` parked three times on 26/10 (09:25, 10:36, 11:42), 26 to 30 minutes each (the `wake` table, `raised_at` to `delivered_at`).
- Fix: in quiet, deliver at once the wakes the primary can settle alone (pipeline gates, worker questions for the primary), with the instruction to settle them without chat text; batch only what would speak to the captain.
- Acceptance: with presence quiet, a pipeline gate wake is delivered to a parked `edi wait` within its long poll, its digest says to end the turn without text, and a decision wake for the captain still waits for the batch.

### F3. Time booked on JIRA includes waiting on the captain (the captain's hours)

- The `worked:` line is each worker's own wall-clock estimate (`application/brief/BriefSections`, gated by `time.requireWorkedLine`); the fleet ledger relays statuses only (`application/ledger/EmitFleetLedger`); `task_cost` holds tokens only.
- The event log has every status change with its time (this week 297 working, 86 needs-decision, 69 paused, 60 resolved, 54 done, 10 blocked), plus decisions raised and answered, holds and releases.
- Fix: a domain `WorkedTime` over a task's timeline that sums the stretches in working, each ending at the next non-working status, a decision raised, a hold or the worker's end; shown in `edi task show`, on the task panel, and written as a `task.worked` ledger record at cleanup for the timesheet tool.
- Acceptance: a timeline working 10:00 to 10:30, needs-decision until 11:00 and working until 11:15 gives 45 minutes; `edi task show` prints it; the ledger record carries it.
- Once it exists, the brief's own `worked:` line can quote EDI's number instead of the worker's estimate.

### F4. Parallel EDI pull requests conflict with each other (review load)

- On 23 and 26 October the captain asked for all of the open EDI pull requests to be put on one branch with no merge conflicts (EDI-47 and EDI-48), because one of them conflicted and the others probably would too; the closed pull requests 35, 37 and 38 kept showing as waiting on him.
- The primary also sends "main has moved, merge origin/main" steers by hand.
- Fix: after EDI follows a merge, it steers every other in-flight task of that project with an open request to merge the base and re-run its pipeline, and a closed request leaves the review zone at once.
- Acceptance: after a followed merge on project P, each other task of P with an open request receives one steer naming the merged request; a task whose request was closed no longer appears under "needs your decision".

### F5. Halting for the night and resuming in the morning is by hand (daily ritual)

- The captain's own requests set the daily ritual: on 21 and 22 October he asked to halt every worker and stow before putting the laptop to sleep or wrapping up, and on 23 and 26 October he asked to restart all the halted workers from the evening before.
- Seven workers stopped on 21/10 were resumed at 08:54 to 08:58 the next morning; a worker that died on Friday 23/10 at 16:54 was noticed on Monday at 08:15.
- Fix: `edi park` stops every worker cleanly and records the set; `edi resume` relaunches exactly that set; the sitrep shows "parked for the night" with the count until resumed.
- Acceptance: after `edi park`, `edi worker list` shows every worker stopped by EDI and the sitrep shows the parked block; `edi resume` relaunches each of them with its recorded session and clears the block.

### F6. A steer waits until the worker's turn ends (latency)

- Outside the core outages fixed in EDI-38, four steers took 46, 69, 50 and 96 minutes to arrive; there were 16 "steer not received" alarms this week.
- `WorkerHooks` answers only session start, prompt, stop, stop failure, notification and session end; there is no tool-call hook.
- Idea, to verify in a spike first: also deliver a waiting steer at the worker's next tool call, through a `PostToolUse` hook's added context, and record receipt then.
- Acceptance for the spike: a worker in a long turn receives a steer within one tool call of it being sent, in a real Claude Code session on a private tmux socket, and the steer's receipt is recorded.

### F7. A finished scout came back as Ongoing (clarity)

- D38 on a finished scout was raised at 14:00 (the duplicate of D37 from re-arming the board), the scout's done was accepted at 15:35, and the answer at 16:01 put it under Ongoing with the state "done".
- Cause: `AnswerDecision.answer` calls `Task.decisionAnswered` (`domain/task/Task.java`, lines 64 to 71), which lifts the hold of any task that is not a captain task; `FleetSnapshotQuery.ongoing` lists every unheld in-flight task.
- PR pull request 47 (EDI-51) refuses re-asking an answered card, which removes the trigger, not the path.
- Fix: a task whose latest worker's done stands and that has no open request shows as "report ready" (a scout) or "landed, cleaning up" (a ship), not as ongoing.
- Acceptance: a snapshot test with a scout whose done was accepted and whose decision was then answered has no Ongoing entry and one "report ready" entry.

### F8. A board round cannot be read without consuming it (trust)

- A round's full text exists only as an inbox message to the scout (`application/board/ReceiveBoardFeedback`, line 80); `board_answer` keeps only answers to bound decisions, and the `BoardAnswered` event only counts.
- `GET /api/inbox` requires a worker and marks what it returns as received (`application/worker/WorkerInbox`, lines 42 to 47 and 66 to 73); running `lavish-axi poll` takes over EDI's listener (`adapter/board/LavishBoardHost`, line 45).
- Two steers on one scout existed only to ask whether every answer to Q1 to Q17 had arrived.
- Fix: `edi board show <task> [--round N]`, read-only for the primary, printing each round's prompts and the bound answers with their settlement; the task panel shows the same.
- Acceptance: after a round arrives, `edi board show` prints every prompt, the message's `received_at` stays empty, no `SteerReceived` is recorded, and the scout's `edi inbox` still delivers it.

### F9. Quiet on silence cannot be switched off (comfort)

- `PresenceSettings` rejects a zero or negative `autoQuietAfter` (lines 16 to 18); a demo holds present for three hours at most (`ChangePresence`, line 21).
- Fix: accept `PT0S` as "never quiet on silence"; meetings still turn presence quiet.
- Acceptance: with `autoQuietAfter: PT0S`, presence stays present after eight hours without a prompt, and a meeting in progress still turns it quiet.

### F10. GitHub Actions has refused every job since 22/10, and every EDI pipeline still parks on it (friction)

- D13 B accepts local verification instead, yet each run waits at its CI gate and ends "passed with override" (pipelines of at39-step2, primary-after-core-restart, context-exhausted-worker); Dependency-Check cannot run locally either.
- Fix: a standing per-project note, set and lifted on the captain's word, that CI is unavailable and local verification counts; the pipeline gate reads it instead of asking each run.
- Acceptance: with the note set for EDI, a pipeline's CI gate passes with the note named in its result and no wake for the primary; once lifted, the gate asks again.

### F11. A squash-merged request can fail cleanup (edge case)

- Not real as stated in the brief: cleanup already compares by patch identity with `git cherry -v` (`adapter/git/Git.commitsBeyond`, lines 164 to 169) and by content with `merge-tree --write-tree` (lines 171 to 174), since EDI-7.
- The three refusals this week were right: two were refused before their requests merged, and the third held a commit that was really unlanded (F1).
- The gap left: a squash merge matches no commit by patch identity and relies on the content check, which fails once the base has changed the same lines again; `CleanUpTask.requireLanded` (lines 116 to 123) never consults the forge's merged state.
- Fix: when the delivery is merged, judge only the commits after the delivered head (`Delivery.headCommit`) and count everything up to it as landed.
- Acceptance: a squash-merged request cleans up although the base later rewrote the same lines; a commit added after the delivered head is still refused and named.

### F12. The word for effort above high is per task, so a plan needs it once per slice (friction)

- Since EDI-49 the word is held for one task, one act and the current exchange (`domain/authority/Authorisation`, `CaptainsAuthority.saidForActOnTask`, `DispatchTask`, lines 95 to 99; `docs/decisions/captains-word.md`, line 18); `edi captain said` takes a single `--task`.
- Fix: `--task` may repeat, so one sentence for `effort-above-high` records one authorisation per named task; the captains-word record gains a history line.
- Acceptance: `edi captain said --task a --task b --for effort-above-high --text "..."` lets `a` and `b` dispatch at xhigh, and a third task is still refused.

### F13. The test step's 30-minute budget belongs to no-mistakes, not EDI (configuration)

- no-mistakes defaults `test_agent_timeout` to 30 minutes; `~/.no-mistakes/config.yaml` does not set it; its `repository_overrides` accept only `commit` and `pr`.
- A registered project has no `.no-mistakes.yaml` test command, so an agent runs the suite inside that budget; a slice noted that the test step was approved on the evidence after the test agent ran out of its 30-minute budget.
- Fix, outside EDI: a registered project `.no-mistakes.yaml` with a test command, checked on one run to confirm a command is free of the agent budget; or a raised global timeout.
- Acceptance: a registered project full-suite pipeline run finishes its test step without the budget finding.

### Not real: blocked-by waits for done, not merged

- `domain/task/Blocker.isResolved` means done, and a task becomes done only through `Task.cleanedUp` after `CleanUpTask.requireLanded`, or through a captain task's decision; done therefore means landed in the base, which is stricter than merged.
- Dependants are released in the same transaction as the cleanup (`CleanUpTask`, line 136), and `FollowMerges` cleans up as soon as a merge is followed.
- The real stall is a refused cleanup, which F11 and the refused-cleanup warning (plan slice 14) address.

### Smaller items, not planned in this round

- JIRA story files are still pushed by hand after a merge.
- A dead worker's window was sometimes noticed only at its merge, hours later (at39-latency-flake, presence-slash-commands).
- A board or console answer stops counting as the captain's word after a primary restart (noted in the board-go-counts report).

### Already fixed this week, not reproposed

Ready pull requests refused as not drafts, pipeline commits without the key (EDI-21), decision-log conflicts (EDI-42), a go given on the console or a board (EDI-40), boards and blocked decisions missing from the report (EDI-24), the Outlook calendar (EDI-34), the presence slash commands (EDI-32), the update restart race (EDI-36), workers stalling after a core outage (EDI-38), context exhaustion (EDI-35), boot-clock drift (EDI-39), Zulu times (EDI-33), the sitrep layout and opening (EDI-27, EDI-28, EDI-43, EDI-44), operations tasks without a request (EDI-41), tests flaky under load (EDI-29, EDI-37, EDI-45), the pipeline reviewer without the spec (EDI-30), pipeline routing (EDI-46), effort above high (EDI-49); the doubled work key is EDI-50, queued.


## 9. Plan

An ordered plan of small EDI changes, each its own task and pull request on `feature/EDI-<n>`, each shippable on its own; the order puts merge safety first, then the captain's hours and the primary's pace, then the main panel, then the remaining fixes.
Each slice names what it changes, the records it touches, and its acceptance criteria; every slice also passes `./mvnw verify`, and a slice that touches Spring configuration, resources or the command passes `./mvnw -Pnative verify`.
Sizes: XS under two hours, S about half a day, M about a day.

### Merge safety

1. **A done waits for its steers (F1a), S.**
   `DoneClaims.verify` (or `RecordStatus.report` before it) refuses a done while the task's inbox holds an unacknowledged message, naming the message numbers in the refusal.
   - With steer N sent and not acknowledged, `edi status done` is refused, the refusal names message N, and the worker's last status is unchanged.
   - After `edi inbox ack N`, the same done is accepted.
   - A scout's done follows the same rule.
2. **"Changing" before "ready for review" (F1b), S.**
   `FleetSnapshotQuery` puts a request in the review zone only when the worker's done stands, no pipeline run is running and no steer waits unacknowledged; otherwise the snapshot marks the delivery changing.
   `edi sitrep`, `edi report` and the console show "changing, hold the merge" with the reason (pipeline step, steer number); `docs/decisions/fleet-snapshot.md` gains a history line.
   - A snapshot test with an accepted done, an open request and a running pipeline run lists the request as changing, not ready.
   - The same with a sent, unacknowledged steer; with neither, the request is ready.
   - The console shows a red "changing" badge and the reason in place of the amber "review" badge.

### The captain's hours and the primary's pace

3. **Active minutes per task (F3), S.**
   A domain `WorkedTime` over a task's timeline sums the stretches in working (and resolved), each ending at the next non-working status, a decision raised, a hold or the worker's session end; `edi task show` prints "active <h>h<mm>m of <h>h<mm>m"; cleanup writes a `task.worked` record to the fleet ledger; `docs/decisions/trackers-and-time-booking.md` gains a history line.
   - A timeline working 10:00 to 10:30, needs-decision until 11:00 and working until 11:15 gives 45 minutes.
   - `edi task show` prints the active and wall-clock time; the ledger record carries the active minutes and the work key.
   - The prototype over 26/10's event log gives, for example, 139 active of 191 minutes for one scout, against the worker's own "2h40m".
4. **Quiet delivers the primary's own notifications at once (F2), S.**
   In quiet and away, `WaitForWakes` delivers at once the wakes the primary settles without the captain (pipeline gates, a worker's question for the primary, a done to accept), with the digest telling the primary to end its turn without chat text; wakes that would speak to the captain keep the 30-minute batch; `docs/decisions/presence.md` gains a history line.
   - With presence quiet, a pipeline-gate wake reaches a parked `edi wait` within its long poll.
   - A decision raised for the captain still waits for the batch.
   - AT-46 still holds.
5. **`autoQuietAfter` accepts `PT0S` (F9), XS.**
   `PresenceSettings` accepts zero as "never quiet on silence", and `FollowThePresence` skips the silence rule then; `docs/configuration.md` and `docs/decisions/presence.md` describe it.
   - With `autoQuietAfter: PT0S`, presence stays present after eight hours without a typed prompt.
   - A meeting in progress still turns it quiet.

### The main panel

6. **Link chips on every row (Q2), M.**
   An application service `TaskLinks` builds, per task, the tracker link (through the `Tracker` port, so JIRA adds `/browse/<KEY>` and the EDI-native and no-tracker adapters give none), the request with its state and pipeline step, the branch on the forge (from the registry's `forge`), the board link kept after its round, the report, and the decisions; `ConsoleSitrep` carries them on decision cards, waiting, ongoing, charted and landed rows; a read-only route renders `report.md` and `brief.md` as HTML under the console session.
   - On the laptop, an Ongoing row of a task on a JIRA-tracked project links its key to `https://tracker.example.test/browse/<KEY>`; an EDI row shows its key as plain text.
   - A GitLab project's branch chip opens `https://<host>/<path>/-/tree/<branch>`, a GitHub project's `https://github.com/<path>/tree/<branch>`.
   - A board's chip remains after its round is answered; the report chip renders the task's `report.md`, and a task without one shows no chip.
   - The page still fits 1920 by 1080 at 100% and 125% (`docs/decisions/console-look.md`).
7. **View buttons in the header (Q7 note, Q8), S.**
   The header gains buttons that work as tabs for the views the captain chose in Q8, each with its own route; the Sitrep button carries the count of what waits on him; the sitrep page itself is unchanged.
   - Each view is reachable by its address and by its button, and the browser's back button returns to the previous view.
   - The Sitrep button shows the waiting count from any view.
8. **The task panel (D41), M.**
   `/task/<id>` opens a panel over the current view with the task's facts and chips, and tabs for timeline, decisions, brief, report and board rounds; Esc and a close control return to the view; a new `GET /api/tasks/<id>` returns the detail `edi task show --json` already assembles.
   - Clicking a row's title opens its panel; the address is shareable within the console session and survives a reload.
   - The decisions column stays visible beside the panel from 1200 pixels wide; below that the panel takes the page.
   - The panel has no control that changes the fleet.
9. **Live check-in (Q3 A), M.**
   `WorkerRuntime` gains a capture with colours (`capture-pane -p -e`); a stream opened only while a panel's Live tab is open sends the pane about once a second and stops when the tab closes; the console renders it read-only beside the worker's model, effort, context, tokens, active time and any waiting steer.
   - The Live tab shows the same lines as `edi worker peek <task>`, with colours, within two seconds of a change in the pane.
   - Closing the tab ends the capture; no capture runs with no panel open.
   - The page has no input that reaches a worker's pane, and the stream refuses any caller but the captain's console session and the primary.
10. **The Fleet view (Q7 c, e), M.**
    Worker tiles with state, model, effort, context and the last lines of the pane; a health strip with whether the primary session is live and its `edi wait` runs, and the five-hour and weekly use with their resets, taken from the latest status-line reading the core already parses (`StatusLinePayload.sessionIn`, `rate_limits.five_hour` and `seven_day`), which the core starts keeping.
    - A worker above 80% context shows its context in amber.
    - With no `edi wait` live while a task is in flight, the strip says so in red.
    - The usage meters show the reading's time and turn grey when it is older than ten minutes.
11. **The Week view (F3), S.**
    Active and wall-clock time per task per day from slice 3, with the work key and a control that copies a task's line for the timesheet tool.
    - The figures equal `edi task show` for the same task and day.
    - Time on an EDI key shows against the evergreen tooling ticket the ledger books it on.
12. **Tab title and browser notification (Q7 a), S.**
    The document title reads "(N) SITREP · EDI" while N things wait on the captain, and a browser notification announces a new decision or review while the page is open and the captain allowed notifications.
    - A new decision raises one notification and the title count rises; answering it lowers the count.
    - Without the browser's permission, nothing is asked twice and nothing fails.
13. **Quiet and away buttons (Q7 b), S.**
    The presence control gains Quiet and Away, away asking for its mandate in a small form; `docs/decisions/presence.md` records that the console may now set them.
    - Quiet from the console sets quiet with the captain as cause; Away requires a mandate text and records it.
    - The return brief still comes at the captain's next typed prompt.

### Week-one fixes

14. **Report ready, not ongoing, and the refused cleanup shown (F7, Q7 d), S.**
    `FleetSnapshotQuery` lists a task whose latest worker's done stands and that has no open request as "report ready" (scout) or "landed, cleaning up" (ship); a task whose request merged but whose cleanup was refused shows in red under Ongoing with the refusal's reason and the commits that block it.
    - A scout with an accepted done and a later answered decision has no Ongoing entry and one "report ready" entry.
    - After a refused cleanup, the row names the unlanded commits, as `edi cleanup` printed them.
15. **`edi board show` (F8), S.**
    A read-only primary command and endpoint print each round's prompts and the bound answers with their settlement, without touching the inbox; the task panel's board tab shows the same.
    - After a round arrives, `edi board show <task>` prints every prompt; the message's `received_at` stays empty and no `SteerReceived` is recorded; the scout's `edi inbox` still delivers it.
16. **Open requests stay mergeable (F4), M.**
    After a followed merge, EDI steers every other in-flight task of the same project with an open request to merge the base and re-run its pipeline; a closed request leaves the review zone at once.
    - After a followed merge on project P, each other task of P with an open request receives exactly one steer naming the merged request.
    - A task whose request was closed no longer appears under "needs your decision".
17. **`edi park` and `edi resume` (F5), M.**
    `edi park` stops every running worker through the existing stop and records the set; `edi resume` relaunches exactly that set with their recorded sessions; the sitrep shows "parked for the night" with the count until resumed; `docs/decisions/recovery.md` gains a history line.
    - After `edi park`, `edi worker list` shows each worker stopped by EDI and the sitrep shows the parked block.
    - `edi resume` relaunches each of them, clears the block, and leaves a worker that was already stopped before the park untouched.
18. **Three small fixes, one slice each, S.**
    - 18a, a standing CI note (F10): `edi captain said` can record, per project, that CI is unavailable and local verification counts; the pipeline gate reads it until it is lifted.
      - Acceptance: with the note set for EDI, a pipeline's CI gate passes naming the note and wakes nobody; once lifted, the gate asks again.
    - 18b, squash-merge cleanup (F11): when the delivery is merged, `CleanUpTask.requireLanded` judges only the commits after `Delivery.headCommit`.
      - Acceptance: a squash-merged request cleans up although the base later rewrote the same lines; a commit after the delivered head is still refused and named.
    - 18c, one effort word for several tasks (F12): `edi captain said --task` may repeat, recording one authorisation per named task; `docs/decisions/captains-word.md` gains a history line.
      - Acceptance: `--task a --task b --for effort-above-high` lets both dispatch at xhigh, and a third task is still refused.
19. **Spike: a steer at the next tool call (F6).**
    In a throwaway branch, a worker hook on `PostToolUse` returns a waiting steer as added context and records its receipt; the spike's report says whether Claude Code acts on it mid-turn and whether the steering record should change.
    - In a real Claude Code session on a private tmux socket, a steer sent during a long turn is received within one tool call.
    - The spike changes no production behaviour; a follow-up slice is proposed only if it works.

### Base art, asked on the board after the go

20. **EDI's concept art in the repository, S.**
    The captain's two concept images go into the repository as base art for later use (the console, the README, the wallpaper), from the task directory's `art/` folder, under a folder such as `docs/art/` with a short README naming what each image is; a third image is the face shot cut out on a pure black background, at a size fit for a terminal wallpaper.
    - The originals are committed as they are, or re-encoded without visible loss if their size matters to the repository; the README states their source as the captain's concept art.
    - The black-background face image has no trace of the bridge behind it at the edges of the hair and the shoulders, and reads well behind light terminal text at 1920 by 1080.
    - `./mvnw verify` and the publish check pass; no tracked file names a person, an employer or a machine path.
    - The captain's own `.wezterm.lua` is not touched; the slice's report gives the `background` snippet he can paste.

### Outside EDI

- A task on that project gives it a `.no-mistakes.yaml` test command (F13), checked on one full pipeline run to confirm the test step no longer hits the 30-minute agent budget; if a command is still bound by it, the global `test_agent_timeout` is raised instead.

### Not planned

- A command line in the page, by the captain's Q4 and Q5; to revisit after a week of the links and the task panel.
- JIRA story upkeep after a merge, late notice of a dead worker's window, and a board answer counting after a primary restart (section 8, smaller items).



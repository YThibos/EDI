# EDI-105: EDI's own validation pipeline, revised on the task lifecycle

This is the research report for the question whether EDI should run its own validation pipeline instead of driving the external no-mistakes tool, and how.
It was first written on 27 October 2026, the captain answered its board on 29 October 2026, and EDI-105 revised its design on 24 November 2026 on the stored task stage, the plans and the work merged or in review since.
The report authorises nothing by itself: the captain's answers do, and the revision's own questions are on its research board.
The measurements are those of 21 to 27 October 2026 and were not taken again: the revision read the code, the records and the backlog, and no new numbers are extrapolated from the old ones.
Work projects other than EDI are named generically, and the run identifiers are those of the pipeline tool's state on the captain's machine.
Paths and line numbers in the sections carried over from the first research are those of the code at the time; the revised sections cite the code of `origin/main` at `f1a7c61f` and the branches in review on 24 November 2026.

## The answer in short

Yes, EDI runs its own validation pipeline, built in slices and cut over one project at a time after a shadow period: the captain chose it on 29 October 2026 (D47 A), and that answer and the four design answers stand as requirements.
Every failure mode measured since 21/10/2026 comes from a part of no-mistakes that EDI either does not need (the gate mirror, the rebase step, the daemon's long-lived CI monitor) or already does better itself (limit-aware resumption, account-bound forge calls, restart-safe state).
The revision places the validation on the task's stored stage: a validation of record starting is the CHANGING trigger "a pipeline run starts", a running one refuses the done, its passing is the READY guard's "latest run passed", and the head it publishes is the head landing compares with.
Much of what the first design had to build now exists: the stage, the holds as a set, plans that dispatch themselves, the checks on a request's head, the request-text check, the review records and the rule that full builds run in CI.
What remains is the validation record, the reviewer, the worker's answers, the publication on the bound account, the ready mark and the cut-over, and the revision adds what the captain asked for since: CI as the full build, a conflict-fix scope, no cap on fix rounds, and request texts that EDI composes and checks.
The running cost estimate stands at about 48% fewer pipeline tokens against the measured baseline, and the build at about 75M to 95M weighted tokens over eight slices.

## What changed since the first research

Every point below is read from its source on 24 November 2026.

- EDI's GitHub Actions run on every pull request: the JVM build and tests, the native build and smoke test, Dependency-Check, npm audit, the SonarQube quality gate, the secret and personal-material scan and CodeQL (`.github/workflows/ci.yml`; `gh pr view 333 --json statusCheckRollup` lists ten passed checks).
  Failure mode 3, the CI step waiting on Actions that never start, no longer applies to EDI; its measurement stays as the evidence it was.
- Full builds run in CI where the project's CI runs, and locally only as the fallback (EDI-251, merged): the brief leaves the full build to CI (`BriefSections.fullBuildLeftToCi`), and EDI's own `.no-mistakes.yaml` test step runs the fast suite, `./mvnw -B -ntp test -Pfast`, since 10 November 2026 (`git log -- .no-mistakes.yaml`, commit `36066315`).
  So the test step that cost 45% of the pipeline's tokens in October already runs a smaller suite on EDI, by an amount nobody has measured.
- The task's stage is stored (EDI-85, merged 12 November 2026), plans and their waits are stored (EDI-88, merged 13 November 2026), and review rounds and findings are recorded per task with its route (EDI-97, merged 23 November 2026, pull request 311).
- A request merged into a branch other than the base keeps its task in flight, and a request names the branch it is stacked on (`Delivery.stackedOn`, EDI-244, merged).
- A request's title and description pass the publish guard before EDI posts or accepts them, and a ship's done is refused while they fail it (`PublishCheck.checkText`, `DoneClaims`, EDI-150 and EDI-237, [publish-guard](../decisions/publish-guard.md)).
- EDI-69, a standing CI note per project, was dropped on 3 November 2026, so the registry's `ciRuns` is the one setting that says whether a project's CI runs (issue 92, context).
- EDI-157 was folded into EDI-86: a request watched by its pipeline after it reached CI-ready, at the forge's head with green checks, reads READY, not CHANGING (issue 138).
- A running ship's decisions are gathered into five-minute decision rounds (EDI-96, merged), and the sitrep shows what needs the captain as one list he ticks off (EDI-330, in review, pull request 334).
- The captain's requirements of 2 and 24 November 2026, under "Requirements since the first round" below.

## How EDI uses no-mistakes today

### The brief

- A `draft-review` ship's brief tells the worker to commit, then run `no-mistakes axi run --base-branch <base> --intent "$(cat '<task>/intent.md')"` from the isolated copy (`edi-core/src/main/java/io/github/ythibos/edi/application/brief/BriefSections.java`, `draftReview`).
- The worker answers every gate itself and never passes `--yes`; an `ask-user` finding becomes `edi status needs-decision --key nm-<run>-<step>` and the worker stops.
- The pipeline pushes the branch and opens the draft; the worker marks it ready once checks are green, and its done needs the merge request open at its last commit (`shipDone`).
- `edi deliver <task>` opens the draft under the project's bound account when the pipeline could not (`application/delivery/DeliverDraft.java`).
- Since EDI-104 the call also passes `--skip` with the steps `ProjectEntry.skippedPipelineSteps` names, and the brief has the worker merge `origin/<base>` before each run ([pipeline-steps](../decisions/pipeline-steps.md)).

### Intent

- EDI writes the intent itself from the captain's intent, the spec, a `## Commits` section and the craft rules (`domain/delivery/PipelineIntent.java`), and rewrites it when a task is noted.
- No-mistakes' transcript-based intent extraction is therefore never used: every run's `intent.log` reads `using intent supplied by the agent` (for example in run `01M5YES29XEK3GD0E8AW22NMDK`).

### Configuration EDI writes into no-mistakes

- `agent_args_override.claude` from the dispatch rules' `pipeline` entry at every launch (`docs/decisions/pipeline-agent.md`, `adapter/pipeline/AgentArguments.java`).
- `repository_overrides.<remote>.commit` and `.pr` keys at every draft-review launch (`docs/decisions/pipeline-commit-keys.md`, `adapter/pipeline/CommitKeys.java`).
- Both go through `adapter/pipeline/PipelineConfiguration.java`, which rewrites one top-level entry of `~/.no-mistakes/config.yaml` in place.
- The EDI repository also carries `.no-mistakes.yaml` with the test command, `document.instructions` and `review.path_instructions` for `docs/decisions/**` and `*.java` (`git show origin/main:.no-mistakes.yaml`); its test command was `./mvnw -B -ntp verify` in October and is `./mvnw -B -ntp test -Pfast` since EDI-251.

### What EDI reads back

- `ObservePipelines` runs `no-mistakes axi status` in each live copy every 60 seconds and parses its `run:` block (`adapter/pipeline/AxiStatus.java`, `docs/decisions/supervision.md`).
- `PipelineSpend` and `PipelineReviews` open `~/.no-mistakes/state.sqlite` read-only for token spend and review rounds (`adapter/pipeline/PipelineSpend.java`, `PipelineReviews.java`).
- The `Pipeline` port (`application/port/Pipeline.java`) has five operations: `latestRunOf`, `tokensSpentOn`, `reviewOf`, `runAgentOn`, `keyCommitsPushedTo`.
- Since EDI-97, `CleanUpTask` stores the run's review rounds and findings (`PipelineReview`) through `PipelineReviewStore` with the task's cost, and the Week view shows the yield per effort (`EffortYield`).

### Skills

- The user-level `no-mistakes` skill (`~/.claude/skills/no-mistakes/SKILL.md`, 394 lines) is installed by `no-mistakes init` and teaches any session to drive `axi run`, `axi respond` and `axi sync`.
- EDI's own `claude/skills/edi-ship/SKILL.md` says the worker runs the pipeline and answers its gates, and that the primary never answers a worker's gate.

### The gate mirror

- `no-mistakes init` adds a `no-mistakes` remote pointing at a bare repository under `~/.no-mistakes/repos/<id>.git`; a push to it triggers a run in a disposable worktree under `~/.no-mistakes/worktrees/` (no-mistakes `docs/src/content/docs/concepts/gate-model.md`).
- The gate keeps a private mirror ref per branch and refuses a new submission when that ref holds content the new head lacks ("private mirror reconciliation", the same document).

### Gates, fix rounds and evidence

- The fixed order is `intent -> rebase -> review -> test -> document -> lint -> push -> pr -> ci`.
- `auto_fix` in `~/.no-mistakes/config.yaml` allows 3 attempts for rebase, lint, test, document and ci, and 0 for review, so every review finding parks at a gate for the worker.
- Each fix is a fresh `claude -p` agent session with its own cold cache; `session_reuse: true` keeps one reviewer session per run.
- Test evidence (screenshots, logs) stays in a temporary directory; `store_in_repo` is off.
- In October, on EDI, the PR body's local verification was the evidence the captain relied on, because GitHub Actions did not run; since then Actions run on every EDI pull request.

## Today's failure modes, with evidence

These are the failure modes measured from 21 to 27 October 2026, kept as they were found.

### 1. Rebase, then a failed fix agent, strands the gate (EDI-102 and EDI-79)

- Run `01M5YES29XEK3GD0E8AW22NMDK` (EDI-102) rebased `ef30a66` onto main as `937f9d4` (`~/.no-mistakes/logs/01M5YES29XEK3GD0E8AW22NMDK/rebase.log`: "updated head SHA to 937f9d460d8b").
- Its review fix agent then stopped with "You've hit your session limit · resets 12:20pm (Europe/Brussels)" and the run failed at 12:03 (`review.log` in the same directory, `daemon.log` line 4866).
- Run `01M5YF3T1PART5Z8F3PP7KGJ82` (EDI-79) failed the same way at 12:03, and another registered project's `01M5YEKRFVWMFG7G19JCMRVCYY` a few seconds later.
- The workers' next `axi run` was refused: "refusing to reconcile private mirror ref refs/heads/feature/EDI-102: 1 at-risk commit(s) contain content absent from live head a58a7f1: ef30a66" (EDI store, event 2982; EDI-79's equivalent is event 2985).
- `axi sync --check`, `--recover --keep-local` and `--bind-archive-ref` changed nothing, so both workers blocked at 12:25 and stayed blocked until the captain had the gate ref moved by hand at 14:56 (events 2982, 2985, 3089, 3091; times are local, the store keeps UTC).
- Cost: two ships stalled for 2h31 each, two captain decisions, a hand force-push on the local gate, and two archive branches left behind (`archive/EDI-102-ef30a66`, `archive/EDI-79-9112ecf`).

### 2. Fix and step agents exit on usage limits, and the run fails instead of pausing

- All nine `claude exited` failures since 21/10 are session limits, found by `grep "hit your session limit" ~/.no-mistakes/logs/<run>/*.log`: `01M5KWYTHVP9C61KSMZA614VNA`, `01M5M0MY8M19EDDNQFT1571KX8`, `01M5M0PBBPG07MB98BW3QDYC33` (23/10), `01M5WSNCSVNN6HGE36FTCC9YSK`, `01M5WSX86ZZ8JNB70ATGX5RB5K`, `01M5WSZMTFB3DNA25S9TEPWZB4` (26/10), and the three runs of 1 above (27/10).
- The 26/10 runs hit the limit before 23:10 and were marked failed only at 07:24 the next morning, so the overnight window was lost.
- EDI already detects a worker's limit stop and resumes it at the reset (`WorkerRuntime.usageLimitStopIn`, `docs/decisions/supervision.md`); the pipeline's agents are outside that supervision.

### 3. The CI step waits on GitHub Actions that never run

- 40 of the 67 `ci` steps on EDI since 21/10 carry findings that name the billing block ("The job was not started because recent account payments have failed or your spending limit needs to be increased").
- Each such step parks six `ask-user` findings that the worker must approve under the captain's standing rule, and its agent spent 8.8M weighted tokens over 67 calls diagnosing jobs that never started (the pipeline-drive log of EDI-79).
- Since then EDI's Actions run (see "What changed since the first research"), and EDI-104 skips the `ci` step on a project whose registry entry says `ciRuns: false`.

### 4. The CI monitor rebases an open pull request

- `ci_timeout: "168h"` keeps a monitor on each open pull request, which "auto-rebases when the base branch advances" (`~/.no-mistakes/config.yaml`).
- On EDI-99's run `01M5YX8HDME1K6W9TGPQT2TMRQ` the monitor found "PR has merge conflicts with the base branch" and its agent reported "Rebase is clean" (`~/.no-mistakes/logs/01M5YX8HDME1K6W9TGPQT2TMRQ/ci.log`), rewriting commits already published on pull request 62.
- The captain asked for merges, never rebases, of a worker's commits (EDI-104's captain's words).

### 5. A daemon restart interrupts the CI watch, and crashes fail runs

- Run `01M5YDHT1RQ1TDKAVHPR7S2YQ4` (chore/EDI-103, pull request 55) ended `ci_monitor_interrupted`: "ci monitor interrupted by daemon restart; PR remains open", and the daemon logged "active run cannot be safely resumed" at 11:19:59 (`daemon.log` line 4811).
- A registered project's `01M5WT412JVRCH5640ZT9VCHK6` ended the same way on 27/10 at 07:20.
- Four runs failed with "daemon crashed during execution" or "daemon shutting down" since 21/10 (`01M5F7BP38BEN6DEKM3ZEX9TQG`, `01M5JCRTZ2RBCEH0W5MD97AZ6J`, `01M5YBXYS41891W7QGYQCKEVJM`, `01M5YCWQ8G2CJGWX4M6H86CM22`).

### 6. Pushes use whichever gh account is active

- The daemon pushes from its mirror with `gh auth git-credential`, which answers with the active account, so EDI's account binding does not cover the pipeline's pushes (`docs/verification/no-mistakes.md`, "Account binding").

### 7. EDI's shortcut, EDI-104, and what it leaves

- EDI-104 (task `the-validation-pipeline-merges-the-base`, then in flight, since merged as [pipeline-steps](../decisions/pipeline-steps.md)) passes `--skip rebase` on every run, also skips `ci` on projects whose CI does not run, and has the worker merge `origin/<base>` with a merge commit before each run.
- It removes failure modes 1 and 3 for EDI and the rebase half of 4 for the step itself.
- It leaves 2 (limit exits still fail runs and force a full rerun), 5 (the daemon's monitor still dies with the daemon), 6 (the account gap), the CI monitor's own rebase on a project whose CI does run, and EDI's two writes into another tool's configuration.

### 8. Found on 24 November 2026: the request step writes what no check reads

These two findings are the captain's evidence of 24 November 2026, on issue 91; they were not measured over a window.

- A merged merge request of a project carried a "Generated with" line naming the coding assistant at the end of its description: the pipeline's request step ran its own agent in a second run after a late scope change, and that agent rewrote the description under the assistant's default attribution, while other requests of the same day came out clean.
- The pipeline copies the task's notes and the dispatch spec into a request's description as its intent, so two merge requests of a project carried paths into the primary's home directory, the word "brief", and decision numbers and board question codes; the primary removed the paths by hand.
- EDI checks a request's text when `edi deliver` posts it and when a done is accepted (`DoneClaims`), but it does not read the text again after the pipeline changes it, and its text check knows no attribution line ([publish-guard](../decisions/publish-guard.md); `AgentAttribution` guards only the drafts `DraftProposal` builds and the commit hooks).

### 9. Found on 24 November 2026: the fleet stood still on the usage limit

- The fleet stood still for about 85 minutes on the five-hour usage limit, with up to eight workers each running the external pipeline and its own agents (issue 155, the primary's comment of 24 November 2026).
- The pipeline's agents start outside EDI's supervision, so nothing in EDI holds them back while the workers stand on the limit, nor counts them against any capacity.

## What the pipeline costs today

Numbers cover the 93 runs created from 21/10 to 27/10, read from `~/.no-mistakes/state.sqlite` and `$EDI_HOME/data/edi.db`; weighted tokens count input ×1, cache read ×0.1, cache write ×1.25 and output ×5, as EDI-75 did.
They were not measured again for the revision.

| Measure | Value |
|---|---|
| Run outcomes | 58 completed, 13 failed (9 usage limits, 4 daemon), 17 cancelled, 2 CI monitors interrupted, 3 running |
| Pipeline share of a costed task's tokens | 36% weighted (71 tasks in `task_cost`), median 1.9M against the worker's 3.0M |
| Pipeline tokens per run | 2.59M weighted |
| Cache writes | 66.0M in the pipeline against 31.3M in the workers, because every step and fix starts cold |
| EDI run wall time | median 55 minutes, 90th percentile 331 minutes, of which a median 16 minutes parked at gates |

Where the pipeline's weighted tokens go:

| Step and purpose | Calls | Weighted | Share | Agent hours |
|---|---|---|---|---|
| test | 131 | 107.3M | 45% | 22.7 |
| review | 182 | 64.9M | 28% | 6.1 |
| review fix | 85 | 26.8M | 11% | 4.3 |
| document | 81 | 16.0M | 7% | 1.7 |
| ci | 67 | 8.8M | 4% | 2.0 |
| test fix | 16 | 6.1M | 3% | 1.7 |
| pr | 79 | 5.5M | 2% | 0.5 |
| rebase and lint | 6 | 0.5M | 0% | 0.1 |

- The test step is the largest cost: on EDI its agent reran `./mvnw -B -ntp verify`, which the worker already ran before committing, with a median of 15.1 minutes against 3 to 5 minutes for the command alone (the workers' verify logs).
- On a registered project the test agent does real end-to-end work, starting the backend against a throwaway MariaDB (`~/.no-mistakes/logs/01M5KWYTHVP9C61KSMZA614VNA/test.log`), which a command cannot replace.

## Options

The first research put these options; the captain chose B (D47 A, below).

| Option | What it is | Verdict |
|---|---|---|
| A. Keep no-mistakes with EDI-104 | `--skip rebase`, `ci` skipped where CI does not run, merge before each run | Cheapest now; leaves failure modes 2, 5 and 6 and the double configuration ownership. |
| B. EDI's own validation pipeline | A validation run in EDI's core and store, its reviewer a supervised EDI session, its CI watch a supervision reading | Recommended: removes every measured failure mode and about half the pipeline's tokens. |
| C. Change no-mistakes upstream | Ask for a merge setting, limit-aware pauses and restart-safe monitors | Outside EDI's control and timeline; the gate mirror stays. |
| D. Let the worker validate itself | The worker reviews, tests and publishes in its own session | Loses the independent reviewer, the one part that catches what the author misses. |

## The captain's answers, round 1 (29/10/2026)

The first research board put five questions before the captain, the decision card first; EDI raised the card as D47 and bound it to question Q1.
The captain answered every question with option A, and D47 is recorded as his word for A.

| Question | Answer | Requirement it sets |
|---|---|---|
| D47 (Q1) | A | Build all six slices: the validation record and Verify, the reviewer, the fix loop and BaseSync, Publish, Checks, then cut-over and removal of no-mistakes. |
| Q2 | A, with a note | The worker fixes review findings in its own session, committing on top, on the conditions below. |
| Q3 | A | `Verify` runs the project's verify commands everywhere, plus an end-to-end agent on projects that opt in; reworded by the captain on 2 November 2026, below. |
| Q4 | A | The reviewer is a supervised EDI session, a review launch through `WorkerRuntime`, with read-only tools. |
| Q5 | A | EDI marks a request ready for review once its checks pass, or once `Verify` and `Publish` pass on a project without CI. |

The options the board offered were: for D47, all six slices (A), slices 1 and 2 in shadow only (B), or no-mistakes with EDI-104 and nothing shipped (C); for Q2, the worker in its own session (A) or a fresh fix agent (B); for Q3, commands everywhere with an opt-in end-to-end agent (A), an agent test step everywhere (B) or commands only (C); for Q4, a supervised EDI session (A) or a headless call EDI retries after the reset (B); for Q5, EDI (A) or the worker (B).
He also said that EDI work goes to the upstream backlog instead of being dispatched on his machine, that EDI may change a great deal before this work is picked up, and that this is to be verified against the EDI-52 and EDI-74 slices.

### Q2: the captain's note, and the answer to it

The captain's note: if an independent fixer would make the code quality a lot higher, he leans to B; if the worker fixing is safe and unbiased enough, with a good review ruleset and guidelines, A is fine.
The judgement was that an independent fixer would not raise quality a lot, so A stands, on four conditions that are requirements of the reviewer, the fix loop and the cut-over.

- The independence that catches mistakes is the reviewer's, which deciding what is wrong needs, and A keeps it: the reviewer is always a fresh session that never shares the worker's context.
- Today the fixer is not where the judgement happens either: `auto_fix.review` is 0 in `~/.no-mistakes/config.yaml`, so the worker already chooses which review findings get fixed and a cold agent only types the fix, at 26.8M weighted tokens over 85 calls.
- When that cold agent died at 12:03, the EDI-79 worker fixed the same findings by hand and `./mvnw verify` passed (EDI store, event 2985), so the worker can carry a fix the reviewer then judges.
- Condition 1: the reviewer re-reads every fix as a delta review, so a fix the worker gets wrong is a new finding, not an accepted one.
- Condition 2: the review ruleset is explicit and versioned: the brief's craft rules, the project's path-scoped review instructions (`.edi/review.md`, seeded from `.no-mistakes.yaml`'s `review.path_instructions` and `document.instructions`), and the decision-log rules.
- Condition 3: a worker may dismiss a finding only with a stored reason, the next review round reads every dismissal, and a finding the reviewer raises again after a dismissal goes to the captain as an `ask-captain` decision instead of a second dismissal.
- Condition 4: the shadow period counts the findings the worker dismissed that no-mistakes' fix agent would have fixed and the captain later wanted; any such finding in the ten shadow deliveries puts Q2 before the captain again, with option B.

## Requirements since the first round

These are the captain's words on issue 155 and issue 91, and they bind the revised design as the round 1 answers do.

- 2 November 2026, the conflict-fix scope: when the only new commits since the last passed validation are a merge of the base branch and the resolution of its conflicts, the reviewer reads only the resolution (the merge commit's diff against both parents, and any commit made to make the merged code pass), not both branches again, while Verify still runs the full verify commands and the end-to-end checks.
  The evidence: a merge request whose conflicts were resolved at 14:44 was pushed only at about 15:40, after three full review rounds of the whole merged change.
- 2 November 2026: no cap on fix rounds for now; the new flow is watched first.
- 2 November 2026, considered and dropped: letting review notes go out with the push and be settled afterwards.
- 2 November 2026, replacing Q3's wording: the full build and test suites run on the CI environment configured for the project, the worker runs only quick, localised builds and tests locally, and only where the project's CI does not run does Verify fall back to the full local build and validation.
  The captain's words: "The default flow should be the same for any project: try to run pipelines on the CI environment configured for it. If the CI works, use that for the big full builds to validate (smaller, localised builds for quick-testing can still be left to workers). If CI does not work, fallback to the local build and validation pipelines."
- 24 November 2026, on issue 91: the title and description are composed by EDI from the task's own record, never by an agent left to its defaults; before a draft is opened or updated, and before a done is accepted, they are refused when they hold a tool attribution (a "Generated with" line, a co-author or sign-off trailer naming a model, an agent or a tool, or the assistant's address), naming the line; the push check's patterns for commit messages and this check share one list; and every agent EDI starts runs with the assistant's own attribution for commits and pull requests switched off through its documented setting, with a test on the launch.
- 24 November 2026, on issue 91: the description holds the acceptance criteria and the evidence in the project's own words, never the worker's instructions, a machine path, a decision number or a board code, and the check on a request's text refuses those too, with the same list the push check uses.
- 24 November 2026: EDI's own validation pipeline is the highest priority once the work in flight is done, and it is to go next.
- Standing: EDI validates its own changes with no-mistakes until EDI is cut over, so a broken validation of EDI's own can never approve its own fix; no code path rewrites history (no rebase, no amend of a submitted commit, no force-push); no seventh port.

## The lifecycle the design rests on

Each row is read from its real source on 24 November 2026: `origin/main` for merged work, `git diff origin/main...<branch>` for work in review, and the issue for work specified only.

| Item | State on 24 November 2026 | What it gives the validation | Read from |
|---|---|---|---|
| EDI-84, EDI-85 the stage is stored | merged | `TaskStage`, `StageTransitions` with the triggers `Change(ChangeCause.PIPELINE_RUN_STARTED)`, `DoneReported.pipelineRunning` and `ForgeObserved.latestRunPassed` and `atTheDeliveredHead`; every stage change goes through `TaskStages` with a `StageChanged` event | `domain/task/StageTrigger.java`, `StageTransitions.java`, [task-lifecycle](../decisions/task-lifecycle.md) |
| EDI-86 delivered, changing, ready | specified only (issue 138) | the services fire the CHANGING triggers (a pipeline run starts, a steer, the head or the base moves, the request closes), the done guard reads a running run, and READY comes only from the forge's view; a request watched after CI-ready at the forge's head with green checks reads READY | issue 138 and its comment of 2 November 2026; on main only `HandoverStages` fires `WORKER_WORKING` |
| EDI-70 landing by the merged head | in review, pull request 335 | every commit up to the merged head lands however the forge merged it, a commit after it is named and raises one follow-up decision, and a commit counts as up to the merged head when it is the same change by patch, "so a head the pipeline rebuilt lands" | `git diff origin/main...origin/feature/EDI-70 -- docs/decisions/` |
| EDI-87 the reasons a task waits, as a set | in review, branch `feature/EDI-87` (local, not pushed) | a task waits on several decisions at once, and an answer releases only its own | `git diff origin/main...feature/EDI-87 -- docs/decisions/` |
| EDI-67 the base moved | specified only (issue 144) | after a followed merge, each other DELIVERED or READY task of the project moves to CHANGING and receives exactly one steer to merge the base and re-run its pipeline | issue 144 |
| EDI-88 plans and their waits | merged | a plan of slices, each with its own key and one wait, after N merged by default | [plans](../decisions/plans.md) |
| EDI-89 the plan dispatches its slices | in review, pull request 333, its checks green | the supervision round dispatches each eligible slice within the plan's and the project's capacity, under a go that cites the captain's stored word: a decision raised on a task keyed by the plan's umbrella key, answered with an option that authorises promote | `git diff origin/main...origin/feature/EDI-89 -- docs/decisions/plans.md` |
| EDI-97 effort and review yield | merged | `PipelineReview` (rounds, findings) stored with `TaskCost` and its route; the Week view's `EffortYield` | `domain/delivery/PipelineReview.java`, `EffortYield.java`, `adapter/store/PipelineReviewStore.java` |
| EDI-69 a standing CI note | dropped on 3 November 2026 | nothing: `ciRuns` is the one setting | issue 92, context |
| EDI-244 merges into the base only | merged | a request names the branch it is stacked on, `Delivery.stackedOn` | [merge-following](../decisions/merge-following.md) |
| EDI-150, EDI-237 request texts pass the guard | merged | `PublishCheck.checkText` on a request's title and description, at `edi deliver` and at the done | [publish-guard](../decisions/publish-guard.md) |
| EDI-251 full builds in CI | merged | the brief leaves the full build to CI where `ciRuns`; EDI's pipeline test step runs the fast suite | `BriefSections.fullBuildLeftToCi`, issue 251 |
| EDI-96 decision rounds | merged | a running ship's decisions gather into five-minute rounds | [decision-rounds](../decisions/decision-rounds.md) |
| EDI-330 the captain's list | in review, pull request 334 | a changing request is ongoing work, not an entry; a review is an entry until ticked | `git diff origin/main...origin/feature/EDI-330 -- docs/decisions/`, its new record of the captain's list |

Parts of the design that rest on work not yet merged are marked in the text as [EDI-86], [EDI-67], [EDI-70], [EDI-87] or [EDI-89], so they can be checked when that work lands.

## The design, revised

### Principles

- The isolated copy is the only working tree; a validation reads a pinned commit of it and never rewrites history.
- Every stage's state lives in EDI's store, so a core restart or a machine restart loses nothing and a stage resumes from its record.
- The reviewer is independent: a fresh session that reads the change against the captain's intent, never the author's session.
- The worker owns its commits: it fixes findings and merges the base in its own session, always on top, so no commit it made is ever discarded.
- Every forge call stays on the allow-list and the bound account; nothing merges and nothing force-pushes.
- New since the first design: a validation of record is the task's pipeline run for the stored stage, never a second state beside it; a validation in shadow records only and moves nothing.
- New since the first design: the full build runs where the project's CI runs, and locally only where it does not.
- New since the first design: what EDI posts on a request is composed by EDI and checked before it leaves, and no agent EDI starts adds its own attribution.

### The domain

- `Validation` is a run of the pipeline on one task at one head commit, with its target branch and base commit, the intent it was judged against, its role, its stages in order, and an outcome: running, awaiting the worker, awaiting the captain, passed, failed or superseded.
- Its role is `record` when the project's validation is EDI's, and `shadow` when EDI validates beside no-mistakes; only a validation of record moves a stage or reaches the worker.
- Its target is the branch the request targets: the task's base, or the branch it is stacked on (`Delivery.stackedOn`, EDI-244).
- `ValidationStage` is one of `BaseSync`, `Review`, `Verify`, `Publish`, `Checks`; each holds its state, its findings, its evidence and its round.
- `Finding` carries a severity (error, warning, info), a file and line where it has one, a description and an action: `fix` (the worker's to fix), `ask-captain` (a decision) or `note` (no action); an answered finding carries the answer, `fixed` or `dismissed` with its reason.
- `ReviewScope` says what a review round reads: the whole change from the target, the delta since the last approved head, or the conflict-fix scope when the only new commits are a merge of the target and the commits that make it pass.
- A new `Validation` on a head that descends from the previous one keeps the previous approvals for stages the delta cannot affect and reviews only its scope.
- A validation whose head does not descend from its predecessor's is refused, naming the commits it would drop: the same data-loss guard the gate mirror gave, without a mirror.
- These live in `domain/validation`, free of Spring and processes, as `ArchitectureRulesTest` requires.

### The stages

| Stage | Replaces | What runs | Who acts on a finding |
|---|---|---|---|
| `BaseSync` | rebase | `git merge-base --is-ancestor origin/<target> <head>` through `adapter.git` | The worker merges `origin/<target>` with a merge commit through the base-moved steer [EDI-67], and validates again. |
| `Review` | intent, review, document, lint judgement | A reviewer session (below) reading its scope, the intent and the project's review rules, including the documentation rules no-mistakes' document step checked | The worker fixes `fix` findings; `ask-captain` findings become a decision. |
| `Verify` | test, lint commands | Where the project's CI runs: the project's quick checks (EDI's `./mvnw -B -ntp test -Pfast`) and `edi publish-check`; where it does not: the project's verify commands through `ProjectBuild.verifies`, the native quick build when `NativeImageInput` says the change reaches the image, and `edi publish-check`; always in a detached worktree pinned at the head; an end-to-end session only on projects that opt in | The worker fixes a failure. |
| `Publish` | push, pr | `git push` of the head to the registered remote through the bound credential helper, after `PublicationTargets`; `Forge.openDraft`, or an update of the open request's text, with a title and description EDI composes and checks | None; a refusal names the cause. |
| `Checks` | ci | The request's checks on the pushed head, as `FollowMerges` already observes them (`Delivery.checks`, `HeadChecks`), and its mergeability, per the project's `ciRuns` | A failing check or a conflict becomes a `fix` finding for the worker, who merges the target; nothing rebases. |

- The commit key needs no template: the worker writes every commit, and `BranchName.workKeyCapture` already holds the key.
- When `Checks` passes, or the project's CI does not run and `Verify` and `Publish` passed, EDI marks the request ready for review (a new allow-list entry: `gh pr ready` and `glab mr update --ready`), which keeps I-04: a draft first, ready once green, never merged.
- A passed validation ends there: there is no monitor that keeps watching a request after its checks pass, so the EDI-157 case, a request read as changing while its pipeline only watches it, cannot arise from EDI's own validation.
- The order is fixed and the review comes before the push, as the captain kept it on 2 November 2026.

### Verify on the CI the project has

- The captain's rule of 2 November 2026 decides Verify by `ciRuns`, the one setting since EDI-69 was dropped.
- On a project whose CI runs, CI's run on the pushed head is the full-build evidence, read by `Checks`; `Verify` runs only the project's quick checks, the same fast suite EDI-251 gave no-mistakes' test step, so a red fast suite costs minutes before a push instead of a CI round and a fix round after it.
- The registry gains the quick checks command beside `verify` and `nativeBuild`; a project that names none runs only `edi publish-check` in `Verify` when its CI runs.
- On a project whose CI does not run, `Verify` is the full local build and validation, as the brief asks the worker today (`BriefSections.evidenceOfTheMergeRequest`).
- The conflict-fix scope narrows only the review: `Verify` and the end-to-end checks always run in full on the head, as the captain set.
- `Verify` runs queue one at a time per machine; they are the core's own runs, outside the workers' panes that `NoticeHeavyRunsAtOnce` reads, so the queue is what keeps them from piling up ([local-native-builds](../decisions/local-native-builds.md)).

### The reviewer

- The reviewer runs as an EDI-supervised session through the existing `WorkerRuntime` port, a new `LaunchKind.REVIEW` with read-only tools, in the pinned worktree, on the dispatch rules' `pipeline` route, under the same guards as a worker (Q4 A).
- It reads its `ReviewScope`, the pipeline intent (`PipelineIntent`), the brief's craft rules, the project's review rules (`.edi/review.md`, path-scoped, seeded on EDI from `.no-mistakes.yaml`) and every dismissal of the earlier rounds with its reason, and records the commit of the rules it judged against, so the ruleset is versioned (Q2 condition 2).
- It reports its findings with a new command, `edi review report --findings -`, as JSON the core validates against `Finding`.
- Because it is a supervised session, a usage limit pauses it and the supervision round resumes it at the reset (`usageLimitStopIn`), instead of failing the validation (failure mode 2).
- Reviewer launches queue per machine and none starts while a worker stands on the usage limit (`Worker.limitPark`), so EDI's own pipeline adds no session to a fleet already stopped on the limit (failure mode 9); question Q8 on the board asks how many run at once.
- Its tokens are counted from its transcript into `task_cost`'s pipeline columns, in place of `Pipeline.tokensSpentOn`, and its rounds and findings into `PipelineReview` through `PipelineReviewStore`, in place of `Pipeline.reviewOf`, so EDI-97's records and the Week view read EDI's own review once a project is cut over; a shadow validation keeps its counts on its own record and leaves EDI-97's untouched.
- Every agent EDI launches, the reviewer, an end-to-end session and every worker, runs with the assistant's own attribution for commits and pull requests switched off through its documented setting, with a test on the launch (issue 91, 24 November 2026).

### The worker's side

- On a project whose validation is EDI's, `edi validate` starts a validation of the copy's head and returns at once.
- Each stage that needs the worker arrives as one inbox message with its findings, and the worker answers with `edi validation answer <stage> --fixed <ids> | --dismiss <ids> --why "<reason>"` after committing its fixes, or raises `needs-decision` for an `ask-captain` finding.
- An `ask-captain` finding becomes its own open decision beside any other on the task [EDI-87], gathered into the running ship's decision round (EDI-96).
- A finding the reviewer raises again after a dismissal becomes an `ask-captain` decision, never a second dismissal (Q2 condition 3); fix rounds are not capped (2 November 2026).
- The worker waits on its validation exactly as it waits on its pipeline today, so supervision's `Awaited` rule keeps it from being stale.
- The brief's draft-review section shrinks to: commit, `edi validate`, act on each inbox message, done when the request is ready for review at the last commit; it no longer tells the worker to mark the request ready.

### The stage, and where a validation moves it

A validation of record is the task's pipeline run for the stored stage ([task-lifecycle](../decisions/task-lifecycle.md)); a shadow validation moves no stage.

| Moment | Trigger or guard of the stored stage | Depends on |
|---|---|---|
| A validation of record starts on a DELIVERED or READY task | `Change(ChangeCause.PIPELINE_RUN_STARTED)`, to CHANGING, its reason naming what moves ("its head is validated again"), never a stage's name | [EDI-86] wires the trigger in the services |
| The worker reports done while a validation of record runs | `DoneReported.pipelineRunning` refuses the done | [EDI-86] the delivery guard |
| The validation passed and EDI marked the request ready | `ForgeObserved` with `latestRunPassed` from the validation and `atTheDeliveredHead` comparing the request's head with the published head, to READY | [EDI-86] READY from the forge's view |
| A steer to merge the target, from `BaseSync` or after a followed merge | `Change(ChangeCause.STEER_SENT)`, one steer per task per move of the target | [EDI-67] the base-moved steer |
| The request is merged | `RequestMerged` with the merged head; every commit up to it lands, and a commit after the published head is named | [EDI-70] landing by the merged head |

- The published head is the head `Publish` pushed and recorded on the validation, and the done's delivered head is that head, so landing compares the merged head with what EDI published [EDI-70].
- EDI's own pipeline never rebuilds a commit, so on a project cut over the merged head is the published head commit for commit; EDI-70's credit for "the same change by patch, so a head the pipeline rebuilt lands" is then needed only for requests no-mistakes rebased.

### Store and events

- Three new tables, `validation`, `validation_stage` and `validation_finding`, by a Flyway migration numbered after the ones in review (V35 to V38 on the branches of EDI-330, EDI-89, EDI-70 and EDI-87); `pipeline_run` stays until no-mistakes is removed.
- Events `ValidationStarted`, `StageFinished`, `FindingsRaised`, `ValidationPassed` and `ValidationFailed` feed the timeline, the sitrep and the console the way `PipelineRunObserved` does now.
- The supervision round drives the stages: it starts the next stage when the previous passed, reads the request's checks in each delivery round, and resumes a paused reviewer, so there is no daemon and no long-lived monitor to lose.
- A `Verify` command whose exit is recorded is never run again; one cut short by a core stop runs again once.

### Ports and architecture

- No seventh port: the reviewer runs through `WorkerRuntime`, verify commands through the concrete `ProjectBuild`, pushes through the concrete `Git` adapter, and checks through `Forge`.
- `Forge` already reads a request's checks on its head (`MergeRequestView.checks`, from `statusCheckRollup` on GitHub and `head_pipeline` on GitLab) and its text (`Forge.textOf`); it gains the request's mergeability in the same view, `markReady(forge, address)` and an update of the request's text, and `ForgeCommand` gains `gh pr ready` and `glab mr update` restricted to `--ready`, the title and the description; AT-03b keeps proving that no merge argument exists.
- `Git` gains a push that refuses `--force` and any target `PublicationTargets` refuses, through the repository-local credential helper `IsolatedCopies` already sets, which closes the account gap.
- When no-mistakes is removed, the `Pipeline` port, `NoMistakesPipeline`, `AgentArguments`, `CommitKeys`, `PipelineConfiguration`, `AxiStatus`, `PipelineSpend`, `PipelineReviews`, `PassedChecks`, `PipelineGuard` and `Program.NO_MISTAKES` go, leaving five ports; `docs/decisions/ports-and-architecture-rules.md` and `PortInventoryTest` change with that slice.

### What to keep from no-mistakes, and what to drop

| Keep | Drop |
|---|---|
| A fixed stage order that projects can add to but never reorder | The rebase step and the CI monitor's rebase |
| A fresh-context reviewer judging against the stated intent | The gate remote, the bare mirror and private mirror reconciliation |
| Path-scoped review instructions per project | Transcript-based intent extraction (EDI writes the intent) |
| Findings with a severity and an action, `ask-user` parked for a human | Cold fix agents for every finding |
| The data-loss guard: no head may drop a commit already submitted | The daemon and its restart recovery |
| Re-review of the delta after fixes, and token counts per call | Writing another tool's configuration at every launch |
| Draft first, ready once green | An agent diagnosing CI jobs that never started |
| | An agent writing the request's title and description |

## What the lifecycle items now cover, and what remains

| Part of the first design | Now covered by | What remains, and in which slice |
|---|---|---|
| Restart-safe state | The stored stage (EDI-85) keeps where the task is | The validation's own record and stages (slice 1) |
| The validation's start and end as stage triggers | The domain's triggers exist on main; EDI-86 wires them [EDI-86] | Feeding them from a validation of record (slice 6) |
| A request ready only when it is | EDI-86's READY from the forge's view, and EDI-157 folded into it [EDI-86] | `markReady` feeding that view (slice 5) |
| `BaseSync`'s merge-the-base message | EDI-67's base-moved steer after a merge [EDI-67] | The ancestry check at a validation's start, sending the same steer once (slice 3) |
| The published head for landing | EDI-70 lands by the merged head [EDI-70] | Recording the pushed head as the delivered head (slice 4) |
| The checks policy | `ciRuns` (EDI-104), EDI-69 dropped | Nothing new: `Verify` and `Checks` read `ciRuns` (slices 1 and 5) |
| `Forge.checksOf` | `MergeRequestView.checks` (`HeadChecks`), read by `FollowMerges` each delivery round | Mergeability and the ready mark (slice 5) |
| The request's text checked | `PublishCheck.checkText` at `edi deliver` and the done (EDI-150, EDI-237) | Attribution lines in that check, a check whenever the text changes, attribution off at launch (slice 0); EDI composing the text (slice 4) |
| Review rounds and findings per task | `PipelineReview`, `PipelineReviewStore`, `EffortYield` (EDI-97) | Supplying them from EDI's reviewer (slice 2, live at slice 6) |
| An `ask-captain` finding beside other decisions | Holds as a set [EDI-87]; decision rounds (EDI-96) | Nothing new (slice 2 uses them) |
| The slices dispatched in order | Plans (EDI-88) and the plan dispatching its slices [EDI-89] | Entering the slices as one plan, on the captain's go (the board's card) |
| The stacked branch as the base | `Delivery.stackedOn` (EDI-244) | Validating against the request's target (slice 1) |
| Full builds in CI | The brief and the fast pipeline test step (EDI-251) | `Verify` by `ciRuns` (slice 1) |
| The reviewer, the worker's answers, `Publish` on the bound account, the ready mark, the cut-over and the removal | Nothing | Slices 2 to 7 |

None of these items builds a validation pipeline, so every slice of the first plan is still needed; what they settle is the lifecycle the slices plug into.

## The shadow

D47 A cuts a project over after a shadow period, and the first plan counted ten shadow EDI deliveries before EDI moves.
The revision finds that the first plan's shadow could not exercise every slice: while no-mistakes pushes, opens the request and watches CI, EDI cannot publish beside it, and a worker answering both pipelines' findings would do every review twice.

- Recommended (question Q7): EDI starts a shadow validation itself on each head a no-mistakes run validates (`PipelineRun.headCommit`, observed by `ObservePipelines`), on a project whose `validation` setting is `shadow`.
- A shadow validation runs `Review` and `Verify` in full, composes and checks the request's text without posting it, reads the checks without marking anything ready, and sends nothing to the worker.
- It moves no stage and refuses no done, so slices 1 to 5 do not wait for EDI-86, and only the slice that makes EDI's validation the one of record does.
- The comparison per delivery is EDI's findings against no-mistakes' findings on the same heads, and the worker's answers at no-mistakes' review gates, where the worker already chooses which findings get fixed (`auto_fix.review` is 0), are the dismissals Q2's condition 4 counts.
- The shadow starts as soon as slice 2 merges and runs while slices 3 to 5 are built, so its ten deliveries cost no calendar time on the critical path.
- The fix loop, the publication and the ready mark are proven by their tests and by EDI's own first deliveries after its cut-over, with `no-mistakes` one setting away as the fallback.

## Migration plan, revised

Each slice is one ship on its own key, delivered as a draft pull request; no-mistakes keeps validating EDI's own changes until slice 6 cuts EDI over, so the pipeline never validates the code that is replacing it in the same run.
The keys are the backlog's: slice 1 is EDI-144 (issue 88), 2 EDI-145 (89), 3 EDI-146 (90), 4 EDI-147 (91), 5 EDI-148 (92) and 6 EDI-149 (93); slice 0 and slice 7 are the revision's, put to the captain on the board (Q6 and the card).
The cut and the shadow below follow the recommended answers to Q6, Q7 and Q8; each answer's effect on the slices is on the board.

### Slice 0: no attribution and no internal text reaches a request (Q6)

- The request-text check gains the attribution lines of `AgentAttribution`, one list shared with the commit hooks' rules `CO_AUTHOR_TRAILER` and `GENERATED_WITH_LINE`, widened to sign-off trailers naming a model, an agent or a tool and to the assistant's address.
- The core reads a delivered request's text again whenever its head or its text changes, and a failing text keeps the task from READY and wakes the worker, so a pipeline that rewrites a description after the done is caught [EDI-86 for the READY guard].
- Every agent EDI launches runs with the assistant's attribution for commits and pull requests switched off through its documented setting, with a test on the launch.
- The check refuses the worker's instructions, a machine path, a decision number and a board code in a request of a project other than EDI, with the push check's list (already the case for the vocabulary since EDI-237; the brief's word and the machine paths are added).
- It needs no validation record, so it protects today's no-mistakes flow at once; slice 4 then composes the text EDI posts.
- Blockers: none beyond this revision.

### Slice 1: the validation record and the Verify stage, in shadow (EDI-144)

- Builds `domain/validation`, the three tables, the events, the per-project `validation` setting (`no-mistakes`, `shadow` or `edi`), `edi validation status` and the `Verify` stage; briefs do not change.
- Acceptance: on a project set to `shadow`, each head a no-mistakes run validates gets a shadow validation that runs `Verify` in a detached worktree pinned at that head and stores each command's exit, duration and log path as evidence.
- Acceptance: `Verify` runs the project's quick checks and `edi publish-check` where the project's CI runs, and the full verify commands, the native quick build when `NativeImageInput` says so and `edi publish-check` where it does not.
- Acceptance: a validation's target is the request's target branch, the task's base or the branch it is stacked on.
- Acceptance: stopping the core in the middle of `Verify` and starting it again finishes the stage from its record, running again only a command whose exit was not recorded (a test with the `Clock` port and a recording `ProjectBuild`).
- Acceptance: two validations on the same machine run their verify commands one after the other.
- Acceptance: a validation whose head does not descend from the previous one's is refused, naming the commits it would drop.
- Acceptance: a shadow validation moves no stage, refuses no done and sends no message; `PortInventoryTest` and `ArchitectureRulesTest` pass unchanged.
- Blockers: EDI-85 (merged), this revision.

### Slice 2: the reviewer (EDI-145)

- Adds `LaunchKind.REVIEW`, `edi review report`, the `Review` stage with its whole-change scope, `.edi/review.md` (seeded from `.no-mistakes.yaml` on EDI), the documentation rules in the review prompt, and the launch queue.
- Acceptance: a reviewer session runs on the dispatch rules' `pipeline` route with read-only tools in the pinned worktree, under the same guards as a worker, and never in or from the worker's own session.
- Acceptance: its findings are validated against `Finding`, a report that does not validate is refused naming the field, and the findings are stored with the commit of the review rules they were judged against.
- Acceptance: a reviewer that hits a usage limit is resumed at the reset by the supervision round, and the validation stays running, not failed (a test on a recorded transcript with a limit stop).
- Acceptance: reviewer launches queue per machine as Q8 sets, and none starts while a worker stands on the usage limit.
- Acceptance: the reviewer runs with the assistant's attribution switched off (slice 0's launch setting).
- Acceptance: a shadow review's findings, rounds and tokens are stored on the validation and shown beside no-mistakes' findings for the same head, and EDI-97's records are not touched.
- Blockers: slice 1, EDI-87 [in review] for an `ask-captain` finding beside another decision.

### Slice 3: the fix loop, the review scopes and BaseSync (EDI-146)

- Adds `edi validation answer`, the delta and conflict-fix scopes, the dismissals read by the next round, and the `BaseSync` stage; all of it acts only on a validation of record.
- Acceptance: `--fixed <ids>` after the worker's fix commits, or `--dismiss <ids> --why "<reason>"`, records the answer; a dismissal without a reason is refused.
- Acceptance: after a fix, the next validation reviews only the delta from the last approved head, its round count rises by one, and approvals of stages the delta cannot affect are kept; rounds are not capped.
- Acceptance: when the only new commits since the last passed validation are a merge of the target and the commits that make it pass, the reviewer reads only the merge commit's diff against both parents and those commits, while `Verify` runs in full.
- Acceptance: the next review round is given every dismissal with its reason, and a finding raised again after a dismissal becomes an `ask-captain` decision.
- Acceptance: `BaseSync` checks whether `origin/<target>` is an ancestor of the head; when it is not, the worker gets the base-moved steer of EDI-67 once per move of the target, telling it to merge with a merge commit whose subject starts with the work key [EDI-67].
- Acceptance: no code path in EDI runs `git rebase`, `git commit --amend` on a submitted commit or a force-push (a rule in `ArchitectureRulesTest` on `adapter.git`'s calls).
- Blockers: slice 2, EDI-67 [specified only].

### Slice 4: Publish (EDI-147)

- Adds the guarded push in `Git`, folds `DeliverDraft` into the `Publish` stage, and composes the request's title and description from the task's record.
- Acceptance: the push goes through the bound credential helper whatever `gh` account is active (a test with two recorded accounts).
- Acceptance: a push to any repository other than the registered one is refused (AT-43 extended), and no allow-list entry or argument carries `--force` (AT-03b extended).
- Acceptance: the title is the work key and the task's title; the description holds the spec's acceptance criteria, each `Verify` command with its result and the worker's delivery note, never the worker's instructions, a machine path, a decision number or a board code, and slice 0's check passes on it before it is posted or updated.
- Acceptance: the pushed head is recorded on the validation as the published head, which the done takes as the delivered head [EDI-86, EDI-70].
- Acceptance: a shadow validation composes and checks the text and records it beside the description no-mistakes posted, and pushes nothing.
- Acceptance: `edi deliver` still opens a draft by hand for a task whose validation did not publish.
- Blockers: slice 3, slice 0, EDI-70 [in review].

### Slice 5: Checks and ready for review (EDI-148)

- Adds the request's mergeability to `MergeRequestView`, `Forge.markReady`, the `Checks` stage in the delivery round, and the ready mark.
- Acceptance: a project whose CI does not run (`ciRuns: false`) is marked ready after `Verify` and `Publish` pass, with no CI finding raised.
- Acceptance: a failing check or a conflict with the target reaches the worker as a `fix` finding, the conflict through the same steer as slice 3, and nothing rebases or force-pushes.
- Acceptance: restarting the core while a request's checks are pending loses nothing; the next round reads them again.
- Acceptance: the request is marked ready only when its checks passed at the head EDI pushed, and the mark feeds EDI-86's READY guard rather than bypassing it [EDI-86].
- Acceptance: a passed validation ends with the ready mark, and nothing keeps watching the request.
- Blockers: slice 4, EDI-86 [specified only].

### Slice 6: EDI cut over (EDI-149)

- Makes EDI's validation the one of record on a project set to `edi`, switches the brief per project, and wires a validation of record into the stored stage as "The stage, and where a validation moves it" tables it [EDI-86].
- Acceptance: the cut-over pull request records the shadow comparison over at least ten EDI deliveries: no finding no-mistakes raised that EDI's review missed and the captain later wanted, and every finding the worker dismissed at no-mistakes' review gates that the captain later wanted; if there is any of the latter, the cut-over waits and Q2 goes before the captain again with option B.
- Acceptance: a validation of record starting on a DELIVERED or READY task moves it to CHANGING, a done is refused while it runs, and READY needs its pass at the published head; a shadow validation does none of these.
- Acceptance: a project that opts in gets an end-to-end session in `Verify`, launched like the reviewer and told to exercise the change against a throwaway environment, whose findings join the validation's.
- Acceptance: EDI's tokens, rounds and findings feed `task_cost` and `PipelineReview` for a project set to `edi`.
- Acceptance: EDI moves first; the brief for `edi` gives `edi validate` and its inbox messages, and for `no-mistakes` the call of [pipeline-steps](../decisions/pipeline-steps.md).
- Blockers: slice 5, EDI-86 [specified only], and ten shadow deliveries, a wait the captain closes.

### Slice 7: the other projects, and no-mistakes removed (the card)

- Moves each other registered project after five deliveries on `edi` with no fallback, then removes the no-mistakes adapter, the `Pipeline` port, `Program.NO_MISTAKES`, the configuration writers and their tests, and the EDI repository's `.no-mistakes.yaml` once `.edi/review.md` holds its rules.
- It is a slice of its own because EDI follows one request per task (EDI-70, 24 November 2026), and the removal waits weeks after EDI's cut-over.
- Acceptance: `docs/decisions/pipeline-agent.md`, `pipeline-commit-keys.md`, `pipeline-steps.md`, `supervision.md` and `ports-and-architecture-rules.md` are rewritten in place with history lines, `docs/verification/no-mistakes.md` is retired, and `PortInventoryTest` counts five ports under the updated record.
- Acceptance: no tracked file outside the decision history names no-mistakes (`grep -ri "no-mistakes" --exclude-dir=.git .`).
- Blockers: slice 6, and five deliveries on `edi` for every other project, a wait the captain closes.

### The slices as a plan

- Recommended (the card): the slices enter as one plan under the umbrella key EDI-105, capacity 2, slice 0 and slice 1 with no wait, each later slice after the one before it merged, and the lifecycle items as blockers of their slices; the core dispatches each slice once its wait is met [EDI-89].
- The card's go is a decision raised on this research task, keyed EDI-105, with an option that authorises promote, which is the stored word of the captain a plan's go cites (`PlanGo`, `CaptainsGo`) [EDI-89].

## Cost

### Running cost against the measured baseline

Estimated from the per-step figures above, per 93 runs; nothing is measured again, and each estimate says its basis.

| Step | Measured, 21 to 27 October | First estimate | Revised estimate | Basis |
|---|---|---|---|---|
| review and document | 80.9M | about 70M | about 70M or less | one reviewer reads the documentation rules too; the conflict-fix scope removes whole re-reviews after a merge of the base, by an amount the shadow measures |
| review fix | 26.8M | about 13M | about 13M | fixes in the worker's warm session instead of a cold agent; an estimate |
| test and test fix | 113.4M | about 40M | about 40M | commands and no agent wherever CI runs, the opt-in end-to-end session kept on the project that needs it (about a third of the measured test tokens) |
| ci | 8.8M | 0 | 0 | a forge reading, no agent |
| pr, rebase, lint | 6.0M | 0 | 0 | EDI composes the title and description |
| Total | 236.1M | about 123M | about 123M | about 48% less, about 1.3M weighted per run |

- The revised design changes where the full build runs, not how many tokens it takes: commands cost no tokens, wherever they run.
- The baseline has moved since it was measured: EDI-251 gave EDI's test step the fast suite on 10 November 2026, so today's spend on that row is lower than 107.3M by an amount not measured, and the saving against today is smaller than 48% on EDI; the shadow measures it.
- The shadow adds a one-off cost: a shadow review per no-mistakes run of a shadowed project, about 0.75M weighted each on the estimate above (70M over 93 runs), so ten EDI deliveries at two runs each come to about 15M.
- Time: no agent reruns a build on a project whose CI runs, and no gate waits on CI that cannot start.
- Failures: usage limits pause instead of failing (9 of 13 failed runs), core restarts lose nothing (6 more), and the rebase strand cannot happen.
- The standstill of 24 November 2026 is not priced: EDI's own pipeline starts no session while a worker stands on the limit, which is what lets a fleet resume at the reset instead of meeting the limit again at once.

### Cost of building it

- Eight slices at roughly EDI's mean ship cost (5.4M weighted for the worker and 3.1M for its pipeline, measured in October) come to about 68M weighted tokens, plus review rounds; a fair estimate is 75M to 95M.
- Slices 0 and 1 can start as soon as this revision merges; slices 4 to 6 wait for EDI-70, EDI-86 and EDI-67, so the calendar depends on those more than on the slices themselves.
- At about ten deliveries a working day, each with at least one run, and about 1.3M weighted saved per run against the measured baseline, the build pays back in at most six to seven working days; against today's lower baseline it takes longer by an amount the shadow measures.

## Risks

- EDI takes on code that no-mistakes maintains today; the slices keep it small by reusing the copies, the runtime, the store, the stage, the forge's existing readings and the text check, and dropping the mirror entirely.
- Review quality could drop if the reviewer's prompt is weaker than no-mistakes'; the shadow measures that before any project leaves no-mistakes.
- The worker fixing its own findings could argue its way past them; a dismissal needs a stored reason, the reviewer re-reads the delta, and a finding raised again goes to the captain.
- A shadow does not exercise the fix loop, the publication or the ready mark on real deliveries; their tests and EDI's first deliveries after the cut-over do, with `no-mistakes` one setting away.
- Four parts of the design rest on work not yet merged, marked [EDI-86], [EDI-67], [EDI-70] and [EDI-87]; if that work lands differently, the slice that uses it changes with it, and its item says which.
- EDI validates its own changes; until slice 6, no-mistakes validates EDI's code, so a broken EDI pipeline cannot approve its own fix.

## Questions for the captain, round 2

D47 and Q2 to Q5 are answered and are not put again; the revision's board puts these before the captain, with its card first.

- The card: release the revised plan of slices 0 to 7 as one plan under EDI-105, dispatched by the core (recommended), release slices 0 and 1 only and the rest by hand, or not yet.
- Q6: bring the request-text check and the attribution setting forward into slice 0, protecting today's flow (recommended), or keep them in Publish.
- Q7: the shadow: EDI validates each no-mistakes head itself and records only (recommended), the worker runs both pipelines, or no shadow.
- Q8: how many reviewers run at once: one at a time per machine and none while a worker stands on the limit (recommended), a reviewer takes a worker's place under the project's capacity, or no limit.

## Evidence

The measurements live on the captain's machine: the pipeline tool's state database and logs (read-only), EDI's store tables `event` and `task_cost`, and the no-mistakes source at the version the report names.
The queries are described in the sections above; the revision did not rerun them.

The revision's evidence, all read-only:

- The lifecycle on main: `git show origin/main:docs/decisions/task-lifecycle.md`, `edi-core/src/main/java/io/github/ythibos/edi/domain/task/StageTrigger.java`, `grep -rn 'new Change(' edi-core/src/main/java` (only `HandoverStages` fires a CHANGING trigger).
- The work in review: `git diff origin/main...origin/feature/EDI-89 -- docs/decisions/`, the same for `origin/feature/EDI-70` and `origin/feature/EDI-330`, and `git diff origin/main...feature/EDI-87 -- docs/decisions/`.
- The work specified only: `gh issue view 138` (EDI-86) and `gh issue view 144` (EDI-67).
- The slice items and their blockers: `gh issue view 88` to `93`, and `gh api repos/<owner>/<repo>/issues/<n>/dependencies/blocked_by`.
- The captain's requirements: `gh issue view 155 --comments` and `gh issue view 91 --comments`.
- EDI's CI: `.github/workflows/ci.yml` and `gh pr view 333 --json statusCheckRollup`.
- The checks and text the forge already reads: `edi-core/src/main/java/io/github/ythibos/edi/adapter/forge/GitHubForge.java` (`FIELDS`, `checksOn`), `application/port/Forge.java` (`textOf`).

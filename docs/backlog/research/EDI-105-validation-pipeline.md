# EDI-105: EDI's own validation pipeline, carried over from the research

This is the research report for the question whether EDI should run its own validation pipeline instead of driving the external no-mistakes tool.
It was written on 27 October 2026 and authorises nothing by itself: the decision card D47 on the research board does, and the captain has parked that board until EDI-74 step 18 (EDI-89) lands.
When that happens, EDI-105 revises this report on the new task record and plans, keeps its measurements, and puts D47 again; see the EDI-105 item.
Work projects other than EDI are named generically, and the run identifiers are those of the pipeline tool's state on the captain's machine.
Paths and line numbers are those of the code at the time.

# EDI-105: EDI's own validation pipeline

Research report for the question: what would it take for EDI to run its own validation pipeline (review, test, document, lint, push, pull or merge request, CI watch, gates that ask the captain) instead of driving no-mistakes, and should it?
This report authorises nothing; the decision card on the research board does.

## The answer in short

Yes, EDI should run its own validation pipeline, built in six slices and cut over one project at a time after a shadow period.
Every failure mode measured since 21/10/2026 comes from a part of no-mistakes that EDI either does not need (the gate mirror, the rebase step, the daemon's long-lived CI monitor) or already does better itself (limit-aware resumption, account-bound forge calls, restart-safe state).
The part EDI really uses, an independent reviewer that reads the change against the captain's intent, is small and maps onto what EDI already has: isolated copies, the worker runtime, the store, the inbox, decisions and the forge adapters.
The estimated saving is about half of the pipeline's tokens (36% of all task spend today), and the build pays for itself within about a week of deliveries.
EDI-104's `--skip rebase` shortcut removes the worst strand now and stays the bridge until the cut-over.

## How EDI uses no-mistakes today

### The brief

- A `draft-review` ship's brief tells the worker to commit, then run `no-mistakes axi run --base-branch <base> --intent "$(cat '<task>/intent.md')"` from the isolated copy (`edi-core/src/main/java/io/github/ythibos/edi/application/brief/BriefSections.java`, `draftReview`).
- The worker answers every gate itself and never passes `--yes`; an `ask-user` finding becomes `edi status needs-decision --key nm-<run>-<step>` and the worker stops.
- The pipeline pushes the branch and opens the draft; the worker marks it ready once checks are green, and its done needs the merge request open at its last commit (`shipDone`).
- `edi deliver <task>` opens the draft under the project's bound account when the pipeline could not (`application/delivery/DeliverDraft.java`).

### Intent

- EDI writes the intent itself from the captain's intent, the spec, a `## Commits` section and the craft rules (`domain/delivery/PipelineIntent.java`), and rewrites it when a task is noted.
- No-mistakes' transcript-based intent extraction is therefore never used: every run's `intent.log` reads `using intent supplied by the agent` (for example in run `01M5YES29XEK3GD0E8AW22NMDK`).

### Configuration EDI writes into no-mistakes

- `agent_args_override.claude` from the dispatch rules' `pipeline` entry at every launch (`docs/decisions/pipeline-agent.md`, `adapter/pipeline/AgentArguments.java`).
- `repository_overrides.<remote>.commit` and `.pr` keys at every draft-review launch (`docs/decisions/pipeline-commit-keys.md`, `adapter/pipeline/CommitKeys.java`).
- Both go through `adapter/pipeline/PipelineConfiguration.java`, which rewrites one top-level entry of `~/.no-mistakes/config.yaml` in place.
- The EDI repository also carries `.no-mistakes.yaml` with `commands.test: "./mvnw -B -ntp verify"`, `document.instructions` and `review.path_instructions` for `docs/decisions/**` and `*.java` (`git show origin/main:.no-mistakes.yaml`).

### What EDI reads back

- `ObservePipelines` runs `no-mistakes axi status` in each live copy every 60 seconds and parses its `run:` block (`adapter/pipeline/AxiStatus.java`, `docs/decisions/supervision.md`).
- `PipelineSpend` and `PipelineReviews` open `~/.no-mistakes/state.sqlite` read-only for token spend and review rounds (`adapter/pipeline/PipelineSpend.java`, `PipelineReviews.java`).
- The `Pipeline` port (`application/port/Pipeline.java`) has five operations: `latestRunOf`, `tokensSpentOn`, `reviewOf`, `runAgentOn`, `keyCommitsPushedTo`.

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
- On EDI, the PR body's local verification is the evidence the captain relies on, because GitHub Actions does not run.

## Today's failure modes, with evidence

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

- EDI-104 (task `the-validation-pipeline-merges-the-base`, in flight) passes `--skip rebase` on every run, also skips `ci` on projects whose CI does not run, and has the worker merge `origin/<base>` with a merge commit before each run.
- It removes failure modes 1 and 3 for EDI and the rebase half of 4 for the step itself.
- It leaves 2 (limit exits still fail runs and force a full rerun), 5 (the daemon's monitor still dies with the daemon), 6 (the account gap), the CI monitor's own rebase on a project whose CI does run, and EDI's two writes into another tool's configuration.

## What the pipeline costs today

Numbers cover the 93 runs created from 21/10 to 27/10, read from `~/.no-mistakes/state.sqlite` and `$EDI_HOME/data/edi.db`; weighted tokens count input ×1, cache read ×0.1, cache write ×1.25 and output ×5, as EDI-75 did.

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

- The test step is the largest cost: on EDI its agent reruns `./mvnw -B -ntp verify`, which the worker already ran before committing, with a median of 15.1 minutes against 3 to 5 minutes for the command alone (the workers' verify logs).
- On a registered project the test agent does real end-to-end work, starting the backend against a throwaway MariaDB (`~/.no-mistakes/logs/01M5KWYTHVP9C61KSMZA614VNA/test.log`), which a command cannot replace.

## Options

| Option | What it is | Verdict |
|---|---|---|
| A. Keep no-mistakes with EDI-104 | `--skip rebase`, `ci` skipped where CI does not run, merge before each run | Cheapest now; leaves failure modes 2, 5 and 6 and the double configuration ownership. |
| B. EDI's own validation pipeline | A validation run in EDI's core and store, its reviewer a supervised EDI session, its CI watch a supervision reading | Recommended: removes every measured failure mode and about half the pipeline's tokens. |
| C. Change no-mistakes upstream | Ask for a merge setting, limit-aware pauses and restart-safe monitors | Outside EDI's control and timeline; the gate mirror stays. |
| D. Let the worker validate itself | The worker reviews, tests and publishes in its own session | Loses the independent reviewer, the one part that catches what the author misses. |

## The design

### Principles

- The isolated copy is the only working tree; a validation reads a pinned commit of it and never rewrites history.
- Every stage's state lives in EDI's store, so a core restart or a machine restart loses nothing and a stage resumes from its record.
- The reviewer is independent: a fresh session that reads the change against the captain's intent, never the author's session.
- The worker owns its commits: it fixes findings and merges the base in its own session, always on top, so no commit it made is ever discarded.
- Every forge call stays on the allow-list and the bound account; nothing merges and nothing force-pushes.

### The domain

- `Validation` is a run of the pipeline on one task at one head commit, with its base commit, the intent it was judged against, its stages in order, and an outcome: running, awaiting the worker, awaiting the captain, passed, failed or superseded.
- `ValidationStage` is one of `BaseSync`, `Review`, `Verify`, `Publish`, `Checks`; each holds its state, its findings, its evidence and its round.
- `Finding` carries a severity (error, warning, info), a file and line where it has one, a description and an action: `fix` (the worker's to fix), `ask-captain` (a decision) or `note` (no action).
- A new `Validation` on a head that descends from the previous one keeps the previous approvals for unchanged stages and re-reviews only the range since the approved head.
- A validation whose head does not descend from its predecessor's is refused, naming the commits it would drop: the same data-loss guard the gate mirror gave, without a mirror.
- These live in `domain/validation`, free of Spring and processes, as `ArchitectureRulesTest` requires.

### The stages

| Stage | Replaces | What runs | Who acts on a finding |
|---|---|---|---|
| `BaseSync` | rebase | `git merge-base --is-ancestor origin/<base> <head>` through `adapter.git` | The worker merges `origin/<base>` with a merge commit and validates again. |
| `Review` | intent, review, document, lint judgement | A reviewer session (below) reading the diff, the intent and the project's review instructions, including the documentation rules no-mistakes' document step checked | The worker fixes `fix` findings; `ask-captain` findings become a decision. |
| `Verify` | test, lint commands | The project's verify commands through `ProjectBuild.verifies` in a pinned detached worktree, plus the native build when `NativeImageInput` says the change reaches the image, plus `edi publish-check`; an end-to-end agent only on projects that opt in | The worker fixes a failure. |
| `Publish` | push, pr | `git push` of the head to the registered remote through the bound credential helper, after `PublicationTargets`; `Forge.openDraft` with a body carrying the intent and the `Verify` evidence | None; a refusal names the cause. |
| `Checks` | ci | A supervision reading of the merge request's checks and mergeability through `gh pr view` and `glab mr view`, per the project's checks policy (`forge` or `none`) | A failing check or a conflict becomes a `fix` finding for the worker, who merges the base; nothing rebases. |

- The commit key needs no template: the worker writes every commit, and `BranchName.workKeyCapture` already holds the key; the draft's title comes from the task's title with its key, as `DraftProposal` does today.
- When `Checks` passes, or the project's policy is `none` and `Verify` passed, EDI marks the request ready for review (a new allow-list entry: `gh pr ready` and `glab mr update --ready`), which keeps I-04: a draft first, ready once green, never merged.

### The reviewer

- The reviewer runs as an EDI-supervised session through the existing `WorkerRuntime` port, a new `LaunchKind.REVIEW` with read-only tools, in the pinned worktree, on the dispatch rules' `pipeline` route.
- It reports its findings with a new command, `edi review report --findings -`, as JSON the core validates against the `Finding` type.
- Because it is a supervised session, a usage limit pauses it and the supervision round resumes it at the reset (`usageLimitStopIn`), instead of failing the validation; its tokens are counted from its transcript into `task_cost`'s pipeline columns.
- The review instructions move from `.no-mistakes.yaml` into a project file EDI reads, `.edi/review.md` with path-scoped sections, so EDI owns the format; the brief's craft rules stay where they are.

### The worker's side

- `edi validate` starts a validation of the copy's head and returns at once.
- Each stage that needs the worker arrives as one inbox message with its findings, and the worker answers with `edi validation answer <stage> --fixed <ids> | --dismiss <ids> --why "<reason>"` after committing its fixes, or raises `needs-decision` for an `ask-captain` finding, as it does for `nm-` keys today.
- The worker waits on its validation exactly as it waits on its pipeline today, so supervision's `Awaited` rule keeps it from being stale.
- The brief's draft-review section shrinks to: commit, `edi validate`, act on each inbox message, done when the request is ready for review at the last commit.

### Store and events

- Three new tables, `validation`, `validation_stage` and `validation_finding`, by Flyway migration, replace `pipeline_run`.
- Events `ValidationStarted`, `StageFinished`, `FindingsRaised`, `ValidationPassed` and `ValidationFailed` feed the timeline, the sitrep and the console the way `PipelineRunObserved` does now.
- The supervision round drives the stages: it starts the next stage when the previous passed, reads checks every `edi.delivery.pipelines` (PT60S), and resumes a paused reviewer, so there is no daemon and no long-lived monitor to lose.
- Concurrency is EDI's: `Verify` runs queue per machine, which also answers the pile-up that the local-native-builds decision only warns about.

### Ports and architecture

- No seventh port: the reviewer runs through `WorkerRuntime`, verify commands through the concrete `ProjectBuild`, pushes through the concrete `Git` adapter, and checks through `Forge`.
- When the cut-over ends, the `Pipeline` port, `NoMistakesPipeline`, `AgentArguments`, `CommitKeys`, `PipelineConfiguration`, `AxiStatus`, `PipelineSpend`, `PipelineReviews` and `Program.NO_MISTAKES` go, leaving five ports; `docs/decisions/ports-and-architecture-rules.md` and `PortInventoryTest` change with that slice.
- `Forge` gains `checksOf(forge, address)` and `markReady(forge, address)`; `ForgeCommand` gains `gh pr ready` and `glab mr update` restricted to `--ready`; AT-03b keeps proving that no merge argument exists.
- `Git` gains a push that refuses `--force` and any target `PublicationTargets` refuses, through the repository-local credential helper `IsolatedCopies` already sets, which closes the account gap.

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

## Migration plan

Each slice is one ship on `feature/EDI-105-<n>` or its own key, delivered as a draft pull request; no-mistakes keeps validating EDI's own changes until slice 6 cuts EDI over, so the pipeline never validates the code that is replacing it in the same run.

### Slice 0: the shortcut (EDI-104, in flight)

- Already dispatched: `--skip rebase` on every run, `ci` skipped where CI does not run, and a merge of the base before each run.

### Slice 1: the validation record and the Verify stage, in shadow

- Builds `domain/validation`, the three tables, the events, `edi validate` and the `Verify` stage only; briefs do not change, and the worker may run it beside no-mistakes.
- Acceptance: a validation of a copy's head runs the project's verify commands in a pinned detached worktree and stores their exit, duration and log path as evidence.
- Acceptance: stopping the core in the middle of `Verify` and starting it again finishes the stage without a second build (a test with the `Clock` port and a recording `ProjectBuild`).
- Acceptance: two validations on the same machine run their verify commands one after the other.
- Acceptance: a validation whose head does not descend from the previous one's is refused, naming the commits it would drop.
- Acceptance: `PortInventoryTest` and `ArchitectureRulesTest` pass unchanged.

### Slice 2: the reviewer

- Adds `LaunchKind.REVIEW`, `edi review report`, the `Review` stage, `.edi/review.md` (seeded from `.no-mistakes.yaml` on EDI), and the documentation rules in the review prompt.
- Acceptance: a reviewer session runs on the dispatch rules' `pipeline` route with read-only tools in the pinned worktree, and its findings are stored and sent to the worker as one inbox message.
- Acceptance: a reviewer that hits a usage limit is resumed at the reset by the supervision round, and the validation stays running, not failed (a test on a recorded transcript with a limit stop).
- Acceptance: an `ask-captain` finding reaches the worker as a decision to raise, never as one the worker may answer.
- Acceptance: the reviewer's tokens appear in `task_cost`'s pipeline columns.

### Slice 3: the fix loop and BaseSync

- Adds `edi validation answer`, the delta re-review, and the `BaseSync` stage; the worker merges the base itself.
- Acceptance: after a fix commit, the next validation re-reviews only the range from the approved head, and the stage's round count rises by one.
- Acceptance: a branch behind its base gets a `BaseSync` finding telling the worker to merge `origin/<base>` with a merge commit, and no code path in EDI runs `git rebase` (an `ArchitectureRulesTest` rule on `adapter.git`'s calls).
- Acceptance: a dismissed finding needs a reason, and the reason is stored with it.

### Slice 4: Publish

- Adds the guarded push in `Git` and folds `DeliverDraft` into the `Publish` stage, with the evidence in the request's body.
- Acceptance: the push goes through the bound credential helper whatever `gh` account is active (a test with two recorded accounts).
- Acceptance: a push to any repository other than the registered one is refused (AT-43 extended), and no allow-list entry or argument carries `--force` (AT-03b extended).
- Acceptance: the draft's title starts with the work key and its body lists each verify command with its result.

### Slice 5: Checks and ready for review

- Adds `Forge.checksOf` and `markReady`, the project's checks policy in the registry (taking over EDI-104's skip list), and the `Checks` stage in the supervision round.
- Acceptance: a project with policy `none` (EDI while Actions is blocked) is marked ready after `Verify` and `Publish` pass, with no CI finding raised.
- Acceptance: a failing check or a conflict with the base reaches the worker as a `fix` finding, and nothing rebases or force-pushes.
- Acceptance: restarting the core while a request's checks are pending loses nothing; the next round reads them again.
- Acceptance: the request is marked ready only when every required check passed at the head EDI pushed.

### Slice 6: cut-over and removal

- Adds a per-project `validation` setting in the registry, `edi` or `no-mistakes`, and switches the brief per project: EDI first, then the other registered projects after five deliveries each without a no-mistakes fallback.
- A registered project keeps an opt-in end-to-end test agent in `Verify` (a reviewer-like session told to exercise the change against a throwaway database), because its test step does work a command cannot.
- Once every project is on `edi`, removes the no-mistakes adapter, the `Pipeline` port, `Program.NO_MISTAKES`, the configuration writers and their tests, rewrites `docs/decisions/pipeline-agent.md`, `pipeline-commit-keys.md`, `supervision.md` and `ports-and-architecture-rules.md`, and retires `docs/verification/no-mistakes.md`.
- Acceptance: no tracked file outside the decision history names no-mistakes, and `PortInventoryTest` counts five ports under the updated record.
- Acceptance: the shadow comparison (slices 1 to 5 run beside no-mistakes on at least ten EDI deliveries) shows no finding no-mistakes raised that EDI's review missed and the captain later wanted, and is recorded in the cut-over pull request.

## Cost

### Running cost against today

Estimated from the per-step figures above, per 93 runs:

| Step | Today | EDI's pipeline | Basis |
|---|---|---|---|
| review and document | 80.9M | about 70M | one reviewer reads the documentation rules too |
| review fix | 26.8M | about 13M | fixes in the worker's warm session instead of a cold agent; an estimate |
| test and test fix | 113.4M | about 40M | commands on EDI, the end-to-end agent kept on that project (about a third of today's test tokens) |
| ci | 8.8M | 0 | a forge reading, no agent |
| pr, rebase, lint | 6.0M | 0 | deterministic title and body |
| Total | 236.1M | about 123M | about 48% less, about 1.3M weighted per run |

- Time: an EDI run's test step falls from a median of 15 minutes to the verify command's 3 to 5, and gates no longer wait on billing-blocked CI.
- Failures: usage limits pause instead of failing (9 of 13 failed runs), core restarts lose nothing (6 more), and the rebase strand cannot happen.

### Cost of building it

- Six slices at roughly EDI's mean ship cost (5.4M weighted for the worker and 3.1M for its pipeline) come to about 50M weighted tokens, plus review rounds; a fair estimate is 60M to 80M and two to three working days of fleet time.
- At about ten deliveries a working day and about 1.5M weighted saved per delivery, the build pays back in roughly four to six working days, before counting the captain's time on stranded gates.

## Risks

- EDI takes on code that no-mistakes maintains today; the slices keep it small by reusing the copies, the runtime, the store and the forge adapters, and dropping the mirror entirely.
- Review quality could drop if the reviewer's prompt is weaker than no-mistakes'; the shadow period in slice 6 measures that before any project leaves no-mistakes.
- The worker fixing its own findings could argue its way past them; a dismissal needs a stored reason, and the reviewer re-reads the delta.
- EDI validates its own changes; until slice 6, no-mistakes validates EDI's code, so a broken EDI pipeline cannot approve its own fix.

## Questions for the captain

The research board put five questions before the captain, the decision card first; EDI raised the card as D47 and bound it to question Q1.
The captain parked the board on 28 October 2026 until the lifecycle upgrade lands (see EDI-105 in the backlog).

- Card Q1 (D47): build EDI's own validation pipeline in the six slices above (recommended), build only slices 1 and 2 in shadow and decide on measurements, or keep no-mistakes with EDI-104 and ship nothing.
- Q2: who fixes review findings: the worker in its own session (recommended), or a fresh fix agent as today.
- Q3: the test stage: commands everywhere with an opt-in end-to-end agent per project (recommended), an agent test step everywhere as today, or commands only.
- Q4: how the reviewer runs: as a supervised EDI session that pauses on limits (recommended), or as a headless `claude -p` call EDI retries after the reset.
- Q5: who marks a request ready for review: EDI once checks pass or the project has none (recommended), or the worker as today.

## Evidence

The evidence lives on the captain's machine: the pipeline tool's state database and logs (read-only), EDI's store tables `event` and `task_cost`, and the no-mistakes source at the version the report names.
The queries are described in the sections above; rerun them there when the research is revised.

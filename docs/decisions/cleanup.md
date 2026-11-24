# Cleanup retires a task only once its work has provably landed, and records what the task cost

**Kind:** Design

## Context

Cleanup refuses while any commit or file is not provably landed, and only the captain's explicit word discards (I-12).
The pipeline rebuilds or rebases commits, and the forge squash-merges, so ancestry alone would call landed work unlanded.
A squash matches no commit of a branch with several by patch, and once the base changes the same lines again its content no longer matches either.
A scout is cleaned up only once its report exists and its done passed, and its nested repositories go first.
At cleanup EDI records the task's cost, the workers' transcript usage plus the pipeline's spend, and never routes by it (I-17).
Whether an effort above high pays can only be judged once each cost says which model and effort spent it, beside the review rounds and findings the pipeline counted.

## Decision

`LandedWorkCheck` fetches the clone's origin and gathers the facts with git; `LandedWork` in the domain decides.
Work is landed exactly when the copy has nothing uncommitted or untracked beyond EDI's own state, holds no nested repository, every commit `git cherry` lists is in `origin/<base>` by ancestry, by an equal patch, by content (when `merge-tree` of the base and the head gives the base's tree), or by the merge of its request (when the forge shows the task's request merged into the base, the commit that merge landed is in `origin/<base>`, and the commit is the request's merged head, one before it, or the same change by patch as one of them, since the pipeline may have rebuilt them), and, for `local-branch`, the captain's checkout holds the head.
So every commit up to the merged head lands however the forge merged it, and a commit after it is named.
When the copy is gone the branch in the clone stands in for it; each failure is a finding naming the path, the repository or the commit.
`CleanUpTask` refuses a task that is not in flight or is held, a scout without an accepted done and its `report.md`, and a task whose latest worker still works unless its recorded delivery is merged into the base branch, since a merge into the base the forge shows hands the work over as an accepted done does, while a merge into another branch does not ([merge-following](merge-following.md)).
A refusal records `CleanupRefused` with the findings, unless the task's latest refusal named the same findings, and says that nothing is discarded without the captain's word.
The core tries the clean-up of a followed merge again in every delivery round while its task is in flight, so it holds once what it found has landed or been discarded ([merge-following](merge-following.md)).
A landed task is retired by `CopyRetirement`: its cost is counted (`TaskCosts`, each transcript once from its earliest launch, plus the pipeline's spend or unknown, with the route its first worker was launched on, the model and effort it was dispatched at), its worker windows are closed, the copy is removed with `git worktree remove`, the branch is deleted, the workers' state directories with their settings and tokens are deleted, and in one transaction the workers and boards are retired and the task moves to RETIRED ([task-lifecycle](task-lifecycle.md)), with `TaskCostRecorded` and `TaskCleanedUp`.
`task_cost` keeps that route beside the raw token counts and `TaskCostRecorded` carries it; a cost counted before the route was recorded took its first worker's route when the store migrated, and a task that never launched a worker has none.
`EffortYield` sums the costs of a set of tasks that know their route and their review rounds and findings (`task_review`) per effort, with the tokens weighted as input-token equivalents (`TokenUsage.weighted`: input at 1, cache read at 0.1, cache write at 1.25, output at 5, the measurements' weighting); no dollar figure is given, because the plan is not billed per token.
The Week view's rows per effort count ship tasks only (`WeekOfWork`), since a scout's research is never reviewed by the pipeline and would skew the cost per task.
Dropping a task and discarding work share the same retirement steps on the captain's word ([captains-word](captains-word.md)).
A discard whose word is the answer that chose a follow-up task for the commits after a merged head is refused until the branch of a task dispatched since that decision holds them on the forge, by patch or by content (`FollowUpCarriage`), and the refusal names what is missing, so nothing is lost by the order the two tasks are handled in.
`edi task recount-costs --since <date>` recounts the pipeline's spend of every task cost counted since that day (`RecountTaskCosts`): it reads the spend of each task's branch again, keeps what the workers spent and when the cost was counted, and saves each cost it changes with `TaskCostRecounted`, the cost before and after; a spend the pipeline no longer knows, because its store is unreadable or holds no run of the branch any more, leaves the recorded one.

## Why

The rule that protects unlanded work is a few lines of domain code tested in milliseconds, while git gathers the facts it judges.
A finished task leaves nothing behind but its records, a refused cleanup leaves everything as it was, and a merged request no longer holds its task in flight.
The first worker's route is the one the dispatch chose, which is the choice the yield is meant to judge; a later incarnation runs on another route only when the dispatch rules changed in between or the captain chose another model or effort for its recovery.
A recount through the core keeps the core the only writer of the store and the event log the record of what changed, and it serves whenever the pipeline's reading of its spend is corrected.

## Alternatives rejected

- Ancestry alone - every squash merge and pipeline rebuild would look unlanded.
- Taking the forge's merge as the landing of the whole branch - a merged request can still leave commits after its head and unpushed work in the copy.
- Crediting the merged head before the commit the merge landed is in the base - a clone that has not fetched the merge would retire work its base does not hold yet.
- Trying a refused clean-up only once - a merged task whose late commit landed later, or whose unlanded work was discarded, would stay in flight until someone ran the clean-up by hand.
- Recording every refusal of every round - the primary would be woken every ten minutes with the same findings.
- Discarding on the follow-up answer at once - the late commits would be gone if the follow-up task had not yet carried them to the forge.
- Correcting recorded costs at a start of the core, in a migration or with a script against the store - one-off code that lingers, a migration reaching into the pipeline's store, or a write outside the core with no event.
- Recording the route in `task_review` as well - the review and the cost share the task, so one copy of the route is enough.
- Recording the route of the latest worker, or of the one that spent most - a recovery's fallback model would take the credit for the effort the dispatch chose.
- Storing only weighted tokens - another weighting could no longer be applied to the recorded costs.
- Pooling scouts with ships in the rows per effort - a scout's unreviewed research lowers the cost per task of the efforts it runs at, so the efforts no longer compare like with like.
- Removing the copy with `--force`, or deleting the inbox rows - untracked work would vanish unseen, and the rows are the task's history.
- Waiting for the worker's done after a merge - a worker idle until the merge never reports one by itself.

## Consequences

- Good: AT-12 runs every case on real repositories, and `edi task show` shows the task's cost, request and pipeline run.
- Bad: a branch whose change the base later reverted counts as landed by its patch, and a worker still running when its request merges is closed, keeping only what the landed-work test protects.
- Bad: a refused clean-up costs one fetch of the clone in every delivery round until it holds.

## When to revisit

When a delivery mode lands work without its commits or content reaching the base, or a worker should be told before it is closed after its merge.

## How we got here

- 16/10/2026: slice S5 built the landed-work test and cleanup with the task's cost.
- 17/10/2026: slice S7 retired board listeners at cleanup; slice S8 shared the retirement with dropping and discarding.
- 21/10/2026: a merge the forge shows started to hand a busy worker's work over (EDI-23).
- 09/11/2026: the pipeline's spend had been read with its cache reads and writes counted again as input since 2 November 2026, so `edi task recount-costs` recounts the costs recorded since a day (EDI-235).
- 12/11/2026: the stored stage replaced the task's state, so a cleanup moves the task through LANDED to RETIRED and a scout's accepted done is its DELIVERED stage (EDI-85).
- 20/11/2026: an effort above high took 23.5% of the first week's worker spend with nothing to show whether it paid, so each cost records the route its task was dispatched on and the Week view shows the cost and review yield per effort of the week's ship tasks (EDI-97).
- 24/11/2026: only a merge into the base branch hands a busy worker's work over, since a request merged into a slice branch has not landed (EDI-244).
- 24/11/2026: a squash merge failed the landed-work test once the base changed the same lines again, and a refused clean-up was never tried again, so a merged request lands every commit up to its merged head and the core retries a refused clean-up every round (EDI-70).

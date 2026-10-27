# Every pipeline run skips the rebase step, and the CI step on a project whose CI does not run, and the worker merges the base in itself

**Kind:** Design

## Context

The validation pipeline (no-mistakes) rebases the branch onto its base in its `rebase` step, rewriting the worker's commits.
A run that fails after its own rebase leaves the gate on the commit from before the rebase, so the pipeline refuses every later run on that branch as work at risk of being lost; on 27/10/2026 two ships were stuck that way at once.
No-mistakes 1.84.0 has no setting that makes it merge instead of rebase, but `axi run --skip` takes a comma-separated list of steps to leave out of the run ([docs/verification/no-mistakes.md](../verification/no-mistakes.md)).
Its `ci` step waits for the forge's CI and, while it waits, rebases the branch again whenever the base moves; on a project whose CI does not run, it only ever waits.

## Decision

`ProjectEntry.skippedPipelineSteps` is the one place that decides which `PipelineStep`s a project's pipeline runs leave out: always `rebase`, and `ci` as well when the project's registry entry says `ciRuns: false`.
A ship's brief gives the pipeline call with `--skip` and that list (`BriefSections.delivery`), so every run a worker starts skips them.
The brief tells the worker to fetch before each run and, when the branch is behind `origin/<base>`, to merge it in with `git merge --no-ff` and a message that starts with the work key, and never to rebase.
The hard rules forbid merging into a base branch, not merging the base into the worker's own branch.

## Why

A merge commit adds to the worker's history instead of rewriting it, so a failed run leaves nothing the next run would refuse, and the captain asked for merges over rebases.
Deciding the list from the registry keeps a project with working CI waiting on its checks, while a project whose CI never runs no longer has a run that waits on nothing and rebases behind the worker's back.

## Alternatives rejected

- A setting in the pipeline's configuration - 1.84.0 has none for the rebase strategy or for skipping steps.
- Letting the pipeline rebase and recovering the gate after a failed run - every stuck run would need someone to clear it by hand.
- Running the pipeline's steps from EDI's own code flow - the lasting answer, left to a research task; this change is the shortcut until then.

## Consequences

- Good: a failed run no longer strands a branch, and the worker's commits reach the merge request as the worker wrote them.
- Bad: the pipeline's review and tests run on the branch as the worker last merged it, so a base that moves during the run is not part of what they checked.
- Bad: a project whose CI starts running again needs `ciRuns: false` removed from its entry, or its runs keep skipping the wait for checks.

## When to revisit

When EDI drives the validation steps in its own code flow, or the pipeline gains a merge strategy for its base.

## How we got here

- 27/10/2026: runs that failed after their own rebase refused every later run on two branches, so every run skips the rebase step, a project whose CI does not run also skips the CI step, and the worker merges the base in (EDI-104).

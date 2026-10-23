# Work is delivered as a merge request the captain merges, or into the captain's checkout, and a done counts only once its claims hold

**Kind:** Architecture

## Context

Workers do the work on their own branch and deliver a reviewable copy on the real repository; the captain merges by hand, and on some projects a merge to the main branch deploys.
EDI has no merge capability of any kind (I-03), and every merge request EDI or its pipeline opens is a draft (I-04).
Every merge request is ready for review by the time it is presented to the captain, and the captain may merge it before the worker's done arrives.
No pushed `pom.xml` may gain a SNAPSHOT version (I-11), and a delivery into the captain's checkout must leave their working tree as it was (I-07).

## Decision

A project's delivery mode is `draft-review`, `local-branch` or `local-apply`, and a task may carry its own; the registry refuses `local-apply` as a default, so it is asked for per task (`DeliveryMode.registryDefault`).
For `draft-review`, the worker runs the validation pipeline with the intent EDI wrote ([worker-brief](worker-brief.md)); the pipeline pushes the branch and opens the request as a draft, the worker marks it ready for review once its checks are green, and the captain merges.
`RecordStatus` asks `DoneClaims` before it records a ship's `done`: the branch adds no `-SNAPSHOT` word to any `pom.xml` line since `origin/<base>` (`SnapshotVersions`), and for `draft-review` the open request from the task's branch, or else the latest merged one, comes from that branch, targets the base and shows the copy's head; it may be a draft, ready or merged, and only a request closed without a merge is a gap.
A refused done names every gap in one sentence, is recorded as `DeliveryRefused`, and leaves the worker's status as it was; a scout's done carries no delivery claim, and a mechanical task's done is checked first ([mechanical-tasks](mechanical-tasks.md)).
`DeliverAcceptedWork` then delivers by mode.
`local-branch` runs `git -C <captainCheckout> fetch <worktree> <branch>:<branch>`, which moves one ref forward or is refused, and records `BranchDeliveredToCaptain` only when the checkout's branch holds the copy's head (`CaptainCheckouts`).
`local-apply` merges the base and the branch in memory with `git merge-tree`, writes the diff from the base to the result as `change.patch`, and applies it with `git apply` only when it touches no file with uncommitted work and deletes, renames or changes the mode of nothing; otherwise it records `ChangeNotApplied` with the patch's path.
When the pipeline could not open the request, `edi deliver <task>` does it behind the same guards: the push checks of `PublishCheck.checkPush` ([publish-guard](publish-guard.md)), a draft only, the key and title as its title, a body without agent attribution (`DraftProposal`), and every claim above.

## Why

The captain keeps the merge, which may deploy, in their own hands, and every delivery is reviewable before it lands.
The done checks what the captain needs, the work at its head on the forge from the right branch to the right base, whatever order the pipeline, the worker and the captain act in.
The captain's checkout changes only in the way they asked, and anything that could lose their work waits for their own look at the patch.

## Alternatives rejected

- EDI merging green requests - forbidden by I-03.
- Checking claims later in the supervision round - the worker would stop believing its work delivered.
- Demanding, or restoring, the draft state at the done - it undoes the captain's own marking.
- Pushing into the captain's checkout, or cherry-picking into it - a push can move a checked-out working tree, and a cherry-pick commits.

## Consequences

- Good: nothing reaches a base branch without the captain's action, and a worker learns at once, in its own terminal, what keeps its work from being delivered.
- Bad: every delivery waits for the captain, a done waits for one forge call, and a forge outage refuses a done until the forge answers.
- Bad: a security-sensitive change that only adds or modifies files is applied locally like any other.

## When to revisit

Never for the merge authority; the claims are revisited when the pipeline itself marks requests ready or refuses SNAPSHOT versions.

## How we got here

- 15/10/2026: the captain set the flow of drafts they merge; the specification had the pipeline open the draft itself.
- 16/10/2026: slice S3 fetched a `local-branch` ship into the captain's checkout by its ref; slice S5 added the claims of a done, `local-apply` and `edi deliver`.
- 21/10/2026: a done was refused because the captain had marked or merged its request first, so the done stopped demanding a draft (EDI-23).

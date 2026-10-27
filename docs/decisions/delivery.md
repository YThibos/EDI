# Work is delivered as a merge request the captain merges, into the captain's checkout, or by its report, and a done counts only once its claims hold

**Kind:** Architecture

## Context

Workers do the work on their own branch and deliver a reviewable copy on the real repository; the captain merges by hand, and on some projects a merge to the main branch deploys.
EDI has no merge capability of any kind (I-03), and every merge request EDI or its pipeline opens is a draft (I-04).
Every merge request is ready for review by the time it is presented to the captain, and the captain may merge it before the worker's done arrives.
No pushed `pom.xml` may gain a SNAPSHOT version (I-11), and a delivery into the captain's checkout must leave their working tree as it was (I-07).
Some ships are operations on the machine with nothing to commit: no merge request can carry them, so their done was refused, and a `resolved` status left the task in flight with its worker and copy.
A busy worker receives a steer only when its turn ends ([steering](steering.md)), so a done reported inside that turn ran ahead of the steer, and its request was merged while the worker was still changing it.

## Decision

A project's delivery mode is `draft-review`, `local-branch`, `local-apply` or `report`, and a task may carry its own; the registry refuses `local-apply` and `report` as defaults, so both are asked for per task (`DeliveryMode.registryDefault`).
For `draft-review`, the worker runs the validation pipeline with the intent EDI wrote ([worker-brief](worker-brief.md)); the pipeline pushes the branch and opens the request as a draft, the worker marks it ready for review once its checks are green, and the captain merges.
`RecordStatus` asks `DoneClaims` before it records any `done`, and `DoneClaims` refuses first of all while the task's inbox holds a message the worker has not acknowledged, naming every such message's number, for a scout as for a ship.
For a ship it then checks that the branch adds no `-SNAPSHOT` word to any `pom.xml` line since `origin/<base>` (`SnapshotVersions`), and for `draft-review` the open request from the task's branch, or else the latest merged one, comes from that branch, targets the base and shows the copy's head; it may be a draft, ready or merged, and only a request closed without a merge is a gap.
For `report` the task directory holds `report.md` (`Workplace.report`) and the copy holds no change, which `LandedWorkCheck` decides as it does for cleanup: nothing uncommitted, no nested repository and no commit beyond what `origin/<base>` holds.
A scout's done claims only its findings: the task directory holds `report.md`, or the done is refused naming that path.
A refused done names every gap in one sentence, is recorded as `DeliveryRefused`, and leaves the worker's status as it was.
These claims are checked when the done is reported, and for a mechanical task the mechanical check then gates the delivery ([mechanical-tasks](mechanical-tasks.md)).
`DeliverAcceptedWork` then delivers by mode, and for `report` runs `CleanUpTask` at once, since the report is the whole delivery and nothing waits for the captain.
`local-branch` runs `git -C <captainCheckout> fetch <worktree> <branch>:<branch>`, which moves one ref forward or is refused, and records `BranchDeliveredToCaptain` only when the checkout's branch holds the copy's head (`CaptainCheckouts`).
`local-apply` merges the base and the branch in memory with `git merge-tree`, writes the diff from the base to the result as `change.patch`, and applies it with `git apply` only when it touches no file with uncommitted work and deletes, renames or changes the mode of nothing; otherwise it records `ChangeNotApplied` with the patch's path.
When the pipeline could not open the request, `edi deliver <task>` does it behind the same guards: the push checks of `PublishCheck.checkPush` ([publish-guard](publish-guard.md)), a draft only, the key and title as its title, a body without agent attribution (`DraftProposal`), and every claim above.
The push itself is refused a SNAPSHOT version in any outgoing commit ([publish-guard](publish-guard.md)); the done checks the branch again, since a `local-branch` or `local-apply` delivery is never pushed.

## Why

The captain keeps the merge, which may deploy, in their own hands, and every delivery is reviewable before it lands.
The done checks what the captain needs, the work at its head on the forge from the right branch to the right base, whatever order the pipeline, the worker and the captain act in.
A message that waits unacknowledged may still change the work, so no other claim is worth checking until the worker has acted on it, and the refusal sends the worker to its inbox in its own terminal.
The captain's checkout changes only in the way they asked, and anything that could lose their work waits for their own look at the patch.
A machine operation is a property of one task, and a project defaulting to `report` would have every code ship refused at its done.
An operation finishes on what proves it, its report and a copy with nothing in it to lose, instead of staying in flight for want of a merge request.

## Alternatives rejected

- EDI merging green requests - forbidden by I-03.
- Checking claims later in the supervision round - the worker would stop believing its work delivered.
- Refusing only while a message waits unreceived - a worker that has read a steer but not yet acted on it would still report done ahead of it.
- Demanding, or restoring, the draft state at the done - it undoes the captain's own marking.
- Pushing into the captain's checkout, or cherry-picking into it - a push can move a checked-out working tree, and a cherry-pick commits.
- A task kind for operations - an operation is a ship in every respect but how its work arrives, so a kind would repeat each ship rule in intake, dispatch, branches and briefs.
- Accepting an operation's done and leaving its retirement to `edi cleanup` - the task would stay in flight until someone remembered it, the failure the mode exists to remove.

## Consequences

- Good: nothing reaches a base branch without the captain's action, and a worker learns at once, in its own terminal, what keeps its work from being delivered.
- Bad: every delivery waits for the captain, a done waits for one forge call, and a forge outage refuses a done until the forge answers.
- Bad: a security-sensitive change that only adds or modifies files is applied locally like any other.
- Bad: a worker that acts on a message but forgets `edi inbox ack` is refused until it acknowledges it.
- Bad: a `report` done fetches the clone's origin twice, for its claim and its cleanup, and closes the worker's window while its `edi status done` returns.

## When to revisit

Never for the merge authority; the claims are revisited when the pipeline itself marks requests ready.

## How we got here

- 15/10/2026: the captain set the flow of drafts they merge; the specification had the pipeline open the draft itself.
- 16/10/2026: slice S3 fetched a `local-branch` ship into the captain's checkout by its ref; slice S5 added the claims of a done, `local-apply` and `edi deliver`.
- 21/10/2026: a done was refused because the captain had marked or merged its request first, so the done stopped demanding a draft (EDI-23).
- 23/10/2026: an operation with nothing to push could not report done, so the `report` delivery finishes a task on its report and a copy that holds no change, and the captain kept it out of the registry's defaults (EDI-41).
- 26/10/2026: a done reported inside a turn ran ahead of a steer that arrived at the turn's end, so a done waits until every message in the task's inbox is acknowledged, and the record now places the mechanical check after the claims, gating the delivery (EDI-52).
- 26/10/2026: the EDI-74 review found a scout's done accepted without its report, which only its cleanup asked for, so the done claims the report (EDI-77).
- 27/10/2026: the publish guard refuses a SNAPSHOT version at the push, and the done keeps its own check for deliveries that are never pushed (EDI-78).

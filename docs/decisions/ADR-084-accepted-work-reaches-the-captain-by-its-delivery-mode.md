# ADR-084: Accepted work reaches the captain by its delivery mode, and local-apply is asked for per task

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-062 fetched a ready `local-branch` ship into the captain's checkout by its ref only and left two things to slice S5: checking the delivery against the head, and `local-apply`.
Section 8.4: `local-apply` is never a registry default but a mode the captain asks for on one task ("locally only, without committing"); section 8.1 gives a task its own delivery mode.
Section 13.4: `local-apply` applies the change as a patch into the captain's working tree without committing, after checking the files it touches are clean there; when the branch carries commits already merged elsewhere only the relevant change is applied as a scoped patch; destructive, irreversible or security-sensitive changes are surfaced before applying.

**Decision:** A task may carry its own delivery mode (`edi task add --delivery`), stored on the task, and otherwise follows its project's; the registry refuses `delivery: local-apply`.
`DeliverAcceptedWork` delivers an accepted ship by its mode: `draft-review` records the draft, `local-branch` and `local-apply` go to `CaptainCheckouts`.
`local-branch` fetches the branch into the captain's checkout as ADR-062 decided and records `BranchDeliveredToCaptain` only when the checkout's branch then holds the isolated copy's head; any other outcome is `BranchDeliveryFailed`, which now wakes the primary.
`local-apply` fetches the clone's origin, merges the base and the branch in memory with `git merge-tree`, and takes the diff from the base to that result as the scoped patch, so commits the base already holds contribute nothing; it writes the patch to `change.patch` in the task directory, and applies it to the captain's working tree with `git apply`, after `--check`, only when the change touches no file with uncommitted work in his checkout and deletes, renames or changes the mode of nothing.
Otherwise it applies nothing and records `ChangeNotApplied` with the reason and the patch's path, which wakes the primary; a successful apply is `ChangeAppliedToCaptain` with the files.

**Motivation:** The captain's checkout changes only in the way he asked for, and anything that could lose his work or surprise him waits for his own look at the patch.

**Alternatives considered:**
- Cherry-picking the branch's commits into his checkout - it commits, which `local-apply` must not.
- Applying deletions and renames when the files are clean - they cannot be undone from the working tree alone, so they stay for his eye.

**Consequences:**
- Good: a diverged branch in his checkout, a dirty file or a deletion each stop the delivery with a precise reason, and his checkout stays as it was.
- Bad: a security-sensitive change that only adds or modifies files is applied like any other; the publish guard does not run on a local apply.

**Revisit when:** The captain asks for local-apply to stage the change in his index, or to surface other kinds of change.

**Sources:** EDI rewrite specification, sections 8.1, 8.4, 13.4 and I-07; ADR-062; `application.delivery.DeliverAcceptedWork`, `CaptainCheckouts`, `domain.delivery.LocalChange`, `ChangedPath`, `PathChange`, `domain.project.DeliveryMode.registryDefault`, `domain.task.Intake.deliveryOr`, `adapter.git.Git.changeSince`, `uncommittedAmong`, `applyToTheWorkingTree`, `adapter.git.RawDiff`.

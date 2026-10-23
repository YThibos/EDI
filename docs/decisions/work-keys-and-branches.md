# Every piece of work carries a key, and a branch name is built from the work's kind, its key and, beside another task's branch, a follow-up number

**Kind:** Design

## Context

Branches carry a work key and every commit subject starts with it (I-08, I-10).
On the work laptop the key is a JIRA ticket, while the private desktop and EDI itself have no external tracker.
Branches are `feature|chore|research/<KEY>-<n>`, never `fm/` or `edi/`, and the old harness once branched a project on `fm/` because a per-project setting was missing.
The captain splits one ticket's work into several tasks on its key, and git refuses to put a second worktree on a branch another worktree holds, so the second dispatch on a key failed.

## Decision

A project without an external tracker gets EDI-native keys `<KEY>-<n>` from a sequence per key prefix in the store, set with `tracker.projectKey` in the registry; changing the prefix affects future tasks only.
The next key is one above the larger of the sequence and the highest number any task already carries under the prefix, so keys given by hand or migrated are never issued again (`WorkKeySequenceStore`).
EDI's own repository uses `EDI-<n>`, and `EDI-1` is the evergreen upkeep key, so Renovate's commits and pull requests carry a key too ([dependency-upkeep-and-scanners](dependency-upkeep-and-scanners.md)).
How a task gets its key at intake, from either kind of tracker, is in [trackers-and-time-booking](trackers-and-time-booking.md).
`BranchName` holds a `BranchKind` and a `WorkKey` and cannot be built without a key; `BranchName.parse` refuses `fm/` and `edi/` by name and every other shape that is not kind, slash, key and an optional follow-up number.
A dispatch builds the branch once: a scout on `research/<KEY>-<n>`, a ship on the kind its dispatch category names, which the project's `branchKinds` must allow ([dispatch-routing](dispatch-routing.md)).
When another task on the key was dispatched or adopted on a branch of that kind (`DispatchStore.branchesOfTasksKeyedOn`, `AdoptionStore.branchesOfTasksKeyedOn`), `BranchName.forWorkBeside` numbers the new branch one above the highest follow-up among them, so the second task gets `<kind>/<KEY>-<n>-1`; a closed task's branch counts too, so no number is ever given twice.
The branch is recorded on the task's dispatch and reused by every later launch, and a task without a key is refused at dispatch.

## Why

Branches and commits are keyed from day one on every machine, and a later EDI-native work board keeps the same keys.
No configuration can produce a harness prefix, because no prefix is configured anywhere.
A follow-up number keeps I-08's kind and key at the front of the name, and the store already holds every branch EDI gave, so distinct tasks never share one.

## Alternatives rejected

- No keys where no tracker exists - breaks I-08 and I-10 on the desktop.
- A per-project branch prefix in the registry - the failure the old harness had.
- An `--branch-kind` option on `edi dispatch` - a second place to decide what the category already says.
- A suffix from the task's id - long names, and a second naming scheme beside I-08's.
- Refusing a second task on a key - the captain splits a ticket's work into tasks on purpose.
- Reusing the number of a closed task's branch - a merged request from the old branch would answer the new task's done ([delivery](delivery.md)).
- Asking the clone which branches exist - a dispatch would run git inside its transaction, for branches EDI did not make.

## Consequences

- Good: one branch and commit convention everywhere, and AT-08 holds for every registered project.
- Bad: EDI owns a key sequence per prefix, two projects sharing a prefix share its sequence, and a ship whose nature differs from its category's branch kind needs another category.
- Bad: a branch made outside EDI under the name a follow-up would take still makes that dispatch fail.

## When to revisit

When an EDI-native work board replaces the external tracker on a machine, or a branch made outside EDI takes a follow-up's name.

## How we got here

- 15/10/2026: the captain asked for project keys in their own development too; the specification gave untracked projects EDI-native keys and EDI the upkeep key `EDI-1`.
- 16/10/2026: slice S3 built branch names from kind and key; slice S6 moved the key sequence into the store, seeded above every key already in use.
- 23/10/2026: a second task on a key could not be dispatched because the first held its branch, so each task beside another on the same kind and key gets the next follow-up number (EDI-41).

# EDI-70 Landing by the merged head: squash-merge cleanup

| Field | Value |
|---|---|
| Key | EDI-70 |
| Kind | ship, a change to the repository, on branch `feature/EDI-70` |
| Wave and order | wave 3, order 9 |
| Source | EDI-74 plan, step 13, with EDI-52 slice 18b |
| Size | M |
| Blocked by | [EDI-85](EDI-85.md) |
| Status | open |
| Cloud session | ok |

## Goal

A merged task lands by its merged head: every commit up to it counts as landed however the forge merged it, a commit after it is named, a merge while the task was still changing raises one decision, and a refused cleanup is retried by the core.

## Context

Cleanup compares the copy with the base by patch identity, which matches no commit after a squash merge, and rescues itself only when the base and the head produce exactly the same tree, which fails once the base changes the same lines again or the copy holds an extra commit.
The three refusals of the first week were right, so the gap is latent, but a squash merge with a moved base would be refused wrongly.
The core also never retries after a refused cleanup: only a manual command tries again.
This item takes over EDI-52's slice 18b.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 6.2 defect c and step 13, and [the EDI-52 plan](research/EDI-52-plan.md), finding F11.

## Acceptance criteria

- MERGED to LANDED judges the commits up to the merged head as landed and names any commit after it; a commit after the merged head keeps the task MERGED.
- A squash-merged request cleans up although the base later rewrote the same lines (defect test c), and a commit added after the delivered head is still refused and named.
- A merge while the task was CHANGING raises one decision naming the unlanded commits, offering a follow-up request from the same branch or discarding them, the discard option carrying `"authorises": "discard-work"`.
  With the first week's timeline, one decision is raised naming the commit made after the merged head.
- The core retries a refused cleanup in every delivery round until it holds.
- `docs/decisions/cleanup.md` gains a history line.

## Requirements from decisions and answers

- Q6 A (EDI-74 round 1), widened on 27 October 2026: any merge with commits after the merged head raises the follow-up decision, whatever the stage before it.
- The captain asked for merges, never rebases, of a worker's commits; no code path here may rebase.
- Never discard unlanded work without the captain's word (I-12); the discard option is the word.
- Re-scoping rather than dropping kept this task's key: this item is EDI-74 step 13 and EDI-52 slice 18b in one.

## Research and measurements

- Cleanup uses `git cherry -v` (patch identity) and `merge-tree --write-tree` (content); a squash matches no commit by patch identity.
- The existing acceptance test pins only a squash with an unchanged base.
- `CleanUpTask.requireLanded` never consulted the forge's merged state before the domain's `RequestMerged` trigger.

## Read first

- [cleanup](../decisions/cleanup.md), [merge-following](../decisions/merge-following.md), [task-lifecycle](../decisions/task-lifecycle.md), [delivery](../decisions/delivery.md).
- `edi-core/src/main/java/io/github/ythibos/edi/application/delivery/CleanUpTask.java`, `FollowMerges.java`, `LandedWorkCheck.java`, `adapter/git/Git.java`, `domain/landing/`.
- The test `WorkIsolationAndGitHygieneTest` in the invariant package, which pins the squash case.

## How to verify

- `./mvnw verify`.
- A git test over temporary repositories (`FixtureRepository`, isolated from user git configuration): squash with a moved base, squash with an extra later commit, a merge commit, a rebase merge.
- A decision-raising test for the merge while CHANGING.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.

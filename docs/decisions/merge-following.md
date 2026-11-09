# Merges are observed from the forge, never performed, and follow-up work waits for the refreshed clone

**Kind:** Design

## Context

After the captain merges, EDI must refresh the project clone before briefing follow-up work, move tickets and clean up (I-29).
A merge on the forge's website leaves the local clone stale, which once let follow-up work start on outdated code.
The captain may mark a request ready and merge it before the worker reports done, and a branch name is reused by every task on the same work key.

## Decision

`Forge.mergeRequestsFrom` lists every request from a branch, and `DeliveryRecords.requestFrom` takes the open one as the task's, or a merged one only when it was merged at or after the task's dispatch and EDI has not recorded it for another task.
`FollowMerges` records a `draft-review` ship's request as soon as it exists (`DraftRecorded`): each time the task's pipeline run changes, each time its worker reports a status, and when a done is accepted.
`DeliveryRound` asks `FollowMerges` every `edi.delivery.drafts` (PT10M by default) to view each open delivery of a task in flight, on the account its project binds now; only an exact `merged` state counts.
Each view saves a change of the request's head, draft flag or state, so an open request stays current after the done as before it.
A followed merge moves the task to MERGED ([task-lifecycle](task-lifecycle.md)) and names the branch's commits after the merged head as not landed.
A merge records `DraftMerged` with the commit it landed and a `CloneRefresh`; a closed request records `DraftClosed`; both wake the primary.
`edi merged <task|url>` asks the forge at once, so the captain's word is followed the same way and never taken alone; a task delivered to the captain's checkout goes straight to its clean-up, whose landed-work test decides.
`CloneRefreshes` fetches the clone and verifies a refresh once the merge commit is an ancestor of `origin/<base>`; `DispatchTask` refreshes the project before it launches anything there, refuses while a refresh is unverified, and keeps a deferred dispatch deferred.
Once its refresh is verified, a merge's clean-up is tried exactly once, at once or in a later round ([cleanup](cleanup.md)); a clean-up a failing program stops is retried, and a refused one wakes the primary with its findings.
Each delivery and each clone is followed on its own, so one the forge or git cannot answer is logged while the others go on.
Nothing in the domain names a merge as an act: `MergeRequestView`, `DraftMerged` and `MergedWork` observe, and `NameAMergeOperation` keeps it so ([ports-and-architecture-rules](ports-and-architecture-rules.md)).

## Why

Neither the captain's word nor a stale view can make EDI treat unmerged work as merged, because every merge it follows is one the forge reports, whatever order the pipeline, the captain and the worker act in.
No brief is written on a base that lacks the work just merged, however the merge was noticed.

## Alternatives rejected

- The captain's word only, or without asking the forge - forgotten merges leave clones stale, and a mistyped task id would retire live work.
- Forge webhooks - a service reachable from the forge.
- Asking the forge about every task in each round - one call per task every ten minutes, when a run change or a status already says when to look.
- Fetching on every dispatch and trusting it - a fetch can succeed while the merge has not reached the base.

## Consequences

- Good: the whole tail of a delivery, from merge to a cleaned-up task, runs without the captain saying anything.
- Bad: up to ten minutes pass between a merge and EDI's notice when the captain says nothing, and until its request is recorded each status report of a task costs one forge call.
- Bad: a merge commit that never reaches the base, such as one rewritten on the forge, blocks the project until the captain clears it.

## When to revisit

When the forges offer a cheap local notification of a new or merged request.

## How we got here

- 15/10/2026: the specification settled observing merges every ten minutes besides the captain's word.
- 16/10/2026: slice S5 followed merges, refreshed clones and released clean-ups.
- 21/10/2026: a request marked ready and merged before its done was never recorded, so EDI started recording requests as soon as they exist and following merged ones it finds (EDI-23).
- 09/11/2026: an open request's head and draft flag went stale after the done, so every view of it is saved, and a followed merge names the commits after the merged head (EDI-85).

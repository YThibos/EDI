# ADR-088: Follow-up work on a project waits until its clone holds the commit a merge landed

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-29: when a delivered merge request is seen merged, or the captain says he merged it, EDI fetches the project clone and verifies the expected commit before briefing any follow-up in that repository.
AT-29: dispatching follow-up work on a project with a pending refresh blocks until the fetch verified the merge commit.
A merge on the forge's website leaves the local clone stale, which once let follow-up work start on outdated code.

**Decision:** Following a merge stores a `CloneRefresh`: the task, its project, the merge request's target branch and the commit the merge landed.
`CloneRefreshes` fetches the clone's origin and verifies a refresh once the commit is an ancestor of `origin/<base>`, recording `CloneRefreshed`.
`DispatchTask` refreshes the project before it launches anything there and refuses while a refresh stays unverified, naming the commit it waits for; a deferred dispatch stays deferred until its project's clone is fresh; a failed fetch refuses the dispatch with git's reason.
The delivery round retries every pending refresh, and a verified refresh releases its merge's clean-up (ADR-087).

**Motivation:** No brief can be written on a base that lacks the work just merged, however the merge was noticed.

**Alternatives considered:**
- Fetching on every dispatch and trusting it - a fetch can succeed while the merge has not reached the branch the work starts from.
- Blocking only the merged task's own follow-ups - every task on the project starts from the same base.

**Consequences:**
- Good: AT-29 shows the refusal while the commit is missing and the dispatch once it arrives.
- Bad: a merge commit that never reaches the base, such as one rewritten on the forge, blocks the project until the captain clears it.

**Revisit when:** A forge reports the base's head after a merge, or the captain asks for a way to waive a refresh.

**Sources:** EDI rewrite specification, sections 13.1 and I-29 (AT-29); ADR-023, ADR-087; `domain.delivery.CloneRefresh`, `application.delivery.CloneRefreshes`, `application.dispatch.DispatchTask`, `LaunchDeferredDispatches`, `adapter.store.CloneRefreshStore`, `adapter.git.Git.isAncestor`.

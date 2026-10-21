# ADR-156: A task's merge request is recorded as soon as it exists, and a merge the forge shows is followed whatever the order

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-083 recorded a task's merge request only when its done was accepted, and ADR-087 followed only recorded deliveries.
When the captain marked the pull request of EDI-20 ready for review and merged it before the worker's done, EDI never recorded it: `edi merged <task>` answered that the captain merged it locally, `edi merged <url>` that EDI delivered nothing there, the done was refused for lack of an open draft, and the clean-up waited for a hand-over that could not come.
A GitLab merge request of another project was recorded only because its worker reported done while it was still a draft.
Every merge EDI follows must be one the forge reports (ADR-087).

**Decision:** `Forge.mergeRequestFrom` lists every merge request from a branch, with `gh pr list --state all` and `glab mr list --all`, and `MergeRequestView.deliveryAmong` takes the open one, or else the latest merged one; a closed request is never the task's.
`FollowMerges.followTheRequestOf` looks the request up for a `draft-review` ship whose project registers a forge and which has no recorded delivery, or only a closed one, and records it once as `DraftRecorded`.
`ObservePipelines` asks it each time a task's latest pipeline run changes, and `RecordStatus` each time a worker reports any status but an accepted done; a forge that cannot answer is logged, and the next run change or status asks again.
An accepted done hands its request to `FollowMerges.followTheAcceptedRequest`, which records it when EDI has not yet.
A request found already merged is recorded and its merge recorded in one transaction, `DraftRecorded` then `DraftMerged` with the clone refresh it waits for (ADR-088), and then the clone is refreshed and the clean-up tried as ADR-087 decided; a done that finds its merge already followed tries the clean-up once more.
`edi merged <task>` without a recorded delivery asks the forge for the task's request when the task is a `draft-review` ship on a forge: a merged request is followed, an open one is recorded and the word refused, and no request at all is refused with the branch and the repository; only a task delivered to the captain's checkout goes straight to its clean-up.
`edi merged <url>` without a recorded delivery looks for the task in flight whose request on that repository is at that address and follows it the same way; only when no task's request is there does it answer that EDI delivered nothing at that address.
`edi deliver` refuses a request that is already merged and names `edi merged` instead.

**Motivation:** Whatever the order of the pipeline's run, the captain's ready, his merge and the worker's done, EDI ends with the request recorded and its merge followed, without ever taking a merge the forge does not report.

**Alternatives considered:**
- Recording the request from the address the pipeline's run names - the run names it only once it ends, and a request opened by `edi deliver` or by hand would be missed.
- Asking the forge about every task in flight in each delivery round - one forge call per task every ten minutes, when a run change or a status report already says when to look.
- Taking the captain's word without asking the forge - a mistyped task id would retire live work (ADR-087).

**Consequences:**
- Good: a request is recorded at the pipeline's next step or the worker's next status, and `edi merged` finishes any task whose request the forge shows merged.
- Bad: until its request is recorded, each run change and status report of a task costs one forge call, and the status report waits for it.

**Revisit when:** The forges offer a local notification of a new or a merged request.

**Sources:** ADR-083, ADR-087, ADR-088, ADR-155, ADR-157; `application.delivery.FollowMerges`, `DeliveryRecords`, `ObservePipelines`, `DeliverAcceptedWork`, `DeliverDraft`, `application.worker.RecordStatus`, `application.port.Forge.mergeRequestFrom`, `adapter.forge.GitHubForge`, `GitLabForge`, `domain.delivery.MergeRequestView.deliveryAmong`, `ForgeRepository.holds`, `edi-cli` `MergedCommand`.

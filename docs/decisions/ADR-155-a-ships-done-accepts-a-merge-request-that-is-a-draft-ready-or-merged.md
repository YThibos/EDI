# ADR-155: A ship's done is accepted once its claims hold, and its merge request may be a draft, ready for review or merged

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-083 accepted a `draft-review` done only while its merge request was still an open draft.
The captain's standing rule is that every merge request is ready for review when it is presented to him, so a worker marks it ready once its checks are green, and the captain may merge it before the worker's done arrives.
Both made EDI refuse the done, and the pull requests of EDI-20 and EDI-26 were never followed (ADR-156).
I-04 keeps its meaning: every merge request EDI or its pipeline opens is opened as a draft, and only the captain merges (I-03).
Section 11.4 and AT-11 still ask that the named head is on the remote branch and that the branch adds no SNAPSHOT version.

**Decision:** `RecordStatus` asks `DoneClaims` before it records a ship's `done`, and a done whose claims fail is refused with every gap in one sentence and recorded as `DeliveryRefused`, while the worker's status stays as it was.
The SNAPSHOT claim reads the lines the branch adds since `origin/<base>` to any `pom.xml`, and refuses each one that carries `-SNAPSHOT` as a word, case-insensitively, naming its file and line; a SNAPSHOT already on the base is not the branch's to remove.
For `draft-review`, the merge request from the task's branch on the registered forge, the open one or else the latest merged one, must come from that branch, target the task's base and show the isolated copy's head, which proves the head is on the remote branch.
It may be a draft, ready for review or already merged; only a request closed without a merge is a gap, and no claim demands or restores the draft state.
The merge request is found by its branch, so the done's text needs no particular form.
A scout's done carries no delivery claim.
An accepted `draft-review` done hands its merge request to `FollowMerges` (ADR-156); a mechanical task's done is checked for its claims at once and delivered after its mechanical check (ADR-059).
The worker's brief, the `edi-ship` skill and the primary's anchor say that the pipeline opens the request as a draft and the worker marks it ready for review once its checks are green.
AT-04 now reads that a request marked ready for review before the done is accepted at the done, on GitHub and on GitLab.

**Motivation:** The done checks what the captain needs, the work at its head on the forge from the right branch to the right base, and no longer a draft state his own rule removes.

**Alternatives considered:**
- Turning the request back into a draft at the done - it undoes the captain's own marking and leaves him a draft to flip again.
- Accepting any request from the branch, closed ones included - a closed request carries no work to the captain.

**Consequences:**
- Good: the order in which the pipeline, the worker and the captain act no longer decides whether a done is accepted.
- Bad: a done still waits for one forge call, and a forge outage refuses a done until the forge answers.

**Revisit when:** The pipeline itself marks the request ready for review, or the captain wants drafts until he has looked at them.

**Sources:** EDI rewrite specification, sections 11.4, 13.1 and I-04, I-11 (AT-04, AT-11); ADR-059, ADR-083, ADR-156; `application.delivery.DoneClaims`, `DeliveryRecords`, `DeliverAcceptedWork`, `application.worker.RecordStatus`, `CheckMechanicalChange`, `application.brief.BriefSections`, `domain.delivery.SnapshotVersions`, `MergeRequestView.gapsAsTheDeliveryOf`, `adapter.git.Git.buildFileLinesAddedSince`, `claude/skills/edi-ship/SKILL.md`, `claude/primary/CLAUDE.md`.

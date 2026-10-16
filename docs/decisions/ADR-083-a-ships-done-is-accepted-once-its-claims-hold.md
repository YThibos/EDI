# ADR-083: A ship's done is accepted only once its claims hold, and a refused done is recorded

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 11.4: a done is accepted only when its claims hold; for a ship, the named head commit exists on the remote branch, the merge request is a draft (I-04), and the SNAPSHOT check passed (I-11); otherwise the core answers the worker with what is missing.
AT-04: the delivery use case refuses a pipeline outcome whose merge request is not a draft and records a failure; AT-11: a branch adding a SNAPSHOT dependency makes `edi status done` refuse with the file and line.
The captain's rule is that no pushed pom carries a SNAPSHOT version, read as governing what a change adds, not the release plugin's own development-version bump.

**Decision:** `RecordStatus` asks `DoneClaims` before it records a ship's `done`, and a done whose claims fail is refused with every gap in one sentence and recorded as `DeliveryRefused`, while the worker's status stays as it was.
The SNAPSHOT claim reads the lines the branch adds since `origin/<base>` to any `pom.xml`, and refuses each one that carries `-SNAPSHOT` as a word, case-insensitively, naming its file and line; a SNAPSHOT already on the base is not the branch's to remove.
For `draft-review`, the open merge request from the task's branch on the registered forge must be open, a draft, from that branch, to the task's base, and at the isolated copy's head, which proves the head is on the remote branch; the merge request is found by its branch, so the done's text needs no particular form.
A scout's done carries no delivery claim.
An accepted done for `draft-review` records the merge request as the task's delivery (`DraftRecorded`); a mechanical task's done is checked for its claims at once and delivered after its mechanical check (ADR-059).

**Motivation:** A worker learns at once, in its own terminal, what keeps its work from being delivered, and nothing the captain sees as ready is a non-draft or a SNAPSHOT.

**Alternatives considered:**
- Checking claims later in the supervision round - the worker would already have stopped, believing its work delivered.
- Refusing every SNAPSHOT in a touched pom - it would refuse the release plugin's own version bump, which the captain exempted.

**Consequences:**
- Good: AT-04 and AT-11 hold on the real use case with real git.
- Bad: a done waits for one forge call, and a forge outage refuses a done until the forge answers.

**Revisit when:** The pipeline itself refuses SNAPSHOT versions, or reports the draft state of its merge request.

**Sources:** EDI rewrite specification, sections 11.4, 13.1 and I-04, I-11 (AT-04, AT-11); ADR-059; `application.delivery.DoneClaims`, `DeliverAcceptedWork`, `application.worker.RecordStatus`, `CheckMechanicalChange`, `domain.delivery.SnapshotVersions`, `MergeRequestView.gapsAsTheDeliveryOf`, `adapter.git.Git.buildFileLinesAddedSince`.

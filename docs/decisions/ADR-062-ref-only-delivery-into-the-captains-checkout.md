# ADR-062: A ready local-branch ship is fetched into the captain's checkout by its ref only

**Date:** 16/10/2026

**Status:** Superseded by ADR-084

**Kind:** Design

**Context and constraints:** Workers never touch the captain's checkouts; delivery into one uses `git fetch <worktree> <src>:<dst>` only, which refuses a non-fast-forward and touches no working tree, and a dirty checkout stays byte-identical (I-07, AT-07b).
A project with `delivery: local-branch` ends at a ready branch delivered into its `captainCheckout` (sections 8.4 and 13.4).
The invariant catalogue puts AT-07b in slice S3, beside the worker launch, while the delivery modes as a whole arrive with slice S5.

**Decision:** When a ship's done is accepted on a `local-branch` project, `CaptainCheckouts` runs `git -C <captainCheckout> fetch <worktree> <branch>:<branch>` and records `BranchDeliveredToCaptain`, or `BranchDeliveryFailed` with git's reason when the fetch is refused, for example because the captain's branch moved on, or when the registry names no `captainCheckout`.
It never checks out, merges, stashes or resets anything in the captain's checkout.

**Motivation:** The only change in the captain's checkout is one ref that moves forward, so his working tree, index and HEAD stay exactly as he left them.

**Alternatives considered:**
- Pushing from the worktree into the checkout - a push can update a checked-out branch and its working tree.
- Waiting for slice S5 - AT-07b belongs to this slice, and the fetch is the delivery itself.

**Consequences:**
- Good: AT-07b proves byte-identity for a dirty checkout and a refusal for a diverged branch.
- Bad: the delivery is not yet verified against the head the worker named; slice S5 adds the claims check of a done.

**Revisit when:** Slice S5 builds `local-apply` and the claims check of a done.

**Sources:** EDI rewrite specification, sections 8.4, 13.4 and I-07 (AT-07b); `application.worker.CaptainCheckouts`, `RecordStatus`, `adapter.git.Git.fetchBranchInto`.

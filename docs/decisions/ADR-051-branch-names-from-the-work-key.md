# ADR-051: A branch name is built from the work's kind and its key, and nothing else

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Branches are `feature|chore|research/<KEY>-<n>`, never `fm/` or `edi/` (I-08), with an optional `-<m>` suffix for a follow-up branch on the same key (section 8.5).
The old harness once branched a project on `fm/` because a per-project setting was missing.
The kind is chosen by the work's nature.

**Decision:** `BranchName` holds a `BranchKind` and a `WorkKey`; it cannot be built without a key, and `BranchName.parse` refuses `fm/` and `edi/` by name and every other shape that is not kind, slash, key and optional follow-up number.
A dispatch builds the branch once: a scout works on `research/<KEY>-<n>`, and a ship on the kind its dispatch category names, which the project's `branchKinds` in the registry must allow.
The branch is recorded on the task's dispatch and reused by every later launch, even when the category changes.
A task without a work key is refused at dispatch.

**Motivation:** No configuration can produce a harness prefix, because no prefix is configured anywhere; the only inputs are the kind and the key.

**Alternatives considered:**
- A per-project branch prefix in the registry - the failure the old harness had.
- An `--branch-kind` option on `edi dispatch` - a second place to decide what the category already says.

**Consequences:**
- Good: AT-08 holds for every registered project, EDI included.
- Bad: a ship whose nature differs from its category's branch kind needs another category.

**Revisit when:** The captain asks for follow-up branches or combined review branches (section 8.5).

**Sources:** EDI rewrite specification, sections 8.5 and I-08; ADR-016; `domain.branch.BranchName`, `BranchKind`, `application.dispatch.DispatchTask`.

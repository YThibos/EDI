# ADR-022: Work is delivered as a draft the captain merges

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain's delivery flow: "crew mates do the work on their own branch, but deliver a reviewable copy on the real repository. If it looks good, I'll tell to ship it, meaning to create the Merge or Pull Request for me for a final review. I then manually do the merge."
It was later revised so that the validation pipeline opens the draft itself, the craftsmanship rules ride in the pipeline's intent, and nobody re-reviews at publication.
On some projects a merge to the main branch deploys to a test server.

**Decision:** For a project with a remote, the worker commits on its keyed branch and runs the validation pipeline with the task's intent, acceptance criteria, offline-verification stance and, where the project asks for it, the craftsmanship rules verbatim (I-34).
The pipeline pushes the branch and opens a draft merge request or pull request (I-04); EDI verifies the worker's claims and reports the draft; the captain reviews and merges it himself.
Every push first passes the publish guard (ADR-032).
A project with no remote ends at a ready branch delivered into the captain's checkout by a ref-only fetch, and on request a change is applied to his working tree without committing.
EDI itself has no merge capability of any kind (I-03).

**Motivation:** The captain keeps the merge, which may deploy, in his own hands, and every delivery is reviewable before it lands.

**Alternatives considered:**
- Ready-for-review pull requests opened by EDI - the captain wants drafts.
- EDI merging green drafts - forbidden by I-03.

**Consequences:**
- Good: nothing reaches a base branch without the captain's action.
- Bad: every delivery waits for him.

**Revisit when:** Never for the merge authority.

**Sources:** EDI rewrite specification, sections 13.1 to 13.5 (I-03, I-04, I-34); `.no-mistakes.yaml` (`providers.github.draft_pull_requests`).

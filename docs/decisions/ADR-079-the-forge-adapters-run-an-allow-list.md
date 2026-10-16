# ADR-079: The forge adapters run only an allow-list of gh and glab commands

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** Projects live on GitLab (laptop) and GitHub (both machines), so the `Forge` port has two production implementations (ADR-007).
EDI has no merge capability of any kind (I-03): AT-03a forbids any adapter method or HTTP call whose name or path names a merge, and AT-03b asks for command allow-lists without `mr merge`, `pr merge` or a push to a base branch, asserted by a test.
`glab` cannot take a merge request URL outside a repository, so every call names the project with `-R` and the merge request by number (section 13.2).
A merge request record stores provider, host, path, number and URL, and a consumer refuses a record whose parts do not rebuild the URL exactly (section 13.2).
Machine differences are chosen at runtime, never by a build-time condition (ADR-006).

**Decision:** `adapter.forge.ForgeCommand` is the allow-list: `gh auth token`, `gh auth status`, `gh auth git-credential`, `gh pr list`, `gh pr view`, `gh pr create`, `glab api user`, `glab mr list`, `glab mr view` and `glab mr create`, each a program and its fixed leading words.
`ForgeCalls` is the only class in `adapter.forge` that starts a program, and it runs only a `ForgeCommand` followed by flags and values; `GitHubForge` and `GitLabForge` implement the `Forge` port on top of it and read the tools' JSON output.
The port offers four things: the open merge request from a branch, a view of one merge request, opening a draft, and whether the bound account may change workflows; nothing in it performs, arms or schedules a merge.
`gh pr create` and `glab mr create` always pass `--draft`, and a created request is read back through the list call, so its address is built from the registered repository and its number.
A view whose URL does not match the address rebuilt from the registered repository is refused as a forge answer for another repository; `DeliveryStore` refuses a stored row whose parts rebuild a different URL.
GitLab's `opened` and `locked` count as open, and only an exact `merged` (GitLab) or `MERGED` (GitHub) counts as merged; the commit a merge landed is `merge_commit_sha`, then `squash_commit_sha`, then `sha` on GitLab, and `mergeCommit.oid` on GitHub.
`application.delivery.Forges` picks the adapter for a project from the `kind` of its registered forge at the moment of the call.
The domain names merge requests in words that observe, never in words that act: `MergeRequestView`, `MergeRequestState`, `landedCommit`, `DraftMerged`, `FollowMerges`, `MergedWork`; the existing `NameAMergeOperation` rule keeps it so.

**Motivation:** The allow-list makes I-03 a property of the code that a test reads, rather than a promise in prose, and one runner means a new forge call cannot bypass it.

**Alternatives considered:**
- Calling the forges' HTTP APIs from Java - two more clients, their tokens and their pagination, where the captain's tools already hold the logins and are verified on both machines.
- A composite forge that delegates by kind - a third implementation of the port with no behaviour of its own.

**Consequences:**
- Good: AT-03b runs every adapter operation against recording stand-ins of `gh` and `glab` and finds no merge or push argument, and AT-03a finds no merge-naming endpoint or adapter method.
- Bad: a forge feature EDI needs later means a new allow-list entry, and the test reviews each one.

**Revisit when:** A third forge is registered, or `gh` or `glab` change their JSON fields.

**Sources:** EDI rewrite specification, sections 13.2, 13.3 and I-03 (AT-03a, AT-03b), I-04; ADR-006, ADR-007, ADR-023; `adapter.forge.ForgeCommand`, `ForgeCalls`, `GitHubForge`, `GitLabForge`, `application.port.Forge`, `application.delivery.Forges`, `domain.delivery.MergeRequestAddress`, `MergeRequestView`, `ForgeRepository`, `adapter.store.DeliveryStore`; `docs/verification/gh.md`, `docs/verification/glab.md`.

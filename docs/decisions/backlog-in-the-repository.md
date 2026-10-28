# The queued and held work lives in the repository as self-contained specs, and issues only track who has taken what

**Kind:** Design

## Context

EDI's backlog lived in its store, the research reports in the EDI home and the captain's answers in task notes, all on one machine and readable only through the `edi` command.
A Claude Code cloud session on a second account has none of that: it sees the repository and nothing else, and it cannot reach the machine, the store, the memory or any skill outside the repository.
The captain wants a share of the work to run there, so that work is not tied to one machine or one account.
A spec that is a short note pointing at a report on the machine is not a spec a cloud session can use.

## Decision

`docs/backlog/` holds the queued and held work as one file per item, `EDI-<n>.md`, each the full and canonical spec: goal, context, acceptance criteria, the decisions and captain answers that bind it restated as requirements, the research it rests on, what to read first and how to verify.
`docs/backlog/README.md` is the ordered index, in the waves the captain set, and the pickup protocol a session follows; `docs/backlog/research/` carries the research reports in full where an item needs them.
One GitHub issue per open or held item, titled with the item's key and title, links the spec on `main` and copies no criteria; the session claims an item by commenting on its issue and opening a draft pull request that closes it, so GitHub links issue, branch and pull request.
Questions before code go on the issue and questions about code on the pull request, each with lettered options and a recommendation, and the session stops until the captain answers; an answer that changes an item updates its file in the same pull request.
The issues are opened only after the pull request that adds the folder has merged, so they link to files that exist on `main`.
An item that needs the captain's machine, such as one reading his secrets file, is listed with its reason and is not for a cloud session.
`.claude/settings.json` turns off the attribution lines a cloud session would otherwise add to commits and pull requests, so the no-trailer rule holds there too.

## Why

A spec that works with the repository alone is the only kind a session without the machine can use, and it also serves any later contributor.
One source of truth per item, in the repository, is reviewed and versioned with the code, while an issue holds what changes constantly: who has taken the item and where its branch is.
Opening the draft pull request first, naming the item, keeps two sessions from taking the same one.

## Alternatives rejected

- Issues only - the criteria would live outside review, drift from the code and split into comments.
- Specs in the EDI store only - unreachable from a cloud session, which is the reason for the change.
- A pointer file that names reports on the captain's machine - the session could not read what the pointer names.
- Opening the issues before the folder merges - they would link to files that are not on `main`.

## Consequences

- Good: a session with only the repository can pick up, build, verify and deliver an item, and the captain's primary session reviews the pull request.
- Good: the research that justifies an item travels with it.
- Bad: an item's file is a second copy of what EDI's store holds, so a change to the plan must change the file; the file is the source after this point.
- Bad: the research reports carried over are a snapshot of one day and their file paths drift from the code.

## When to revisit

When the repository's own task tracking can hold the full spec, or when more than one cloud session works the backlog at once and claims need more than a comment and a draft pull request.

## How we got here

- 28/10/2026: the captain asked for every defined EDI upgrade and patch to be available on the upstream repository with its full spec, findings and research, so a second account's cloud session could do the work; he chose a mixture, with the canonical specs and strict rules in `docs/backlog` and issues tracking which issue is linked to which branch (EDI-117).

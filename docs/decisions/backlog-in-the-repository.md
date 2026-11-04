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
An item's status and its issue live in its own file's header and nowhere else: the index holds only what rarely changes, the order, the key, the title, the size and the blockers, and `docs/backlog/board.mjs` prints the index with each item's status and issue, and with `--next` the items a session may pick up.
A test holds the index and the item files to each other: every row has its file, every file has one row, and no index table carries a status or an issue.
One GitHub issue per open or held item, titled with the item's key and title, links the spec on `main` and copies no criteria; the session claims an item by commenting on its issue and opening a draft pull request that closes it, so GitHub links issue, branch and pull request.
Questions before code go on the issue and questions about code on the pull request, each with lettered options and a recommendation, and the session stops until the captain answers; an answer that changes an item updates its file in the same pull request.
An item's issue links its file on `main`: the issues of the items the folder brought are opened once its pull request has merged, and a later item's issue is opened together with the pull request that adds the item; either link works once that pull request merges, and until then no session sees the item in the index on `main`.
EDI's own work is not dispatched on the captain's machine: the primary puts it on this backlog with the `edi-backlog-item` skill, whose script `claude/skills/edi-backlog-item/backlog-item.mjs` writes the item's file and its row, opens a ready-for-review pull request on a `chore/` branch and the item's issue, names the issue in the item's file, and holds the local task for a deferral, idempotently and with a dry run.
The script takes the key from `edi task add`, the existing EDI-native allocation, and the hold from `edi hold`, and goes to GitHub as the account the clone is pinned to.
An item that needs the captain's machine, such as one reading his secrets file, is listed with its reason and is not for a cloud session.
`.claude/settings.json` turns off the attribution lines a cloud session would otherwise add to commits and pull requests, so the no-trailer rule holds there too.

## Why

A spec that works with the repository alone is the only kind a session without the machine can use, and it also serves any later contributor.
One source of truth per item, in the repository, is reviewed and versioned with the code, while an issue holds what changes constantly: who has taken the item and where its branch is.
Opening the draft pull request first, naming the item, keeps two sessions from taking the same one.
A delivery that edits only its own item's file cannot conflict with another delivery, while a shared status column made every open pull request conflict with every merge to `main`.

## Alternatives rejected

- Issues only - the criteria would live outside review, drift from the code and split into comments.
- Specs in the EDI store only - unreachable from a cloud session, which is the reason for the change.
- A pointer file that names reports on the captain's machine - the session could not read what the pointer names.
- Opening the issues before the folder merges - they would link to files that are not on `main`.
- Backlogging an item by hand - the hand route was forgotten for six items on 28 October 2026, which got local tasks only and neither a file nor an issue.
- A status column in the index - every delivery edited a line next to another delivery's line, so each merge to `main` left every open pull request in conflict.
- A generated index committed beside the specs - the generated file is again one file every pull request edits.
- An `edi backlog add` subcommand in the core - the core's forge port opens draft merge requests and reads them, but has no issues or labels, and the step is the primary's own orchestration of git, gh and two existing `edi` commands; a script beside the skill does it without widening a port.

## Consequences

- Good: a session with only the repository can pick up, build, verify and deliver an item, and the captain's primary session reviews the pull request.
- Good: the research that justifies an item travels with it.
- Bad: an item's file is a second copy of what EDI's store holds, so a change to the plan must change the file; the file is the source after this point.
- Bad: the research reports carried over are a snapshot of one day and their file paths drift from the code.

## When to revisit

When the repository's own task tracking can hold the full spec, or when more than one cloud session works the backlog at once and claims need more than a comment and a draft pull request.
When the core's forge port gains issues, the script's steps may become an `edi` command.

## How we got here

- 28/10/2026: the captain asked for every defined EDI upgrade and patch to be available on the upstream repository with its full spec, findings and research, so a second account's cloud session could do the work; he chose a mixture, with the canonical specs and strict rules in `docs/backlog` and issues tracking which issue is linked to which branch (EDI-117).
- 29/10/2026: the captain restated that EDI's work goes to the upstream backlog as an item and an issue, routinely and as automated as possible, so the `edi-backlog-item` skill and its script now add an item in one step, with its issue opened alongside its pull request (EDI-142).
- 04/11/2026: every open pull request conflicted in the index's status column after each merge, so the captain moved each item's status and issue into its own file, with `board.mjs` printing the live view and a test holding the index and the files together (EDI-172).

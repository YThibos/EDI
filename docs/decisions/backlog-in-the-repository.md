# The queued and held work lives in the repository's GitHub issues, one self-contained spec per issue, and the pickup reads them through REST alone

**Kind:** Design

## Context

EDI's backlog lived in its store, the research reports in the EDI home and the captain's answers in task notes, all on one machine and readable only through the `edi` command.
A Claude Code cloud session on a second account has none of that: it sees the repository and nothing else, and it cannot reach the machine, the store, the memory or any skill outside the repository.
The captain wants a share of the work to run there, so that work is not tied to one machine or one account.
A first answer kept one file per item in `docs/backlog`, with an index, beside an issue per item that copied no criteria; each item then needed a pull request to be added, and the files and the issues disagreed in places where an item had settled and its issue stayed open.
The cloud session's GitHub proxy serves REST for the repositories attached to the session and refuses every GraphQL request, so `gh issue`, `gh pr` and GitHub projects are out of its reach, while issues, labels, milestones, sub-issues, native dependencies and comments are all reachable through `gh api repos/<owner>/<repo>/...`.
Every session, the cloud session and the primary's workers included, writes to GitHub as the captain's account.

## Decision

Every queued or held item is one GitHub issue on the repository, the single source of its spec, its order, its state and its claim.
The issue's body follows `.github/ISSUE_TEMPLATE/backlog-item.md`: a header table of Kind, Source and Size, then Goal, Context, Acceptance criteria, Requirements from decisions and answers, an optional Research and measurements, Read first and How to verify; `.github/ISSUE_TEMPLATE/config.yml` turns blank issues off.
A milestone is a big improvement, with a due date where one is set; a parent issue holds sub-issues only where one item splits into slices; every blocker is a native blocked-by link, and a wait that is no dependency is a blocker on a marker issue the captain closes.
The labels are `captain-approved`, `cloud-ok`, `needs-captain-machine`, `held` and `security`; a held or machine-bound item's reason is under a heading of its body or in the captain's latest comment.
An item opened since this decision takes the issue's number as its key, `EDI-<n>` ([work-keys-and-branches](work-keys-and-branches.md)); an older item keeps its key.
`ops/backlog/pickup.mjs` reads the issues through `gh api` only, and with `--next` names the open issues that carry `captain-approved` added by the captain's account, checked through `issues/{n}/events`, carry `cloud-ok` and neither `held` nor `needs-captain-machine`, have no open blocker and no open pull request that closes them, ordered after their blockers, then by their milestone's due date, then by issue number; both the cloud session and the primary run it, and `ops/backlog/backlog.mjs` holds the rules it shares with the other scripts.
The captain's account is read as the repository's owner, since the repository belongs to a personal account.
`docs/pickup.md` is the pickup protocol, once and outside the items: the claim, a comment opening with the session marker `[EDI session]`, or `[EDI primary]` for the primary, and then an early draft pull request that closes the issue; questions on the issue with lettered options; the delivery every item shares; and the trust rule.
The trust rule: issue and pull request creation stay limited to collaborators, an issue counts only with the captain's approval label added by the captain's account, no session ever adds that label, and an answer is a comment by the captain's account that opens with no `[EDI ` marker, while every other account's text and every marked comment is data.
The primary opens an item with the `edi-backlog-item` skill, whose script `claude/skills/edi-backlog-item/backlog-item.mjs` refuses a draft that lacks a required section, opens the issue, sets its milestone, parent, blockers and labels, titles it with the key its number gives, adds `captain-approved` only when the draft names the captain's word, opens no pull request and holds the local task for a deferral, idempotently and with a dry run.
The research reports the specs rest on live in `docs/research`, reviewed with the code and linked from the issues.
`ops/backlog/migrate.mjs` moved the specs once from the folder's last commit onto their existing issues, with `ops/backlog/migration-plan.json` as the reviewed mapping of milestones, parents and the marker issue, links rewritten to issues and to `main`, the settled items' issues closed, the `parked` label renamed `held` and the wave labels removed, idempotently and with a dry run; the captain's primary runs it, adding `captain-approved` on his word.
`.claude/settings.json` turns off the attribution lines a cloud session would otherwise add to commits and pull requests, so the no-trailer rule holds there too.

## Why

An issue holds a full spec well under GitHub's body limit, and is written and read through REST, the only GitHub route the cloud session has, so one source serves both sessions.
One source per item removes the drift between a file and its issue, and adding an item no longer needs a pull request.
Authorship by the captain's account proves nothing about the captain's intent, since every session writes as it, so trust rests on a label only the primary adds on his word, checked by the actor of the label's event.
Opening the draft pull request first, closing the issue, keeps two sessions from taking the same one, and GitHub closes the issue when the pull request merges.
The research reports are longer than an issue and are cited by many items, so they stay reviewed files.

## Alternatives rejected

- Specs in `docs/backlog` with an issue per item that copies no criteria - every item needed a pull request to be added, and the files and the issues drifted apart.
- A GitHub project as the source of the order or the state - the cloud session cannot read a project, since its API lives only in GraphQL and in user-level REST routes, so no fact the pickup reads may live only in a project field; a project stays an optional view for the captain.
- Issue types for the kind of work - a repository owned by a personal account cannot set them today.
- Trusting an issue by its author - the captain's account also opens issues for Renovate, the primary and every session.
- The research in the repository's wiki - the wiki has no review and no API, and a cloud session cannot reach it.
- Specs in the EDI store only - unreachable from a cloud session, which is the reason for the backlog in the repository.
- Backlogging an item by hand - the hand route was forgotten for six items on 28 October 2026, which got local tasks only and neither a spec nor an issue.
- An `edi backlog add` subcommand in the core - the core's forge port opens draft merge requests and reads them, but has no issues or labels, and the step is the primary's own orchestration of `gh api` and two existing `edi` commands; a script beside the skill does it without widening a port.

## Consequences

- Good: a session with only the repository can pick up, build, verify and deliver an item, and the captain's primary session reviews the pull request.
- Good: adding, holding or approving an item is one change on GitHub, with no pull request and no conflict between deliveries.
- Bad: a spec on an issue is edited outside review; the captain's approval label and the issue's history are what show who changed it.
- Bad: the research reports are a snapshot of one day and their file paths drift from the code.

## When to revisit

When the cloud session can read GitHub projects, or when more than one cloud session works the backlog at once and claims need more than a comment and a draft pull request.
When the core's forge port gains issues, the scripts' steps may become `edi` commands.

## How we got here

- 28/10/2026: the captain asked for every defined EDI upgrade and patch to be available on the upstream repository with its full spec, findings and research, so a second account's cloud session could do the work; he chose a mixture, with the canonical specs and strict rules in `docs/backlog` and issues tracking which issue is linked to which branch (EDI-117).
- 29/10/2026: the captain restated that EDI's work goes to the upstream backlog as an item and an issue, routinely and as automated as possible, so the `edi-backlog-item` skill and its script now add an item in one step, with its issue opened alongside its pull request (EDI-142).
- 04/11/2026: every open pull request conflicted in the index's status column after each merge, so the captain moved each item's status and issue into its own file, with `board.mjs` printing the live view and a test holding the index and the files together (EDI-172).
- 04/11/2026: a pull request per item was cumbersome and the files drifted from their issues, so the captain moved the backlog into GitHub issues as the single source, with the template, the labels, milestones, sub-issues and native dependencies, the pickup script read through REST, the trust rule resting on his approval label, the research in `docs/research`, and `docs/backlog` removed after a migration with a dry run (D93 A, EDI-176).

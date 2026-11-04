# Making the repository public

This checklist follows the findings of [EDI-132](../backlog/EDI-132.md) and records what was done and what is left.
A box is ticked when the step is done; a step marked *captain's machine* needs the account that owns the repository and the captain's word in the session that does it.
The patterns that find personal material live in the EDI home, never here.

## What blocks the switch

The captain decided on 30 October 2026 that only what could publish credentials or sensitive data blocks the switch: EDI runs on the captain's own machine for the captain alone, and the repository goes public to show EDI to colleagues.

- [x] EDI-122, EDI-123, EDI-124 and EDI-125, the worker escapes, and EDI-131, the pinned actions, merged.
- [x] The hardening findings, EDI-126 to EDI-130 and EDI-138 to EDI-140, do not block the switch; they publish nothing, and they land or are dropped on their own.
  By 3 November 2026 EDI-126 to EDI-129 had merged and EDI-130 and EDI-138 to EDI-140 were in review; EDI-168 made CI green again on `main`.

## 1. History

- [x] Kept as it is, on the captain's decision of 29 October 2026: three early commits carry a personal address as their author, tests hold example ticket keys of a registered work project, and one test names the keys file; none of it is critical enough to rewrite.
- [x] No history is rewritten, no new default branch is made, and `main` stays the default branch.
- [x] The files added to `main` between 30 October and 3 November 2026 were scanned for IPv4 addresses, home paths, e-mail addresses, names and the names of other registered projects: three backlog items named another registered project, one of its merge requests and one of its modules, and called two of its slices by the system they belong to; they are reworded here.
- [ ] *Captain's machine*: their earlier wording stays in the history of those three files; decide whether it may be public, or add its words to the patterns in the EDI home before the scans below.

## 2. Pull request descriptions

- [x] Every closed pull request's title was read, and its description rewritten as a neutral summary of what the change does and how it was verified, one file per pull request in [pull-requests](pull-requests/README.md); each is built from the pull request's own commits and its backlog item's goal, so it quotes nothing of the old description.
  On 3 November 2026 the seventeen pull requests closed since, up to #196, were added the same way, 110 in all.
- [x] The rewritten texts were scanned for IPv4 addresses, host and port pairs, home paths, e-mail addresses, ticket keys of other projects, domain names and the old harness's name; the only hits were source files cited by line and a version number.
- [x] The open pull requests' descriptions were written to the same standard from the start, and from EDI-150 on the publish check reads every text before EDI posts it.
- [ ] *Captain's machine*: scan every title, description, review comment and review, every issue and the dependency dashboard issue with the patterns in the EDI home, and keep the result.
- [ ] *Captain's machine*: apply the rewritten texts, as [pull-requests/README.md](pull-requests/README.md) says.
- [ ] *Captain's machine*: delete every older revision from each description's edit history in the web interface, which the API cannot do.
- [ ] *Captain's machine*: scan the same texts again, and confirm no description keeps an older revision.

## 3. Pull request refs

- [x] Every commit reachable from `main` and from the 101 pull request refs, 415 commits without their merges, was scanned with gitleaks 8.30.1 and its standard rules on 30 October 2026: no secret was found.
- [x] Scanned again on 3 November 2026, every commit reachable from `main`, every branch and the 117 pull request refs, 485 commits without their merges: no secret was found.
- [x] Every closed pull request's ref points at commits already on `main`; only the open pull requests' refs hold commits outside it, and those are this work in review.

## 4. Actions

- [x] The workflows were read for what a public repository changes:
  - CI runs on `push`, `pull_request` and `workflow_dispatch`, never `pull_request_target`, with `contents: read`, so a fork's pull request runs without secrets and cannot write;
  - the publish guard's deny-list comes from the `PUBLISH_DENYLIST` secret, which a fork's run does not get, and is written to a file, never printed;
  - Renovate runs on a schedule and on demand only, with its own token.
- [ ] *Captain's machine*: decide whether the Dependency-Check report stays an artefact of every run, since artefacts of a public repository can be downloaded by anyone signed in; it names known vulnerabilities of public dependencies only.
- [ ] *Captain's machine*: delete every workflow run, with its logs, and every artefact.

## 5. Branches

- [ ] *Captain's machine*: delete the remote branches already merged into `main`, the feature branches of merged items and the cloud session's working branch, after their pull requests are merged or closed.
- [ ] *Captain's machine*: decide on `research/EDI-29`, the one unmerged branch that belongs to no open pull request; Renovate's branches belong to their open pull requests.

## 6. Public settings

- [x] `LICENSE` holds the Apache License 2.0, as decision D60 chose, and `pom.xml` and the README name it.
- [x] `SECURITY.md` asks for private vulnerability reporting and says what is in scope.
- [x] Every action is pinned to a commit (EDI-131); the CI service image's digest and the mise version follow in EDI-140, in review.
- [ ] *Captain's machine*: turn on secret scanning with push protection, dependency alerts and private vulnerability reporting.
- [ ] *Captain's machine*: set the default workflow token to read-only, and require approval for the workflows of first-time contributors.
- [ ] *Captain's machine*: protect `main`: no force-push, no deletion, changes through pull requests only.

## The switch

When every box above is ticked, the captain makes the repository public in its settings: **General**, **Danger Zone**, **Change repository visibility**, **Make public**.
The final report then says what was cleaned, what GitHub Support must purge, if anything the web interface could not remove, and that the switch was made.

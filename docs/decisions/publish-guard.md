# Every outgoing commit passes the publish guard, which spares only the pushing project's own names

**Kind:** Architecture

## Context

No secret and no personal material may ever be published, from EDI or from any project EDI pushes (I-38).
Personal material includes an employer's hostnames and account names, which the tracked harness must not hold either ([machine-and-person-specifics](machine-and-person-specifics.md)).
A finding must name its place, never the matched value, since reports and logs are read and kept.
An employer's project names its own forge host, tracker host and keys file in its tracked files.
No pushed `pom.xml` may carry a SNAPSHOT version (I-11), and a ship's done, which checks for one, comes after the push that published it.

## Decision

Before any push, `PublishCheck` checks the outgoing commits of a revision range four ways:

- a secret scan with gitleaks and its standard rules only, which a repository cannot weaken with its own configuration, ignore file or allow comments, read from a redacted report (`Gitleaks`);
- the captain's deny-list `config/publish-denylist`, matched as case-insensitive literals in every added line, every added or changed path, every message and every author and committer identity, and reported by entry number (`DenyList`);
- a path check refusing the gitignored `personal/` layer and anything under the EDI home (`PersonalPaths`);
- a build-file check refusing every line an outgoing commit adds to a `pom.xml` with a `-SNAPSHOT` word, the rule a ship's done applies to its branch (`SnapshotVersions.findIn`).

The list a push is checked against leaves out every term equal, whatever its case, to one of the pushing project's own names: its forge host and account from the registry and, when it books on the machine's JIRA, the tracker's host and keys-file name (`DenyList.sparing`, `OwnNames`).
`PublicationTargets.ownNamesOf` finds the project only from the repository's top level, its isolated copy or its registered clone, never from a remote URL; any other repository spares nothing.
A path that carries a denied term is never named: every finding on it names it by its position, such as `line 1 of path 2 of commit abc1234` (`PublishVerdict.hidingPathsDeniedBy`); deleted paths publish nothing and are not checked.
`edi publish-check` exposes the guard, each copy's `pre-push` hook runs it ([isolated-copies](isolated-copies.md)), `edi deliver` runs it, and CI runs the same guard over every pushed range through `PublishGuard` with the deny-list from the Actions secret `PUBLISH_DENYLIST`.
`PublishCheckTest` proves it with invented patterns and secrets generated at run time, so the tracked test carries nothing personal.

## Why

A leak is stopped before it leaves the machine, whoever pushes, and the guard itself never becomes the leak.
A SNAPSHOT version is stopped at the push that would publish it, not at a done that comes after it, and every commit is checked because each commit a push publishes carries its own `pom.xml`.
The registry and the profile already state each project's own names, so sparing them needs no new configuration, while every other term and every secret is refused as before.

## Alternatives rejected

- A registry marking that selects the projects the list applies to - drops every term for an unmarked project, another account's name included.
- A list that applies whole to every project - refuses an employer's own commits for naming their own hosts.
- A per-project exemption list, or sparing a term that contains an own name - more configuration, or a broader term such as an employer's domain let through.
- gitleaks alone, regular expressions in the list, or honouring a repository's gitleaks configuration - no personal terms, patterns mistyped into matching nothing, or a branch that switches the scan off.
- Checking only what the range adds as a whole, as the done does - a SNAPSHOT version added in one outgoing commit and removed in a later one would still be published.

## Consequences

- Good: one guard for EDI and every project, and the guard CI runs is the guard the test proves.
- Bad: `edi publish-check` in a checkout outside EDI's home applies the whole list, and a listed term equal to a project's own name no longer guards that project.
- Bad: the CI job builds `edi-core` before it scans.
- Bad: a SNAPSHOT version committed and then removed again before a push must be squashed out of the unpushed commits.

## When to revisit

When the deny-list is reworked, which the captain has asked for, or EDI pushes from a repository other than an isolated copy.

## How we got here

- 15/10/2026: slice S0 built the guard and its CI entry point; slice S3 installed it as the `pre-push` hook.
- 19/10/2026: the registry marking that was to limit the deny-list to personal projects was never added, so every employer push touching its own hosts was refused; the captain chose to keep one list and spare each project's own names.
- 27/10/2026: a SNAPSHOT version was refused only at a ship's done, after the push that published it, so the guard refuses one in any outgoing commit (EDI-78).

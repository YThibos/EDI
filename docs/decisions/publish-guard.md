# Every outgoing commit and every request text EDI posts passes the publish guard, which spares only the pushing project's own names

**Kind:** Architecture

## Context

No secret and no personal material may ever be published, from EDI or from any project EDI pushes (I-38).
Personal material includes an employer's hostnames and account names, which the tracked harness must not hold either ([machine-and-person-specifics](machine-and-person-specifics.md)).
A finding must name its place, never the matched value, since reports and logs are read and kept.
An employer's project names its own forge host, tracker host and keys file in its tracked files.
No pushed `pom.xml` may carry a SNAPSHOT version (I-11), and a ship's done, which checks for one, comes after the push that published it.
A pull request's or merge request's title and description are published too, and the review of 29 October 2026 found internal addresses, hostnames, an account, home paths and work keys in 17 of 87 descriptions whose every commit had passed the guard.

## Decision

Before any push, `PublishCheck` checks the outgoing commits of a revision range four ways:

- a secret scan with gitleaks and its standard rules only, which a repository cannot weaken with its own configuration, ignore file or allow comments, read from a redacted report (`Gitleaks`);
- the captain's deny-list `config/publish-denylist`, matched as case-insensitive literals in every added line, every added or changed path, every message and every author and committer identity, and reported by entry number (`DenyList`);
- a path check refusing the gitignored `personal/` layer and anything under the EDI home (`PersonalPaths`);
- a build-file check refusing every line an outgoing commit adds to a `pom.xml` with a `-SNAPSHOT` word, the rule a ship's done applies to its branch (`SnapshotVersions.findIn`), except a line inside a module's `<parent>` element that names a `pom.xml` of the same repository, at the commit, by its groupId and artifactId and that pom's own version (`Reactor`); the poms are read, with a parser that refuses a DOCTYPE, only for a commit that adds a SNAPSHOT line (`Git.reactorAt`).

The list a push is checked against leaves out every term equal, whatever its case, to one of the pushing project's own names: its forge host and account from the registry and, when it books on the machine's JIRA, the tracker's host and keys-file name (`DenyList.sparing`, `OwnNames`).
`PublicationTargets.ownNamesOf` finds the project only from the repository's top level, its isolated copy or its registered clone, never from a remote URL; any other repository spares nothing.
A path that carries a denied term is never named: every finding on it names it by its position, such as `line 1 of path 2 of commit abc1234` (`PublishVerdict.hidingPathsDeniedBy`); deleted paths publish nothing and are not checked.
A push judges only the commits new to both the remote branch and the base: the copy's hook checks `origin/<base>..<local head>` excluding the remote branch's head, an exclusion `RevisionRange` hands to both `git rev-list` and gitleaks.
`edi publish-check` exposes the guard, each copy's `pre-push` hook runs it ([isolated-copies](isolated-copies.md)), `edi deliver` runs it, and CI runs the same guard over every pushed range through `PublishGuard` with the deny-list from the Actions secret `PUBLISH_DENYLIST`.
`PublishCheckTest` proves it with invented patterns and secrets generated at run time, so the tracked test carries nothing personal.

A request's title and description pass the same guard before EDI posts or accepts them (`PublishCheck.checkText`, `TextVerdict`):

- the gitleaks scan, over the title and the description written to a scan workspace (`Gitleaks.secretsIn(RequestText)`);
- the deny-list, sparing the project's own names as for a push, on the title and every line of the description;
- a home path, such as `/home/<name>`, `/Users/<name>`, `/root`, `/mnt/<drive>/Users/<name>` or `C:\Users\<name>` (`HomePaths`);
- an IP address outside the documentation ranges (192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 and 2001:db8::/32), loopback and private ranges included, and any host given with its port, such as `localhost:5490`, whatever the deny-list names (`NetworkAddresses`); a file name with its line number, such as `ApiTest.java:192`, is not a host.

A finding names its place, `the title` or `line 3 of the description`, and its rule or deny-list entry, never the value.
`edi deliver` checks the draft it would open and refuses to post it, and checks a request the pipeline already opened before it records it; a ship's done is refused while its request's text fails the check, so the request never shows as ready for review, and the refusal tells the worker to rewrite the text on the forge (`DoneClaims`).
The forges read a request's text through `Forge.textOf`; EDI posts no comment and edits no request, and once EDI's own Publish slice ([EDI-147](../backlog/EDI-147.md)) posts the pipeline's descriptions, the check sits in that slice's posting path.

## Why

A leak is stopped before it leaves the machine, whoever pushes, and the guard itself never becomes the leak.
A SNAPSHOT version is stopped at the push that would publish it, not at a done that comes after it, and every commit is checked because each commit a push publishes carries its own `pom.xml`.
A module must name its parent's version, and the parent of a module in the same reactor is released together with it, so that reference is no SNAPSHOT dependency (D82 A).
The registry and the profile already state each project's own names, so sparing them needs no new configuration, while every other term and every secret is refused as before.

## Alternatives rejected

- A registry marking that selects the projects the list applies to - drops every term for an unmarked project, another account's name included.
- A list that applies whole to every project - refuses an employer's own commits for naming their own hosts.
- A per-project exemption list, or sparing a term that contains an own name - more configuration, or a broader term such as an employer's domain let through.
- gitleaks alone, regular expressions in the list, or honouring a repository's gitleaks configuration - no personal terms, patterns mistyped into matching nothing, or a branch that switches the scan off.
- Checking only what the range adds as a whole, as the done does - a SNAPSHOT version added in one outgoing commit and removed in a later one would still be published.
- Judging a push from the remote branch's head alone - a merge of a base that gained a refused line brings the base's commits into the range, and the push is stranded, since force-pushing and skipping the hook are refused.
- Judging a push from the base alone - the branch's commits already pushed are judged again after a rule tightens, and stranded the same way.

## Consequences

- Good: one guard for EDI and every project, and the guard CI runs is the guard the test proves.
- Good: a request's text can no longer carry what a commit could not, so the cleaning of the descriptions stays done.
- Bad: a description that names the core's loopback address and port, or any host with a port, is refused and must say it in words or with a documentation address.
- Bad: the pipeline posts its description before EDI sees it, so a refused text stays on the forge until the worker rewrites it; EDI only keeps the request from counting as ready.
- Bad: `edi publish-check` in a checkout outside EDI's home applies the whole list, and a listed term equal to a project's own name no longer guards that project.
- Bad: the CI job builds `edi-core` before it scans.
- Bad: a SNAPSHOT version committed and then removed again before a push must be squashed out of the unpushed commits.
- Bad: the parent's lines are found as the first `<parent>` to the first `</parent>` after it in the file, so a `pom.xml` that writes either in a comment above the real one may have its parent misplaced, which refuses the reference rather than admitting another line.
- Bad: a commit already on the remote branch or in the base is not judged again by a push, so a tightened rule reaches it only through a ship's done and CI.

## When to revisit

When the deny-list is reworked, which the captain has asked for, or EDI pushes from a repository other than an isolated copy.

## How we got here

- 15/10/2026: slice S0 built the guard and its CI entry point; slice S3 installed it as the `pre-push` hook.
- 19/10/2026: the registry marking that was to limit the deny-list to personal projects was never added, so every employer push touching its own hosts was refused; the captain chose to keep one list and spare each project's own names.
- 27/10/2026: a SNAPSHOT version was refused only at a ship's done, after the push that published it, so the guard refuses one in any outgoing commit (EDI-78).
- 27/10/2026: a merged base that gained a SNAPSHOT line stranded the branch's next push, so the captain chose to judge only the commits new to both the remote branch and the base (EDI-78).
- 29/10/2026: a request's title and description pass the guard before EDI posts or accepts them, and an IP address outside the documentation ranges or a host with its port is refused in them, after the review found internal addresses and hostnames in descriptions whose commits had passed (EDI-150).
- 03/11/2026: a new module's parent reference to its own reactor's SNAPSHOT version was refused at done and push, though every sibling module carries the same line, so the captain allowed it (D82 A) and the guard and the done admit it (EDI-167).

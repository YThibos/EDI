# ADR-144: The publish guard checks every outgoing commit, and a project may publish its own forge and tracker names

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** No secret and no personal material may ever be published, from EDI itself or from any project EDI pushes (I-38).
Personal material includes an employer's hostnames and account names, which must not appear in the tracked harness either (ADR-017).
A finding must be reported with its file and line, never with the matched value, since reports and logs are themselves read and kept.
ADR-032 meant the deny-list for the projects the project registry marks as personal, but that marking was never added, so the guard applied the whole list to every push of every project.
An employer's project names its own forge host, its JIRA host and its keys file in tracked files, and the import seeds the deny-list with exactly those names (ADR-139), so every push of such a project that touched them was refused.
The captain chose to keep one deny-list and let each project's push skip the terms that are that project's own names, as an interim until the deny-list is reworked.
I-38 now reads: every push EDI makes, to any repository, first passes a secret scan with gitleaks, the captain's personal deny-list less the terms that are the pushing project's own forge host, forge account, tracker host and keys-file name, and a path check refusing the personal layer and the EDI home.

**Decision:** This record supersedes ADR-032 and keeps its guard, changed in one place: the deny-list spares the pushing project's own names.
Before any push, EDI checks the outgoing commits (a revision range such as `origin/main..HEAD`) three ways, implemented by `PublishCheck`:
- a secret scan with gitleaks and its standard rules only: `Gitleaks` passes a configuration that extends the default rules, an empty ignore path and `--ignore-gitleaks-allow`, so a repository cannot weaken the scan with its own configuration, ignore file or inline allow comments; it asks for a redacted JSON report and reads only the rule, file, line and commit;
- the captain's personal deny-list from `$EDI_HOME/config/publish-denylist`: one term per line, blank lines and `#` comments ignored, matched as case-insensitive literals in every added line, every path a commit adds or changes, every commit message and every author and committer identity, and reported by entry number (`DenyList`);
- a path check refusing the gitignored personal layer `personal/` and, if the EDI home lies inside the repository, anything under it (`PersonalPaths`).

The deny-list a push is checked against leaves out every term equal, whatever its case, to one of the pushing project's own names (`DenyList.sparing`, `OwnNames`).
A project's own names are its forge's host and account from the project registry (`RegisteredForge.identifyingNames`) and, when the project books its work on the machine's JIRA, the tracker's host and the file name of its keys file from the machine profile (`TrackerSettings.identifyingNames`).
`PublicationTargets.ownNamesOf` finds the project from the repository's top level, compared by real path: the isolated copy under `$EDI_HOME/worktrees/<project>/<task>` that the `pre-push` hook and `edi deliver` check, as a push's registered target is found (ADR-086), or the project's clone the project registry records, by default `$EDI_HOME/projects/<project>`, where the captain runs `edi publish-check` by hand.
Any other repository, such as the captain's checkout or EDI's checkout in CI, spares nothing, and no project is ever guessed from a remote URL.
The push's target check stays with the isolated copy alone.
Every other term still applies, such as another account on the same forge, and so does a term that merely contains an own name or is contained in one.
EDI itself and the captain's projects on a public forge keep the whole list, because none of their own names is on it.
The secret scan and the path check do not depend on the project, and sparing a keys file's name spares only the name: the secret scan still reads the content of every added line.
`DenyListImport` takes the names it seeds from the same two methods, so the import adds exactly the names a project's own push later spares.

A path that carries a denied term is never named in any report, because the path would name the term: every finding on such a path, whether a denied term, a secret or a personal path, names it by its 1-based position in the commit's list of published paths, for example `path 2 of commit abc1234` or `line 1 of path 2 of commit abc1234`; `PublishVerdict.hidingPathsDeniedBy` applies this once to every finding before the verdict is reported, with the whole deny-list, so a project's own names stay hidden in reports too.
`Git` reads each outgoing commit's identities, message, the paths it adds or changes and its added lines with their line numbers in the new version; a removed path publishes nothing new, so deleted paths are not read or checked and removing a file is never refused; merge commits are left to gitleaks.
The paths are numbered in the order `Git` reads them, so `git -c core.quotePath=false -c color.ui=never diff-tree -r --root --no-commit-id --name-only --diff-filter=d -z <commit>`, the adapter's own command, lists them in that order, separated by NUL bytes, for a root commit too.
`RevisionRange` accepts one git revision expression and refuses anything that could be read as an option.
`PublishCheckTest` proves the guard with invented patterns and secrets generated at run time, so the tracked test carries nothing personal and nothing a scanner would flag: a branch with a secret, one with a deny-listed term, one adding a file whose name and content carry a deny-listed term and a secret, reported without the name, and one with personal paths are refused, and a clean branch passes.
AT-38 in `TwoMachinesTest` pushes through the worker's `pre-push` hook: a copy of an employer-shaped project, on GitLab at a private host and tracked on JIRA, may push a commit naming its forge host, its account, its tracker host and its keys file, the same commit from an EDI-shaped project on GitHub is refused, another account's name is still refused beside the own names, and a secret is refused from both.
It also runs the publish check as `edi publish-check` does in each project's registered clone: the employer-shaped project's clone is clear for the same commit, and EDI's clone is refused it.
CI runs this same guard over every pushed range through `PublishGuard`, a plain entry point that wires `PublishCheck` exactly as `PublishCheckTest` does, started by the `publish-guard` Maven profile of `edi-core`; it reads the deny-list from the optional Actions secret `PUBLISH_DENYLIST`, written into a temporary EDI home, prints only locations and fails the job on any finding.
`edi publish-check` exposes the guard, each worktree's `pre-push` hook runs it (ADR-053), and `edi deliver` runs it before it opens a draft (AT-38).

**Motivation:** The registry and the machine profile already state each project's forge, account and tracker, so a project's own names need no new configuration, and the import and the guard cannot drift apart; every other term and every secret is refused as before, so one employer's or account's names still cannot leak into another project.

**Alternatives considered:**
- A registry marking that selects the projects the deny-list applies to (ADR-032) - drops every term for an unmarked project, another account's name included, and is one more setting the captain must keep right.
- A deny-list that applies whole to every project, as the guard did until now - refuses an employer's own commits for naming their own hosts.
- A per-project exemption list, or one deny-list file per project - more configuration for names the registry and the profile already hold.
- Sparing a term that contains or is contained in an own name - would let a broader term, such as an employer's domain, through.
- Finding the project from its isolated copy alone - `edi publish-check` run by hand in the project's clone, where the captain met the refusal, would still refuse the project's own names.
- Finding the project from the captain's checkout or from a remote URL as well - a checkout outside EDI's home is not EDI's to vouch for, and a remote URL can name any repository.
- gitleaks alone - knows nothing of the captain's personal terms or the personal layer.
- Regular expressions in the deny-list - hostnames are literals, and literal matching cannot be mistyped into a pattern that matches nothing.
- Honouring a repository's own gitleaks configuration - would let a branch switch the scan off.

**Consequences:**
- Good: an employer's project ships its own work without the captain trimming the deny-list.
- Good: one guard for EDI and every project, and the guard CI runs is the guard the synthetic test proves, so the two cannot drift apart.
- Bad: `edi publish-check` run by hand in a checkout outside EDI's home, such as the captain's checkout, still applies the whole list, so it can refuse a commit that the same project's copy or clone may push.
- Bad: a term the captain lists that equals a project's own name no longer guards that project's pushes.
- Bad: the CI job builds `edi-core` before it can scan, which costs a few minutes per push.

**Revisit when:** The deny-list is reworked, which the captain has asked for; a personal term must be kept out of the very project whose own name it is; or EDI pushes from a repository other than an isolated copy.

**Sources:** EDI rewrite specification, sections 4.8 (I-38, AT-38), 6 and 13.1 (decision E8); the captain's decision D83; ADR-017, ADR-032, ADR-053, ADR-086, ADR-139; `edi-core/src/main/java/io/github/ythibos/edi/application/publish/`, `edi-core/src/main/java/io/github/ythibos/edi/domain/publish/`, `domain.delivery.RegisteredForge`, `adapter.home.TrackerSettings`, `application.migration.DenyListImport`, `edi-core/src/main/java/io/github/ythibos/edi/adapter/git/`, `edi-core/src/main/java/io/github/ythibos/edi/adapter/gitleaks/`.

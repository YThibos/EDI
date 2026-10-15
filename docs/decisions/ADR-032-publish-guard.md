# ADR-032: The publish guard checks every outgoing commit

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** No secret and no personal material may ever be published, from EDI itself or from any project EDI pushes (I-38).
Personal material includes an employer's hostnames and account names, which must not appear in the tracked harness either (ADR-017).
A finding must be reported with its file and line, never with the matched value, since reports and logs are themselves read and kept.

**Decision:** Before any push, EDI checks the outgoing commits (a revision range such as `origin/main..HEAD`) three ways, implemented by `PublishCheck`:
- a secret scan with gitleaks and its standard rules only: `Gitleaks` passes a configuration that extends the default rules, an empty ignore path and `--ignore-gitleaks-allow`, so a repository cannot weaken the scan with its own configuration, ignore file or inline allow comments; it asks for a redacted JSON report and reads only the rule, file, line and commit;
- the captain's personal deny-list from `$EDI_HOME/config/publish-denylist`: one term per line, blank lines and `#` comments ignored, matched as case-insensitive literals in every added line, every path a commit adds or changes, every commit message and every author and committer identity, and reported by entry number (`DenyList`); it applies only to projects the project registry marks as personal, such as EDI itself and the captain's hobby projects, because an employer's projects legitimately name the employer's hosts;
- a path check refusing the gitignored personal layer `personal/` and, if the EDI home lies inside the repository, anything under it (`PersonalPaths`).

`Git` reads each outgoing commit's identities, message, the paths it adds or changes and its added lines with their line numbers in the new version; a removed path publishes nothing new, so removing a file is never refused; merge commits are left to gitleaks.
A path that carries a denied term is never named in any report, because the path would name the term: every finding on such a path, whether a denied term, a secret or a personal path, names it by its 1-based position in the commit's list of published paths, for example `path 2 of commit abc1234` or `line 1 of path 2 of commit abc1234`; `PublishVerdict.hidingPathsDeniedBy` applies this once to every finding before the verdict is reported.
The paths are numbered in the order `Git` reads them, so `git -c core.quotePath=false -c color.ui=never diff-tree -r --root --no-commit-id --name-only --diff-filter=d -z <commit>`, the adapter's own command, lists them in that order, separated by NUL bytes, for a root commit too.
`RevisionRange` accepts one git revision expression and refuses anything that could be read as an option.
`PublishCheckTest` proves the guard with invented patterns and secrets generated at run time, so the tracked test carries nothing personal and nothing a scanner would flag: a branch with a secret, one with a deny-listed term, one adding a file whose name and content carry a deny-listed term and a secret, reported without the name, and one with personal paths are refused, and a clean branch passes.
CI runs this same guard over every pushed range through `PublishGuard`, a plain entry point that wires `PublishCheck` exactly as `PublishCheckTest` does, started by the `publish-guard` Maven profile of `edi-core`; it reads the deny-list from the optional Actions secret `PUBLISH_DENYLIST`, written into a temporary EDI home, prints only locations and fails the job on any finding.
Slice S1 exposes the guard as `edi publish-check`, slice S3 installs it as each worktree's `pre-push` hook, and slice S5 runs it in `edi deliver` (AT-38) and adds the registry marking that selects the projects the deny-list applies to.

**Motivation:** A leak is stopped before it leaves the machine, whoever pushes, and the guard itself never becomes the leak.

**Alternatives considered:**
- A deny-list that applies to every project - the employer's own projects legitimately name the employer's hosts, so every push to them would be refused.
- One deny-list file per project, or entries that list the hosts where they are allowed - more configuration for the same effect.
- gitleaks alone - knows nothing of the captain's personal terms or the personal layer.
- Regular expressions in the deny-list - hostnames are literals, and literal matching cannot be mistyped into a pattern that matches nothing.
- Honouring a repository's own gitleaks configuration - would let a branch switch the scan off.

**Consequences:**
- Good: one guard for EDI and every project.
- Good: the guard CI runs is the guard the synthetic test proves, so the two cannot drift apart.
- Bad: the CI job builds `edi-core` before it can scan, which costs a few minutes per push.
- Bad: until slice S5 adds the registry marking, the guard applies whatever deny-list it is given; on the laptop it must not be run with the employer's terms against the employer's own projects.

**Revisit when:** `edi publish-check` exists, so CI can run the command instead of the entry point; or a personal term must also be kept out of an employer's project.

**Sources:** EDI rewrite specification, sections 4.8 (I-38, AT-38), 6 and 13.1 (decision E8); `edi-core/src/main/java/io/github/ythibos/edi/application/publish/PublishCheck.java`, `edi-core/src/main/java/io/github/ythibos/edi/domain/publish/`, `edi-core/src/main/java/io/github/ythibos/edi/adapter/git/`, `edi-core/src/main/java/io/github/ythibos/edi/adapter/gitleaks/`.

# ADR-032: The publish guard checks every outgoing commit

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** No secret and no personal material may ever be published, from EDI itself or from any project EDI pushes (I-38).
Personal material includes an employer's hostnames and account names, which must not appear in the tracked harness either (ADR-017).
A finding must be reported with its file and line, never with the matched value, since reports and logs are themselves read and kept.

**Decision:** Before any push, EDI checks the outgoing commits (a revision range such as `origin/main..HEAD`) three ways, implemented by `PublishCheck`:
- a secret scan with gitleaks and its standard rules only: `Gitleaks` passes a configuration that extends the default rules, an empty ignore path and `--ignore-gitleaks-allow`, so a repository cannot weaken the scan with its own configuration, ignore file or inline allow comments; it asks for a redacted JSON report and reads only the rule, file, line and commit;
- the captain's personal deny-list from `$EDI_HOME/config/publish-denylist`: one term per line, blank lines and `#` comments ignored, matched as case-insensitive literals in every added line, every commit message and every author and committer identity, and reported by entry number (`DenyList`); it applies only to projects the project registry marks as personal, such as EDI itself and the captain's hobby projects, because an employer's projects legitimately name the employer's hosts;
- a path check refusing the gitignored personal layer `personal/` and, if the EDI home lies inside the repository, anything under it (`PersonalPaths`).

`Git` reads each outgoing commit's identities, message, touched paths and added lines with their line numbers in the new version; merge commits are left to gitleaks.
`RevisionRange` accepts one git revision expression and refuses anything that could be read as an option.
`PublishCheckTest` proves the guard with invented patterns and secrets generated at run time, so the tracked test carries nothing personal and nothing a scanner would flag: a branch with a secret, one with a deny-listed term, one with personal paths are refused, and a clean branch passes.
CI runs the same three checks over every pushed range with `ops/publish-scan.sh`, which reads the deny-list from the optional Actions secret `PUBLISH_DENYLIST` and prints only locations.
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
- Bad: CI's scan is a shell script beside the Java guard until the command exists; the two must agree.
- Bad: until slice S5 adds the registry marking, the guard applies whatever deny-list it is given; on the laptop it must not be run with the employer's terms against the employer's own projects.

**Revisit when:** `edi publish-check` exists, so CI can run the guard itself; or a personal term must also be kept out of an employer's project.

**Sources:** EDI rewrite specification, sections 4.8 (I-38, AT-38), 6 and 13.1 (decision E8); `edi-core/src/main/java/io/github/ythibos/edi/application/publish/PublishCheck.java`, `edi-core/src/main/java/io/github/ythibos/edi/domain/publish/`, `edi-core/src/main/java/io/github/ythibos/edi/adapter/git/`, `edi-core/src/main/java/io/github/ythibos/edi/adapter/gitleaks/`, `ops/publish-scan.sh`.

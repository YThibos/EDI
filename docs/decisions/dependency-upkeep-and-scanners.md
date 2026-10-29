# Self-hosted Renovate keeps dependencies current, and open-source scanners gate every push

**Kind:** Architecture

## Context

The harness and its tools should be up to date and in best shape at all times, without a hosted bot ([open-source-tools](open-source-tools.md)).
On another repository, five stalled major upgrades filled a concurrency cap of five, so no patch or minor update was proposed for weeks.
Vulnerabilities and quality regressions should show as a red build like any other failure, and an ephemeral SonarQube server keeps no history to compare new code against.

## Decision

Renovate runs in this repository's GitHub Actions (`renovatebot/github-action`), nightly and on demand, with its policy in `renovate.json`:

- managers for Maven, the wrapper, npm, GitHub Actions, mise and the local SonarQube compose file, plus regex managers for the Node the frontend plugin downloads and the version of GraalVM Community in `mise.toml`, read from its `graal-` release tags, whose archive names and checksums are then moved by hand ([java-toolchain-and-build](java-toolchain-and-build.md));
- minor, patch, pin and digest updates share a budget of ten pull requests, while each major is its own pull request within a separate budget of three;
- Spring Boot, Angular and the console's Node are grouped, and a release must be three days old;
- every pull request is a draft, nothing is automerged, and subjects and titles start with `EDI-1` ([work-keys-and-branches](work-keys-and-branches.md));
- `pinDigests` is on for the GitHub Actions manager, so every `uses:` in `.github/workflows` names a full commit SHA with its tag as a comment on the same line, and Renovate moves both together; `PinnedActionsTest` fails on an action named by a tag alone.

CI runs on every push and pull request:

- OWASP Dependency-Check, failing at CVSS 7 or above, with its vulnerability database cached between runs and NVD requests spaced to avoid the NVD's 403s;
- `npm audit --audit-level=high` over the console's build dependencies;
- SonarQube Community Build as a service container, with a quality gate on overall code: reliability, security and maintainability rated A and at most 3% duplication;
- gitleaks and the publish guard ([publish-guard](publish-guard.md)).

A finding is fixed, not suppressed: when Spring Boot's managed Tomcat carried vulnerabilities and no Spring Boot release fixed them, the parent `pom.xml` overrode `tomcat.version`, kept current by a Renovate regex manager, until Spring Boot 4.1.2 managed the fixed Tomcat.
Likewise, when the Angular build pinned a vulnerable Vite, the console's `package.json` overrode `vite` with its patched release, which Renovate's npm manager keeps current.
The `edi-upkeep` skill lists Renovate's pull requests with their checks, raises one decision per green batch, and turns a red major into a migration task.

## Why

The same tool the captain already runs elsewhere, configured so a stalled major can never block the rest, and every upgrade proven by the full build, native included.
Security and quality are part of green, and the gate exists to fail on exactly what it finds.
A tag can be moved by whoever maintains an action, and once the repository is public its workflows run on whatever the tag then points at; a commit SHA cannot move, and the tag beside it keeps the pin readable.

## Alternatives rejected

- A skill that upgrades by hand - slow and easy to forget.
- The hosted Renovate app or SonarCloud - third-party services.
- A new-code quality gate - meaningless on a server with no history.
- OWASP DockSec - analyses container images, which EDI does not ship.
- Suppressing the Tomcat findings, or waiting for Spring Boot - hides real vulnerabilities, or leaves them in the core and the gate red.

## Consequences

- Good: dependencies, plugins, actions, the wrapper and the toolchain stay current.
- Bad: nightly native builds cost CI minutes, a cold Dependency-Check run downloads its whole database for over an hour, coverage is reported but not gated, and an override such as the Vite one must be removed by hand once its upstream catches up.

## When to revisit

When CI minutes become a problem, Renovate's mise manager learns GraalVM Community, Angular's build depends on a fixed Vite, EDI ships a container image, or coverage becomes a gate.

## How we got here

- 15/10/2026: the captain asked for automatic dependency upgrades, a vulnerability scanner and a code-quality scanner; slice S0 set up Renovate and the four scans, and overrode Tomcat for nine CVEs the scanner found.
- 27/10/2026: GraalVM Community moved to mise's `http` backend and its `graal-` release tags, so its regex manager followed, and `npm audit` found a high-severity Vite the Angular build pinned, which the console overrides (EDI-100).
- 28/10/2026: Spring Boot 4.1.2 manages Tomcat 11.0.27, so the `tomcat.version` override and its regex manager were removed (EDI-100).
- 28/10/2026: every GitHub Action is pinned to a full commit SHA, verified against the action's repository, with its tag beside it, and Renovate pins and keeps the digests, before the repository is made public (EDI-131).

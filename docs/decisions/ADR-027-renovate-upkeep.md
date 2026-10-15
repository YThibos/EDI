# ADR-027: Dependency upkeep through self-hosted Renovate

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain: "And what about automatic dependency upgrades in Maven? A skill for this, perhaps? I want to be up-to-date and have the harness/ship in best shape at all times."
On another repository, five stalled major upgrades permanently filled a concurrency cap of five, so no patch or minor update was proposed for weeks.

**Decision:** Renovate runs in this repository's GitHub Actions (`renovatebot/github-action`, nightly at 03:00 UTC and on demand) with the policy in `renovate.json`:
- managers for Maven, the Maven wrapper, npm, GitHub Actions and mise, plus two regex managers: the Node version the frontend Maven plugin downloads, and GraalVM Community in `mise.toml`, which the mise manager cannot read and which follows GraalVM Community's `jdk-*` releases;
- minor, patch, pin and digest updates share a budget of ten pull requests, and each major is its own pull request with a separate budget of three, so a stalled major can never starve the others;
- the Spring Boot parent, plugin and managed dependencies are one group, the Angular packages another, and the Node used to build the console a third;
- `minimumReleaseAge` of three days with strict internal checks, so a broken release is usually withdrawn before it is proposed;
- every pull request is a draft and nothing is automerged; the captain merges;
- commit subjects and pull request titles start with `EDI-1`, the upkeep key, and Renovate's `renovate/` branch names are accepted;
- the Dependency Dashboard issue is enabled, titled "EDI-1 Dependencies and tooling upkeep".

Every Renovate pull request runs the full CI, including the native build and smoke test.
The `edi-upkeep` skill (a later slice) lists open Renovate pull requests with their CI state, raises one decision per green batch, turns a red major into a migration task, and reports the versions of the tools EDI drives.
Renovate needs a token with rights on the repository, stored as the Actions secret `RENOVATE_TOKEN`.

**Motivation:** The same tool the captain already runs elsewhere, configured with the lesson that a stalled major must never block the rest.

**Alternatives considered:**
- A skill that upgrades by hand - slow and easy to forget.
- The hosted Renovate app - a third-party service, against "as few third-party services as possible".

**Consequences:**
- Good: dependencies, plugins, actions, the wrapper and the toolchain stay current, each change proven by CI.
- Bad: nightly pull requests cost CI minutes, the native build most of all.

**Revisit when:** The CI cost of nightly native builds becomes a problem, or Renovate's mise manager learns GraalVM Community.

**Sources:** EDI rewrite specification, section 5.7 (decision E18); `renovate.json`, `.github/workflows/renovate.yml`.

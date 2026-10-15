# ADR-006: Machine differences are chosen at runtime

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** EDI runs on a work laptop with an on-premises GitLab, JIRA and a time-booking tool, and on a private desktop with GitHub only.
A native image evaluates `@Profile` and `@ConditionalOnProperty` once, at build time, so one executable could not serve both machines if the choice were made that way.

**Decision:** Forge, tracker, ticket keys, worklog emission, secrets and paths come from the machine profile (`machine.yaml`) and the project registry at runtime, through an adapter registry.
No class or bean method uses `@Profile` or `@ConditionalOnProperty`; an architecture rule enforces it.

**Motivation:** One codebase and one set of executables serve both machines (I-37), and no code path assumes JIRA, GitLab or the laptop.

**Alternatives considered:**
- Spring profiles per machine - fixed at native build time, so it would need one build per machine.
- Two builds - doubles CI and invites drift.

**Consequences:**
- Good: the same tests run under both machine profiles (AT-37).
- Bad: the adapter registry is hand-written selection code instead of Spring's conditional configuration.

**Revisit when:** Native images support evaluating conditions at runtime.

**Sources:** EDI rewrite specification, sections 5.5, 7.2 and I-37; `ArchitectureRulesTest.noClassChoosesMachineDifferencesAtBuildTime` and `noBeanMethodChoosesMachineDifferencesAtBuildTime`.

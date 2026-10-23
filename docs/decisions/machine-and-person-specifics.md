# Machine and person specifics live in the EDI home and are read at runtime

**Kind:** Architecture

## Context

EDI runs on a work laptop with an on-premises GitLab, JIRA and a time-booking tool, and on a private desktop with GitHub only, from one public-ready codebase.
A native image evaluates `@Profile` and `@ConditionalOnProperty` once, at build time, so one executable could not serve both machines if the choice were made that way.
The harness should be reusable by anyone, so nothing about one person, employer or machine may be tracked.

## Decision

Tracked files hold generic code, defaults and documented examples with invented names only.
Everything about a person, an employer or a machine lives in the EDI home (`$EDI_HOME`, default `~/.edi`): the machine profile `config/machine.yaml`, the project registry, the dispatch rules, the personal configuration, the publish deny-list, memory, the store and state; or in the checkout's gitignored `personal/` directory, which holds personal skills and addenda to the anchor and briefs.
The EDI home is private, never a git repository and never shared between machines.
Forge, tracker, ticket keys, worklog emission, secrets and paths are chosen from the machine profile and the registry at the moment of use, through hand-written selection such as `Forges` and `Trackers`.
No class or bean method uses `@Profile` or `@ConditionalOnProperty`, which `ArchitectureRulesTest` enforces.
The core module's Surefire runs the whole suite twice in `./mvnw verify`, under a `laptop` fixture profile with JIRA, the fleet ledger and the worked line, and under a `desktop` one with none of them (`edi.test.machine`, `MachineFixture`); AT-37 checks the profiles differ only there.
The publish guard keeps personal material from ever leaving the machine ([publish-guard](publish-guard.md)).

## Why

One codebase and one set of executables serve both machines and anyone else, and nothing personal can leak through the code itself.
A code path that assumes one machine fails the other profile's run, which a single run could never show.

## Alternatives rejected

- Spring profiles per machine, or two builds - fixed at native build time, doubling the build and inviting drift.
- Personal configuration in a private repository - ties the harness to one person and one employer.
- Parameterising every test by profile - every existing test would change.

## Consequences

- Good: decision records, tests and examples use invented names, and the same tests prove both machines in every build.
- Bad: setting up a machine needs the EDI home's configuration, which the repository cannot ship, and the selection code is hand-written.
- Bad: the core's tests run twice, which adds about forty seconds to `./mvnw verify`.

## When to revisit

When native images can evaluate conditions at runtime, or a third machine profile appears.

## How we got here

- 15/10/2026: the specification made the harness person- and employer-agnostic and chose machine differences at runtime, because native images fix Spring's conditions at build time.
- 17/10/2026: slice S6 ran the suite under both fixture profiles.

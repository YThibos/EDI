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
It is open to its owner alone: `ops/install.sh` makes it mode 0700, `edi up` and the systemd unit start the core under umask 077, and at every start, before the store opens, `PrivateHome` creates a missing home with mode 0700, takes every group and other permission off the home itself and off everything under `config`, `data`, `memory` and `state`, and logs one line naming how many it closed, only when it closed any.
Forge, tracker, ticket keys, worklog emission, secrets, paths and Claude logins are chosen from the machine profile and the registry at the moment of use, through hand-written selection such as `Forges`, `Trackers` and `ClaudeLogins`.
A Claude login's configuration directory is a machine fact, named under `claude.logins` in the machine profile, and a registry entry names only the login its project runs on ([claude-logins](claude-logins.md)).
`MachineProfile` reads `config/machine.yaml` at every call and parses it again only when its content differs from the content it parsed last, so an edit applies at the next call and a malformed profile is refused at every call.
No class or bean method uses `@Profile` or `@ConditionalOnProperty`, which `ArchitectureRulesTest` enforces.
The core module's Surefire runs the whole suite twice in `./mvnw verify`, under a `laptop` fixture profile with JIRA, the fleet ledger, the worked line and a second Claude login, and under a `desktop` one with none of them (`edi.test.machine`, `MachineFixture`); AT-37 checks the profiles differ only there.
The publish guard keeps personal material from ever leaving the machine ([publish-guard](publish-guard.md)).

## Why

One codebase and one set of executables serve both machines and anyone else, and nothing personal can leak through the code itself.
Parsing the profile took a YAML parse for every question asked of it, such as one for every event the fleet ledger follows, while the file rarely changes.
A code path that assumes one machine fails the other profile's run, which a single run could never show.
The store, the ledger, the memory and the state hold decisions, task texts and token digests, and a home other users cannot enter keeps all of it from them, whatever mode a file was written with.

## Alternatives rejected

- Spring profiles per machine, or two builds - fixed at native build time, doubling the build and inviting drift.
- Personal configuration in a private repository - ties the harness to one person and one employer.
- Keeping the parsed profile until the file's modification time changes - two writes within the clock's resolution would keep the older one.
- Parameterising every test by profile - every existing test would change.
- Closing every file under the home at each start - walks every project copy for nothing, since a home other users cannot enter already keeps them out.

## Consequences

- Good: decision records, tests and examples use invented names, and the same tests prove both machines in every build.
- Bad: setting up a machine needs the EDI home's configuration, which the repository cannot ship, and the selection code is hand-written.
- Bad: the permissions keep other users of the machine out, and nothing more: every process the captain runs, workers included, reads the home as before; the projects' copies under `worktrees` keep their modes, closed only by the home's own; and on a file system without POSIX permissions nothing is changed.
- Bad: the core's tests run twice, which adds about forty seconds to `./mvnw verify`.

## When to revisit

When native images can evaluate conditions at runtime, or a third machine profile appears.

## How we got here

- 15/10/2026: the specification made the harness person- and employer-agnostic and chose machine differences at runtime, because native images fix Spring's conditions at build time.
- 17/10/2026: slice S6 ran the suite under both fixture profiles.
- 23/10/2026: a test that followed the log for every finished ship spent part of its time parsing the unchanged machine profile, so `MachineProfile` parses again only when the content changed (EDI-45).
- 30/10/2026: the store and the state files were created readable by every user, so the home is kept for its owner alone, repaired at each start (EDI-129).
- 10/11/2026: a project can run on a second Claude login, whose directory is a machine fact in the machine profile, and the laptop fixture profile carries one (EDI-253).

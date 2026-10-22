# Architecture

EDI is one local core, one thin command and a Claude Code layer ([ADR-002](decisions/ADR-002-one-core-one-command-one-claude-layer.md)).
It is a rewrite of an older bash harness, keeping its operating model and none of its code ([ADR-001](decisions/ADR-001-a-rewrite-not-a-fork.md)).

## Parts

- `edi-core`: the Spring Boot application that owns EDI's state, listens on `127.0.0.1` only and serves the JSON API and the EDI console.
- `edi-cli`: the `edi` command every actor uses to talk to the core, compiled to a native executable.
- `edi-console`: the Angular single-page application the core serves.
- `claude/`: the Claude Code layer, which is the primary's anchor and settings, the worker's brief, settings and system prompt, and the skills (see [The Claude layer](claude-layer.md)).

The three Maven modules and the build baseline are set out in [ADR-008](decisions/ADR-008-three-maven-modules-and-build-baseline.md), and both the command and the installed core are native executables ([ADR-005](decisions/ADR-005-native-executables.md)).

## The core

The core keeps tasks, captain holds, decisions with their D numbers and an event log in its SQLite store and serves them over its authenticated API ([ADR-010](decisions/ADR-010-sqlite-store.md), [ADR-011](decisions/ADR-011-commands-queries-and-outbox.md), [ADR-020](decisions/ADR-020-loopback-only-and-authenticated.md)).
It is the only component that starts, observes and stops workers.
Its rules talk to the outside world through exactly six ports - the clock, the forge, the validation pipeline, the research-board host, the tracker and the worker runtime ([ADR-007](decisions/ADR-007-six-ports.md)) - and the architecture rules are executable tests ([ADR-009](decisions/ADR-009-architecture-rules-as-tests.md)).
One fleet snapshot sorts the open work for every report, page and digest ([ADR-012](decisions/ADR-012-fleet-snapshot-and-presenters.md)).

## The command

Every actor talks to the core through the `edi` command: the captain, the primary session, workers and Claude Code hooks ([ADR-021](decisions/ADR-021-command-only-agent-interface.md)).
Every command prints text, or the core's JSON with `--json`; a refusal is one sentence on standard error with exit code 1.
The command reads the core's port and token from the EDI home, and a worker's command uses the token EDI gave that worker ([ADR-049](decisions/ADR-049-callers-told-apart-by-token.md)).

## The core at run time

The core keeps its store, configuration and state in `EDI_HOME`, default `~/.edi`.
It listens on port 47318 unless `SERVER_PORT` says otherwise, records the port it bound in `$EDI_HOME/state/core.port`, and writes a fresh API token to `$EDI_HOME/state/core.token` at every start ([ADR-042](decisions/ADR-042-one-home-token.md)); the command reads both.
Only one core runs per home: it locks `$EDI_HOME/state/core.pid`, and `edi up` starts it detached, logging to `$EDI_HOME/state/logs/core.log` ([ADR-129](decisions/ADR-129-one-core-per-home-started-by-edi-up-and-stopped-by-edi-down.md)).
`edi down` stops the core only when no worker runs; `--force-stop-workers` stops them too, on the captain's word.
A core counts as stopped once it has let go of `state/core.pid`, so `edi down` and `edi update` return or start the new core only then ([ADR-167](decisions/ADR-167-a-core-has-stopped-once-it-lets-go-of-the-home.md)).
`edi up` and `edi update` find the EDI repository in `EDI_CHECKOUT`, default `~/EDI`, and the core executable next to `edi` or in `EDI_CORE`.
`edi update` pulls the repository, builds, tests and installs it, restarts the core while workers keep running, and has the primary re-read its anchor ([ADR-130](decisions/ADR-130-edi-update-rebuilds-installs-and-restarts-the-core.md)).
`ops/edi-core.service` is an optional systemd user unit for a core that restarts on failure.

## Where EDI stands

EDI is built in slices.
The repository is at slice S10, the import of the old harness and the take-over of its open work, on top of the Claude layer, memory and presence of slice S8, the console and boards of slice S7, the tracker and time of slice S6, the delivery of slice S5, the supervision and recovery of slice S4, the workers of slice S3, the reports of slice S2, the domain and store of slice S1 and the foundations of slice S0.
What was cut from the scope, and why, is in [ADR-035](decisions/ADR-035-scope-cuts.md).

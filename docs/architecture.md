# Architecture

EDI is one local core, one thin command and a Claude Code layer ([core-and-command](decisions/core-and-command.md)).
It is a rewrite of an older bash harness, keeping its operating model and none of its code ([rewrite-and-scope](decisions/rewrite-and-scope.md)).

## Parts

- `edi-core`: the Spring Boot application that owns EDI's state, listens on `127.0.0.1` only and serves the JSON API and the EDI console.
- `edi-cli`: the `edi` command every actor uses to talk to the core, compiled to a native executable.
- `edi-console`: the Angular single-page application the core serves.
- `claude/`: the Claude Code layer, which is the primary's anchor and settings, the worker's brief, settings and system prompt, and the skills (see [The Claude layer](claude-layer.md)).

The three Maven modules, the build baseline and the native executables of the command and the installed core are set out in [java-toolchain-and-build](decisions/java-toolchain-and-build.md).

## The core

The core keeps tasks, captain holds, decisions with their D numbers and an event log in its SQLite store and serves them over its authenticated API ([store](decisions/store.md), [events-and-followers](decisions/events-and-followers.md), [api-access](decisions/api-access.md)).
It is the only component that starts, observes and stops workers.
Its rules talk to the outside world through exactly six ports - the clock, the forge, the validation pipeline, the research-board host, the tracker and the worker runtime ([ports-and-architecture-rules](decisions/ports-and-architecture-rules.md)) - and the architecture rules are executable tests ([ports-and-architecture-rules](decisions/ports-and-architecture-rules.md)).
One fleet snapshot sorts the open work for every report, page and digest ([fleet-snapshot](decisions/fleet-snapshot.md)).

## The command

Every actor talks to the core through the `edi` command: the captain, the primary session, workers and Claude Code hooks ([core-and-command](decisions/core-and-command.md)).
Every command prints text, or the core's JSON with `--json`; a refusal is one sentence on standard error with exit code 1.
The command reads the core's port and token from the EDI home, and a worker's command uses the token EDI gave that worker ([api-access](decisions/api-access.md)).

## The core at run time

The core keeps its store, configuration and state in `EDI_HOME`, default `~/.edi`.
It listens on port 5490 unless `SERVER_PORT` says otherwise, records the port it bound in `$EDI_HOME/state/core.port`, and writes a fresh API token to `$EDI_HOME/state/core.token` at every start ([api-access](decisions/api-access.md)); the command reads both.
When another program holds the port, the core stops at start with a message in its log that names the port and `SERVER_PORT`.
Only one core runs per home: it locks `$EDI_HOME/state/core.pid`, and `edi up` starts it detached, logging to `$EDI_HOME/state/logs/core.log` ([core-lifecycle](decisions/core-lifecycle.md)).
`edi down` stops the core only when no worker runs; `--force-stop-workers` stops them too, on the captain's word.
A core counts as stopped once it has let go of `state/core.pid`, so `edi down` and `edi update` return or start the new core only then ([core-lifecycle](decisions/core-lifecycle.md)).
`edi up` and `edi update` find the EDI repository in `EDI_CHECKOUT`, default `~/EDI`, and the core executable next to `edi` or in `EDI_CORE`.
`edi update` pulls the repository, builds, tests and installs it, restarts the core while workers keep running, and has the primary re-read its anchor ([core-lifecycle](decisions/core-lifecycle.md)).
`ops/edi-core.service` is an optional systemd user unit for a core that restarts on failure.

## Where EDI stands

EDI is built in slices.
The repository is at slice S10, the import of the old harness and the take-over of its open work, on top of the Claude layer, memory and presence of slice S8, the console and boards of slice S7, the tracker and time of slice S6, the delivery of slice S5, the supervision and recovery of slice S4, the workers of slice S3, the reports of slice S2, the domain and store of slice S1 and the foundations of slice S0.
What was cut from the scope, and why, is in [rewrite-and-scope](decisions/rewrite-and-scope.md).

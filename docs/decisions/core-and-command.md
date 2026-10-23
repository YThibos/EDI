# One local core owns all state, and one thin command is every actor's only interface

**Kind:** Architecture

## Context

Supervision is made of timers, long polls, child processes and transactional state.
In the old harness each of these was a script with its own watcher, wake queue and lock files, and hooks paid a script's start-up on every turn.
Everything runs on the developer's own machine, with no hosted service.
Spring Boot with Angular is the stack the captain reviews best for personal tools, and it already runs on both machines.

## Decision

EDI has three parts.
`edi-core` is one Spring Boot application, the only owner of state and the only component that starts, observes and stops workers; it serves a JSON API and the console on loopback ([api-access](api-access.md)).
`edi` is a thin picocli command without Spring, used by every actor: the captain, the primary session, the workers and Claude Code's hooks.
The Claude Code layer, kept in `claude/`, is the primary's anchor, the skills, the hook definitions that call `edi hook <event>` and the worker brief template ([primary-anchor-and-skills](primary-anchor-and-skills.md)).

The command is a pipe.
It sends `Accept: text/plain`, or `application/json` with `--json`, prints the body to standard output with exit code 0, or a refusal to standard error with exit code 1, and parses no JSON; requests that carry data send JSON written by `JsonBody`, and large inputs travel on standard input or `--file`, never as arguments.
In the core, controllers return read models, and content negotiation picks the representation: Jackson writes JSON with domain values as plain strings (`JsonConfiguration`), and `PlainTextConverter` writes text through the `TextPresenter` registered for the read model.
`ApiRefusals` writes every refusal as one plain sentence: 404 for an unknown task or decision, 409 for a refused change and 400 for an invalid request.
Each command belongs to an authority class (read, worker, primary, captain, captain-word, hook or core-only) that the core checks against its caller.
Version 1 offers no other agent interface; an MCP server could later sit behind the same use cases.

## Why

One long-running process owns timers, children and transactions, which removes the watcher, wake-queue and lock machinery entirely.
One command for every actor keeps one interface and one permission rule, `Bash(edi *)`, and everything an agent can do shows in `edi --help`.
Presentation lives in one place with its tests, so the native command stays small and starts in milliseconds.

## Alternatives rejected

- Scripts around a shared state directory - recreates the old harness's locking and liveness problems.
- An MCP server in version 1 - a second interface to secure and keep in step.
- Rendering in the command - it would need a JSON parser and every presenter, and grow with every report.

## Consequences

- Good: one process to start, health-check and restart ([core-lifecycle](core-lifecycle.md)); a new command is a request and a presenter.
- Bad: the core is a long-running service that must be kept alive, and agents read text or JSON instead of calling typed tools.
- Bad: a reader must know that a controller's return value becomes text through `PlainTextConverter`.

## When to revisit

When EDI must serve more than one machine from one process, when a typed tool interface would remove a real failure class, or when a caller needs a representation other than text or JSON.

## How we got here

- 15/10/2026: the specification settled the three parts and the command-only interface; slice S1 made the core render text and JSON and the command a pipe.

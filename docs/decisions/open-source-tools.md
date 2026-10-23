# EDI uses open-source, standard tools only, and nothing in its runtime runs on Node

**Kind:** Architecture

## Context

EDI runs unattended on the developer's own machines, and any vendor relationship is a way for a part of it to stop working.
The Claude Agent SDK runs the same agent loop as Claude Code by starting the Claude Code executable and talking to it over a JSON stream; it is a TypeScript or Python library.

## Decision

EDI is built with GraalVM Community Edition, Spring Boot, picocli, SQLite with the Xerial driver, Flyway Community, Jackson, Angular, JUnit, AssertJ, ArchUnit, Maven, Renovate, OWASP Dependency-Check, npm audit, SonarQube Community Build, gitleaks and mise.
The external programs EDI drives stay as they are: git, tmux, the GitLab and GitHub command-line tools, the validation pipeline, the research-board tool, and Claude Code itself, the only proprietary piece.
No hosted third-party service is added.
Node is used only at build time, by the frontend Maven plugin, to compile the console; if structured access to a session's messages is ever needed, Java reads Claude Code's own JSON stream behind the `WorkerRuntime` port ([worker-sessions](worker-sessions.md)).

## Why

Every dependency can be inspected, pinned and kept running without an account or a licence that could switch it off.
The Agent SDK would add in-process callbacks but no speed or token savings, since the model, prompts and tool calls are identical, while it would cost a second runtime on both machines, a bridge process and the interactive worker pane.
Java's foreign function and memory API calls native C libraries and cannot host a JavaScript or Python library, and GraalJS lacks the Node runtime the SDK needs.

## Alternatives rejected

- Hosted scanners, dependency bots or routing services - third-party services.
- The Agent SDK with a Node sidecar - a second runtime in the critical path for no efficiency gain.

## Consequences

- Good: one runtime to install, pin and keep current.
- Bad: EDI self-hosts what a hosted service would run for it, such as Renovate and SonarQube ([dependency-upkeep-and-scanners](dependency-upkeep-and-scanners.md)), and builds its own reader if it ever wants headless workers.

## When to revisit

When a needed capability has no open-source tool, or Claude Code stops offering its JSON stream.

## How we got here

- 15/10/2026: the specification set the tool list and kept the rule that tools EDI maintains run on Java and Angular, never Node, after weighing the Agent SDK on the review board.

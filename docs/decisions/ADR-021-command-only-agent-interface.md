# ADR-021: The `edi` command is the only agent interface in v1

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** Agents, hooks and the captain all need to reach the core, and Claude Code's permission rules are simplest when every call is one command.

**Decision:** Version 1 offers the `edi` command only, with compact text output, `--json` for machines, large inputs on standard input or `--file`, and one plain sentence on standard error with a non-zero exit when something is missing.
Each command belongs to an authority class (read, worker, primary, captain, captain-word, hook, core-only) that the core checks against the caller's identity.
An MCP server can come later behind the same use cases.

**Motivation:** One interface for agents, hooks and the captain, and one permission rule (`Bash(edi *)`).

**Alternatives considered:**
- An MCP server in v1 - a second interface to secure and keep in step.

**Consequences:**
- Good: everything an agent can do is visible in `edi --help`.
- Bad: agents parse text or JSON output instead of calling typed tools.

**Revisit when:** A typed tool interface would remove a real failure class.

**Sources:** EDI rewrite specification, sections 9.1 and 9.2 (decision E11); `edi-cli/`.

# Decision records

This directory records EDI's architecture and design decisions with the motivation behind each of them.
Code carries no explanatory comments; a record names the classes, files and settings it governs, so a search for any of those names finds the why.

## Rules

1. Records are append-only: a new record takes the next number, and numbers are never reused or renumbered.
2. A changed decision gets a new record that supersedes the old one; the old record's status becomes `Superseded by ADR-nnn` and nothing else in it changes.
3. One decision per record: two decisions that could be reversed independently get two records.
4. Only mistakes are corrected in place, such as a typo or a broken link.
5. A record states its motivation and the alternatives it rejected; where the source gives no motivation, it says so instead of inventing one.
6. Records describe the harness, never a particular person, employer or machine; those live in the EDI home and the gitignored `personal/` directory (ADR-017).

The kind of a record is `Architecture` when undoing it would reshape boundaries, technology or an integration, and `Design` when it changes how one component behaves.

## Template

```markdown
# ADR-nnn: Short title of the decision

**Date:** DD/MM/YYYY

**Status:** Proposed | Accepted | Superseded by ADR-nnn

**Kind:** Architecture | Design

**Context and constraints:** What was true at the time.

**Decision:** What was decided.

**Motivation:** Why this option was chosen.

**Alternatives considered:**
- Alternative - why it was not chosen.

**Consequences:**
- Good: what this makes easier or safer.
- Bad: what this makes harder, and what it costs.

**Revisit when:** The change in constraints that should trigger reconsidering this decision.

**Sources:** Where the decision was made and where it is implemented.
```

## Index

| Record | Decision | Kind |
|---|---|---|
| [ADR-001](ADR-001-a-rewrite-not-a-fork.md) | EDI is a rewrite, not a fork | Architecture |
| [ADR-002](ADR-002-one-core-one-command-one-claude-layer.md) | One local core, one thin command and a Claude Code layer | Architecture |
| [ADR-003](ADR-003-java-baseline.md) | Java 25 until GraalVM Community supports a newer JDK | Architecture |
| [ADR-004](ADR-004-preview-features.md) | Preview features are on, and each adopted one gets its own record | Design |
| [ADR-005](ADR-005-native-executables.md) | Native executables for the command and the installed core | Architecture |
| [ADR-006](ADR-006-machine-differences-at-runtime.md) | Machine differences are chosen at runtime | Architecture |
| [ADR-007](ADR-007-six-ports.md) | Ports only where they pay: exactly six | Architecture |
| [ADR-008](ADR-008-three-maven-modules-and-build-baseline.md) | Three Maven modules and the build baseline | Architecture |
| [ADR-009](ADR-009-architecture-rules-as-tests.md) | Architecture rules are executable tests | Design |
| [ADR-010](ADR-010-sqlite-store.md) | SQLite with Flyway is the store, written only by the core | Architecture |
| [ADR-011](ADR-011-commands-queries-and-outbox.md) | Commands and queries on one database, with an in-process outbox | Architecture |
| [ADR-012](ADR-012-fleet-snapshot-and-presenters.md) | One fleet snapshot query, many presenters | Design |
| [ADR-013](ADR-013-interactive-workers-in-tmux.md) | Interactive Claude Code workers in tmux, observed through hooks | Architecture |
| [ADR-014](ADR-014-no-node-in-the-runtime.md) | No Node in EDI's runtime | Architecture |
| [ADR-015](ADR-015-task-addressed-worktrees.md) | EDI-owned, task-addressed worktrees | Architecture |
| [ADR-016](ADR-016-edi-native-work-keys.md) | EDI-native work keys, with `EDI-1` for upkeep | Design |
| [ADR-017](ADR-017-person-and-employer-agnostic.md) | The harness is person- and employer-agnostic | Architecture |
| [ADR-018](ADR-018-console-spa-served-by-the-core.md) | The EDI console is an Angular SPA served by the core | Architecture |
| [ADR-019](ADR-019-the-approved-skin.md) | The approved dark skin | Design |
| [ADR-020](ADR-020-loopback-only-and-authenticated.md) | The core listens on loopback only and authenticates every caller | Architecture |
| [ADR-021](ADR-021-command-only-agent-interface.md) | The `edi` command is the only agent interface in v1 | Architecture |
| [ADR-022](ADR-022-drafts-the-captain-merges.md) | Work is delivered as a draft the captain merges | Architecture |
| [ADR-023](ADR-023-merges-are-observed.md) | Merges are observed, never performed | Design |
| [ADR-024](ADR-024-sitrep-only.md) | `/sitrep` is the only overview command | Design |
| [ADR-025](ADR-025-dispatch-categories.md) | Dispatch by eight written categories and a project override | Design |
| [ADR-026](ADR-026-research-first-switch.md) | Research before implementation is a piloted switch | Design |
| [ADR-027](ADR-027-renovate-upkeep.md) | Dependency upkeep through self-hosted Renovate | Architecture |
| [ADR-028](ADR-028-security-and-quality-scanners.md) | Security and quality scanners in CI | Architecture |
| [ADR-029](ADR-029-open-source-standard-tools.md) | Open-source, standard tools only | Architecture |
| [ADR-030](ADR-030-presence-modes.md) | Presence modes with automatic quiet and demo mode | Design |
| [ADR-031](ADR-031-address-word-and-voice.md) | A configurable address word and a ship's-intelligence voice | Design |
| [ADR-032](ADR-032-publish-guard.md) | The publish guard checks every outgoing commit | Architecture |
| [ADR-033](ADR-033-no-explanatory-comments.md) | No explanatory comments in code | Design |
| [ADR-034](ADR-034-invariant-catalogue.md) | The invariant catalogue reports pending acceptance tests | Design |
| [ADR-035](ADR-035-scope-cuts.md) | What EDI deliberately does not build | Architecture |
| [ADR-036](ADR-036-tomcat-security-override.md) | Spring Boot's managed Tomcat is overridden for a security fix | Design |

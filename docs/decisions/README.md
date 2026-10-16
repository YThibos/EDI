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
| [ADR-037](ADR-037-current-state-and-event-history.md) | The store keeps current state in tables and history in the event log | Design |
| [ADR-038](ADR-038-immediate-explicit-store-transactions.md) | Store transactions are explicit and take the write lock at once | Design |
| [ADR-039](ADR-039-a-decision-holds-its-task.md) | A decision holds its task, and its number never changes | Design |
| [ADR-040](ADR-040-task-intake.md) | How a task enters the queue | Design |
| [ADR-041](ADR-041-project-registry-is-configuration.md) | The project registry is configuration, read on every use | Design |
| [ADR-042](ADR-042-one-home-token.md) | One home token authenticates the API until callers are told apart (superseded by ADR-049) | Design |
| [ADR-043](ADR-043-the-core-renders-text.md) | The core renders text and JSON; the command is a pipe | Architecture |
| [ADR-044](ADR-044-migration-reads-the-old-grammar.md) | The migration reads the old backlog with its own grammar and maps it through the domain | Design |
| [ADR-045](ADR-045-native-round-trip-in-the-smoke-test.md) | The native round trip is measured by the native smoke test | Design |
| [ADR-046](ADR-046-where-the-fleet-snapshot-puts-open-work.md) | Where the fleet snapshot puts open work | Design |
| [ADR-047](ADR-047-the-captain-report-matches-the-old-renderer.md) | The captain report matches the old renderer byte for byte | Design |
| [ADR-048](ADR-048-the-command-measures-its-pane.md) | The command measures its pane and the core sizes the report | Design |
| [ADR-049](ADR-049-callers-told-apart-by-token.md) | Each worker holds its own token, and every call is checked against its caller | Design |
| [ADR-050](ADR-050-dispatch-rules-read-and-routed.md) | How the dispatch rules are read, checked and routed | Design |
| [ADR-051](ADR-051-branch-names-from-the-work-key.md) | A branch name is built from the work's kind and its key, and nothing else | Design |
| [ADR-052](ADR-052-isolated-copies.md) | EDI makes each isolated copy from a fresh origin base and checks it before every launch | Design |
| [ADR-053](ADR-053-hooks-through-worktree-configuration.md) | The commit-msg and pre-push hooks are installed through each worktree's own configuration | Design |
| [ADR-054](ADR-054-the-claude-launcher.md) | How EDI launches a Claude Code worker in tmux | Architecture |
| [ADR-055](ADR-055-the-worker-brief.md) | The worker brief is one template filled in from the task, its dispatch and the registry | Design |
| [ADR-056](ADR-056-steering-with-proof-of-delivery.md) | A steer waits in the worker's inbox until a hook proves it was received | Design |
| [ADR-057](ADR-057-the-pane-probe.md) | The pane probe reads Claude Code's screen in one place, from captured fixtures | Design |
| [ADR-058](ADR-058-the-pre-tool-guard.md) | The pre-tool guard reads each shell command in the domain and refuses before it runs | Design |
| [ADR-059](ADR-059-the-mechanical-task-guard.md) | A mechanical task names its exact change, and its done is checked before it counts | Design |
| [ADR-060](ADR-060-project-capacity-defers-dispatches.md) | A dispatch beyond a project's capacity waits as deferred | Design |
| [ADR-061](ADR-061-the-supervision-round.md) | One supervision round every 30 seconds keeps the time-based rules of slice S3 | Design |
| [ADR-062](ADR-062-ref-only-delivery-into-the-captains-checkout.md) | A ready local-branch ship is fetched into the captain's checkout by its ref only | Design |
| [ADR-063](ADR-063-dispatches-workers-and-inboxes-in-the-store.md) | The store keeps each task's dispatch, its workers and its inbox | Design |
| [ADR-064](ADR-064-the-stand-in-for-claude-code.md) | A stand-in for Claude Code runs a worker end to end in the native smoke test | Design |

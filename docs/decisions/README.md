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
| [ADR-061](ADR-061-the-supervision-round.md) | One supervision round every 30 seconds keeps the time-based rules of slice S3 (superseded by ADR-077) | Design |
| [ADR-062](ADR-062-ref-only-delivery-into-the-captains-checkout.md) | A ready local-branch ship is fetched into the captain's checkout by its ref only (superseded by ADR-084) | Design |
| [ADR-063](ADR-063-dispatches-workers-and-inboxes-in-the-store.md) | The store keeps each task's dispatch, its workers and its inbox | Design |
| [ADR-064](ADR-064-the-stand-in-for-claude-code.md) | A stand-in for Claude Code runs a worker end to end in the native smoke test | Design |
| [ADR-065](ADR-065-tests-stop-every-process-they-start.md) | Tests stop every process they start, and a leaked one fails the build | Design |
| [ADR-066](ADR-066-hooks-for-the-primary-and-workers.md) | The primary and every worker answer all seven Claude Code hooks through edi hook | Design |
| [ADR-067](ADR-067-the-captains-session-is-primary.md) | The session the captain typed in last is the primary, and any other EDI session stands by | Design |
| [ADR-068](ADR-068-the-primarys-turn-end-rules.md) | The primary's stop waits for the captain report and for a live edi wait | Design |
| [ADR-069](ADR-069-wakes-are-durable-rows.md) | Wakes are queued with the event that causes them, and edi wait long-polls for them | Design |
| [ADR-070](ADR-070-worker-health-from-structured-state.md) | Worker health is read from hooks, the tmux server and the boot, and each condition is noticed once (superseded by ADR-091) | Design |
| [ADR-071](ADR-071-the-boot-id-marks-a-machine-restart.md) | Every worker launched in an earlier boot is dead | Design |
| [ADR-072](ADR-072-usage-limits-from-the-stop-failure-and-the-transcript.md) | A usage limit is confirmed in the transcript, and its reset is met with one typed line | Design |
| [ADR-073](ADR-073-relaunch-resumes-the-recorded-session.md) | A relaunch resumes the recorded session in the task's own copy, and a stop signals only what EDI recorded | Design |
| [ADR-074](ADR-074-the-outage-hold-list.md) | More than one dead worker go on a numbered outage hold list, answered one number at a time | Design |
| [ADR-075](ADR-075-recovery-token-accounting.md) | Every recovery relaunch records the tokens it spent | Design |
| [ADR-076](ADR-076-the-session-start-digest.md) | A session starts from a digest rebuilt from the store | Design |
| [ADR-077](ADR-077-the-supervision-round-judges-the-fleet.md) | The supervision round also judges every worker, meets usage-limit resets and notices due holds | Design |
| [ADR-078](ADR-078-the-store-keeps-supervision-state.md) | The store keeps primary sessions, wakes, notices, the outage list and recovery costs | Design |
| [ADR-079](ADR-079-the-forge-adapters-run-an-allow-list.md) | The forge adapters run only an allow-list of gh and glab commands | Architecture |
| [ADR-080](ADR-080-forge-calls-run-under-the-bound-account.md) | Every forge call for a project runs under the account its registry entry binds | Design |
| [ADR-081](ADR-081-the-pipeline-adapter.md) | The pipeline adapter reads a branch's run from `axi status` and its spend from the pipeline's state, read-only | Design |
| [ADR-082](ADR-082-edi-writes-the-pipeline-intent.md) | EDI writes the pipeline intent beside the brief, with the craft rules verbatim | Design |
| [ADR-083](ADR-083-a-ships-done-is-accepted-once-its-claims-hold.md) | A ship's done is accepted only once its claims hold, and a refused done is recorded | Design |
| [ADR-084](ADR-084-accepted-work-reaches-the-captain-by-its-delivery-mode.md) | Accepted work reaches the captain by its delivery mode, and local-apply is asked for per task | Design |
| [ADR-085](ADR-085-edi-deliver-opens-the-draft-the-pipeline-could-not.md) | `edi deliver` opens the draft the pipeline could not, behind the same guards | Design |
| [ADR-086](ADR-086-publication-targets-are-the-registered-forge.md) | A push from an isolated copy goes only to the repository its project registers | Design |
| [ADR-087](ADR-087-merges-are-followed-from-the-forge.md) | A merge is followed from the forge's word, whether EDI sees it or the captain says so | Design |
| [ADR-088](ADR-088-follow-up-work-waits-for-the-refreshed-clone.md) | Follow-up work on a project waits until its clone holds the commit a merge landed | Design |
| [ADR-089](ADR-089-the-landed-work-test.md) | Work is landed when nothing is uncommitted and every commit is in the base by ancestry, patch or content | Design |
| [ADR-090](ADR-090-cleanup-retires-a-landed-task.md) | Cleanup retires a landed task's copy, branch and workers, and records what the task cost | Design |
| [ADR-091](ADR-091-a-running-pipeline-keeps-an-idle-worker-healthy.md) | Worker health is read from hooks, the tmux server, the boot and the pipeline, and each condition is noticed once | Design |
| [ADR-092](ADR-092-the-store-keeps-deliveries.md) | The store keeps deliveries, pipeline runs, clone refreshes and task costs | Design |

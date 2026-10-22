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
| [ADR-032](ADR-032-publish-guard.md) | The publish guard checks every outgoing commit (superseded by ADR-144) | Architecture |
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
| [ADR-093](ADR-093-the-tracker-port-and-its-three-adapters.md) | The tracker port has three adapters, chosen per project and reachable only where the machine names the tracker | Architecture |
| [ADR-094](ADR-094-work-keys-are-settled-at-intake.md) | A task's work key is settled at intake, and an external tracker's ticket is chosen by the captain | Design |
| [ADR-095](ADR-095-the-answer-books-or-creates-the-ticket.md) | The captain's answer books the work on a ticket or creates it, and a refused answer goes back to him | Design |
| [ADR-096](ADR-096-followers-read-the-event-log-from-their-own-cursor.md) | Followers read the event log from their own cursor after each commit and every 30 seconds | Design |
| [ADR-097](ADR-097-ticket-updates-follow-the-work.md) | Ticket updates follow the work: the sprint at the first dispatch, the draft's link, and the merged status after the merge | Design |
| [ADR-098](ADR-098-the-fleet-ledger-keeps-the-old-harnesss-contract.md) | The fleet ledger keeps the old harness's record contract for the time-booking tool | Design |
| [ADR-099](ADR-099-research-before-implementation-queues-a-plan.md) | Research before implementation queues a research scout and runs the ship as implementation with its plan | Design |
| [ADR-100](ADR-100-the-research-first-pilot.md) | The research-first pilot compares planned ships with unplanned ones and asks the captain after ten | Design |
| [ADR-101](ADR-101-the-worked-line-while-the-profile-requires-it.md) | The brief asks for the worked line only while the machine profile requires it | Design |
| [ADR-102](ADR-102-the-suite-runs-under-both-machine-profiles.md) | The full suite runs under a laptop and a desktop fixture profile | Design |
| [ADR-103](ADR-103-tracker-secrets-are-read-at-call-time-and-hidden.md) | The tracker's token is read at each call and hidden from every message | Design |
| [ADR-104](ADR-104-the-store-keeps-keys-proposals-cursors-and-pilot-records.md) | The store keeps work key sequences, ticket proposals, follower cursors, unplanned ships and reviews | Design |
| [ADR-105](ADR-105-the-console-signs-the-captain-in-with-a-one-time-link.md) | The console signs the captain in with a one-time link and keeps a cookie session | Design |
| [ADR-106](ADR-106-edi-opens-the-console-in-the-captains-browser.md) | EDI opens the console in the captain's browser through the Windows start command | Design |
| [ADR-107](ADR-107-the-sitrep-page-reads-one-document-from-the-fleet-snapshot.md) | The sitrep page reads one document, edi-sitrep.v1, presented from the fleet snapshot | Design |
| [ADR-108](ADR-108-a-console-answer-is-the-captains-word-and-reconcile-asks-for-a-recheck.md) | A console answer is the captain's word through the chat answer's use case, and Reconcile asks for a re-check | Design |
| [ADR-109](ADR-109-the-console-stream-signals-each-commit.md) | The console stream signals each commit, and the page reads the sitrep again | Design |
| [ADR-110](ADR-110-the-console-ports-the-approved-skin-with-its-fonts-bundled.md) | The console ports the approved skin into Angular components and bundles its fonts (superseded by ADR-152) | Design |
| [ADR-111](ADR-111-research-boards-are-armed-through-edi-and-listened-to-by-the-core.md) | Research boards are armed through EDI and listened to by the core | Architecture |
| [ADR-112](ADR-112-the-lavish-result-reader-follows-toon.md) | The Lavish result reader follows TOON and reads both prompt forms | Design |
| [ADR-113](ADR-113-a-board-round-reaches-its-worker-and-its-bound-decisions.md) | A board round reaches its worker's inbox and the decisions the board is bound to | Design |
| [ADR-114](ADR-114-the-store-keeps-console-access-boards-and-board-answers.md) | The store keeps console sign-ins and sessions, boards and board answers | Design |
| [ADR-115](ADR-115-the-captains-word-is-typed-in-the-current-exchange.md) | The captain's word is what the captain typed in the current exchange, and the session the captain typed in last is the primary | Design |
| [ADR-116](ADR-116-a-promoted-scout-queues-a-new-ship.md) | A scout is promoted by queueing a new ship on the captain's word | Design |
| [ADR-117](ADR-117-dropping-and-discarding-work-on-the-captains-word.md) | A task is dropped, and unlanded work discarded, only on the captain's word | Design |
| [ADR-118](ADR-118-the-outage-hold-list-also-drops-a-task-on-the-captains-word.md) | The outage hold list is answered one number at a time and can also drop a task on the captain's word | Design |
| [ADR-119](ADR-119-the-guard-also-keeps-secrets-from-a-shell.md) | The pre-tool guard keeps secrets from a shell and lets the primary run edi while the core is down | Design |
| [ADR-120](ADR-120-startup-memory-is-measured-against-its-budget.md) | Startup memory is measured against its budget, curated from a stow list, and checked daily | Design |
| [ADR-121](ADR-121-the-primarys-system-prompt-is-composed-at-launch.md) | The primary's system prompt is composed at launch from the anchor, its personal addendum and the startup memory | Design |
| [ADR-122](ADR-122-presence-is-held-by-the-core-and-followed-every-round.md) | Presence is held by the core, set by the captain and followed every supervision round | Design |
| [ADR-123](ADR-123-the-primarys-stop-waits-for-the-report-only-while-the-captain-is-present.md) | The primary's stop waits for the captain report while the captain is present, and for a live edi wait always | Design |
| [ADR-124](ADR-124-wakes-are-gathered-into-one-batch-while-the-chat-is-quiet.md) | Wakes are queued with their event, edi wait long-polls for them, and quiet gathers them into batches | Design |
| [ADR-125](ADR-125-notifications-for-the-captain-come-from-an-event-follower.md) | While the chat is quiet, each event that concerns the captain raises one notification, from an event follower | Design |
| [ADR-126](ADR-126-the-windows-side-runs-through-powershell.md) | Notifications and the calendar come from the Windows side of WSL through powershell.exe (superseded by ADR-162) | Architecture |
| [ADR-127](ADR-127-the-captain-returns-to-a-brief-of-what-happened.md) | The captain's first message after quiet or away arrives with a brief of what happened meanwhile | Design |
| [ADR-128](ADR-128-the-voice-lint-keeps-internal-labels-from-the-captain.md) | A voice lint keeps internal labels out of what the captain reads | Design |
| [ADR-129](ADR-129-one-core-per-home-started-by-edi-up-and-stopped-by-edi-down.md) | One core per home, started by edi up and stopped by edi down | Design |
| [ADR-130](ADR-130-edi-update-rebuilds-installs-and-restarts-the-core.md) | edi update pulls, builds and tests natively, installs, and restarts the core while workers keep running | Design |
| [ADR-131](ADR-131-the-primarys-anchor-and-skills.md) | The primary's anchor holds only what must always be in mind, and eleven skills hold the procedures (superseded by ADR-135) | Design |
| [ADR-132](ADR-132-the-session-start-digest-adds-presence-memory-and-setup.md) | A session starts from a digest with the address word, presence, memory, setup and the weekly upkeep | Design |
| [ADR-133](ADR-133-the-console-shows-the-presence.md) | The console shows the presence, with Present and Demo buttons | Design |
| [ADR-134](ADR-134-the-core-writes-the-primarys-status-line.md) | The core writes the primary's status line, and edi statusline pipes it to Claude Code | Design |
| [ADR-135](ADR-135-the-primarys-settings-hold-the-hooks-and-the-status-line.md) | The primary's anchor holds only what must always be in mind, eleven skills hold the procedures, and its settings hold the hooks and the status line | Design |
| [ADR-136](ADR-136-the-import-writes-what-its-dry-run-plans.md) | The import writes what its dry run plans, beside EDI's own work, and overwrites a home in use only when told | Design |
| [ADR-137](ADR-137-the-backlog-import-sets-aside-the-old-harnesss-own-work.md) | The backlog import sets aside the old harness's own work, reads EDI keys from every line, and names each wait in plain words | Design |
| [ADR-138](ADR-138-the-old-memory-is-routed-by-section-and-edited-on-the-way.md) | The old home's memory is routed by section and edited on the way, as tables 18.1 and 18.2 name | Design |
| [ADR-139](ADR-139-the-old-configuration-becomes-edis-without-guessing.md) | The old home's configuration becomes EDI's by adding what EDI lacks, and every gap is listed rather than guessed | Design |
| [ADR-140](ADR-140-the-old-data-is-archived-read-only-and-open-tasks-link-their-folders.md) | The old home's data is archived read-only, and each open task links its old folder | Design |
| [ADR-141](ADR-141-adopting-an-open-task-registers-its-copy-and-moves-it-when-free.md) | Adopting an open task registers its copy in place and moves it under EDI's worktrees once nothing works there | Design |
| [ADR-142](ADR-142-the-install-builds-with-the-toolchain-the-checkout-pins.md) | The install builds with the toolchain the checkout pins, and refuses when it is not installed | Design |
| [ADR-144](ADR-144-a-project-may-publish-its-own-forge-and-tracker-names.md) | The publish guard checks every outgoing commit, and a project may publish its own forge and tracker names | Architecture |
| [ADR-145](ADR-145-at-38-starts-the-workers-pushes-before-edi-delivers.md) | AT-38 starts the worker's pushes before EDI delivers, and the hook's guard runs on a quick-starting JVM | Design |
| [ADR-146](ADR-146-tests-run-git-without-the-developers-git-configuration.md) | Tests run git without the developer's own git configuration | Design |
| [ADR-147](ADR-147-a-fixture-repositorys-git-runs-no-background-maintenance.md) | A fixture repository's git runs no background maintenance | Design |
| [ADR-148](ADR-148-the-old-home-is-compared-entry-by-entry.md) | AT-41 and AT-47 compare every entry of the old home but its access times, and name each one that changed | Design |
| [ADR-149](ADR-149-test-jvms-compile-with-c1-only-and-test-homes-start-from-a-migrated-store.md) | The test JVMs compile with C1 only, and a new test home starts from a store migrated once | Design |
| [ADR-150](ADR-150-edi-opens-the-console-through-windows-or-the-linux-desktop.md) | EDI opens the console in the captain's browser through the Windows start command, or through xdg-open on a Linux desktop | Design |
| [ADR-151](ADR-151-a-new-primary-session-opens-the-sitrep-page.md) | A new primary session opens the console's sitrep page in the captain's browser | Design |
| [ADR-152](ADR-152-the-sitrep-page-fits-one-laptop-screen.md) | The sitrep page fits one laptop screen, with three columns above what landed | Design |
| [ADR-153](ADR-153-the-browser-fetches-the-console-again-on-every-load.md) | The browser fetches the console again on every load | Design |
| [ADR-154](ADR-154-the-native-tests-run-without-the-callers-edi-variables.md) | The native tests start the core without the caller's EDI variables | Design |
| [ADR-158](ADR-158-everything-that-waits-on-the-captain-is-in-the-decision-zone.md) | Everything that waits on the captain is in the decision zone, whatever holds its task | Design |
| [ADR-159](ADR-159-a-board-round-names-its-link-and-its-questions.md) | A board round names its link and how many questions it puts before the captain | Design |
| [ADR-160](ADR-160-a-closed-task-blocks-nobody.md) | A closed task blocks nobody | Design |
| [ADR-161](ADR-161-a-workers-question-is-edis-and-its-acknowledged-steer-settles-it.md) | A worker's question is EDI's, and the acknowledged steer that resolves it settles it | Design |
| [ADR-162](ADR-162-the-calendar-is-read-through-wsls-own-interop.md) | Notifications and the calendar come from the Windows side through powershell.exe, and the calendar is read through WSL's own interop | Architecture |
| [ADR-163](ADR-163-the-calendar-is-read-every-two-minutes-and-an-unreadable-one-is-told-once.md) | The calendar is read every two minutes, presence switches from the last reading, and the captain hears once when it cannot be read | Design |

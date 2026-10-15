# ADR-013: Interactive Claude Code workers in tmux, observed through hooks

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** Workers run long Maven builds that outlive any single call, and the captain wants to watch a worker and step into it.
The old harness judged workers by scraping terminal panes and by shell liveness, which produced "alive" reports for workers that a machine restart had killed.
It also shared the captain's default tmux server, so a lost server took his own sessions and every worker with it.

**Decision:** Each worker is an interactive Claude Code session in tmux on EDI's own server (`tmux -L edi`), launched with an EDI-chosen session id, an explicit model and effort, a name, and a settings file that defines its hooks with attribution off.
EDI learns a worker's state from Claude Code hooks (`SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `Notification`, `Stop`, `StopFailure`, `SessionEnd`), from `edi status`, from the pipeline and from git, never from reading panes.
One narrow pane probe exists, used only before sending a key and by `edi worker peek`.
Hooks are passed with `--settings` and never written into a worktree, because Claude Code merges `--settings` with a worktree's `.claude/settings.local.json` and a leftover file there would keep firing an old hook.

**Motivation:** Hooks give structured state without pane scraping; a private tmux server isolates the fleet from the captain's own sessions; the interactive pane keeps the captain able to step in.
Every hook event, flag and payload field this relies on was verified on Claude Code 2.1.300 ([docs/verification/claude-code.md](../verification/claude-code.md)).

**Alternatives considered:**
- Headless sessions streaming JSON (`claude -p --output-format stream-json`) - structured, but the captain could no longer attach and step in; kept as a later `WorkerRuntime` adapter for unattended scouts.
- The Claude Agent SDK - see ADR-014.

**Consequences:**
- Good: liveness comes from hook events, the recorded tmux server identity and the process table, so a restart cannot fake a live worker.
- Bad: EDI depends on Claude Code's hook contract, which changes with its frequent releases; the verification document records the version it was checked on.

**Revisit when:** Claude Code changes a hook event or payload field EDI relies on, or unattended headless workers are wanted.

**Sources:** EDI rewrite specification, sections 10.2, 11 and 12 (decision E4, I-26, I-28, I-31).

# ADR-054: How EDI launches a Claude Code worker in tmux

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** ADR-013 runs each worker as an interactive Claude Code session on EDI's own tmux server with an EDI-chosen session id, an explicit model and effort, a name, and a settings file with hooks and attribution off (I-09).
Section 11.2 starts the worker in window `<task-id>` with the brief as the first prompt, records the tmux server's pid and start time, the window, the session id and the worktree, and pre-trusts the worktree so no trust dialog appears.
Fleet-sized text never travels as a command-line argument (I-36), and per-home tool exclusions come from the machine profile (U-02).
On Claude Code 2.1.301 a folder it has never seen shows a trust dialog with "No, exit" selected, which EDI must never answer (I-28).

**Decision:** `TmuxClaudeRuntime`, the `WorkerRuntime` adapter, launches in four steps.
It refuses a worktree whose `.claude/settings.local.json` defines hooks (`LocalHooks`).
It sets `hasTrustDialogAccepted` on the entry for the project's clone in Claude Code's own configuration file, `.claude.json` under `edi.workers.claude-config` (by default `$CLAUDE_CONFIG_DIR` or the home directory), only when it is not set yet, keeping every other key and replacing the file in one move (`ClaudeWorkspaceTrust`).
It writes `claude/worker/settings.json` owner-only into the worker's state directory: attribution off, and hooks that run `edi hook session-start`, `user-prompt-submit`, `pre-tool-use` for Bash and the file-writing tools, and `stop`.
It opens the window `<task-id>` in session `workers` on the socket `edi.workers.socket` (default `edi`) in the worktree, with `EDI_HOME`, `EDI_ROLE=worker`, `EDI_TASK_ID`, `EDI_WORKER` and `EDI_TOKEN_FILE` in its environment, running `claude` with `--disallowedTools` from `workers.disallowedTools` when there are any, `--add-dir` for the task directory, `--session-id`, `--model`, `--effort` unless the model ignores it, `--name <task-id>`, `--settings`, the permission flag for `workers.permissionMode` (`bypass`, the default, gives `--dangerously-skip-permissions`; `auto` and `prompt` give `--permission-mode auto` and `default`), `--append-system-prompt` with `claude/worker/system-prompt.md`, and after `--` one line, behind the invisible separator U+2063: `EDI_OP: brief waiting: read <brief> and follow it`, where the brief is `data/tasks/<task-id>/brief.md` in the EDI home.
The command runs through `env -u` for every variable of the core's own environment whose name starts with `CLAUDE`, except `CLAUDE_CONFIG_DIR`, so a core started from inside a Claude Code session, as one the primary restarts would be, never hands that session's id, messaging socket or effort to a worker.
The same `env` always unsets `GIT_CONFIG_COUNT` and `GIT_CONFIG_PARAMETERS`, and every `GIT_CONFIG_KEY_*` and `GIT_CONFIG_VALUE_*` of the core's environment, so command-scope git configuration such as `core.hooksPath`, inherited by the core or by the tmux server it started, never overrides the hooks ADR-053 installs in the worker's copy (I-09).
It returns the window id, the pane's pid and the server's pid and start time, which the launch records with the worker.
`claude` can be replaced with `edi.workers.claude-code`, which the native smoke test points at the stand-in `edi-test-claude`.

**Motivation:** The brief travels as a file named in one short line, so its size never reaches an argument list, and the appended system prompt makes the brief and the inbox first-party instructions without trusting anything else.
One entry per project covers every copy of it, because Claude Code checks a linked worktree's trust on its main checkout, which is EDI's clone; an entry for the worktrees root, tried first in the live smoke, did not suppress the dialog.
Bypass is the permission mode the captain's workers use today; the guard's `PreToolUse` hook still runs under it.

**Alternatives considered:**
- The whole brief as the first prompt - a long argument for every launch, against I-36.
- A trust entry per worktree - it works too, but adds one entry to the captain's Claude Code configuration for every task.
- One entry for `$EDI_HOME/worktrees` - Claude Code does not read it for a worktree, as the live smoke showed.
- A separate Claude Code configuration directory per worker - workers would lose the captain's login.

**Consequences:**
- Good: a launch is one tmux call with everything the worker needs, and its command line is tested exactly on a private tmux server.
- Bad: EDI writes to Claude Code's configuration file, which running sessions also rewrite; the write is one atomic move, and an overwritten entry is written again at the next launch.

**Revisit when:** Claude Code offers trust through `--settings`, changes its trust check, or the captain chooses another permission mode.

**Sources:** EDI rewrite specification, sections 10.2, 11.2, 21.3 (U-02) and I-09, I-28, I-36 (decision E4); ADR-013; `adapter.runtime.TmuxClaudeRuntime`, `Tmux`, `ClaudeCommandLine`, `ClaudeWorkspaceTrust`, `WorkerSettingsFile`, `LocalHooks`, `claude/worker/settings.json`, `claude/worker/system-prompt.md`; [docs/verification/claude-code.md](../verification/claude-code.md).

# Workers are interactive Claude Code sessions on EDI's own tmux server, observed through hooks

**Kind:** Architecture

## Context

Workers run long builds that outlive any single call, and the captain wants to watch a worker and step into it.
The old harness judged workers by scraping panes and by shell liveness, which reported workers "alive" after a machine restart had killed them, and it shared the captain's default tmux server, so a lost server took their own sessions too.
Claude Code merges `--settings` with a worktree's `.claude/settings.local.json`, shows a trust dialog in a folder it has never seen, and sets `CLAUDE_CODE_SESSION_ID` and `CLAUDE_PID` in every hook and Bash command of a session ([docs/verification/claude-code.md](../verification/claude-code.md)).

## Decision

`TmuxClaudeRuntime`, the `WorkerRuntime` adapter, launches each worker as an interactive Claude Code session in window `<task-id>` of session `workers` on EDI's own server (`tmux -L edi`), in the task's isolated copy ([isolated-copies](isolated-copies.md)).
It refuses a copy whose `.claude/settings.local.json` defines hooks (`LocalHooks`), sets `hasTrustDialogAccepted` on the project clone's entry in Claude Code's configuration once, in one atomic move (`ClaudeWorkspaceTrust`), and writes `claude/worker/settings.json`, attribution off, into the worker's state directory.
`ClaudeCommandLine` starts `claude` with an EDI-chosen `--session-id`, `--model`, `--effort` unless the model ignores it, `--name <task-id>`, `--settings`, the permission flag from `workers.permissionMode`, `--disallowedTools` from the machine profile, `--append-system-prompt` with `claude/worker/system-prompt.md`, and one first line naming the brief file, never the brief itself ([worker-brief](worker-brief.md)).
The command runs through `env -u` for every inherited `CLAUDE*` variable except `CLAUDE_CONFIG_DIR` and every `GIT_CONFIG_*` variable, so a core started inside a Claude Code session never hands that session to a worker, and no command-scope git setting overrides the copy's hooks.
`MiseTrust` asks `mise trust --show` (`edi.workers.mise`, `mise` by default) in the project's clone, which the captain trusts once with `mise trust` there, and counts the clone as trusted only when mise succeeds, lists at least one configuration in the clone's own directory or below it, and marks every such configuration `trusted`; configurations of the clone's parent directories do not count, whatever their trust, because a copy elsewhere does not inherit them.
A trusted clone's worker then runs through `mise exec --` in the copy with `MISE_EXEC_AUTO_INSTALL=false`, so the worker and every command it runs use the toolchain the copy's `mise.toml` pins, whatever toolchain the core inherited, and a pinned tool that is missing is reported by mise instead of downloaded at launch.
An untrusted clone's worker, or one whose mise is missing or fails, starts without `mise exec`, on the core's inherited toolchain.
The window's environment carries `EDI_HOME`, `EDI_ROLE`, `EDI_TASK_ID`, `EDI_WORKER` and `EDI_TOKEN_FILE`, the path of the worker's token ([api-access](api-access.md)).
The primary and every worker answer all seven hooks through `edi hook <event>`, and `edi` sends `CLAUDE_CODE_SESSION_ID` and `CLAUDE_PID` as the `EDI-Session` and `EDI-Claude-Pid` headers; `HookController` routes a worker's hooks to `WorkerHooks` and the home token's to `PrimaryHooks`.
EDI learns a worker's state from its hooks, `edi status`, the pipeline and git, never from reading panes; one narrow pane probe exists for typing and `edi worker peek` ([steering](steering.md)).

## Why

Hooks give structured state without pane scraping, a private tmux server isolates the fleet from the captain's own sessions, and the interactive pane keeps the captain able to step in.
The brief travels as a file named in one line, so its size never reaches an argument list, and the appended system prompt makes the brief and the inbox first-party instructions without trusting anything else.
One trust entry per project covers every copy, because Claude Code checks a linked worktree's trust on its main checkout.
Claude Code's Bash commands start from the environment Claude Code started with, and mise's shell hook never runs in them, so a worker launched with the core's own `JAVA_HOME` and `PATH` built with the machine's default JDK instead of the copy's pin; `mise exec` sets the copy's toolchain before Claude Code starts.
mise 2026.10.20 documents two rules: `mise exec` trusts its active configuration automatically unless paranoid mode is on, and trusting a configuration in a repository's main checkout also trusts the same path in its git worktrees.
So a copy, a git worktree of the clone, is trusted exactly when the clone is, and a `mise exec` in a copy of an untrusted clone would trust it by itself.
A trusted `mise.toml` can run commands when `mise exec` starts, before Claude Code and outside EDI's guard, so the captain chose that nothing of an untrusted project's `mise.toml` runs without the captain's say while its worker still starts, as `LocalHooks` refuses local hooks for the same reason.

## Alternatives rejected

- Headless sessions streaming JSON - structured, but the captain could no longer attach; it stays a possible later `WorkerRuntime` adapter.
- The whole brief as the first prompt - a long argument at every launch.
- A trust entry per worktree, or one for the worktrees root - one entry per task, or not read by Claude Code at all.
- A configuration directory per worker - workers would lose the captain's login.
- Trusting each copy's `mise.toml` for mise's shell hook - the hook never runs in Claude Code's Bash commands, so the inherited toolchain would still win.
- A brief telling the worker to build through `mise exec` - every command would have to remember it, and a build tool started by another tool would not.
- Trusting every copy through `MISE_TRUSTED_CONFIG_PATHS`, or letting `mise exec` trust it - any project's `mise.toml` would run commands outside EDI's guard.
- Paranoid mode for an untrusted clone - its worker would not start until the captain trusts the clone.
- Refusing a copy whose `mise.toml` can run commands at start - EDI would have to parse every project's `mise.toml`.

## Consequences

- Good: liveness comes from hooks, the recorded server identity and the process table, so a restart cannot fake a live worker ([supervision](supervision.md)).
- Bad: EDI depends on Claude Code's hook contract and writes to its configuration file, which running sessions also rewrite; the verification document records the version each fact was checked on.
- Bad: a worker starts on its copy's toolchain only when mise is on the `PATH`, as EDI's own install already needs ([core-lifecycle](core-lifecycle.md)), and the captain has trusted the project's clone; otherwise it builds with the core's inherited toolchain.

## When to revisit

When Claude Code changes a hook, a flag or its trust check, or unattended headless workers are wanted.

## How we got here

- 15/10/2026: the specification chose interactive workers in tmux, observed through hooks.
- 16/10/2026: slice S3 built the launcher and four worker hooks; slice S4 answered all seven hooks for the primary and the workers.
- 27/10/2026: native builds in workers failed on the machine's default JDK, which the core handed every worker, so workers start through `mise exec` in their copy (EDI-100).
- 28/10/2026: the captain chose to start a worker through `mise exec` only when mise trusts the project's clone (EDI-100).

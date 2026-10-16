# ADR-058: The pre-tool guard reads each shell command in the domain and refuses before it runs

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 10.2's `PreToolUse` guard refuses `pkill`, `killall` and pattern kills (I-30), tmux without `-L` or `-S` and control of the `edi` server by anyone but the core (I-31), `git push` to a base branch, `git push --force` and merge commands (I-03), and for workers also writes outside the worktree and the task directory (I-07).
Claude Code passes a Bash call's command as one string, which a shell may nest in pipes, substitutions, `bash -c`, `eval`, `xargs`, `find -exec` and wrappers such as `sudo` or `timeout`.

**Decision:** `ShellScript` reads a command string into every command it would run, without running anything: quoting, escapes, `$(...)` and backticks, process substitution, here-documents (whose bodies are data), comments, keywords and assignments, and the wrappers above, refusing to read more than twelve levels of nesting.
The end of a `$(...)` or `<(...)` is found by the same parser, so quotes and here-document bodies inside it can neither end it early nor swallow the commands after it.
A shell such as `bash`, `sh` or `zsh` is read through its `-c` script; one with neither `-c` nor a script file, or with `-s`, runs its standard input (a here-document, a here-string or a pipe) and is refused as unreadable.
`ShellGuard` then refuses, with one reason naming the invariant: `pkill`, `killall`, `skill`, `slay` and `fuser -k`; `kill` whose targets come from a pipe, from a substitution running `pgrep`, `pidof`, `ps`, `lsof` or `fuser`, or that are `0` or negative; a command line that both looks processes up by pattern and kills; tmux without `-L` or `-S` (except `tmux -V`); `kill-*`, `send-*` and `respawn-*` commands on the `edi` socket; a forced push (`--force`, `-f` in any flag group, `--force-with-lease`, `--force-if-includes`, `--mirror`, a `+` refspec); a push of every branch, or to the task's base, the project's base, `main` or `master`; and `gh pr merge`, `glab mr merge` or `accept`, forge API calls on a `merge` endpoint or naming auto-merge, and merge push options.
`GuardToolCall` applies it to a worker's Bash calls with that task's bases, to the primary's with every registered base, and keeps a worker's `Write`, `Edit`, `MultiEdit` and `NotebookEdit` calls inside its worktree and task directory on real paths; every refusal of a worker is recorded as `ToolCallRefused` with its rule, never the command.
`edi hook pre-tool-use` prints Claude Code's deny decision with the reason, and when the core cannot be asked it exits with code 2, which makes Claude Code refuse the call.

**Motivation:** The rules are plain Java tested in milliseconds over a hundred command shapes, and a refusal tells the worker what to do instead.
Failing closed keeps the invariants when the core is down; a worker without its core cannot report anyway.

**Alternatives considered:**
- Regular expressions over the raw command - quoting and nesting defeat them in both directions.
- Containing Bash writes by reading redirections and `cd` - a shell can write anywhere in too many ways to read reliably; the brief states the rule and the file tools are contained.

**Consequences:**
- Good: AT-30 and AT-31 pass for a worker and for the primary, whose settings arrive with slice S8.
- Bad: a command the guard cannot read is refused, and a variable holding a process id is trusted, as the brief's "kill only a process id you captured" asks.

**Revisit when:** The primary's settings arrive (slice S8), whose guard must still let the primary run `edi` when the core is down.

**Sources:** EDI rewrite specification, sections 10.2, 16 and I-03, I-07, I-30, I-31; `domain.guard`, `application.worker.GuardToolCall`, `ToolCall`, `web.hook.HookController`, `edi-cli` `HookCommand`.

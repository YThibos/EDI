# The pre-tool guard reads each shell command in the domain and refuses before it runs

**Kind:** Design

## Context

The `PreToolUse` hook must refuse pattern kills (I-30), tmux without a named socket and control of EDI's own server (I-31), forced pushes, pushes to a base and merges (I-03), and for workers any write outside their copy and task directory (I-07).
Secrets are read only by EDI's adapters and never printed (I-32), and EDI's home token lets its holder act as the primary.
The `commit-msg` and `pre-push` hooks an isolated copy runs through `core.hooksPath` hold the work key (I-10), the bar on trailers (I-09), the secret check (I-38) and the registered forge (I-39), and git skips them on `--no-verify` or another hooks path.
Claude Code passes a Bash call as one string, which a shell may nest in pipes, substitutions, `bash -c`, `eval`, `xargs`, `find -exec` and wrappers such as `sudo` or `timeout`.
A primary whose core is down must still be able to bring it back.

## Decision

`ShellScript` reads a command string into every command it would run, without running anything: quoting, escapes, substitutions, process substitution, here-documents, comments, keywords, assignments, wrappers, and shells read by their shared option grammar (`ShellInvocation`); it refuses more than twelve levels of nesting and anything it cannot read.
`ShellGuard` refuses with one reason naming the invariant: `pkill`, `killall` and kin, kills fed from process lookups or aimed at groups, tmux without `-L` or `-S`, `kill-*`, `send-*` and `respawn-*` on the `edi` socket, any forced push, a push of every branch or to a base, and merges through `gh`, `glab`, a forge API or push options.
`SkippedHooks` refuses `git commit` with `--no-verify` or `-n`, `git push` and `git merge` with `--no-verify`, `-c` or `--config-env` setting `core.hooksPath` on any git command, and `git config` writing it, naming the checks the skipped hook holds.
`BranchDeletions` refuses deleting the recorded branch of a task still queued or in flight, locally or on the remote; the primary may on the captain's word ([captains-word](captains-word.md)).
`SecretReads` refuses a command whose words end in a secret file's path, or that prints a forge token; `GuardToolCall` keeps the home token from every worker always, and the home token, the tracker's keys file and forge tokens from the primary unless the captain's word on the home was recorded in that session.
`GuardToolCall` keeps a worker's `Write`, `Edit`, `MultiEdit` and `NotebookEdit` calls inside its copy and task directory on real paths, and records every refusal of a worker as `ToolCallRefused` with its rule, never the command.
`edi hook pre-tool-use` prints Claude Code's deny decision with the reason, and when the core cannot be asked exits with code 2, which refuses the call, except that the primary may run a plain `edi` command with no shell syntax (`PlainEdiCall`).

## Why

The rules are plain Java tested in milliseconds over a hundred command shapes, and a refusal tells the caller what to do instead.
Matching a secret by its path's ending catches the forms a shell writes it in while leaving a search of EDI's own source alone.
Failing closed keeps the invariants when the core is down, while a plain `edi up` lets the primary bring it back.

## Alternatives rejected

- Regular expressions over the raw command - quoting and nesting defeat them in both directions.
- Containing Bash writes by reading redirections and `cd` - a shell can write anywhere in too many ways; the brief states the rule and the file tools are contained.
- Keeping the keys file from workers too - project tooling a worker runs gets its path by design.
- Running the landed-work test inside the guard - it fetches, too slow for every command, while the task's state already says whether its work landed.

## Consequences

- Good: AT-30, AT-31 and the secret half of AT-06 pass for workers and the primary.
- Good: a worker's commit or push cannot step around the hooks, so AT-09 and AT-10 hold for a commit that asks to skip them.
- Bad: a command the guard cannot read is refused, while a process id, a branch, a secret or a hooks path reached through a variable or after a `cd` passes; the brief and the anchor state those rules.

## When to revisit

When workers run as another user, or the captain wants workers kept from the keys file.

## How we got here

- 16/10/2026: slice S3 built the shell reader and the guard for workers.
- 17/10/2026: slice S8 armed it for the primary, kept secrets from a shell and guarded branch deletions.
- 26/10/2026: EDI-74 refused the flags and settings that skip the commit-msg and pre-push hooks.

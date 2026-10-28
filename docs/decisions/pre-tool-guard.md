# The pre-tool guard reads each shell command in the domain and refuses before it runs

**Kind:** Design

## Context

The `PreToolUse` hook must refuse pattern kills (I-30), tmux without a named socket and control of EDI's own server (I-31), forced pushes, pushes to a base and merges (I-03), and for workers any write outside their copy and task directory (I-07).
Secrets are read only by EDI's adapters and never printed (I-32), and EDI's home token lets its holder act as the primary.
The `commit-msg` and `pre-push` hooks an isolated copy runs through `core.hooksPath` hold the work key (I-10), the bar on trailers (I-09), the secret check (I-38), the SNAPSHOT check (I-11) and the registered forge (I-39), and git skips them on `--no-verify` or another hooks path.
Claude Code's `Read` tool shows a file to the model without a shell.
Claude Code passes a Bash call as one string, which a shell may nest in pipes, substitutions, `bash -c`, `eval`, `xargs`, `find -exec` and wrappers such as `sudo` or `timeout`.
A primary whose core is down must still be able to bring it back.

## Decision

`ShellScript` reads a command string into every command it would run, without running anything: quoting, escapes, substitutions, process substitution, here-documents, comments, keywords, assignments, wrappers, and shells read by their shared option grammar (`ShellInvocation`); it refuses more than twelve levels of nesting and anything it cannot read.
`ShellGuard` refuses with one reason naming the invariant: `pkill`, `killall` and kin, kills fed from process lookups or aimed at groups, tmux without `-L` or `-S`, `kill-*`, `send-*` and `respawn-*` on the `edi` socket, any forced push, a push of every branch or to a base, and merges through `gh`, `glab`, a forge API or push options.
`TmuxSockets` compares a `-S` socket path on its real path, which `GuardToolCall` resolves from the call's working directory (`SocketPaths`), so a link to the `edi` socket is the `edi` socket.
For a worker, `TmuxSockets` also refuses, on any socket, every tmux command that types, pastes or runs commands into a pane: `send-keys`, `send-prefix`, `paste-buffer`, `source-file`, `run-shell`, `if-shell`, `pipe-pane`, `display-popup`, `display-menu`, `respawn-pane`, `respawn-window`, `set-hook`, `bind-key`, `command-prompt` and `confirm-before`, their aliases and any abbreviation of four letters or more, a `#(` shell format, and control mode (`-C`), because the primary runs on the captain's default server and text typed into its pane counts as the captain's word ([captains-word](captains-word.md)).
`SkippedHooks` refuses `git commit` with `--no-verify` or `-n`, `git push` and `git merge` with `--no-verify`, and `-c` or `--config-env` setting `core.hooksPath` on any git command, naming the checks the skipped hook holds.
`BranchDeletions` refuses deleting the recorded branch of a task still queued or in flight, locally or on the remote; the primary may on the captain's word ([captains-word](captains-word.md)).
`SecretReads` refuses a command any of whose words leads to a secret file, or that prints a forge token, and a `Read` of a file whose path or real path ends in one.
A word leads to a secret file when it ends in the file's path as written, or when one of the paths `ShellWordPaths` finds for it does, from the call's working directory and as bash expands it: braces first, including sequences and a slash inside them (`BraceExpansion`); then `~`, `~` with the user's own name, `~+`, `$HOME` and `$EDI_HOME`; then globs, segment by segment, over `*`, `?`, bracket expressions with `!` or `^`, character classes, extended globs and `**` (`ShellGlob`); each path normalised and resolved to its real path; an option's value after `=` counts as a word.
`ShellScript` keeps apart what the shell expands from what it takes as written, so a quoted or escaped glob, brace or tilde is matched as the literal text the program receives.
Where the guard cannot evaluate a form exactly, an equivalence class or an extended glob, it reads it as matching more, never less.
A word whose braces or globs reach more than 1024 paths is refused, since the guard cannot tell which files it reaches.
A worker's `Glob` is refused when its pattern, expanded and rooted at its path or the working directory, reaches a secret file on the written or the real paths, with its leading folders normalised and resolved to their real path (`SecretSearches`).
`GuardToolCall` keeps the home token from a worker's shell, the home token and the tracker's keys file from a worker's `Read`, and the home token, the tracker's keys file and forge tokens from the primary unless the captain's word on the home was recorded in that session.
`WorkerIdentity` refuses a worker's shell command that unsets or reassigns `EDI_TOKEN_FILE`, `EDI_ROLE`, `EDI_WORKER` or `EDI_TASK_ID`, which tell `edi` and the core which worker is calling ([api-access](api-access.md)): through `unset`, `env -u`, `env -i` or an assignment given to `env`, through `export`, `declare`, `typeset`, `local` or `readonly`, or through an assignment before a command or on its own; `ShellScript` reads those assignments, and the primary's environment stays its own.
`GuardToolCall` keeps a worker's `Write`, `Edit`, `MultiEdit` and `NotebookEdit` calls inside its copy and task directory on real paths, and records every refusal of a worker as `ToolCallRefused` with its rule, never the command.
It also refuses a worker any write under its copy's `.claude` directory, as written or on real paths, through those tools and through a shell (`SettingsWrites`): a redirection, `tee`, `touch` or `truncate`, the destination of `cp`, `mv`, `install`, `ln` or `rsync`, `sed -i`, and `dd of=`, because a settings file there can switch off the hooks that guard it ([worker-sessions](worker-sessions.md)).
The worker's settings send `Bash`, `Read`, `Glob` and the file-writing tools to the guard, the primary's `Bash` and `Read`.
`edi hook pre-tool-use` prints Claude Code's deny decision with the reason, and when the core cannot be asked exits with code 2, which refuses the call, except that the primary may run a plain `edi` command with no shell syntax (`PlainEdiCall`).

## Why

The rules are plain Java tested in milliseconds over a hundred command shapes, and a refusal tells the caller what to do instead.
Matching the paths a word leads to, rather than its text, catches a glob, a doubled or dotted segment and a link the way the shell resolves them, while leaving a search of EDI's own source alone.
Reading globs with its own reader rather than Java's `PathMatcher` keeps bash's meaning of `[^x]`, `{m..o}` and a slash inside braces, and leaves no pattern the guard fails to read and so lets through.
Refusing at the match limit, rather than checking the first matches, keeps a wide glob from hiding the secret past the limit.
Keeping the primary from a glob that reaches a secret costs it nothing it needs: it reads `core.port`, the logs and the state directory's listing by name, and the captain's word lifts the refusal.
Failing closed keeps the invariants when the core is down, while a plain `edi up` lets the primary bring it back.
Failing closed on a `Read` is consistent with the guard refusing the primary's shell while the core is down.
Matching a `Read` on its real path closes a link a worker can make in its own copy to a secret file, as the write containment realises paths.

## Alternatives rejected

- Regular expressions over the raw command - quoting and nesting defeat them in both directions.
- Containing Bash writes by reading redirections and `cd` - a shell can write anywhere in too many ways; the brief states the rule and the file tools are contained.
- Keeping the keys file from a worker's shell too - project tooling a worker runs gets its path by design, while a `Read` only ever shows the file to the model.
- Running the landed-work test inside the guard - it fetches, too slow for every command, while the task's state already says whether its work landed.

## Consequences

- Good: AT-30, AT-31 and the secret half of AT-06 pass for workers and the primary.
- Good: a worker's shell cannot type into the primary's pane through any tmux socket, so it cannot forge the captain's word with a tmux command the guard reads.
- Good: a `Read` of the keys file or the home token is refused to a worker and to the primary without the captain's word, also through a link, so AT-32 holds for the file tools.
- Good: a commit with `--no-verify` or `-n`, a push or merge with `--no-verify`, and a `-c` or `--config-env` override of `core.hooksPath` are refused, so AT-09 and AT-10 hold for a commit that asks to skip the hooks.
- Bad: a command the guard cannot read is refused, while a process id, a branch or a hooks path reached through a variable or after a `cd` passes, and so does a secret reached through a variable other than `HOME` or `EDI_HOME`, after a `cd` or through `~-`, through the folder that holds it, such as `grep -r` over the state directory, or through a path a program builds at run time, such as `python3 -c` joining strings; the brief and the anchor state those rules.
- Bad: the guard resolves links as they stand before the command runs, so a link the same command makes and then reads through, such as `ln -s` to the state directory followed by a `cat` through it, passes.
- Bad: a quoted pattern a program expands itself passes as the literal text it is, and a `Glob` whose wildcard, rather than its leading folders, passes through a link to the state directory is matched on the written path only.
- Bad: `~name` for another user is left as written, since it names that user's home and not the captain's.
- Bad: a glob or brace that reaches more than 1024 paths is refused wherever it points, and an unquoted `**` is walked as bash's `globstar` would, so a wide pattern over a large tree is refused until it is narrowed or quoted.
- Bad: a worker's connector tools are not sent to the guard, and neither is `Grep` until EDI-113.
- Bad: a denylist cannot fully contain an agent that runs as the captain's own user, whatever it matches: it keeps a misled worker from the secrets through the tools and commands it reads, and nothing more.
  Two stronger options stand for the captain to weigh: workers running as another operating-system user, so the home token, the keys file, EDI's home and the captain's tmux server are out of their reach; or tokens that grant no primary rights, so a home token a worker reads still cannot act as the primary.
- Bad: while the core is down the guard refuses every `Read` as it refuses a shell, so the primary brings the core back before it reads a file.
- Bad: a `Grep` of a secret file, or of a folder that holds one, still shows its lines; it is a known gap.
- Bad: a hooks path set through the `GIT_CONFIG_*` variables or `git config`, and `git pull --no-verify`, still skip the hooks; they are known gaps.
- Bad: workers run as the captain's own user, so a script file the guard does not open, or a program that speaks tmux's protocol to the socket itself, can still type into the primary's pane, and a worker may still kill a pane or the whole server on the captain's default socket; the guard is a denylist for a misled worker, not a boundary.
- Bad: `read`, `printf -v`, `mapfile`, a `for` loop or a script file the guard does not open can still change a worker's identity variables; the `edi` command's own refusal in a worker's window is the second line ([api-access](api-access.md)).
- Bad: a write under `.claude` after a `cd`, by a program the guard does not read, such as `python3 -c`, or by a script file still passes; the `disableAllHooks: false` the settings state is what keeps the hooks on even then, and it is a Claude Code precedence rule, verified on 2.1.312, that EDI relies on.

## When to revisit

When the captain chooses one of the stronger options above, workers run as another user, or the captain wants workers kept from the keys file in their shell.

## How we got here

- 16/10/2026: slice S3 built the shell reader and the guard for workers.
- 17/10/2026: slice S8 armed it for the primary, kept secrets from a shell and guarded branch deletions.
- 26/10/2026: EDI-74 refused the flags and settings that skip the commit-msg and pre-push hooks.
- 27/10/2026: the pre-push hook's publish check refuses a SNAPSHOT version, so a refusal for a skipped hook names that check too (EDI-78).
- 27/10/2026: the guard refuses a `Read` of a secret file as it refuses a shell's read of it, and keeps the keys file from a worker's `Read` (EDI-80).
- 28/10/2026: the guard keeps a worker's shell from unsetting or reassigning the variables that say which worker is calling (EDI-122).
- 28/10/2026: the guard refuses a worker every tmux command that sends input to a pane, on any socket, and compares a socket path on its real path (EDI-123).
- 28/10/2026: the guard matches a secret on the normalised, glob-expanded real paths a shell word leads to, guards a worker's `Glob`, and states what a denylist cannot contain (EDI-124).
- 28/10/2026: the guard refuses a worker any write under its copy's `.claude` directory, after a verification showed a settings file there could switch its hooks off (EDI-125).

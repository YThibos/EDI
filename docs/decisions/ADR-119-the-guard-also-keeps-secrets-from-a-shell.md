# ADR-119: The pre-tool guard reads each shell command in the domain, keeps secrets from a shell, and lets the primary run edi while the core is down

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 10.2's `PreToolUse` guard refuses `pkill`, `killall` and pattern kills (I-30), tmux without `-L` or `-S` and control of the `edi` server by anyone but the core (I-31), `git push` to a base branch, `git push --force` and merge commands (I-03), and for workers also writes outside the worktree and the task directory (I-07).
I-32: secrets are read at call time by EDI's adapters and never printed; I-06 and AT-06: a secret-touching command needs the captain's word in the current session.
EDI's own home token in `state/core.token` lets its holder act as the primary, and project tooling hands the tracker's keys file to scripts by its path.
Claude Code passes a Bash call's command as one string, which a shell may nest in pipes, substitutions, `bash -c`, `eval`, `xargs`, `find -exec` and wrappers.
A primary whose core is down must still be able to run `edi up` and `edi health`.

**Decision:** `ShellScript` reads a command string into every command it would run, without running anything, through quoting, escapes, substitutions, here-documents, keywords, assignments, wrappers and shells read by their shared option grammar (`ShellInvocation`); what it cannot read it refuses.
`ShellGuard` refuses, with one reason naming the invariant, pattern kills and kills fed from lookups, tmux without a socket and control of the `edi` server, forced pushes and pushes to a base, and merges through `gh`, `glab` or a forge API.
`SecretReads` refuses a command whose words end in the path of a file in its `Secrets`, or, where `Secrets` keeps forge tokens, one that prints a forge's token (`gh auth token`, `glab auth status -t` or `--show-token`, `glab config get token`), with `GuardRule.SECRET`.
`GuardToolCall` keeps the home token and the tracker's keys file from the primary, and its forge tokens, unless the captain's word on the home was recorded in the session the call comes from (ADR-115); it keeps the home token from every worker always, and leaves workers the keys file their project tooling needs.
A worker's file writes stay inside its worktree and task directory, and every refusal of a worker is recorded as `ToolCallRefused` with its rule.
`edi hook pre-tool-use` refuses with exit code 2 when the core cannot be asked, except that the primary may run a plain `edi` command, one whose arguments hold only letters, digits and `=._-` and no shell syntax (`PlainEdiCall`).

**Motivation:** The rules are plain Java tested in milliseconds over a hundred command shapes, and a refusal tells the caller what to do instead.
Matching a secret by its path's ending catches the forms a shell writes it in, and leaves a search for the word `core.token` in source code alone.
Failing closed keeps the invariants when the core is down, while a plain `edi up` lets the primary bring the core back.

**Alternatives considered:**
- Regular expressions over the raw command - quoting and nesting defeat them in both directions.
- Matching a secret file by its bare name - would refuse a search of EDI's own source for the name.
- Keeping the keys file from workers too - a JIRA script a worker runs on its project gets the file's path by design.

**Consequences:**
- Good: AT-30, AT-31 and the secret half of AT-06 pass, for workers and for the primary.
- Bad: a secret reached through a variable or after a `cd` into its directory is not recognised; the brief and the anchor state the rule.
- Bad: a command the guard cannot read is refused.

**Revisit when:** Workers run as another user, or the captain wants workers kept from the keys file.

**Sources:** EDI rewrite specification, sections 10.2 and 16, I-03, I-06, I-07, I-30, I-31, I-32 (AT-06, AT-30, AT-31); ADR-058, ADR-115; `domain.guard.ShellScript`, `ShellGuard`, `ShellInvocation`, `SecretReads`, `Secrets`, `GuardRule`, `application.worker.GuardToolCall`, `ToolCall`, `web.hook.HookController`, `edi-cli` `HookCommand`, `PlainEdiCall`.

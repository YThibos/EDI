# ADR-064: A stand-in for Claude Code runs a worker end to end in the native smoke test

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Tests replace Claude Code with `edi-test-claude`, a stub that reads the brief, emits the hook calls a real session would, runs scripted `edi` commands and exits (section 17.3).
Its hooks call `edi`, so a run end to end needs both executables, and only the `native` profile builds them, in `edi-cli`'s integration tests; `edi-core`'s tests cannot start the command.
tmux tests use a private socket, `edi-test-<uuid>`, and kill only that server.

**Decision:** `edi-cli/src/test/resources/edi-test-claude` is a POSIX shell script that takes Claude Code's launch flags, sends `SessionStart`, `UserPromptSubmit` with its first prompt, a `PreToolUse` for each command, and `Stop` through `edi hook` with the JSON a session sends, runs each `stub-run:` line of its brief's spec unless the guard denies it, reads its inbox when a stop is blocked or a typed line says a message waits, and then waits at an idle composer for typed lines.
`NativeSmokeIT` starts the native core with a private worker socket, the stand-in as `edi.workers.claude-code`, a scratch Claude Code configuration directory, and the native `edi` first on `PATH`, then dispatches a ship whose spec reports working, commits, tries `pkill` and reports done, and steers it at its idle prompt; it checks the status, the commit, the guard's refusal, the brief and the delivery proof, and skips when tmux is missing.
On the JVM, `TmuxClaudeRuntimeTest` launches a recording stand-in on a private tmux server and checks the exact command line, working directory, environment and settings file, and `PrivateTmuxServer` shows captured fixture screens for AT-28.

**Motivation:** The whole worker path, from `edi dispatch` through tmux, the hooks and the guard to `edi status`, runs on every push in CI, without a model and without touching any socket but a private one.

**Alternatives considered:**
- A stub written in Java - it would need a JVM start for every hook it sends.
- Running the end-to-end test on the JVM with a shim for `edi` - the shim would not be the command the captain runs.

**Consequences:**
- Good: a change that breaks the worker path fails the native job.
- Bad: the end-to-end run is skipped where tmux is not installed.

**Revisit when:** The live profile runs a real Claude Code worker in CI, or the stand-in must also play limit and permission dialogs.

**Sources:** EDI rewrite specification, sections 17.2 and 17.3; ADR-045; `edi-cli/src/test/resources/edi-test-claude`, `NativeSmokeIT`, `edi-core` `TmuxClaudeRuntimeTest`, `PrivateTmuxServer`.

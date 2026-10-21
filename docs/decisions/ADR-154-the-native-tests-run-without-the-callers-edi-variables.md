# ADR-154: The native tests start the command and the core without the caller's EDI variables

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** AGENTS.md has a worker run `./mvnw -Pnative verify` when a change touches Spring configuration, and EDI starts every worker with `EDI_HOME`, `EDI_ROLE`, `EDI_TASK_ID`, `EDI_WORKER` and `EDI_TOKEN_FILE` in its environment (`LaunchWorker`).
`NativeSmokeIT`, `NativeRecoveryIT` and `NativeLifecycleIT` start the native command and core with the caller's environment, setting `EDI_HOME` to their temporary home but leaving the other variables in place.
The command reads `EDI_TOKEN_FILE` before the home's token, so inside a worker it presented the worker's own token to the test's core, which refused it: 13 of the 20 native tests failed in a worker and passed outside one.

**Decision:** Each of the three native test classes removes every variable whose name starts with `EDI_` from the environment of the command and the core it starts, before it sets its own, as it already removes the `TMUX` and Claude Code variables.
EDI-27 made the same change for the command's environment on its own branch; this record covers the command and the core alike.

**Motivation:** The native tests prove the same thing in a worker, in the primary's terminal and in CI.

**Alternatives considered:**
- Removing only `EDI_TOKEN_FILE` - the next variable the command learns to read would leak the same way.
- Telling workers to run the native build with a cleaned environment - every worker would need to remember it, and the failure reads like a broken build.

**Consequences:**
- Good: a worker can run the native build its rules ask for.
- Bad: a native test that needs an `EDI_` variable sets it explicitly.

**Revisit when:** The native tests share one helper that starts programs.

**Sources:** AGENTS.md, "Build and test"; ADR-045, ADR-146; `edi-cli` tests `NativeSmokeIT`, `NativeRecoveryIT`, `NativeLifecycleIT`; `edi-core` `application.dispatch.LaunchWorker`.

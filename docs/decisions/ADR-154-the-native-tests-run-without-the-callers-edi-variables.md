# ADR-154: The native tests start the core without the caller's EDI variables

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** AGENTS.md has a worker run `./mvnw -Pnative verify` when a change touches Spring configuration, and EDI starts every worker with `EDI_HOME`, `EDI_ROLE`, `EDI_TASK_ID`, `EDI_WORKER` and `EDI_TOKEN_FILE` in its environment (`LaunchWorker`).
EDI-27 already made `NativeSmokeIT`, `NativeRecoveryIT` and `NativeLifecycleIT` remove every `EDI_` variable from the environment of the native command they start.
`NativeSmokeIT` and `NativeRecoveryIT` still started the native core with the caller's environment, setting `EDI_HOME` to their temporary home but leaving the other variables in place.
Inside a worker the core so inherited the worker's `EDI_ROLE`, `EDI_TASK_ID`, `EDI_WORKER` and `EDI_TOKEN_FILE`, and 13 of the 20 native tests failed in a worker and passed outside one.

**Decision:** `NativeSmokeIT` and `NativeRecoveryIT` remove every variable whose name starts with `EDI_` from the environment of the core they start, before they set their own, as they already remove the `TMUX` and `GIT_CONFIG_` variables.
This extends the rule EDI-27 set for the command to the core, so every program a native test starts runs without the caller's `EDI_` variables.

**Motivation:** The native tests prove the same thing in a worker, in the primary's terminal and in CI.

**Alternatives considered:**
- Removing only `EDI_TOKEN_FILE` - the next variable the command learns to read would leak the same way.
- Telling workers to run the native build with a cleaned environment - every worker would need to remember it, and the failure reads like a broken build.

**Consequences:**
- Good: a worker can run the native build its rules ask for.
- Bad: a native test that needs an `EDI_` variable sets it explicitly.

**Revisit when:** The native tests share one helper that starts programs.

**Sources:** AGENTS.md, "Build and test"; ADR-045, ADR-146; `edi-cli` tests `NativeSmokeIT`, `NativeRecoveryIT`, `NativeLifecycleIT`; `edi-core` `application.dispatch.LaunchWorker`.

# The native tests run both executables end to end with a stand-in for Claude Code, and judge the command's speed against the machine's own pace

**Kind:** Design

## Context

Only the `native` profile builds the native executables, in `edi-cli`'s integration tests, so `edi-core`'s tests cannot start the command ([java-toolchain-and-build](java-toolchain-and-build.md)).
A native command round trip must have a p95 under 50 ms over 200 calls on the specification's laptop (AT-39), while about 70% of a round trip is the command's own start-up, a property of the machine at that moment; a loaded laptop and GitHub's shared runners missed 50 ms on half their calls.
Workers run the native build too, inside an EDI worker whose environment carries `EDI_ROLE`, `EDI_TASK_ID`, `EDI_WORKER` and `EDI_TOKEN_FILE`.

## Decision

`NativeSmokeIT` starts the native core on a fresh home and checks its store, `/api/health` and the console (AT-44), drives the S1 commands against it, and runs a worker end to end: with a private worker socket, the stand-in `edi-test-claude` as `edi.workers.claude-code` and the native `edi` first on `PATH`, it dispatches a ship, sees its status, commit, guard refusal and brief, and steers it at its idle prompt; it skips when tmux is missing.
`edi-test-claude` is a POSIX shell script that takes Claude Code's flags, sends the hooks a session would through `edi hook`, runs each `stub-run:` line of its brief's spec unless the guard denies it, and reads its inbox when told.
`NativeRecoveryIT` and `NativeLifecycleIT` cover relaunch after a simulated restart and the core's start and stop, and every native test removes every `EDI_`, `TMUX` and `GIT_CONFIG_` variable from the environment of the command and the core it starts.
`NativeRecoveryIT`, which restarts the native core within its tests, waits up to 60 seconds for each start and asserts that a test's own work, its time less the start-ups it waited for, stayed under 30 seconds.
AT-39 lives in `NativeSmokeIT`, not the catalogue: `RoundTripBudget` times a bare `edi --version` before each `edi backlog` round trip, and the command meets the budget when its p95 is under 50 ms or, on a slower machine, when its p50 is under twice the bare start-up's p50.
The measurement stops at 200 round trips or 20 seconds, and the build summary names the round trips made, both medians, their ratio and the rule met; two proofs show a command doing its work twice fails and a uniformly slowed machine passes.

## Why

The whole worker path, from `edi dispatch` through tmux, the hooks and the guard to `edi status`, runs on every native build without a model and on a private socket only.
A shell stand-in costs no JVM per hook, and the command under test is the one the captain runs.
The ratio between the medians held at 1.37 to 1.46 while load moved the round trip fivefold, and a doubled command stayed at 2.72 to 2.91, so a multiple of 2 separates them with room on both sides.

## Alternatives rejected

- A Java stub, or an end-to-end run on the JVM with a shim for `edi` - a JVM start per hook, or a command that is not the real one.
- A budget for CI alone, comparing p95s, or `git --version` or `edi health` as the reference - holds only where measured, too noisy, barely moves with load, or shares the code under test.
- A longer timeout for AT-39 or `NativeRecoveryIT` - hides the cost, which the own-work assertion still catches.
- Scaling `NativeRecoveryIT` to a start-up measured before the test - one start-up says little about the next under a load that changes by the second.

## Consequences

- Good: a change that breaks the worker path or slows the command's own work fails the native build, wherever it runs.
- Bad: on a machine too slow for 50 ms, a regression in native start-up itself goes unseen except in the summary, and the multiple was calibrated on one laptop.
- Bad: `NativeRecoveryIT` catches a slower start-up of the core only once it passes 60 seconds.

## When to revisit

When the median ratio on GitHub's runners is above 1.6, the live profile runs a real Claude Code worker, or the native tests share one helper that starts programs.

## How we got here

- 15/10/2026: slice S0 built the native smoke test; slice S1 measured AT-39 in it against a fixed 50 ms.
- 16/10/2026: slice S3 added the stand-in and the end-to-end worker run.
- 21/10/2026: 13 of 20 native tests failed inside a worker, so the native core too starts without the caller's `EDI_` variables (EDI-27).
- 22/10/2026: AT-39 failed on shared runners and a loaded laptop, so it judges the command against the machine's own pace (EDI-37).
- 23/10/2026: `NativeRecoveryIT` overran its timeout while a loaded machine restarted the core, so the core's start-ups no longer count against a test's own thirty seconds (EDI-35).

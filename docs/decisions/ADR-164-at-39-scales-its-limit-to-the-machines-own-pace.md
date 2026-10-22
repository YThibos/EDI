# ADR-164: AT-39 scales its limit to the machine's own pace, measured in the same run

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** AT-39 asks for a native command round trip with a p95 under 50 ms over 200 calls, on the laptop the specification names.
ADR-045 made it a test in `NativeSmokeIT`, because only the `native` profile builds the native executables, that called `edi backlog`, an authenticated read of the store rendered as text, against the native core and failed when p95 reached 50 ms.
On GitHub's shared runners the median round trip sat at 49-51 ms on every run, so the test failed on 4 of 58 native jobs, and it failed on a busy laptop too: at a load average of 18-22 its p50 reached 59 ms, and at about 40 it reached 150 ms.
About 70% of the round trip is the native command's own start-up, which is a property of the machine at that moment more than of the command.
Every test must finish within the 30-second default timeout (AGENTS.md, ADR-065), and at a p50 of 150 ms 200 round trips alone take 30 seconds.
GitHub Actions does not start jobs on the account at the time of writing, so the decision is calibrated on the laptop alone.

**Decision:** AT-39 is a real test in `NativeSmokeIT`, like AT-44, and has no entry in the invariant catalogue, which keeps only the acceptance tests that run on the JVM (ADR-034).
The smoke test also drives the S1 commands against the native core: adding and showing a task, raising and answering a decision, the migration dry run, a refusal, and `edi publish-check --json` reporting a finding when gitleaks is installed.
`RoundTripBudget` measures the machine's pace in the same run: before each `edi backlog` round trip it times a bare start-up, `edi --version`, which starts the same native executable and answers without the core.
The command meets the budget when its p95 is under 50 ms, as AT-39 states, or, on a machine too slow for that, when its p50 is under twice the bare start-up's p50.
The measurement stops at 200 round trips or after 20 seconds, whichever comes first, and the verdict is taken on the round trips made.
The build summary names the round trips made, the command's p50, p95 and slowest call, the bare start-up's p50, the ratio between the two medians, and which rule the command met.
Two proofs measure 50 round trips with the same budget: a command that does its work twice, by running `edi backlog` before the real call, fails it, and a machine slowed by 60 ms on every call, bare start-up and command alike, misses the 50 ms limit but meets the budget.

**Motivation:** A calibration run on the laptop interleaved `edi --version`, `edi backlog`, `edi health`, `git --version`, the command doing its work twice and the command slowed by a fixed 60 ms, at the load other sessions gave it and with three and five builds of `edi-core` running beside it:

| Load average | Rounds | Bare start-up p50 | `edi backlog` p50 / p95 | Median ratio: command / doubled / fixed 60 ms |
|---|---|---|---|---|
| 7-9 | 200 | 22 ms | 30 / 39 ms | 1.37 / 2.78 / 4.17 |
| 16-24 | 200 | 42 ms | 59 / 167 ms | 1.40 / 2.91 / 2.89 |
| 21-30 | 300 | 55 ms | 76 / 217 ms | 1.37 / 2.72 / 2.48 |
| 36-47 | 200 | 109 ms | 159 / 258 ms | 1.46 / 2.84 / 2.07 |

The ratio between the medians held at 1.37-1.46 while the load moved the round trip fivefold, and a command doing twice its work stayed at 2.72-2.91, so a multiple of 2 leaves each side about a quarter in hand.
With the bare start-up timed every second round trip, or over windows of 40, the command's median ratio stayed at or below 1.50 over 200 round trips and 1.86 over 40, and the doubled command's at or above 2.47.
With the budget in place, the test took 10 seconds for its 200 round trips at a load of 8, with a p95 of 32 ms and a ratio of 1.39, and 21 seconds for 92 round trips at a load of 40, with a p95 of 200 ms and a ratio of 1.47.
The 50 ms limit stays as the first rule, so a command that is fast in absolute terms never fails because its bare start-up became faster still.

**Alternatives considered:**
- A budget for CI in this record - it would hold only on the runner it was measured on, and a busy laptop would still fail.
- Comparing each round trip with the bare start-up just before it - under load the two calls' spikes are independent, and the command's ratio (p95 2.06-2.23) overlapped the doubled command's (p5 1.59-1.86).
- Comparing the two p95s - the command's ratio moved between 1.27 and 1.65 from one run to the next, and with the bare start-up timed every second round trip the doubled command's fell to 1.98.
- `git --version` as the reference - it barely moved with the load, from 12 to 34 ms while the bare start-up went from 22 to 109 ms.
- `edi health` as the reference - it tracks the command closely, but it shares the command's connection to the core, so a regression there would cancel out.
- Keeping the command slowed by a fixed 60 ms as the proof - a fixed delay shrinks against a busy machine's pace, and at a load of 40 it measured 2.07, too close to the multiple to prove anything.
- A longer timeout for AT-39 - it hides the cost (ADR-145, ADR-149), and the median is stable well below 200 round trips.

**Consequences:**
- Good: AT-39 passes on a busy laptop and on a runner as slow as GitHub's, and still fails a command whose own work grows, as the doubled command shows.
- Good: the test never runs much longer than 20 seconds, whatever the load.
- Bad: a regression in the native start-up slows the bare start-up as much as the command, so on a machine too slow for the 50 ms limit AT-39 no longer sees it; the build summary's bare start-up p50 shows it.
- Bad: on a machine too slow for the 50 ms limit the verdict judges the median, so a regression that slows only a few calls in twenty passes there.
- Bad: on a very busy machine the verdict rests on fewer than 200 round trips; at a load of 40 it rested on 92.
- Bad: the multiple was calibrated on one laptop, so the first native jobs on GitHub's runners after Actions return confirm it: the native job's step summary must show a median ratio at or below about 1.6, as the laptop did at every load.

**Revisit when:** The median ratio on GitHub's runners is above 1.6, or the command's start-up changes enough to move the laptop's ratio out of 1.37-1.46.

**Sources:** EDI rewrite specification, sections 5.5 and 17.4 (AT-39, AT-44); ADR-005, ADR-034, ADR-045, ADR-065, ADR-145, ADR-149; `edi-cli/src/test/java/io/github/ythibos/edi/cli/RoundTripBudget.java`, `edi-cli/src/test/java/io/github/ythibos/edi/cli/NativeSmokeIT.java`; `.github/workflows/ci.yml`, step "Report the native smoke test".

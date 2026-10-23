# ADR-172: A native recovery test keeps thirty seconds for its own work and waits for the core's start-ups apart

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Every test keeps the 30-second default timeout (AGENTS.md, ADR-065), and a longer timeout must not hide a cost (ADR-145, ADR-164).
`NativeRecoveryIT` starts the native core before every test, and two of its tests stop it and start it again, to simulate a machine restart or to change the supervision round.
On 22/10/2026, with several native-image builds running beside it and a load average of about 22, the machine-restart test overran the 30 seconds while it waited for the restarted core to record its port; three runs of the same class on the same executables passed straight after, in 50, 37 and 26 seconds.
A stand-in core that sleeps 25 seconds before it starts made all three tests fail on "the native core recorded its port in time".
How long the native core takes to start is the machine's state at that moment, not the behaviour these tests check.

**Decision:** `NativeRecoveryIT` waits up to 60 seconds for each start of the native core, and measures how long it waited.
After each test it asserts that the test's own work, its time less the core's start-ups it waited for, stayed under 30 seconds.
The class's timeout is three minutes and the per-test set-up's 90 seconds, ceilings against a hang rather than budgets.

**Motivation:** The test keeps the thirty seconds every test keeps for what EDI does, and stops failing because the machine was slow to start a process.

**Alternatives considered:**
- A longer timeout for the class alone - it would also hide a slower recovery, which the own-work assertion still catches.
- Fewer core restarts - the machine-restart test must restart the core to be the scenario it names.
- Scaling the budget to a start-up measured before the test, as ADR-164 does for AT-39 - one start-up says little about the next one under a load that changes from second to second.

**Consequences:**
- Good: the class passed with a core that takes 25 seconds to start, and in 24 seconds on a quiet machine.
- Bad: this class catches a slower start-up of the core only once it passes 60 seconds.

**Revisit when:** The native core's start-up gets its own budget, or the other native tests show the same overrun.

**Sources:** ADR-065, ADR-145, ADR-164; `edi-cli/src/test/java/io/github/ythibos/edi/cli/NativeRecoveryIT.java`.
